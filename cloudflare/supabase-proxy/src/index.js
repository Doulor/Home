/**
 * Supabase 反向代理
 *
 * 为什么需要它：
 *   `*.supabase.co` 在国内直连是被阻断的（TCP 能连上，但 TLS 握手被 RST，
 *   80 端口的明文 HTTP 也一样被 reset），浏览器和手机只能走代理才能访问。
 *   把这个 Worker 绑到自己的域名上之后，前端和手机只要访问自己的域名即可 ——
 *   Worker 跑在 Cloudflare 的境外节点上，由它去访问 Supabase。
 *
 * 结果：网页显示和手机上报都不再需要代理。
 *
 * 只放行下面几类路径，不做通用代理：
 *   /rest/v1/       PostgREST（前端读设备状态、留言等）
 *   /functions/v1/  Edge Functions（手机上报走这里）
 *   /auth/v1/       GoTrue（supabase-js 初始化时会用到）
 *   /realtime/v1/   Realtime（WebSocket，前端实时刷新；不通也只会退化成 30 秒轮询）
 */

const UPSTREAM = "https://kphhahpdaqdwcbvhvseo.supabase.co"

const ALLOWED_PREFIXES = [
  "/rest/v1/",
  "/functions/v1/",
  "/auth/v1/",
  "/realtime/v1/",
]

export default {
  async fetch(request) {
    const url = new URL(request.url)

    if (!ALLOWED_PREFIXES.some((p) => url.pathname.startsWith(p))) {
      return new Response("Not found", { status: 404 })
    }

    // 原样转发：方法、请求头（含 apikey / Authorization / Upgrade）、请求体
    const target = UPSTREAM + url.pathname + url.search
    return fetch(new Request(target, request))
  },
}
