create table public.exercises (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  name text not null check (char_length(trim(name)) > 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create unique index idx_exercises_user_name_active
  on public.exercises (user_id, lower(trim(name)))
  where deleted_at is null;

create index idx_exercises_user_updated on public.exercises (user_id, updated_at desc);

alter table public.exercises enable row level security;

create policy exercises_select_own on public.exercises
  for select using (auth.uid() = user_id and deleted_at is null);

create policy exercises_insert_own on public.exercises
  for insert with check (auth.uid() = user_id);

create policy exercises_update_own on public.exercises
  for update using (auth.uid() = user_id);

create policy exercises_delete_own on public.exercises
  for delete using (auth.uid() = user_id);

grant select, insert, update, delete on public.exercises to authenticated;

create trigger exercises_updated_at
  before update on public.exercises
  for each row
  execute function public.set_updated_at();

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

  delete from public.heart_rate_samples where user_id = current_user_id;
  delete from public.strength_sets where user_id = current_user_id;
  delete from public.workout_records where user_id = current_user_id;
  delete from public.exercises where user_id = current_user_id;
  delete from public.profiles where id = current_user_id;
  delete from auth.users where id = current_user_id;
end;
$$;

revoke all on function public.delete_account() from public;
grant execute on function public.delete_account() to authenticated;
