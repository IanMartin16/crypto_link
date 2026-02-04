create table if not exists cryptolink_stripe_events (
  event_id varchar(128) primary key,
  created_at timestamptz not null default now()
);
