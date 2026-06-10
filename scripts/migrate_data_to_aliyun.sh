#!/usr/bin/env bash
# 把老 Supabase（新加坡）中"聪"账号的全部训练数据迁移到阿里云 RDS Supabase。
#
# 前提：
#   1. 目标库已执行 supabase/export/schema.sql（表结构 + RLS + 函数已就绪）
#   2. 你已在新实例上注册了新账号，并拿到其 user id（NEW_UID）
#   3. 本机装有 PostgreSQL 17+ 客户端（psql）
#
# 用法：
#   export SOURCE_DB_URL='postgresql://postgres.erukqyqwzbutwlaerzfn:<密码>@<session-pooler主机>:5432/postgres'
#   export TARGET_DB_URL='postgresql://<高权限账号>:<密码>@<RDS地址>:5432/supabase_db'
#   export NEW_UID='<新实例上注册账号的 uuid>'
#   bash scripts/migrate_data_to_aliyun.sh
#
# 注意：
#   - SOURCE_DB_URL 必须用 Supabase Dashboard -> Database -> Session pooler 的
#     IPv4 连接串（直连地址是 IPv6，阿里云 ECS 通常连不上）
#   - 脚本可重复执行：导入前会清掉目标库中 NEW_UID 名下的旧数据

set -euo pipefail

OLD_UID='6b0accb1-06fb-490c-ad83-db8f27109810'  # 老库中"聪"(2849699894@qq.com)

: "${SOURCE_DB_URL:?请先 export SOURCE_DB_URL（老 Supabase 的 Session pooler 连接串）}"
: "${TARGET_DB_URL:?请先 export TARGET_DB_URL（阿里云 RDS 的 supabase_db 连接串）}"
: "${NEW_UID:?请先 export NEW_UID（新实例上注册账号的 uuid）}"

if ! [[ "$NEW_UID" =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ ]]; then
  echo "NEW_UID 不是合法的 uuid: $NEW_UID" >&2
  exit 1
fi

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT
echo "临时目录: $WORKDIR"

echo "==> 1/4 校验目标账号存在"
FOUND=$(psql "$TARGET_DB_URL" -X -t -A -v ON_ERROR_STOP=1 \
  -c "select count(*) from auth.users where id = '$NEW_UID';")
if [ "$FOUND" != "1" ]; then
  echo "目标库 auth.users 中找不到 $NEW_UID，请先在新实例注册账号" >&2
  exit 1
fi

echo "==> 2/4 从老库导出（仅 OLD_UID 名下数据）"
psql "$SOURCE_DB_URL" -X -v ON_ERROR_STOP=1 <<SQL
\\copy (select id, name, sex, age, height_cm, weight_kg, created_at_ms, updated_at, profile_completed from public.profiles where id = '$OLD_UID') to '$WORKDIR/profiles.csv' with (format csv)
\\copy (select id, user_id, name, created_at, updated_at, deleted_at from public.exercises where user_id = '$OLD_UID') to '$WORKDIR/exercises.csv' with (format csv)
\\copy (select id, user_id, local_id, device_id, start_millis, end_millis, workout_type, deleted_at, created_at, updated_at from public.workout_records where user_id = '$OLD_UID') to '$WORKDIR/workout_records.csv' with (format csv)
\\copy (select id, user_id, exercise_id, planned_sets, position, created_at, updated_at from public.training_plan_items where user_id = '$OLD_UID') to '$WORKDIR/training_plan_items.csv' with (format csv)
\\copy (select id, workout_id, user_id, timestamp_millis, bpm, contact_supported, contact_detected, energy_expended_kj, rr_interval_count from public.heart_rate_samples where user_id = '$OLD_UID') to '$WORKDIR/heart_rate_samples.csv' with (format csv)
\\copy (select id, workout_id, user_id, exercise_name, weight, weight_unit, reps, timestamp_millis from public.strength_sets where user_id = '$OLD_UID') to '$WORKDIR/strength_sets.csv' with (format csv)
SQL
wc -l "$WORKDIR"/*.csv

echo "==> 3/4 把 user id 替换为 NEW_UID"
sed -i "s/$OLD_UID/$NEW_UID/g" "$WORKDIR"/*.csv

echo "==> 4/4 导入目标库（先清空 NEW_UID 名下旧数据，再按外键顺序导入）"
psql "$TARGET_DB_URL" -X -v ON_ERROR_STOP=1 <<SQL
begin;
delete from public.training_plan_items where user_id = '$NEW_UID';
delete from public.heart_rate_samples  where user_id = '$NEW_UID';
delete from public.strength_sets       where user_id = '$NEW_UID';
delete from public.workout_records     where user_id = '$NEW_UID';
delete from public.exercises           where user_id = '$NEW_UID';
delete from public.profiles            where id      = '$NEW_UID';
\\copy public.profiles (id, name, sex, age, height_cm, weight_kg, created_at_ms, updated_at, profile_completed) from '$WORKDIR/profiles.csv' with (format csv)
\\copy public.exercises (id, user_id, name, created_at, updated_at, deleted_at) from '$WORKDIR/exercises.csv' with (format csv)
\\copy public.workout_records (id, user_id, local_id, device_id, start_millis, end_millis, workout_type, deleted_at, created_at, updated_at) from '$WORKDIR/workout_records.csv' with (format csv)
\\copy public.training_plan_items (id, user_id, exercise_id, planned_sets, position, created_at, updated_at) from '$WORKDIR/training_plan_items.csv' with (format csv)
\\copy public.heart_rate_samples (id, workout_id, user_id, timestamp_millis, bpm, contact_supported, contact_detected, energy_expended_kj, rr_interval_count) from '$WORKDIR/heart_rate_samples.csv' with (format csv)
\\copy public.strength_sets (id, workout_id, user_id, exercise_name, weight, weight_unit, reps, timestamp_millis) from '$WORKDIR/strength_sets.csv' with (format csv)
select setval('public.heart_rate_samples_id_seq', (select coalesce(max(id), 1) from public.heart_rate_samples));
select setval('public.strength_sets_id_seq',      (select coalesce(max(id), 1) from public.strength_sets));
commit;
SQL

echo "==> 校验目标库行数"
psql "$TARGET_DB_URL" -X -v ON_ERROR_STOP=1 -c "
select 'profiles' t, count(*) n from public.profiles where id = '$NEW_UID'
union all select 'workout_records',     count(*) from public.workout_records     where user_id = '$NEW_UID'
union all select 'heart_rate_samples',  count(*) from public.heart_rate_samples  where user_id = '$NEW_UID'
union all select 'strength_sets',       count(*) from public.strength_sets       where user_id = '$NEW_UID'
union all select 'exercises',           count(*) from public.exercises           where user_id = '$NEW_UID'
union all select 'training_plan_items', count(*) from public.training_plan_items where user_id = '$NEW_UID'
order by t;"

echo "完成。期望: workouts=9, hr_samples=6202, strength_sets=16, exercises=4, plan_items=4"
