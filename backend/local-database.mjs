import { DatabaseSync } from 'node:sqlite';
import { mkdirSync, readFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { PRUNE_QUOTA, quotaBuckets, RESERVE_QUOTA } from './quota.mjs';

export function openDatabase(path) {
  if (path !== ':memory:') mkdirSync(dirname(path), { recursive: true });
  const database = new DatabaseSync(path, { timeout: 5000 });
  database.exec('PRAGMA journal_mode=WAL');
  database.exec(readFileSync(new URL('./schema.sql', import.meta.url), 'utf8'));
  const reserve = database.prepare(RESERVE_QUOTA);
  const prune = database.prepare(PRUNE_QUOTA);
  return {
    database,
    reserve(now = Date.now()) {
      const { day, minute } = quotaBuckets(now);
      database.exec('BEGIN IMMEDIATE');
      try {
        const allowed = reserve.get(day, minute) !== undefined;
        prune.run(day - 7);
        database.exec('COMMIT');
        return allowed;
      } catch (failure) {
        database.exec('ROLLBACK');
        throw failure;
      }
    },
    close() { database.close(); },
  };
}
