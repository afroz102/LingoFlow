-- Persist quota metadata only. Source text and translations never enter Postgres.
create table if not exists public.translation_usage (
  scope text not null,
  bucket timestamptz not null,
  requests integer not null default 0 check (requests >= 0),
  primary key (scope, bucket)
);
alter table public.translation_usage enable row level security;
revoke all on public.translation_usage from public, anon, authenticated;

create or replace function public.consume_translation_quota(p_user_id uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_minute timestamptz := date_trunc('minute', now());
  current_day timestamptz := date_trunc('day', now() at time zone 'UTC') at time zone 'UTC';
  user_scope text := 'user:' || p_user_id::text;
  used integer;
begin
  if p_user_id is null then return false; end if;
  -- One lock serializes all reservations, including concurrent Edge Function instances.
  perform pg_advisory_xact_lock(782419004);
  delete from public.translation_usage where bucket < current_day;
  select requests into used from public.translation_usage where scope = 'global:day' and bucket = current_day;
  if coalesce(used, 0) >= 200 then return false; end if;
  select requests into used from public.translation_usage where scope = 'global:minute' and bucket = current_minute;
  if coalesce(used, 0) >= 10 then return false; end if;
  select requests into used from public.translation_usage where scope = user_scope and bucket = current_minute;
  if coalesce(used, 0) >= 5 then return false; end if;
  insert into public.translation_usage(scope, bucket, requests)
  values ('global:day', current_day, 1), ('global:minute', current_minute, 1), (user_scope, current_minute, 1)
  on conflict (scope, bucket) do update set requests = public.translation_usage.requests + 1;
  return true;
end;
$$;
revoke all on function public.consume_translation_quota(uuid) from public, anon, authenticated;
grant execute on function public.consume_translation_quota(uuid) to service_role;
