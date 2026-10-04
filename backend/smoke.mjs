// Opt-in network check. Persists only status, timing and output-validation flags.
import { writeFileSync } from 'node:fs';

if (process.argv[2] !== '--live' || !process.argv[3]) {
  console.error('Usage: node smoke.mjs --live https://backend.example [report.json]');
  process.exit(2);
}
const base = new URL(process.argv[3]);
if (base.protocol !== 'https:' || base.username || base.password || base.search || base.hash || base.pathname !== '/') {
  throw new Error('Provide a public HTTPS origin only');
}
const report = { checkedAt: new Date().toISOString(), backend: base.origin, checks: [] };

async function check(name, path, expectedStatus, init = {}, validate = () => true) {
  const start = performance.now();
  let result;
  try {
    const response = await fetch(new URL(path, base), { ...init, redirect: 'error', signal: AbortSignal.timeout(25000) });
    const body = await response.json();
    result = { name, status: response.status, ms: Math.round(performance.now() - start),
      passed: response.status === expectedStatus && validate(body) };
  } catch {
    result = { name, ms: Math.round(performance.now() - start), passed: false, error: 'NETWORK_OR_RESPONSE_ERROR' };
  }
  report.checks.push(result);
  console.log(JSON.stringify(result));
}

const post = body => ({ method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
await check('health_configuration_and_database', '/healthz', 200, {}, body => body.status === 'ok');
await check('no_auth_route', '/auth/signup', 404);
await check('wrong_method', '/v1/translate', 405);
await check('invalid_direction', '/v1/translate', 400, post({ text: 'hello', direction: 'OTHER' }));
await check('english_to_hindi_without_credentials', '/v1/translate', 200,
  post({ text: 'How are you?', direction: 'ENGLISH_TO_HINDI' }),
  body => typeof body.translation === 'string' && /[\u0900-\u097f]/u.test(body.translation));
await check('hindi_to_english_without_credentials', '/v1/translate', 200,
  post({ text: 'आप कैसे हैं?', direction: 'HINDI_TO_ENGLISH' }),
  body => typeof body.translation === 'string' && /[a-z]/i.test(body.translation));

const romanized = body => typeof body.translation === 'string' && /[a-z]/i.test(body.translation) &&
  !/[\u0900-\u097f]/u.test(body.translation);
await check('auto_english_direction', '/v1/translate', 200,
  post({ text: 'How are you?', direction: 'AUTO' }),
  body => body.direction === 'ENGLISH_TO_HINDI' && /[\u0900-\u097f]/u.test(body.translation));
await check('auto_hinglish_preserves_negation', '/v1/translate', 200,
  post({ text: 'main kal nahi aa sakta', direction: 'AUTO' }),
  body => body.direction === 'HINGLISH_TO_ENGLISH' && romanized(body) && /not|cannot|can.t|won.t|unable/i.test(body.translation));
await check('auto_mixed_hinglish', '/v1/translate', 200,
  post({ text: 'main meeting mein late aaunga, please wait', direction: 'AUTO' }),
  body => body.direction === 'HINGLISH_TO_ENGLISH' && romanized(body) && /late/i.test(body.translation) && /wait/i.test(body.translation));
await check('english_to_romanized_hindi', '/v1/translate', 200,
  post({ text: 'How are you?', direction: 'ENGLISH_TO_HINGLISH' }),
  body => body.direction === 'ENGLISH_TO_HINGLISH' && romanized(body) && /kaise|kaisey|kaisi/i.test(body.translation));
await check('hindi_to_romanized_hindi_preserves_negation', '/v1/translate', 200,
  post({ text: 'मैं कल नहीं आ सकता।', direction: 'HINDI_TO_HINGLISH' }),
  body => body.direction === 'HINDI_TO_HINGLISH' && romanized(body) && /nahi|nahin|nhi/i.test(body.translation));
await check('hinglish_to_devanagari', '/v1/translate', 200,
  post({ text: 'aap kaise ho?', direction: 'HINGLISH_TO_HINDI' }),
  body => body.direction === 'HINGLISH_TO_HINDI' && /[\u0900-\u097f]/u.test(body.translation));

report.passed = report.checks.every(result => result.passed);
if (process.argv[4]) writeFileSync(process.argv[4], JSON.stringify(report, null, 2) + '\n');
process.exitCode = report.passed ? 0 : 1;
