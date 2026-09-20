import { serve } from "https://deno.land/std@0.177.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const GEOCODE_URL = "https://api.bigdatacloud.net/data/reverse-geocode-client"

serve(async (req) => {
  if (req.method !== 'POST') {
    return new Response("Method not allowed", { status: 405 })
  }

  try {
    const body = await req.json()
    const { device_id, location, ...rest } = body

    if (!device_id) {
      return new Response(JSON.stringify({ error: "device_id required" }), { status: 400 })
    }

    // 反向地理编码：坐标 → 地址
    let resolvedLocation = location
    if (location && /^[\d.]+,\s*[\d.]+$/.test(location)) {
      const [lat, lng] = location.split(",").map((s: string) => s.trim())

      // 不暴露原始坐标，地理编码失败时标记为"未知位置"
      resolvedLocation = "未知位置"

      try {
        const resp = await fetch(
          `${GEOCODE_URL}?latitude=${lat}&longitude=${lng}&localityLanguage=zh`,
          { signal: AbortSignal.timeout(5000) }
        )
        const geo = await resp.json()
        const parts: string[] = []
        if (geo.principalSubdivision) parts.push(geo.principalSubdivision)
        if (geo.city) parts.push(geo.city)
        if (geo.locality && geo.locality !== geo.city) parts.push(geo.locality)
        if (parts.length > 0) resolvedLocation = parts.join(" · ")
      } catch {
        resolvedLocation = "未知位置"
      }
    }

    // 写入数据库
    const supabase = createClient(
      Deno.env.get("DB_URL")!,
      Deno.env.get("DB_SERVICE_KEY")!
    )

    const payload = {
      device_id,
      ...rest,
      location: resolvedLocation,
      updated_at: new Date().toISOString(),
    }

    const { error } = await supabase.from("device_status").upsert(payload)

    if (error) {
      return new Response(JSON.stringify({ error: error.message }), { status: 500 })
    }

    return new Response(JSON.stringify({ ok: true, location: resolvedLocation }), {
      headers: { "Content-Type": "application/json" },
    })
  } catch (e) {
    return new Response(JSON.stringify({ error: (e as Error).message }), { status: 500 })
  }
})