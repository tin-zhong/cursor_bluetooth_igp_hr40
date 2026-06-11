-- Add per-plan-item rest time (seconds) between sets.

alter table public.training_plan_items
  add column rest_seconds int not null default 60
    check (rest_seconds >= 0 and rest_seconds <= 3600);
