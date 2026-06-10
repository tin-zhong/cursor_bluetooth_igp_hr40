-- HR40 表结构导出（用于迁移到阿里云 RDS Supabase）
-- 目标数据库：supabase_db （内含 auth / storage 等 schema）
-- 来源：Supabase 项目 erukqyqwzbutwlaerzfn (region ap-southeast-1, PostgreSQL 17)
-- 本文件为 supabase/migrations/*.sql 的合并最终态，与线上结构一致。
-- 用法：psql "<TARGET_URL>/supabase_db" -f supabase/export/schema.sql

begin;

-- ========================= profiles =========================
create table if not exists public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  name text not null default '',
  sex text not null default 'male' check (sex in ('male', 'female')),
  age int not null default 30,
  height_cm int not null default 170,
  weight_kg numeric(5, 2) not null default 70.00,
  created_at_ms bigint not null default (extract(epoch from now()) * 1000)::bigint,
  updated_at timestamptz not null default now(),
  profile_completed boolean not null default false
);

-- ========================= workout_records =========================
create table if not exists public.workout_records (
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

-- ========================= heart_rate_samples =========================
create table if not exists public.heart_rate_samples (
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

-- ========================= strength_sets =========================
create table if not exists public.strength_sets (
  id bigserial primary key,
  workout_id uuid not null references public.workout_records (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  exercise_name text not null,
  weight numeric(8, 2) not null,
  weight_unit text not null default 'kg' check (weight_unit in ('kg', 'lb')),
  reps int not null check (reps > 0),
  timestamp_millis bigint not null
);

-- ========================= exercises =========================
create table if not exists public.exercises (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  name text not null check (char_length(trim(name)) > 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

-- ========================= training_plan_items =========================
create table if not exists public.training_plan_items (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  exercise_id uuid not null references public.exercises (id) on delete cascade,
  planned_sets int not null default 1 check (planned_sets >= 0 and planned_sets <= 100),
  position int not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, exercise_id)
);

-- ========================= 索引 =========================
create index if not exists idx_workouts_user_start on public.workout_records (user_id, start_millis desc);
create index if not exists idx_workouts_user_updated on public.workout_records (user_id, updated_at);
create index if not exists idx_hr_workout_time on public.heart_rate_samples (workout_id, timestamp_millis);
create index if not exists idx_strength_workout on public.strength_sets (workout_id);
create unique index if not exists idx_exercises_user_name_active
  on public.exercises (user_id, lower(trim(name))) where deleted_at is null;
create index if not exists idx_exercises_user_updated on public.exercises (user_id, updated_at desc);
create index if not exists idx_plan_items_user_position on public.training_plan_items (user_id, position);

-- ========================= 函数与触发器 =========================
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

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row
  execute function public.handle_new_user();

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists workout_records_updated_at on public.workout_records;
create trigger workout_records_updated_at
  before update on public.workout_records
  for each row execute function public.set_updated_at();

drop trigger if exists exercises_updated_at on public.exercises;
create trigger exercises_updated_at
  before update on public.exercises
  for each row execute function public.set_updated_at();

drop trigger if exists training_plan_items_updated_at on public.training_plan_items;
create trigger training_plan_items_updated_at
  before update on public.training_plan_items
  for each row execute function public.set_updated_at();

-- 注销账户：删除当前用户的全部数据及 auth 账号
create or replace function public.delete_account()
returns void
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  current_user_id uuid := auth.uid();
begin
  if current_user_id is null then
    raise exception 'Not authenticated';
  end if;

  delete from public.training_plan_items where user_id = current_user_id;
  delete from public.heart_rate_samples where user_id = current_user_id;
  delete from public.strength_sets where user_id = current_user_id;
  delete from public.workout_records where user_id = current_user_id;
  delete from public.exercises where user_id = current_user_id;
  delete from public.profiles where id = current_user_id;
  delete from auth.users where id = current_user_id;
end;
$$;

revoke all on function public.delete_account() from public;

-- ========================= 行级安全 (RLS) =========================
alter table public.profiles enable row level security;
alter table public.workout_records enable row level security;
alter table public.heart_rate_samples enable row level security;
alter table public.strength_sets enable row level security;
alter table public.exercises enable row level security;
alter table public.training_plan_items enable row level security;

drop policy if exists profiles_select_own on public.profiles;
create policy profiles_select_own on public.profiles for select using (auth.uid() = id);
drop policy if exists profiles_update_own on public.profiles;
create policy profiles_update_own on public.profiles for update using (auth.uid() = id);
drop policy if exists profiles_insert_own on public.profiles;
create policy profiles_insert_own on public.profiles for insert with check (auth.uid() = id);

drop policy if exists workouts_select_own on public.workout_records;
create policy workouts_select_own on public.workout_records for select using (auth.uid() = user_id and deleted_at is null);
drop policy if exists workouts_insert_own on public.workout_records;
create policy workouts_insert_own on public.workout_records for insert with check (auth.uid() = user_id);
drop policy if exists workouts_update_own on public.workout_records;
create policy workouts_update_own on public.workout_records for update using (auth.uid() = user_id);
drop policy if exists workouts_delete_own on public.workout_records;
create policy workouts_delete_own on public.workout_records for delete using (auth.uid() = user_id);

drop policy if exists hr_select_own on public.heart_rate_samples;
create policy hr_select_own on public.heart_rate_samples for select using (auth.uid() = user_id);
drop policy if exists hr_insert_own on public.heart_rate_samples;
create policy hr_insert_own on public.heart_rate_samples for insert with check (auth.uid() = user_id);
drop policy if exists hr_delete_own on public.heart_rate_samples;
create policy hr_delete_own on public.heart_rate_samples for delete using (auth.uid() = user_id);

drop policy if exists strength_select_own on public.strength_sets;
create policy strength_select_own on public.strength_sets for select using (auth.uid() = user_id);
drop policy if exists strength_insert_own on public.strength_sets;
create policy strength_insert_own on public.strength_sets for insert with check (auth.uid() = user_id);
drop policy if exists strength_delete_own on public.strength_sets;
create policy strength_delete_own on public.strength_sets for delete using (auth.uid() = user_id);

drop policy if exists exercises_select_own on public.exercises;
create policy exercises_select_own on public.exercises for select using (auth.uid() = user_id and deleted_at is null);
drop policy if exists exercises_insert_own on public.exercises;
create policy exercises_insert_own on public.exercises for insert with check (auth.uid() = user_id);
drop policy if exists exercises_update_own on public.exercises;
create policy exercises_update_own on public.exercises for update using (auth.uid() = user_id);
drop policy if exists exercises_delete_own on public.exercises;
create policy exercises_delete_own on public.exercises for delete using (auth.uid() = user_id);

drop policy if exists plan_items_select_own on public.training_plan_items;
create policy plan_items_select_own on public.training_plan_items for select using (auth.uid() = user_id);
drop policy if exists plan_items_insert_own on public.training_plan_items;
create policy plan_items_insert_own on public.training_plan_items for insert with check (auth.uid() = user_id);
drop policy if exists plan_items_update_own on public.training_plan_items;
create policy plan_items_update_own on public.training_plan_items for update using (auth.uid() = user_id);
drop policy if exists plan_items_delete_own on public.training_plan_items;
create policy plan_items_delete_own on public.training_plan_items for delete using (auth.uid() = user_id);

-- ========================= 授权 (PostgREST 需要显式 grant) =========================
grant usage on schema public to anon, authenticated;
grant select, insert, update, delete on public.profiles to authenticated;
grant select, insert, update, delete on public.workout_records to authenticated;
grant select, insert, delete on public.heart_rate_samples to authenticated;
grant select, insert, delete on public.strength_sets to authenticated;
grant select, insert, update, delete on public.exercises to authenticated;
grant select, insert, update, delete on public.training_plan_items to authenticated;
grant usage, select on all sequences in schema public to authenticated;
grant execute on function public.delete_account() to authenticated;

commit;
