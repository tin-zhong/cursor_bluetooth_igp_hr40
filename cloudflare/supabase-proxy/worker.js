/**
 * HR40 Supabase 反向代理 Worker
 *
 * 作用：把 Cloudflare 边缘节点当作 Supabase（ap-southeast-1 / 新加坡）的前置入口，
 *       国内客户端连 Cloudflare 自有域名，由 Cloudflare 骨干网回源 Supabase，
 *       降低 TLS 握手 / 连接建立的往返延迟，并规避 *.supabase.co 在国内的不稳定。
 *
 * 适用范围：本项目 Web 端与 Android Online 端只用到 Supabase 的
 *           REST（/rest/v1）与 Auth（/auth/v1）等 HTTP 接口，未使用 Realtime
 *           WebSocket，因此一个纯 HTTP 反代即可，无需处理 Upgrade。
 *
 * 部署：见同目录 wrangler.toml 与 README.md。务必绑定「自有域名（橙云代理开启）」，
 *       *.workers.dev 在国内常被墙，无法稳定加速。
 */

// 回源地址。可在 wrangler.toml 用 [vars] SUPABASE_ORIGIN 覆盖。
const DEFAULT_ORIGIN = "https://erukqyqwzbutwlaerzfn.supabase.co";

function corsHeaders(request) {
  const h = new Headers();
  h.set("Access-Control-Allow-Origin", request.headers.get("Origin") || "*");
  h.set("Vary", "Origin");
  h.set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
  // 反射浏览器声明的请求头（apikey / authorization / content-type / prefer 等）
  h.set(
    "Access-Control-Allow-Headers",
    request.headers.get("Access-Control-Request-Headers") || "*"
  );
  h.set("Access-Control-Max-Age", "86400");
  return h;
}

export default {
  async fetch(request, env) {
    const origin = (env && env.SUPABASE_ORIGIN) || DEFAULT_ORIGIN;
    const originHost = new URL(origin).host;
    const url = new URL(request.url);

    // 预检请求直接在边缘回应，不回源，省一次往返
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders(request) });
    }

    // 轻量健康检查
    if (url.pathname === "/__health") {
      return new Response("ok", {
        status: 200,
        headers: { "content-type": "text/plain", ...Object.fromEntries(corsHeaders(request)) },
      });
    }

    // 原样转发路径 + 查询串到 Supabase
    const targetUrl = origin + url.pathname + url.search;

    const fwdHeaders = new Headers(request.headers);
    // 让回源 TLS / 路由用 Supabase 的主机名
    fwdHeaders.set("Host", originHost);
    // 去掉 Cloudflare 注入、对回源无意义的头
    fwdHeaders.delete("cf-connecting-ip");
    fwdHeaders.delete("cf-ipcountry");
    fwdHeaders.delete("cf-ray");
    fwdHeaders.delete("cf-visitor");

    const hasBody = request.method !== "GET" && request.method !== "HEAD";
    const upstream = await fetch(targetUrl, {
      method: request.method,
      headers: fwdHeaders,
      body: hasBody ? request.body : undefined,
      redirect: "manual",
    });

    const respHeaders = new Headers(upstream.headers);
    const cors = corsHeaders(request);
    for (const [k, v] of cors) respHeaders.set(k, v);

    return new Response(upstream.body, {
      status: upstream.status,
      statusText: upstream.statusText,
      headers: respHeaders,
    });
  },
};
