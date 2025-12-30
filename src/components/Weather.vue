<template>
  <div class="weather-container">
    <!-- 天气信息展示 -->
    <div v-if="weatherData" class="weather-card">
      <div class="city-update">
        <div class="city-info-group">
          <span class="city-name">{{ weatherData.city }}</span>
          <span class="update-time">更新于 {{ weatherData.updateTime }}</span>
        </div>
        <el-tooltip
          content="点击此处修改默认天气地区"
          placement="right"
          v-model:visible="showTip"
          :manual="true"
          :show-arrow="false"
          effect="dark"
          popper-class="weather-tip-popper"
        >
          <button 
            @click="handleSearchClick" 
            class="search-toggle"
          >
            <Edit theme="outline" size="20" fill="#fff"/>
          </button>
        </el-tooltip>
      </div>
      <div class="weather-detail">
        <div class="temp-cond">
          <!-- 温度：在原基础上扩大 -->
          <div class="temperature">{{ weatherData.temp }}°C</div>
          <!-- 天气符号 + 天气文字（大小=原温度大小） -->
          <div class="condition-wrap">
            <span class="weather-icon">{{ getWeatherIcon(weatherData.condition) }}</span>
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
import { Edit } from "@icon-park/vue-next";

// 配置区域 - 在这里修改默认城市

// 配置区域 - 在这里修改默认城市
const DEFAULT_CITY = "荆州"; 
const STORAGE_KEY = "home-weather-city";

// 高德API配置
const AMAP_WEB_KEY = "3b3b7736c187fa7bffae3bb1ecb30ef0"; 
const GEOCODE_API = "https://restapi.amap.com/v3/geocode/geo";
const WEATHER_API = "https://restapi.amap.com/v3/weather/weatherInfo";

// 状态管理
const isLoading = ref(false);
const errorMessage = ref("");
const weatherData = ref(null);
const cityName = ref(DEFAULT_CITY);
const showSearch = ref(false);
const searchInput = ref(null);
const showTip = ref(false);
const TIP_STORAGE_KEY = "weather-location-tip-shown";

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

// 加载本地存储城市
const loadSavedCity = () => {
  try {
    const saved = window.localStorage.getItem(STORAGE_KEY);
    if (saved) {
      cityName.value = saved;
    }
  } catch (err) {
    console.warn("读取本地城市失败", err);
  }
};

const saveCity = (city) => {
  try {
    window.localStorage.setItem(STORAGE_KEY, city);
  } catch (err) {
    console.warn("保存本地城市失败", err);
  }
};

// 组件挂载时加载默认/本地城市天气
onMounted(() => {
  loadSavedCity();
  fetchWeather();
  
  // 气泡提示逻辑
  try {
    const hasShown = localStorage.getItem(TIP_STORAGE_KEY);
    // 移动端不显示气泡 (宽度小于 720px)
    const isMobile = window.innerWidth < 720;
    
    if (!hasShown && !isMobile) {
      // 1/3 概率触发
      if (Math.random() < 0.33) {
        // 延迟显示，等待开场动画结束（约2秒）
        setTimeout(() => {
          showTip.value = true;
          // 5秒后自动消失，避免一直遮挡
          setTimeout(() => {
            showTip.value = false;
          }, 5000);
        }, 2000);
      }
    }
  } catch (e) {
    console.warn("LocalStorage access failed", e);
  }
});

// 处理搜索按钮点击
const handleSearchClick = () => {
  showSearch.value = !showSearch;
  
  // 如果气泡正在显示，点击后标记为不再显示
  if (showTip.value) {
    showTip.value = false;
    try {
      localStorage.setItem(TIP_STORAGE_KEY, "true");
    } catch (e) {}
  } else {
    // 即使用户没看到气泡，只要会用了，也可以标记为已读（可选，这里为了保险起见也标记）
    try {
      localStorage.setItem(TIP_STORAGE_KEY, "true");
    } catch (e) {}
  }

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

// 查询天气主函数（无修改）
const fetchWeather = async () => {
  const city = cityName.value.trim();
  if (!city) {
    errorMessage.value = "请输入城市名";
    return;
  }

  isLoading.value = true;
  errorMessage.value = "";
  
  try {
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
  } catch (err) {
    errorMessage.value = err.message;
    showSearchBox();
  } finally {
    isLoading.value = false;
  }
};
</script>

<style lang="scss">
.weather-tip-popper {
  /* 确保在深色背景下文字为白色 */
  .el-popper__content {
    color: #fff !important;
    font-weight: 500;
  }
  
  /* 自定义气泡样式 */
  &.el-popper {
    margin-left: 12px !important;
    border-radius: 8px;
    background: #000 !important; /* 纯黑背景 */
    border: 1px solid rgba(255, 255, 255, 0.1) !important; /* 微弱边框 */
    
    /* 隐藏默认箭头（以防万一） */
    .el-popper__arrow {
      display: none;
    }

    /* 自定义左侧箭头（当气泡在右侧时） */
    &[data-popper-placement^="right"]::before {
      content: "";
      position: absolute;
      top: 50%;
      left: -6px;
      transform: translateY(-50%);
      width: 0;
      height: 0;
      border-top: 6px solid transparent;
      border-bottom: 6px solid transparent;
      border-right: 6px solid #000; /* 颜色与背景一致 */
    }
    
    /* 简单的边框修饰（可选，为了完美衔接边框需要更复杂的双层伪元素，这里简化处理） */
  }
}
</style>

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
.weather-icon {
  font-size: 28px; /* 等于原温度大小，与天气文字匹配 */
  line-height: 1;
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
</style>