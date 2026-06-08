-- Per-user strength training plan: ordered list of (exercise, planned sets).

create table public.training_plan_items (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  exercise_id uuid not null references public.exercises (id) on delete cascade,
  planned_sets int not null default 1 check (planned_sets >= 0 and planned_sets <= 100),
  position int not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, exercise_id)
);

create index idx_plan_items_user_position
  on public.training_plan_items (user_id, position);

alter table public.training_plan_items enable row level security;

create policy plan_items_select_own on public.training_plan_items
  for select using (auth.uid() = user_id);

create policy plan_items_insert_own on public.training_plan_items
  for insert with check (auth.uid() = user_id);

create policy plan_items_update_own on public.training_plan_items
  for update using (auth.uid() = user_id);

create policy plan_items_delete_own on public.training_plan_items
  for delete using (auth.uid() = user_id);

grant select, insert, update, delete on public.training_plan_items to authenticated;

create trigger training_plan_items_updated_at
  before update on public.training_plan_items
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
grant execute on function public.delete_account() to authenticated;
