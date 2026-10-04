// One conditional upsert reserves both limits atomically, across all Worker instances.
// Clock skew must not reset a newer minute bucket back to an older one.
export const RESERVE_QUOTA = `
  INSERT INTO translation_usage(day, minute, requests, minute_requests)
  VALUES (?, ?, 1, 1)
  ON CONFLICT(day) DO UPDATE SET
    requests = translation_usage.requests + 1,
    minute_requests = CASE WHEN excluded.minute > translation_usage.minute
      THEN 1 ELSE translation_usage.minute_requests + 1 END,
    minute = MAX(translation_usage.minute, excluded.minute)
  WHERE translation_usage.requests < 200
    AND (excluded.minute > translation_usage.minute OR translation_usage.minute_requests < 10)
  RETURNING requests
`;
export const PRUNE_QUOTA = 'DELETE FROM translation_usage WHERE day < ?';

export function quotaBuckets(now = Date.now()) {
  return { day: Math.floor(now / 86_400_000), minute: Math.floor(now / 60_000) };
}

export async function reserveD1Quota(database, now = Date.now()) {
  const { day, minute } = quotaBuckets(now);
  // D1 executes batch statements in a transaction; a database failure blocks model calls.
  const results = await database.batch([
    database.prepare(RESERVE_QUOTA).bind(day, minute),
    database.prepare(PRUNE_QUOTA).bind(day - 7),
  ]);
  if (results.some(result => !result.success)) throw new Error('QUOTA_DATABASE_ERROR');
  return results[0].results.length === 1;
}
