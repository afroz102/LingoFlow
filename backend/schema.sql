-- Aggregate counts only. No users, IP addresses, source text, or translations.
CREATE TABLE IF NOT EXISTS translation_usage (
  day INTEGER PRIMARY KEY,
  minute INTEGER NOT NULL,
  requests INTEGER NOT NULL CHECK (requests > 0),
  minute_requests INTEGER NOT NULL CHECK (minute_requests > 0)
) STRICT;
