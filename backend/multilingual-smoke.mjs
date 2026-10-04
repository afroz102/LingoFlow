import { writeFile } from 'node:fs/promises';

const origin = new URL(process.argv[2] ?? 'https://lingoflow-backend.lingoflow-backend.workers.dev');
if (origin.protocol !== 'https:' || origin.username || origin.password || origin.search || origin.hash) {
  throw Error('Use a public HTTPS backend origin');
}
const cases = [
  { name: 'auto_roman_hindi_english', text: 'main kal raid mein nahi aa sakta', target: 'en', check: /tomorrow/i },
  { name: 'auto_native_hindi_english', text: 'मैं कल नहीं आ सकता', target: 'en', check: /tomorrow/i },
  { name: 'english_roman_hindi', text: 'I cannot come tomorrow.', target: 'hi-Latn', check: /kal/i },
  { name: 'english_russian', text: 'I cannot join the raid tomorrow.', target: 'ru', check: /\p{Script=Cyrillic}/u },
  { name: 'english_french', text: 'I cannot join the raid tomorrow.', target: 'fr', check: /demain/i },
  { name: 'english_spanish', text: 'I cannot join the raid tomorrow.', target: 'es', check: /mañana/i },
  { name: 'english_german', text: 'I cannot join the raid tomorrow.', target: 'de', check: /morgen/i },
  { name: 'english_japanese', text: 'I cannot join the raid tomorrow.', target: 'ja', check: /[\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}]/u },
  { name: 'english_roman_bengali', text: 'I cannot come tomorrow.', target: 'bn-Latn', check: /kal/i },
  { name: 'auto_korean_english', text: '내일은 올 수 없어요.', target: 'en', check: /tomorrow/i },
];
const results = [];
for (const sample of cases) {
  const started = performance.now();
  const response = await fetch(new URL('/v1/translate', origin), {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, signal: AbortSignal.timeout(30_000),
    body: JSON.stringify({ text: sample.text, direction: 'MULTILINGUAL', sourceLanguage: 'auto', targetLanguage: sample.target }),
  });
  const body = await response.json();
  const pass = response.status === 200 && body.direction === 'MULTILINGUAL' && body.sourceLanguage === 'auto' &&
    body.targetLanguage === sample.target && typeof body.translation === 'string' && sample.check.test(body.translation) &&
    (!sample.target.endsWith('-Latn') || !/[^\p{Script=Latin}\p{Script=Common}\p{Script=Inherited}]/u.test(body.translation));
  const result = { case: sample.name, target: sample.target, status: response.status,
    duration_ms: Math.round(performance.now() - started), pass };
  results.push(result); console.log(JSON.stringify(result));
}
const record = { recorded_at: new Date().toISOString(), scope: 'Synthetic hosted multilingual transport/script smoke; not human translation-quality evaluation',
  model_calls: cases.length, results, passed: results.every(result => result.pass) };
if (process.argv[3]) await writeFile(process.argv[3], JSON.stringify(record, null, 2) + '\n');
if (!record.passed) process.exitCode = 1;
