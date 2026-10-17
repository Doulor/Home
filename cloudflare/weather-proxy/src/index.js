/**
 * 天气反向代理 Worker（高德 / 和风）
 *
 * 为什么需要它（渗透报告 #27 / #60，MEDIUM）：
 *   www.doulor.cn 的前端 bundle 里明文出现高德与和风的 API Key。根因是 Vite 在
 *   构建时会把 `import.meta.env.VITE_*` **静态内联**进产物 —— 只要 Key 从 VITE_
 *   变量进过前端代码，它就已经是公开信息，改代码"藏起来"没用。唯一能修的是把
 *   Key 从前端彻底挪走：浏览器只调本代理，Key 只在这里的 secret 里。
 *
 *   ⚠️ 代码改完不等于泄露结束：已经被抓走的两个 Key 必须去控制台**轮换/删除新建**，
 *      否则它们依旧是公开可用的。本 Worker 只负责让新 Key 不再进浏览器。
 *
 * 端点（两种部署方式都支持，前端只要把代理地址当前缀拼）：
 *   同源路由 doulor.cn/api/weather*  → GET /api/weather/ip、GET /api/weather?city=<名字>
 *   独立子域 weather.doulor.cn       → GET /ip、GET ?city=<名字>
 *   返回：IP 定位 { city }；天气 { city, temp, condition, updateTime, source }
 *
 * 数据源顺序与前端原来的回退链一致：高德 → 和风。
 * （OpenMeteo 本来就不需要 Key，继续由前端直连兜底，不经过本 Worker。）
 *
 * 环境变量（用 `wrangler secret put` 配置，不要写进仓库）:
 *   AMAP_KEY       高德开放平台「Web 服务」Key
 *   QWEATHER_KEY   和风天气 Key（可选，作为备选）
 *   ALLOWED_ORIGINS 可选，允许的前端来源（逗号分隔）。
 *                  同源路由部署时用不到；独立子域部署时必须列全。
 */

const AMAP_BASE = "https://restapi.amap.com";
const QWEATHER_GEO_API = "https://geoapi.qweather.com/v2/city/lookup";
const QWEATHER_NOW_API = "https://devapi.qweather.com/v7/weather/now";

/** 上游超时：天气是首屏点缀，宁可快速失败让前端回退到 OpenMeteo */
const UPSTREAM_TIMEOUT_MS = 6000;

/** 默认允许的来源：站点自己的域名 + 本地开发 */
const DEFAULT_ALLOWED_ORIGINS = [
	"https://doulor.cn",
	"https://www.doulor.cn",
	"http://localhost:3000", // npm run dev（vite server.port = 3000）
	"http://localhost:4173", // vite preview
];

function allowedOrigins(env) {
	const raw = env && env.ALLOWED_ORIGINS;
	if (!raw) return DEFAULT_ALLOWED_ORIGINS;
	return String(raw)
		.split(",")
		.map((item) => item.trim())
		.filter(Boolean);
}

/** 只对白名单来源回 ACAO；同源请求不会带 Origin，也就不需要这个头 */
function corsHeaders(request, env) {
	const headers = { Vary: "Origin" };
	const origin = request.headers.get("Origin");
	if (origin && allowedOrigins(env).includes(origin)) {
		headers["Access-Control-Allow-Origin"] = origin;
		headers["Access-Control-Allow-Methods"] = "GET, OPTIONS";
		headers["Access-Control-Allow-Headers"] = "Content-Type";
	}
	return headers;
}

function json(data, request, env, status = 200, extraHeaders = {}) {
	return new Response(JSON.stringify(data), {
		status,
		headers: {
			"Content-Type": "application/json; charset=utf-8",
			...extraHeaders,
			...corsHeaders(request, env),
		},
	});
}

/** 上游请求：带超时；失败时抛出的错误里不含 Key（Key 只在 URL 里，由本函数掌握） */
async function getJson(url) {
	const res = await fetch(url, { signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS) });
	if (!res.ok) throw new Error(`上游返回 ${res.status}`);
	return await res.json();
}

/** 城市名清洗：去掉控制字符、限长，空值视为缺参 */
function sanitizeCity(raw) {
	const city = String(raw || "")
		.replace(/[\u0000-\u001f\u007f]/g, "")
		.trim();
	if (!city || city.length > 40) return "";
	return city;
}

/** 统一返回结构，temperature 一律转成字符串，前端直接渲染 */
function toWeather(city, temp, condition, updateTime, source) {
	return {
		city,
		temp: String(temp),
		condition: String(condition || ""),
		// 上游给的是 "2026-03-02 10:00:00"，截出 HH:mm；拿不到就用本地时间
		updateTime: updateTime || new Date().toISOString().slice(11, 16),
		source,
	};
}

