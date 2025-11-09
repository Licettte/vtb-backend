CREATE TABLE IF NOT EXISTS obligations (
  id varchar(80) primary key,
  user_id bigint not null,
  source varchar(32) not null,
  merchant_key varchar(160) not null,
  title varchar(200) not null,
  category varchar(40) not null,
  currency varchar(8) not null,
  avg_amount_minor bigint not null,
  periodicity varchar(16) not null,
  typical_day int,
  next_due_date date not null,
  repeats int not null,
  confidence double precision not null,
  created_at timestamp not null
);