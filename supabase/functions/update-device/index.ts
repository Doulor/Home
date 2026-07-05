import { serve } from "https://deno.land/std@0.177.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

serve(async (req) => {
  // 只接受 POST
  if (req.method !== 'POST') {
    return new Response('Method not allowed', { status: 405 })
  }

  try {
    const body = await req.json()
    const { device_id, location, ...rest } = body

    if (!device_id) {
      return new Response(JSON.stringify({ error: 'device_id required' }), { status: 400 })
    }

    // 反向地理编码：坐标 → 地址
    let resolvedLocation = location
    if (location && /^[\d.]+,\s*[\d.]+$/.test(location)) {
      try {
        const [lat, lng] = location.split(',').map(s => s.trim())
        const geoResp = await fetch(
          `https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=${lat}&longitude=${lng}&localityLanguage=zh`
        )
        const geoData = await geoResp.json()
        if (geoData.city || geoData.locality) {
          const parts = []
          if (geoData.principalSubdivision) parts.push(geoData.principalSubdivision)
          if (geoData.city) parts.push(geoData.city)
          if (geoData.locality && geoData.locality !== geoData.city) parts.push(geoData.locality)
          resolvedLocation = parts.join(' · ') || location
        }
      } catch {
        // 转换失败就保留原始值
      }
    }

    // 写入数据库（用 service_role key，不暴露给客户端）
    const supabase = createClient(
      Deno.env.get('DB_URL')!,
      Deno.env.get('DB_SERVICE_KEY')!
    )

    const payload = {
      device_id,
      ...rest,
      location: resolvedLocation,
      updated_at: new Date().toISOString(),
    }

    const { error } = await supabase
      .from('device_status')
      .upsert(payload)

    if (error) {
      return new Response(JSON.stringify({ error: error.message }), { status: 500 })
    }

    return new Response(JSON.stringify({ ok: true, location: resolvedLocation }), {
      headers: { 'Content-Type': 'application/json' },
    })
  } catch (e) {
    return new Response(JSON.stringify({ error: e.message }), { status: 500 })
  }
})
