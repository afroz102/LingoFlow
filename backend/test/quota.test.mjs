import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { Worker } from 'node:worker_threads';
import { openDatabase } from '../local-database.mjs';
import { reserveD1Quota } from '../quota.mjs';

const minute = 20_000 * 86_400_000;

test('minute and day limits persist across database reopening', () => {
  const directory = mkdtempSync(join(tmpdir(), 'lingoflow-quota-'));
  const path = join(directory, 'usage.sqlite');
  let store;
  try {
    store = openDatabase(path);
    for (let i = 0; i < 10; i++) assert.equal(store.reserve(minute), true);
    assert.equal(store.reserve(minute), false);
    store.close();
    store = openDatabase(path);
    assert.equal(store.reserve(minute), false);
    for (let bucket = 1; bucket < 20; bucket++) {
      for (let i = 0; i < 10; i++) assert.equal(store.reserve(minute + bucket * 60_000), true);
    }
    assert.equal(store.reserve(minute + 20 * 60_000), false);
    assert.equal(store.database.prepare('SELECT requests FROM translation_usage').get().requests, 200);
    assert.equal(store.reserve(minute + 86_400_000), true);
  } finally { store?.close(); rmSync(directory, { recursive: true, force: true }); }
});

test('clock skew cannot reset a full minute and old buckets are pruned', () => {
  const store = openDatabase(':memory:');
  try {
    for (let i = 0; i < 10; i++) store.reserve(minute + 60_000);
    assert.equal(store.reserve(minute), false);
    assert.equal(store.reserve(minute + 2 * 60_000), true);
    assert.equal(store.reserve(minute + 8 * 86_400_000), true);
    assert.equal(store.database.prepare('SELECT COUNT(*) AS count FROM translation_usage').get().count, 1);
  } finally { store.close(); }
});

test('concurrent connections share the atomic minute cap', async () => {
  const directory = mkdtempSync(join(tmpdir(), 'lingoflow-concurrent-'));
  const path = join(directory, 'usage.sqlite');
  const seed = openDatabase(path);
  seed.close();
  try {
    const module = new URL('../local-database.mjs', import.meta.url).href;
    const counts = await Promise.all(Array.from({ length: 4 }, () => new Promise((resolve, reject) => {
      const worker = new Worker(`
        const { parentPort, workerData } = require('node:worker_threads');
        import(workerData.module).then(({ openDatabase }) => {
          const store = openDatabase(workerData.path);
          let allowed = 0;
          for (let i = 0; i < 20; i++) if (store.reserve(workerData.minute)) allowed++;
          store.close(); parentPort.postMessage(allowed);
        });
      `, { eval: true, workerData: { module, path, minute } });
      worker.once('message', resolve); worker.once('error', reject);
      worker.once('exit', code => { if (code !== 0) reject(Error(`worker exit ${code}`)); });
    })));
    assert.equal(counts.reduce((a, b) => a + b, 0), 10);
  } finally { rmSync(directory, { recursive: true, force: true }); }
});

test('D1 uses the same SQL inside one atomic batch and fails closed on database errors', async () => {
  let batches = 0;
  const database = {
    prepare: sql => ({ bind: (...args) => ({ sql, args }) }),
    batch: async statements => {
      batches++;
      assert.equal(statements.length, 2);
      assert.equal(statements[0].args[0], 20_000);
      return [{ success: true, results: [{ requests: 1 }] }, { success: true, results: [] }];
    },
  };
  assert.equal(await reserveD1Quota(database, minute), true);
  assert.equal(batches, 1);
  database.batch = async () => [{ success: true, results: [] }, { success: true, results: [] }];
  assert.equal(await reserveD1Quota(database, minute), false);
  database.batch = async () => [{ success: false, results: [] }];
  await assert.rejects(() => reserveD1Quota(database, minute));
});