/** 高德：地址 → adcode → 实时天气 */
async function fetchAmapWeather(city, key) {
	const geocode = await getJson(
		`${AMAP_BASE}/v3/geocode/geo?address=${encodeURIComponent(city)}&city=${encodeURIComponent(city)}&key=${encodeURIComponent(key)}`,
	);
	if (geocode.status !== "1" || !geocode.geocodes?.length)
		throw new Error("高德无法识别该城市");

	const adcode = geocode.geocodes[0].adcode;
	const weather = await getJson(
		`${AMAP_BASE}/v3/weather/weatherInfo?city=${encodeURIComponent(adcode)}&extensions=base&key=${encodeURIComponent(key)}`,
	);
	if (weather.status !== "1" || !weather.lives?.length)
		throw new Error("高德实时天气查询失败");

	const live = weather.lives[0];
	return toWeather(
		live.city,
		live.temperature,
		live.weather,
		String(live.reporttime || "").slice(11, 16),
		"amap",
	);
}

/** 和风：城市名 → location id → 实时天气 */
async function fetchQWeather(city, key) {
	const geo = await getJson(
		`${QWEATHER_GEO_API}?location=${encodeURIComponent(city)}&key=${encodeURIComponent(key)}`,
	);
	if (geo.code !== "200" || !geo.location?.length)
		throw new Error("和风无法识别该城市");

	const location = geo.location[0];
	const now = await getJson(
		`${QWEATHER_NOW_API}?location=${encodeURIComponent(location.id)}&key=${encodeURIComponent(key)}`,
	);
	if (now.code !== "200" || !now.now) throw new Error("和风实时天气查询失败");

	return toWeather(
		location.name,
		now.now.temp,
		now.now.text,
		String(now.now.obsTime || "").slice(11, 16),
		"qweather",
	);
}

/** IP 定位：和风没有等价接口，这里只用高德 */
async function handleIpLocation(request, env) {
	const key = env.AMAP_KEY;
	if (!key) return json({ error: "服务端未配置 AMAP_KEY" }, request, env, 503);

	let data;
	try {
		data = await getJson(`${AMAP_BASE}/v3/ip?key=${encodeURIComponent(key)}`);
	} catch (err) {
		// 只记录我们自己的短错误，避免把带 Key 的 URL 打进日志
		console.warn("IP 定位失败:", err.message);
		return json({ error: "IP 定位失败" }, request, env, 502);
	}

	if (data.status !== "1") return json({ error: "IP 定位失败" }, request, env, 502);

	// 直辖市等情况 city 可能为空或为数组，退回省份
	const city =
		typeof data.city === "string" && data.city.length > 0
			? data.city
			: typeof data.province === "string"
				? data.province
				: "";
	if (!city) return json({ error: "无法从 IP 解析城市" }, request, env, 502);

	return json({ city }, request, env, 200, { "Cache-Control": "private, no-store" });
}

async function handleWeather(request, env, url) {
	const city = sanitizeCity(url.searchParams.get("city"));
	if (!city) return json({ error: "缺少或非法的 city 参数" }, request, env, 400);

	const amapKey = env.AMAP_KEY;
	const qweatherKey = env.QWEATHER_KEY;
	if (!amapKey && !qweatherKey)
		return json({ error: "服务端未配置天气 Key" }, request, env, 503);

	const failures = [];

	if (amapKey) {
		try {
			return json(await fetchAmapWeather(city, amapKey), request, env, 200, {
				// 天气 5 分钟内不会变，让浏览器/边缘缓存扛住重复请求，省配额
				"Cache-Control": "public, max-age=300",
			});
		} catch (err) {
			failures.push(`amap: ${err.message}`);
		}
	}

	if (qweatherKey) {
		try {
			return json(await fetchQWeather(city, qweatherKey), request, env, 200, {
				"Cache-Control": "public, max-age=300",
			});
		} catch (err) {
			failures.push(`qweather: ${err.message}`);
		}
	}

	// 全部失败：日志留细节，响应只给一句话（前端会回退到 OpenMeteo）
	console.warn("天气查询失败:", failures.join(" | "));
	return json({ error: "天气查询失败" }, request, env, 502);
}

export default {
	async fetch(request, env) {
		if (request.method === "OPTIONS") {
			return new Response(null, { status: 204, headers: corsHeaders(request, env) });
		}
		if (request.method !== "GET")
			return json({ error: "Method Not Allowed" }, request, env, 405);

		const url = new URL(request.url);
		const path = url.pathname.replace(/\/+$/, "") || "/";

		// 路径兼容两种部署方式：
		//   同源路由 doulor.cn/api/weather*  → /api/weather、/api/weather/ip
		//   独立子域 weather.doulor.cn       → /、/ip
		// 这样前端只要把代理地址当前缀拼，两种部署都不用改代码。
		if (path === "/api/weather/ip" || path === "/ip") return handleIpLocation(request, env);
		if (path === "/api/weather" || path === "/weather" || path === "/")
			return handleWeather(request, env, url);

		return json(
			{
				error: "Not Found",
				endpoints: ["GET /api/weather?city=<城市名>", "GET /api/weather/ip"],
			},
			request,
			env,
			404,
		);
	},
};
