# 国内访问加速（Cloudflare）

降低 HR40 Online 在国内访问的高延迟。本文是一份可照着执行的 runbook。

## 现状与瓶颈

| 组件 | 现在 | 国内问题 |
|------|------|----------|
| 前端 Web（Nuxt SPA） | GitHub Pages | GitHub Pages 在国内被限速/不稳定 |
| 后端 Supabase | `erukqyqwzbutwlaerzfn.supabase.co`（新加坡 ap-southeast-1） | 直连跨境，TLS 握手/每次请求往返延迟高，`*.supabase.co` 偶发被干扰 |
| Android Online | 直连同一 Supabase 地址 | 同上 |

两端都**只用 REST(`/rest/v1`) + Auth(`/auth/v1`)**，没有 Realtime/WebSocket，所以一个纯 HTTP 反向代理即可覆盖全部流量。

## 方案

1. **Supabase API → Cloudflare Worker 反向代理**：国内连 Cloudflare 边缘，由 Cloudflare 骨干网回源 Supabase，减少跨境握手往返，并绕开 `*.supabase.co` 的不稳定。Worker 已在仓库 `cloudflare/supabase-proxy/`。
2. **前端 → Cloudflare Pages**：替代 GitHub Pages，配合自有域名国内更快。工作流见 `.github/workflows/deploy-web-cloudflare.yml`。

> ⚠️ **关键前提：自有域名。** `*.workers.dev` 和 `*.pages.dev` 在国内常被墙，**必须**用一个 DNS 托管在 Cloudflare 的自有域名（橙云代理开启）才能稳定加速。还没有域名时，下面的代码/配置已就绪，拿到域名后按步骤一次性接通即可——**无需改任何业务代码**。

## 仓库已经做好的事

- `cloudflare/supabase-proxy/`：Worker 源码 `worker.js` + `wrangler.toml` + 说明，可复现部署。
- `app/build.gradle`：Online 的 `SUPABASE_URL` 改为可被 Gradle 属性覆盖，默认仍直连 Supabase（不影响现状）。
- `.github/workflows/deploy-web-cloudflare.yml`：前端发到 Cloudflare Pages 的工作流（仅手动触发）。
- Web 端的 Supabase 地址本来就走环境变量 `NUXT_PUBLIC_SUPABASE_URL`，换地址无需改代码。

**默认行为不变**：不传任何代理域名时，Web 和 Android 仍直连 Supabase，现有部署照常工作。

## 拿到域名后，接通步骤

设你的代理域名为 `api.example.com`（API）和 `app.example.com`（前端），域名 DNS 已托管在 Cloudflare。

### 1) 部署 / 绑定 Worker 代理

```bash
cd cloudflare/supabase-proxy
# 编辑 wrangler.toml，取消 routes 注释并改成 api.example.com
npx wrangler deploy
curl https://api.example.com/__health   # 返回 ok 即通
```

### 2) Web 切到代理 + 发到 Cloudflare Pages

仓库 Secrets 配置：`CLOUDFLARE_API_TOKEN`、`CLOUDFLARE_ACCOUNT_ID`、
`VITE_SUPABASE_URL=https://api.example.com`、`VITE_SUPABASE_ANON_KEY=<publishable key>`。

手动触发 **Deploy Web to Cloudflare Pages** 工作流（base 用 `/`），
然后在 Cloudflare Pages 项目 `hr40-web` 里把自定义域名设为 `app.example.com`。

### 3) Android 切到代理

```bash
./gradlew :app:assembleOnlineDebug -PsupabaseUrl=https://api.example.com
```

或在 `.github/workflows/build-dist-online-apk.yml` 的构建命令里加同样的 `-PsupabaseUrl=...`。

### 4) Supabase 后台（重要，避免登录/注册回调出错）

到 Supabase → Authentication → URL Configuration：
- **Site URL** 改成 `https://app.example.com`
- **Redirect URLs** 增加 `https://app.example.com/**`

邮件确认/找回密码链接里嵌的是 Site URL，不是 API 地址，所以这步要单独改。

## 验证

- `curl -i https://api.example.com/auth/v1/health`（经 Cloudflare）应正常返回。
- 浏览器开 `https://app.example.com`，登录/拉取运动记录正常。
- 国内实测对比直连 `*.supabase.co` 的 TTFB / 登录耗时。

## 排错

- **CORS 报错**：Worker 已反射 `Origin` 与 `Access-Control-Request-Headers`，预检在边缘直接回 204。若仍报错，确认请求确实打到了 `api.example.com` 而非旧地址。
- **401 / apikey 无效**：anon/publishable key 没变，只换地址；检查客户端是否还带着 `apikey` 头（Worker 原样透传）。
- **`__health` 不通**：多半是 `routes` 没绑定成功或 DNS 不是橙云代理；在 Cloudflare 后台确认该域名为 Proxied。
- **仍然慢**：Cloudflare 免费版无中国大陆 POP，国内会就近落到香港/新加坡/日本节点；相比 GitHub Pages 与 `*.supabase.co` 直连通常已明显改善，但不等于大陆境内节点。若要大陆境内加速需企业版「中国网络」或改用境内云厂商方案。

## 回滚

把客户端地址改回 `https://erukqyqwzbutwlaerzfn.supabase.co`（Web 改 Secret 重发；Android 去掉 `-PsupabaseUrl`），即恢复直连。Worker / Pages 保留不影响。
