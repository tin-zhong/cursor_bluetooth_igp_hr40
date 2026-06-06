-- HR40 cloud schema: profiles, workouts, heart rate samples, strength sets.

create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  name text not null default '',
  sex text not null default 'male' check (sex in ('male', 'female')),
  age int not null default 30,
  height_cm int not null default 170,
  weight_kg numeric(5, 2) not null default 70.00,
  created_at_ms bigint not null default (extract(epoch from now()) * 1000)::bigint,
  updated_at timestamptz not null default now()
);

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles (id, name)
  values (new.id, coalesce(new.raw_user_meta_data ->> 'name', ''));
  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row
  execute function public.handle_new_user();

create table public.workout_records (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  local_id text not null,
  device_id text,
  start_millis bigint not null,
  end_millis bigint not null,
  workout_type text not null check (workout_type in ('aerobic', 'strength')),
  deleted_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, local_id)
);

create table public.heart_rate_samples (
  id bigserial primary key,
  workout_id uuid not null references public.workout_records (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  timestamp_millis bigint not null,
  bpm int not null check (bpm > 0 and bpm < 300),
  contact_supported boolean not null default false,
  contact_detected boolean not null default false,
  energy_expended_kj int,
  rr_interval_count int not null default 0
);

create table public.strength_sets (
  id bigserial primary key,
  workout_id uuid not null references public.workout_records (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  exercise_name text not null,
  weight numeric(8, 2) not null,
  weight_unit text not null default 'kg' check (weight_unit in ('kg', 'lb')),
  reps int not null check (reps > 0),
  timestamp_millis bigint not null
);

create index idx_workouts_user_start on public.workout_records (user_id, start_millis desc);
create index idx_workouts_user_updated on public.workout_records (user_id, updated_at);
create index idx_hr_workout_time on public.heart_rate_samples (workout_id, timestamp_millis);
create index idx_strength_workout on public.strength_sets (workout_id);

alter table public.profiles enable row level security;
alter table public.workout_records enable row level security;
alter table public.heart_rate_samples enable row level security;
alter table public.strength_sets enable row level security;

create policy profiles_select_own on public.profiles
  for select using (auth.uid() = id);

create policy profiles_update_own on public.profiles
  for update using (auth.uid() = id);

create policy profiles_insert_own on public.profiles
  for insert with check (auth.uid() = id);

create policy workouts_select_own on public.workout_records
  for select using (auth.uid() = user_id and deleted_at is null);

create policy workouts_insert_own on public.workout_records
  for insert with check (auth.uid() = user_id);

create policy workouts_update_own on public.workout_records
  for update using (auth.uid() = user_id);

create policy workouts_delete_own on public.workout_records
  for delete using (auth.uid() = user_id);

create policy hr_select_own on public.heart_rate_samples
  for select using (auth.uid() = user_id);

create policy hr_insert_own on public.heart_rate_samples
  for insert with check (auth.uid() = user_id);

create policy hr_delete_own on public.heart_rate_samples
  for delete using (auth.uid() = user_id);

create policy strength_select_own on public.strength_sets
  for select using (auth.uid() = user_id);

create policy strength_insert_own on public.strength_sets
  for insert with check (auth.uid() = user_id);

create policy strength_delete_own on public.strength_sets
  for delete using (auth.uid() = user_id);

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create trigger workout_records_updated_at
  before update on public.workout_records
  for each row
  execute function public.set_updated_at();
