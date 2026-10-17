// import axios from "axios";

/**
 * 音乐播放器
 */

// 已移除旧的 API 调用逻辑

/**
 * 一言
 */

// 获取一言数据
export const getHitokoto = async () => {
  const res = await fetch("https://v1.hitokoto.cn");
  return await res.json();
};

/**
 * 天气（走自建后端代理）
 *
 * 为什么不再直接调高德/和风（渗透报告 #27 / #60，MEDIUM）：
 *   Vite 在构建时会把 `import.meta.env.VITE_*` 静态内联进前端产物，
 *   Key 只要出现在前端代码里就必然明文存在于 bundle 中（.env.local 的
 *   VITE_WEATHER_KEY / VITE_QWEATHER_KEY 就是这么泄露到 www.doulor.cn 的）。
 *   现在浏览器只认识这个代理地址，Key 只在 Worker 的 secret 里，
 *   调用的上游域名也只有 Worker 知道。
 *
 * 默认走同源 /api/weather（Worker 路由绑在 doulor.cn/api/weather*）；
 * 若代理部署在独立子域，用 VITE_WEATHER_PROXY_URL 指向它。
 */
const WEATHER_PROXY = (
  import.meta.env.VITE_WEATHER_PROXY_URL || "/api/weather"
).replace(/\/+$/, "");

/**
 * 按请求方 IP 定位（服务端调高德 IP 定位）
 * @returns {Promise<string|null>} 城市名，失败返回 null（调用方回退到默认城市）
 */
export const getIpLocation = async () => {
  try {
    const res = await fetch(`${WEATHER_PROXY}/ip`, {
      headers: { Accept: "application/json" },
    });
    if (!res.ok) return null;
    const data = await res.json();
    return typeof data.city === "string" && data.city ? data.city : null;
  } catch (err) {
    console.warn("IP 定位失败", err);
    return null;
  }
};

/**
 * 实时天气：服务端按「高德 → 和风」顺序回退
 * @param {string} city 城市名
 * @returns {Promise<{city: string, temp: string, condition: string, updateTime: string, source: string}>}
 */
export const getWeather = async (city) => {
  const res = await fetch(
    `${WEATHER_PROXY}?city=${encodeURIComponent(city)}`,
    { headers: { Accept: "application/json" } },
  );

  if (!res.ok) {
    let msg = `天气服务返回 ${res.status}`;
    try {
      const err = await res.json();
      if (err.error) msg = err.error;
    } catch {
      /* 非 JSON 错误体 */
    }
    throw new Error(msg);
  }

  const data = await res.json();
  if (!data || !data.city || data.temp === undefined || !data.condition)
    throw new Error("天气数据格式不正确");
  return data;
};
