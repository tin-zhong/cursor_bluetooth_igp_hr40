-- Add per-plan-item suggested reps for each set.

alter table public.training_plan_items
  add column if not exists suggested_reps int not null default 8
    check (suggested_reps >= 0 and suggested_reps <= 1000);

comment on column public.training_plan_items.suggested_reps is '每组建议完成次数';
