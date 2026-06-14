-- Stripe fulfillment hardening

alter table cryptolink_fulfillments
  add column if not exists customer_id text,
  add column if not exists subscription_id text,
  add column if not exists price_id text,
  add column if not exists product_id text,
  add column if not exists subscription_status text,
  add column if not exists cancel_at_period_end boolean not null default false,
  add column if not exists cancellation_scheduled boolean not null default false,
  add column if not exists current_period_end timestamptz,
  add column if not exists stripe_cancel_at timestamptz,
  add column if not exists revoked_at timestamptz,
  add column if not exists updated_at timestamptz not null default now();

alter table cryptolink_stripe_events
  add column if not exists status varchar(64) not null default 'PROCESSING',
  add column if not exists processed_at timestamptz,
  add column if not exists detail varchar(255);

create unique index if not exists ux_cryptolink_fulfillments_session
  on cryptolink_fulfillments(session_id)
  where session_id is not null;

create unique index if not exists ux_cryptolink_fulfillments_subscription
  on cryptolink_fulfillments(subscription_id)
  where subscription_id is not null;

create unique index if not exists ux_cryptolink_fulfillments_api_key
  on cryptolink_fulfillments(api_key);

drop index if exists idx_fulfill_api_key;

update cryptolink_stripe_events
set
  status = 'COMPLETED',
  processed_at = coalesce(processed_at, created_at)
where processed_at is null;