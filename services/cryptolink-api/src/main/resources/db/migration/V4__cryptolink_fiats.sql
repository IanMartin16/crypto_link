create table if not exists cryptolink_fiats (
  code       varchar(8) primary key,
  active     boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

insert into cryptolink_fiats (code, active) values
  ('USD', true),
  ('MXN', true),
  ('EUR', true)
on conflict (code) do nothing;
