-- PostgREST requires explicit grants in addition to RLS policies.

grant usage on schema public to anon, authenticated;

grant select, insert, update, delete on public.profiles to authenticated;
grant select, insert, update, delete on public.workout_records to authenticated;
grant select, insert, delete on public.heart_rate_samples to authenticated;
grant select, insert, delete on public.strength_sets to authenticated;

grant usage, select on all sequences in schema public to authenticated;
