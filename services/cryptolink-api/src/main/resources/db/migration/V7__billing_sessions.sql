create table if not exists cryptolink_billing_sessions (
  session_id      varchar(255) primary key,
  subscription_id varchar(255),
  email           varchar(320),
  plan            varchar(32) not null,
  api_key         varchar(128) not null,
  created_at      timestamptz not null default now()
);

create index if not exists idx_billing_sessions_email on cryptolink_billing_sessions(email);
