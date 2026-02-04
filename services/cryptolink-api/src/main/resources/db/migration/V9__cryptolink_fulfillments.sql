create table if not exists cryptolink_fulfillments (
  id bigserial primary key,
  created_at timestamptz not null default now(),
  email text not null,
  plan text not null,
  api_key text not null,
  source text not null,         -- "stripe" | "manual" | "rotate"
  event_id text null,
  session_id text null,
  email_status text not null default 'PENDING',  -- PENDING | SENT | FAILED
  email_error text null
);

create index if not exists idx_fulfill_email_created on cryptolink_fulfillments(email, created_at desc);
create index if not exists idx_fulfill_api_key on cryptolink_fulfillments(api_key);
