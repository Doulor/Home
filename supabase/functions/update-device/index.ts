import { serve } from "https://deno.land/std@0.177.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const GEOCODE_URL = "https://api.bigdatacloud.net/data/reverse-geocode-client"

// 允许写入的字段白名单。
// 调用方用的是公开的 publishable key（打包在网页里，任何人都能拿到），
// 不限制的话任何人都能往任意列写值。device_id / updated_at 单独处理。
const ALLOWED_FIELDS = [
  "device_name",
  "device_type",
  "status",
  "battery_level",
  "battery_charging",
  "custom_status",
  "active_app",
  "location",
  "media_type",
  "media_title",
  "media_detail",
  "media_elapsed",
  "media_duration",
]

// 允许写入的设备白名单，防止有人冒充别的设备（比如覆盖电脑那一行）。
// 要加设备时用 secret 覆盖即可，不用改代码：
//   supabase secrets set ALLOWED_DEVICE_IDS="android-phone,android-tablet"
const ALLOWED_DEVICE_IDS = (Deno.env.get("ALLOWED_DEVICE_IDS") ?? "android-phone")
  .split(",")
  .map((s) => s.trim())
  .filter((s) => s.length > 0)

// 坐标：允许负数，也允许后面还跟着海拔等多余字段（只取前两个数）
const COORDS_RE = /^\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)/

// 最终防线：任何"数字,数字"形态的值都绝不允许原样入库。
// 否则一旦解析失败，精确经纬度就会以纯文本躺在表里，而这张表是公开可读的。
const COORDS_LOOSE_RE = /^-?\d+(?:\.\d+)?\s*,\s*-?\d+(?:\.\d+)?/

// 反向地理编码结果缓存：同一个坐标短期内不重复请求。
// 宏每次亮屏/切应用都会上报一次，每次都打免费接口很容易被限流，
// 而限流正是「未知位置」反复出现的原因。
const GEO_CACHE_TTL_MS = 10 * 60 * 1000
const geoCache = new Map<string, { value: string; at: number }>()

/**
 * 坐标 → 地址。返回 null 表示这次没解析出来（接口失败或该坐标没有地名数据）。
 * 失败会重试一次；成功结果缓存 10 分钟。
 */
async function reverseGeocode(lat: string, lng: string): Promise<string | null> {
  const key = `${lat},${lng}`
  const hit = geoCache.get(key)
  if (hit && Date.now() - hit.at < GEO_CACHE_TTL_MS) return hit.value

  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      const resp = await fetch(
        `${GEOCODE_URL}?latitude=${lat}&longitude=${lng}&localityLanguage=zh`,
        { signal: AbortSignal.timeout(8000) },
      )
      if (!resp.ok) throw new Error(`geocode HTTP ${resp.status}`)
      const geo = await resp.json()
      const parts: string[] = []
      if (geo.principalSubdivision) parts.push(geo.principalSubdivision)
      if (geo.city) parts.push(geo.city)
      if (geo.locality && geo.locality !== geo.city) parts.push(geo.locality)
      if (parts.length === 0) return null
      const value = parts.join(" · ")
      geoCache.set(key, { value, at: Date.now() })
      return value
    } catch {
      // 第一次失败就再试一次，第二次失败放弃
    }
  }
  return null
}

serve(async (req) => {
  if (req.method !== 'POST') {
    return new Response("Method not allowed", { status: 405 })
  }

  try {
    const body = await req.json()

    const deviceId = typeof body.device_id === "string" ? body.device_id : ""
    if (!deviceId) {
      return new Response(JSON.stringify({ error: "device_id required" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      })
    }
    if (!ALLOWED_DEVICE_IDS.includes(deviceId)) {
      return new Response(
        JSON.stringify({ error: `device_id not allowed: ${deviceId}` }),
        { status: 403, headers: { "Content-Type": "application/json" } },
      )
    }

    // 只挑白名单字段，其余一律丢弃
    const payload: Record<string, unknown> = { device_id: deviceId }
    const ignored: string[] = []
    for (const [key, value] of Object.entries(body)) {
      if (key === "device_id") continue
      if (ALLOWED_FIELDS.includes(key)) {
        payload[key] = value
      } else {
        ignored.push(key)
      }
    }

    // 反向地理编码：坐标 → 地址
    const location = payload.location
    let geocode: "ok" | "skipped" | "failed" = "skipped"

    // MacroDroid 取不到定位时，会把本地化的兜底文案当作 magic text 的值发上来
    // （实测是「未知位置」）。它既不是坐标也不是地址，直接当"没有位置"处理。
    const LOCATION_PLACEHOLDERS = new Set([
      "未知位置",
      "未知",
      "unknown",
      "unavailable",
      "null",
      "undefined",
    ])
    if (typeof location === "string" && LOCATION_PLACEHOLDERS.has(location.trim().toLowerCase())) {
      payload.location = null
      geocode = "failed"
    } else if (typeof location === "string" && location.length > 0) {
      const m = location.match(COORDS_RE)
      if (m) {
        // 解析不出来就直接不带位置（前端不显示），绝不把坐标原样写进去
        const resolved = await reverseGeocode(m[1], m[2])
        if (resolved) {
          payload.location = resolved
          geocode = "ok"
        } else {
          payload.location = null
          geocode = "failed"
        }
      }
    }

    // 兜底：不管上面走了哪条分支，只要最终值还长得像坐标，一律清掉
    if (typeof payload.location === "string" && COORDS_LOOSE_RE.test(payload.location.trim())) {
      payload.location = null
      geocode = "failed"
    }

    payload.updated_at = new Date().toISOString()

    // 写入数据库
    const supabase = createClient(
      Deno.env.get("DB_URL")!,
      Deno.env.get("DB_SERVICE_KEY")!,
    )

    const { error } = await supabase.from("device_status").upsert(payload)

    if (error) {
      return new Response(JSON.stringify({ error: error.message }), { status: 500 })
    }

    return new Response(
      JSON.stringify({ ok: true, location: payload.location, geocode, ignored }),
      { headers: { "Content-Type": "application/json" } },
    )
  } catch (e) {
    return new Response(JSON.stringify({ error: (e as Error).message }), { status: 500 })
  }
})
