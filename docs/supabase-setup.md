# HR40 Supabase 与 Web 配置说明

本项目在 **GitHub 云端开发**，不需要本地部署服务器。架构如下：

```text
GitHub 仓库（代码）
    ├── supabase/migrations/   → 通过 CLI 推送到 Supabase 云端
    └── web/                   → 通过 GitHub Actions 部署到 GitHub Pages

Supabase 云端（HR40-Dev）
    ├── 数据库 + RLS
    ├── 用户登录
    └── 存储训练数据

浏览器 / 手机
    └── 直接访问线上地址，无需本机 npm run dev
```

## 已部署内容（HR40-Dev）

- 数据库表：`profiles`、`workout_records`、`heart_rate_samples`、`strength_sets`
- 行级安全（RLS）：用户只能访问自己的数据
- 新用户注册时自动创建 `profiles` 记录

迁移文件：

```text
supabase/migrations/20250606000000_initial_schema.sql
```

## 一、你现在就能用的（无需本地环境）

| 能力 | 在哪里操作 |
|------|------------|
| 看数据库表 | [Supabase Table Editor](https://supabase.com/dashboard/project/erukqyqwzbutwlaerzfn/editor) |
| 建测试用户 | [Supabase Authentication → Users](https://supabase.com/dashboard/project/erukqyqwzbutwlaerzfn/auth/users) |
| 插测试数据 | [Supabase SQL Editor](https://supabase.com/dashboard/project/erukqyqwzbutwlaerzfn/sql/new) |
| 改登录设置 | [Supabase Auth Providers](https://supabase.com/dashboard/project/erukqyqwzbutwlaerzfn/auth/providers) |
| 看 API 密钥 | [Supabase Project Settings → API](https://supabase.com/dashboard/project/erukqyqwzbutwlaerzfn/settings/api) |

**后端已经在云上跑着了**，不依赖你电脑本地是否安装了 Node、Docker 等。

## 二、部署网页（GitHub Actions → GitHub Pages）

仓库已包含自动部署工作流：`.github/workflows/deploy-web.yml`  
合并到 `main` 后，推送 `web/` 变更会自动构建并发布网页。

### 首次启用（在 GitHub 网页上操作，约 3 分钟）

1. 打开仓库 **Settings → Secrets and variables → Actions**
2. 添加两个 Repository secrets：

   | Name | Value |
   |------|-------|
   | `VITE_SUPABASE_URL` | `https://erukqyqwzbutwlaerzfn.supabase.co` |
   | `VITE_SUPABASE_ANON_KEY` | 你的 `sb_publishable_...` 密钥 |

3. **Settings → Pages → Build and deployment**
   - Source 选 **GitHub Actions**
4. 合并 PR 或手动触发 **Actions → Deploy Web to GitHub Pages → Run workflow**

部署成功后访问：

```text
https://tin-zhong.github.io/cursor_bluetooth_igp_hr40/
```

### Supabase 里要配的回调地址

在 [Authentication → URL Configuration](https://supabase.com/dashboard/project/erukqyqwzbutwlaerzfn/auth/url-configuration)：

| 字段 | 值 |
|------|-----|
| Site URL | `https://tin-zhong.github.io/cursor_bluetooth_igp_hr40/` |
| Redirect URLs | `https://tin-zhong.github.io/cursor_bluetooth_igp_hr40/**` |

开发阶段建议 **关闭 Confirm email**，否则注册后要先收验证邮件。

## 三、推送数据库变更（在云端 Agent / CI 中执行）

不需要本地电脑，只要有 Access Token，在任意能跑命令的环境执行：

```bash
export SUPABASE_ACCESS_TOKEN=你的_access_token
npx supabase link --project-ref erukqyqwzbutwlaerzfn
npx supabase db push
```

`SUPABASE_ACCESS_TOKEN` 放在 GitHub Secrets 里，不要写进代码。

## 四、Web 功能（MVP）

- 邮箱注册 / 登录
- 训练记录列表（只读）
- 训练详情：心率曲线、区间统计、力量组、估算卡路里

## 五、插入测试数据

先在 Dashboard 创建用户，再在 SQL Editor 执行（替换 UUID）：

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

然后在网页登录同一账号，即可看到记录。

## 六、密钥说明

| 密钥 | 用途 | 放哪里 |
|------|------|--------|
| `sb_publishable_...` | Web / Android 客户端 | GitHub Secrets（构建用） |
| `service_role` | 服务端管理 | 绝不提交 |
| `SUPABASE_ACCESS_TOKEN` | CLI 推迁移 | GitHub Secrets，用完可撤销 |

## 七、Android 后续接入

1. 增加 `INTERNET` 权限
2. 用 Supabase REST API 或 Android SDK
3. 训练结束后上传 `workout_records` + `heart_rate_samples` + `strength_sets`
4. `local_id` 对应 App 内 session UUID，用于去重

## 八、生产环境

验证完成后建议新建 `HR40-Prod`，不要把 Dev 库直接给正式用户。
