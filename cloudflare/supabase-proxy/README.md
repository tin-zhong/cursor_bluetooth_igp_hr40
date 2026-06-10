# hr40-supabase-proxy（Cloudflare Worker 反向代理）

把 Supabase（`ap-southeast-1` 新加坡）的 HTTP 接口放到 Cloudflare 边缘后面，降低国内访问延迟。
本项目 Web 与 Android Online 端只用 REST + Auth，**未用 Realtime/WebSocket**，纯 HTTP 反代即可。

## 部署 / 更新

```bash
cd cloudflare/supabase-proxy
npx wrangler login        # 首次
npx wrangler deploy
```

Worker 名字 `hr40-supabase-proxy` 已存在于账号中，`deploy` 会原地覆盖。

## 绑定自有域名（国内加速关键）

`*.workers.dev` 在国内常被墙，**必须**绑定一个 DNS 托管在 Cloudflare 的自有域名：

1. 在 `wrangler.toml` 取消 `routes` 注释，把 `api.example.com` 改成你的域名。
2. `npx wrangler deploy`，Cloudflare 会自动建好路由与 DNS（橙云代理默认开启）。
3. 自测：`curl https://api.你的域名/__health` 返回 `ok`。

## 切到代理

- Web：部署环境变量 `NUXT_PUBLIC_SUPABASE_URL=https://api.你的域名`
- Android：构建参数 `-PsupabaseUrl=https://api.你的域名`

匿名 key 不变（仍是 Supabase 的 publishable key，仅地址换成代理域名）。

完整背景与排错见 [`docs/cloudflare-china-acceleration.md`](../../docs/cloudflare-china-acceleration.md)。
