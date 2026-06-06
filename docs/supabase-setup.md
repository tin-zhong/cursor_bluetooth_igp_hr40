# HR40 Supabase 与 Web 配置说明

本项目已接入 Supabase 云端后端，并附带一个只读 Web 页面，用于跨设备查看训练记录。

## 已部署内容（HR40-Dev）

- 数据库表：`profiles`、`workout_records`、`heart_rate_samples`、`strength_sets`
- 行级安全（RLS）：用户只能访问自己的数据
- 新用户注册时自动创建 `profiles` 记录

迁移文件位于：

```text
supabase/migrations/20250606000000_initial_schema.sql
```

## 本地推送数据库变更

如需把新的 SQL 迁移推送到 Supabase：

```bash
export SUPABASE_ACCESS_TOKEN=你的_access_token
npx supabase link --project-ref erukqyqwzbutwlaerzfn
npx supabase db push
```

注意：`SUPABASE_ACCESS_TOKEN` 是账号级密钥，不要提交到 Git。

## Dashboard 必做设置

在 [Supabase Dashboard](https://supabase.com/dashboard/project/erukqyqwzbutwlaerzfn) 中确认：

1. **Authentication → Providers → Email**：已开启
2. **开发阶段**可将 **Confirm email** 暂时关闭，方便测试
3. **Authentication → URL Configuration**：
   - Site URL：`http://localhost:5173`
   - Redirect URLs：`http://localhost:5173/**`
4. 部署网页后，把正式域名加入 Redirect URLs

## Web 本地运行

```bash
cd web
cp .env.example .env
```

编辑 `.env`：

```env
VITE_SUPABASE_URL=https://erukqyqwzbutwlaerzfn.supabase.co
VITE_SUPABASE_ANON_KEY=你的_publishable_key
```

然后：

```bash
npm install
npm run dev
```

浏览器打开 `http://localhost:5173`。

## Web 功能范围（MVP）

- 邮箱注册 / 登录
- 训练记录列表（只读）
- 训练详情：心率曲线、区间统计、力量组、估算卡路里

## 插入测试数据

可先在 Dashboard → Authentication → Users 创建测试用户，再执行 SQL（把 UUID 换成该用户 ID）：

```sql
insert into public.workout_records (
  user_id, local_id, start_millis, end_millis, workout_type
) values (
  '你的用户uuid',
  'demo-session-001',
  extract(epoch from now() - interval '40 minutes') * 1000,
  extract(epoch from now()) * 1000,
  'aerobic'
) returning id;
```

拿到 `id` 后插入心率样本：

```sql
insert into public.heart_rate_samples (
  workout_id, user_id, timestamp_millis, bpm
)
select
  '你的workout_uuid',
  '你的用户uuid',
  extract(epoch from now() - interval '1 minute' * gs) * 1000,
  110 + (gs % 25)
from generate_series(0, 30) as gs;
```

## 密钥说明

| 密钥 | 用途 | 能否提交 Git |
|------|------|--------------|
| `sb_publishable_...` | Web / Android 客户端 | 可以（仍建议放 `.env`） |
| `service_role` / Secret keys | 服务端管理 | 绝对不行 |
| `SUPABASE_ACCESS_TOKEN` | CLI 推送迁移 | 绝对不行 |

## Android 后续接入建议

1. 增加 `INTERNET` 权限
2. 引入 Supabase Android SDK 或 REST API
3. 训练结束后上传：
   - `workout_records`（`local_id` = App 内 session UUID）
   - `heart_rate_samples`
   - `strength_sets`
4. 删除训练时设置 `deleted_at`，不要只删本地

## 生产环境

验证完成后建议新建 `HR40-Prod` 项目，不要把开发库直接用于正式用户。
