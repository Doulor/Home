<template>
  <div class="weather-container" :class="{ 'minimalist': store.minimalistMode }">
    <!-- 天气信息展示 -->
    <div v-if="weatherData" class="weather-card">
      <div class="city-update">
        <div class="city-info-group">
          <span class="city-name">{{ weatherData.city }}</span>
          <span class="update-time">更新于 {{ weatherData.updateTime }}</span>
        </div>
        <div class="city-ops">
          <el-tooltip content="自动定位" placement="top" effect="dark" :show-arrow="false">
             <button @click="handleAutoLocation" class="search-toggle auto-loc-btn">
               <Aiming theme="outline" size="20" fill="#fff" />
             </button>
          </el-tooltip>
          <el-tooltip content="修改位置" placement="top" effect="dark" :show-arrow="false">
            <button 
              @click="handleSearchClick" 
              class="search-toggle"
            >
              <Edit theme="outline" size="20" fill="#fff"/>
            </button>
          </el-tooltip>
        </div>
      </div>
      <div class="weather-detail">
        <div class="temp-cond">
          <!-- 温度：在原基础上扩大 -->
          <div class="temperature">{{ weatherData.temp }}°C</div>
          <!-- 天气符号 + 天气文字（大小=原温度大小） -->
          <div class="condition-wrap">
            <span class="weather-icon-wrapper">
              <span :class="['weather-icon', getWeatherAnimationClass(weatherData.condition)]">
                {{ getWeatherIcon(weatherData.condition) }}
              </span>
            </span>
            <span class="condition">{{ weatherData.condition }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 加载状态 -->
    <div v-if="isLoading" class="loading-state">
      <span>加载中...</span>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="errorMessage" class="error-state">
      <span>{{ errorMessage }}</span>
      <button 
        @click="showSearch = !showSearch" 
        class="search-toggle"
      >
        <i class="icon-search"></i>
      </button>
    </div>

    <!-- 搜索框（默认隐藏） -->
    <div v-if="showSearch" class="search-group">
      <input
        v-model="cityName"
        placeholder="输入城市名"
        @keyup.enter="fetchWeather"
        class="city-input"
        ref="searchInput"
        @blur="hideSearchOnBlur"
      />
      <button @click="fetchWeather" class="search-btn">确认</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from "vue";
import { Edit, Aiming } from "@icon-park/vue-next";
import { ElMessage } from "element-plus";
import { mainStore } from "@/store";

const store = mainStore();

// 配置区域 - 在这里修改默认城市

// 配置区域 - 在这里修改默认城市
const DEFAULT_CITY = "荆州"; 
const STORAGE_KEY = "home-weather-city";

// 高德API配置
// 修改：优先使用环境变量，如果没有则使用空字符串或抛出警告
const AMAP_WEB_KEY = import.meta.env.VITE_WEATHER_KEY || ""; 
const AMAP_IP_API = "https://restapi.amap.com/v3/ip";
const GEOCODE_API = "https://restapi.amap.com/v3/geocode/geo";
const WEATHER_API = "https://restapi.amap.com/v3/weather/weatherInfo";

// 和风天气 API 配置
const QWEATHER_KEY = import.meta.env.VITE_QWEATHER_KEY || "";
const QWEATHER_GEO_API = "https://geoapi.qweather.com/v2/city/lookup";
const QWEATHER_WEATHER_API = "https://devapi.qweather.com/v7/weather/now";

// OpenMeteo API 配置 (无 Key 限制)
const OPEN_METEO_GEO_API = "https://geocoding-api.open-meteo.com/v1/search";
const OPEN_METEO_WEATHER_API = "https://api.open-meteo.com/v1/forecast";

// API 优先级配置 (可手动调整顺序)
// 可选值: "amap" (高德), "qweather" (和风), "openmeteo" (OpenMeteo)
const API_PRIORITY = ["amap", "qweather", "openmeteo"];

// 状态管理
const isLoading = ref(false);
const errorMessage = ref("");
const weatherData = ref(null);
const cityName = ref(DEFAULT_CITY);
const showSearch = ref(false);
const searchInput = ref(null);

// WMO 天气代码映射
const getWmoWeatherIcon = (code) => {
  const map = {
    0: { icon: "☀️", text: "晴" },
    1: { icon: "🌤️", text: "多云" },
    2: { icon: "⛅", text: "多云" },
    3: { icon: "☁️", text: "阴" },
    45: { icon: "🌫️", text: "雾" },
    48: { icon: "🌫️", text: "雾" },
    51: { icon: "🌧️", text: "小雨" },
    53: { icon: "🌧️", text: "中雨" },
    55: { icon: "🌧️", text: "大雨" },
    56: { icon: "🌧️", text: "冻雨" },
    57: { icon: "🌧️", text: "冻雨" },
    61: { icon: "🌧️", text: "小雨" },
    63: { icon: "🌧️", text: "中雨" },
    65: { icon: "🌧️", text: "大雨" },
    66: { icon: "🌧️", text: "冻雨" },
    67: { icon: "🌧️", text: "冻雨" },
    71: { icon: "❄️", text: "小雪" },
    73: { icon: "❄️", text: "中雪" },
    75: { icon: "❄️", text: "大雪" },
    77: { icon: "❄️", text: "雪粒" },
    80: { icon: "🌧️", text: "阵雨" },
    81: { icon: "🌧️", text: "阵雨" },
    82: { icon: "🌧️", text: "阵雨" },
    85: { icon: "❄️", text: "阵雪" },
    86: { icon: "❄️", text: "阵雪" },
    95: { icon: "⛈️", text: "雷阵雨" },
    96: { icon: "⛈️", text: "雷阵雨" },
    99: { icon: "⛈️", text: "雷阵雨" },
  };
  return map[code] || { icon: "🌤️", text: "未知" };
};

// 天气状况 -> 天气符号映射（覆盖常见天气）
const getWeatherIcon = (condition) => {
  const weatherMap = {
    "晴": "☀️",
    "多云": "⛅",
    "少云": "⛅",
    "晴间多云": "⛅",
    "阴": "☁️",
    "雾": "🌫️",
    "霾": "🌫️",
    "小雨": "🌧️",
    "中雨": "🌧️",
    "大雨": "🌧️",
    "暴雨": "🌧️",
    "雷阵雨": "⛈️",
    "小雪": "❄️",
    "中雪": "❄️",
    "大雪": "❄️",
    "雨夹雪": "❄️🌧️",
    "阵雪": "❄️"
  };
  return weatherMap[condition] || "🌤️";
};

// 获取天气动画类
const getWeatherAnimationClass = (weather) => {
  if (!weather) return "anim-float";
  if (weather.includes("晴")) return "anim-rotate";
  if (weather.includes("雨") || weather.includes("雪")) return "anim-rain";
  if (weather.includes("云") || weather.includes("阴")) return "anim-shake";
  if (weather.includes("雷") || weather.includes("电")) return "anim-flash";
  return "anim-float";
};

// IP 定位
const getIpLocation = async () => {
  if (!AMAP_WEB_KEY) return null;
  try {
    const res = await fetch(`${AMAP_IP_API}?key=${AMAP_WEB_KEY}`);
    const data = await res.json();
    if (data.status === "1") {
      // 优先使用城市，如果是空（如直辖市可能返回空或者是省份名），则使用省份
      const city = (data.city && typeof data.city === 'string' && data.city.length > 0) ? data.city : data.province;
      return city;
    }
  } catch (e) {
    console.error("IP Location failed", e);
  }
  return null;
};

// 加载本地存储城市
const loadSavedCity = () => {
  try {
    const saved = window.localStorage.getItem(STORAGE_KEY);
    if (saved) {
      cityName.value = saved;
      return true;
    }
  } catch (err) {
    console.warn("读取本地城市失败", err);
  }
  return false;
};

const saveCity = (city) => {
  try {
    window.localStorage.setItem(STORAGE_KEY, city);
  } catch (err) {
    console.warn("保存本地城市失败", err);
  }
};

// 组件挂载时加载默认/本地城市天气
onMounted(async () => {
  // 1. 尝试加载本地存储
  if (loadSavedCity()) {
    fetchWeather();
  } else {
    // 2. 本地无数据，尝试自动定位
    const locatedCity = await getIpLocation();
    if (locatedCity) {
      cityName.value = locatedCity;
      fetchWeather();
    } else {
      // 3. 定位失败，使用默认城市
      cityName.value = DEFAULT_CITY;
      fetchWeather();
    }
  }
});

// 手动定位
const handleAutoLocation = async () => {
  isLoading.value = true;
  const city = await getIpLocation();
  if (city) {
    cityName.value = city;
    ElMessage.success(`已定位到: ${city}`);
    fetchWeather();
  } else {
    ElMessage.warning("定位失败，请检查网络或配置");
    isLoading.value = false;
  }
};

// 处理搜索按钮点击
const handleSearchClick = () => {
  showSearch.value = !showSearch.value;

  if (showSearch.value) {
    nextTick(() => {
      searchInput.value?.focus();
    });
  }
};

// 显示搜索框并自动聚焦
const showSearchBox = () => {
  showSearch.value = true;
  nextTick(() => {
    searchInput.value?.focus();
  });
};

// 失去焦点时隐藏搜索框
const hideSearchOnBlur = () => {
  if (!errorMessage.value) {
    setTimeout(() => {
      showSearch.value = false;
    }, 300);
  }
};

// 查询天气主函数
const fetchWeather = async () => {
  const city = cityName.value.trim();
  if (!city) {
    errorMessage.value = "请输入城市名";
    return;
  }

  isLoading.value = true;
  errorMessage.value = "";
  
  let lastError = null;

  // 按优先级尝试 API
  for (const apiName of API_PRIORITY) {
    try {
      if (apiName === "amap") {
        if (!AMAP_WEB_KEY) throw new Error("未配置高德 Key");
        await fetchAmapWeather(city);
        return; // 成功则直接返回
      } else if (apiName === "qweather") {
        if (!QWEATHER_KEY) throw new Error("未配置和风 Key");
        await fetchQWeather(city);
        return; // 成功则直接返回
      } else if (apiName === "openmeteo") {
        await fetchOpenMeteoWeather(city);
        return; // 成功则直接返回
      }
    } catch (err) {
      console.warn(`${apiName} API 失败:`, err);
      lastError = err;
      // 继续尝试下一个 API
    }
  }

  // 所有 API 都失败
  errorMessage.value = "天气查询失败";
  showSearchBox();
  isLoading.value = false;
};

// 和风天气 API 实现
const fetchQWeather = async (city) => {
  // 1. 城市地理编码
  const geoRes = await fetch(
    `${QWEATHER_GEO_API}?location=${encodeURIComponent(city)}&key=${QWEATHER_KEY}`
  );
  const geoData = await geoRes.json();
  
  if (geoData.code !== "200" || !geoData.location?.length) {
    throw new Error("无法识别城市");
  }

  const location = geoData.location[0];
  const locationId = location.id;

  // 2. 获取实时天气
  const weatherRes = await fetch(
    `${QWEATHER_WEATHER_API}?location=${locationId}&key=${QWEATHER_KEY}`
  );
  const weatherDataRes = await weatherRes.json();
  
  if (weatherDataRes.code !== "200" || !weatherDataRes.now) {
    throw new Error("查询失败");
  }

  const now = weatherDataRes.now;
  weatherData.value = {
    city: location.name,
    temp: now.temp,
    condition: now.text,
    updateTime: now.obsTime.slice(11, 16)
  };

  cityName.value = location.name;
  saveCity(location.name);
  showSearch.value = false;
  isLoading.value = false;
};

// 高德 API 实现
const fetchAmapWeather = async (city) => {
  const geocodeRes = await fetch(
    `${GEOCODE_API}?address=${encodeURIComponent(city)}&city=${encodeURIComponent(city)}&key=${AMAP_WEB_KEY}`
  );
  const geocodeData = await geocodeRes.json();
  
  if (geocodeData.status !== "1" || !geocodeData.geocodes?.length) {
    throw new Error("无法识别城市");
  }

  const adcode = geocodeData.geocodes[0].adcode;
  const weatherRes = await fetch(
    `${WEATHER_API}?city=${adcode}&extensions=base&key=${AMAP_WEB_KEY}`
  );
  const weatherDataRes = await weatherRes.json();
  
  if (weatherDataRes.status !== "1" || !weatherDataRes.lives?.length) {
    throw new Error("查询失败");
  }

  const weather = weatherDataRes.lives[0];
  weatherData.value = {
    city: weather.city,
    temp: weather.temperature,
    condition: weather.weather,
    updateTime: weather.reporttime.slice(11, 16)
  };

  cityName.value = weather.city;
  saveCity(weather.city);
  showSearch.value = false;
  isLoading.value = false;
};

// OpenMeteo API 实现 (无 Key)
const fetchOpenMeteoWeather = async (city) => {
  // 1. 地理编码
  const geoRes = await fetch(
    `${OPEN_METEO_GEO_API}?name=${encodeURIComponent(city)}&count=1&language=zh&format=json`
  );
  const geoData = await geoRes.json();
  
  if (!geoData.results?.length) {
    throw new Error("无法识别城市");
  }

  const location = geoData.results[0];
  
  // 2. 获取天气
  const weatherRes = await fetch(
    `${OPEN_METEO_WEATHER_API}?latitude=${location.latitude}&longitude=${location.longitude}&current_weather=true&timezone=auto`
  );
  const weatherDataRes = await weatherRes.json();
  
  if (!weatherDataRes.current_weather) {
    throw new Error("查询失败");
  }

  const current = weatherDataRes.current_weather;
  const wmo = getWmoWeatherIcon(current.weathercode);

  weatherData.value = {
    city: location.name, // OpenMeteo 返回的城市名
    temp: Math.round(current.temperature),
    condition: wmo.text,
    updateTime: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  };

  cityName.value = location.name;
  saveCity(location.name);
  showSearch.value = false;
  isLoading.value = false;
};

</script>



<style scoped>
.weather-container {
  padding: 8px 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

/* 搜索按钮（无修改） */
.search-toggle {
  background: transparent;
  border: none;
  color: rgba(255, 255, 255, 0.8);
  cursor: pointer;
  padding: 0; /* 移除内边距，使用 Flex 居中 */
  border-radius: 6px;
  transition: all 0.2s;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px; /* 稍微调小一点，更精致 */
  height: 32px;
  flex-shrink: 0; /* 防止被压缩 */

  &:hover {
    color: #fff;
    background-color: rgba(255, 255, 255, 0.1);
  }

  .icon-search {
    display: flex;
    align-items: center;
    justify-content: center;
    line-height: 1;
  }
}

/* 搜索框样式（无修改） */
.search-group {
  display: flex;
  gap: 8px;
  width: 100%;
  margin-top: 6px;
  animation: fadeIn 0.2s ease;
  padding: 0 4px;
}

.city-input {
  flex: 1;
  padding: 10px 12px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 6px;
  background-color: rgba(255, 255, 255, 0.1);
  color: #fff;
  font-size: 15px;
  outline: none;
  transition: border-color 0.2s;

  &::placeholder {
    color: rgba(255, 255, 255, 0.5);
  }

  &:focus {
    border-color: #4285f4;
  }
}

.search-btn {
  padding: 0 16px;
  background-color: #4285f4;
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 15px;
  transition: background-color 0.2s;
  min-height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 状态提示（无修改） */
.loading-state,
.error-state {
  padding: 8px 4px;
  text-align: left;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.loading-state {
  color: rgba(255, 255, 255, 0.7);
}

.error-state {
  color: #ff6b6b;
}

.weather-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px 4px;
  width: 100%;
}

.city-update {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: nowrap; /* 防止换行导致布局错乱 */
  font-size: 16px;
  color: rgba(255, 255, 255, 0.9);
  width: 100%;
  gap: 8px;
}

.city-info-group {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  overflow: hidden; /* 防止文字过长溢出 */
}

.city-name {
  font-size: 20px;
  font-weight: bold;
  flex-shrink: 0;
}

.update-time {
  color: rgba(255, 255, 255, 0.6);
  font-size: 12px;
  white-space: nowrap;
}

.weather-detail {
  display: flex;
  align-items: center;
  padding: 4px 0;
  flex-wrap: wrap;
}

/* 调整温度与天气的间距（因两者字体变大，避免拥挤） */
.temp-cond {
  display: flex;
  align-items: center;
  gap: 20px; /* 比原16px增大，适配更大字体 */
  flex-wrap: wrap;
}

/* 核心修改1：温度字体在原基础上扩大（原28px→36px） */
.temperature {
  font-size: 36px; /* 桌面端：原温度28px → 扩大到36px */
  font-weight: bold;
  color: #fff;
  line-height: 1.1;
}

/* 天气符号+文字容器（确保对齐） */
.condition-wrap {
  display: flex;
  align-items: center;
  gap: 8px; /* 符号与文字间距，适配更大字体 */
}

/* 天气符号：与天气文字同高，避免不协调 */
.weather-icon-wrapper {
  display: inline-block;
  transition: transform 0.3s ease;
  cursor: pointer;

  &:hover {
    transform: scale(1.4);
  }
}

.weather-icon {
  font-size: 28px; /* 等于原温度大小，与天气文字匹配 */
  line-height: 1;
  display: inline-block; /* 必须为 inline-block 才能应用 transform */
  
  /* 默认浮动动画 */
  &.anim-float {
    animation: float 4s ease-in-out infinite;
  }
  
  /* 晴天旋转 */
  &.anim-rotate {
    animation: rotate 12s linear infinite;
  }
  
  /* 雨天/雪天下落感 */
  &.anim-rain {
    animation: rain-drop 1.5s ease-in-out infinite;
  }
  
  /* 多云摇晃 */
  &.anim-shake {
    animation: shake 4s ease-in-out infinite;
  }
  
  /* 雷电闪烁 */
  &.anim-flash {
    animation: flash 2s ease-in-out infinite;
  }
}

/* 核心修改2：天气文字大小=原温度大小（原14px→28px） */
.condition {
  font-size: 28px; /* 桌面端：等于原温度28px，与温度新大小（36px）形成层级 */
  color: rgba(255, 255, 255, 0.8);
  white-space: nowrap;
  font-weight: 500; /* 增加字重，与温度视觉协调 */
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 移动端适配：同步放大，保持比例 */
@media (max-width: 375px) {
  .city-update {
    justify-content: flex-start;
  }
  
  .temp-cond {
    gap: 16px; /* 移动端缩小间距，避免溢出 */
  }
  
  /* 移动端温度：原24px→30px（同比例扩大） */
  .temperature {
    font-size: 30px;
  }
  
  /* 移动端天气符号：与天气文字匹配 */
  .weather-icon {
    font-size: 24px; /* 等于移动端原温度大小 */
  }
  
  /* 移动端天气文字：等于原温度24px */
  .condition {
    font-size: 24px;
  }
}

/* 极简模式样式 */
.weather-container.minimalist {
  background: none;
  padding: 0;
  backdrop-filter: none;
  box-shadow: none;
  
  .city-ops,
  .update-time {
    display: none !important;
  }

  .weather-card {
    align-items: flex-end;
    gap: 4px;
  }

  .city-update {
    justify-content: flex-end;
    width: auto;
    margin-bottom: 0;
  }

  .city-info-group {
    justify-content: flex-end;
    flex: none;
  }

  .city-name {
    font-size: 16px;
    opacity: 0.8;
    font-weight: 500;
    letter-spacing: 1px;
  }

  .weather-detail {
    display: block;
    padding: 0;
    margin-top: 0;
  }

  .temp-cond {
    gap: 8px;
    align-items: center;
    justify-content: flex-end;
    flex-direction: row-reverse;
  }

  .temperature {
    font-size: 20px;
    font-weight: 400;
  }

  .weather-icon {
    font-size: 18px;
    opacity: 0.9;
  }

  .condition {
    font-size: 14px;
    opacity: 0.7;
  }
}

.city-ops {
  display: flex;
  align-items: center;
  gap: 4px;
  
  /* 默认隐藏自动定位按钮 */
  .auto-loc-btn {
    opacity: 0;
    transform: translateX(10px);
    pointer-events: none;
    transition: all 0.3s ease;
  }
}

/* 鼠标悬停在整个区域时显示自动定位按钮 */
.city-update:hover .auto-loc-btn {
  opacity: 1;
  transform: translateX(0);
  pointer-events: auto;
}
</style>