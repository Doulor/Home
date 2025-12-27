<template>
  <div class="func-container">
    <!-- 搜索框 -->
    <div class="search-card">
      <Search />
    </div>

    <!-- 内容区：时间天气 -->
    <div class="content-row">
      <div class="time-weather-card">
        <div class="time-section">
          <div class="date">
            <span class="date-text">{{ currentTime.year }}年{{ currentTime.month }}月{{ currentTime.day }}日</span>
            <span class="weekday">{{ currentTime.weekday }}</span>
          </div>
          <div class="clock-container">
            <div class="clock">{{ currentTime.hour }}:{{ currentTime.minute | formatNumber }}:{{ currentTime.second | formatNumber }}</div>
          </div>
        </div>
        <div class="weather-section">
          <Weather />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from "vue";
import { getCurrentTime } from "@/utils/getTime";
import Weather from "@/components/Weather.vue";
import Search from "@/components/Search.vue";

const currentTime = ref({});
let timeInterval = null;

const formatNumber = (value) => {
  return value.toString().padStart(2, "0");
};

const updateTimeData = () => {
  currentTime.value = getCurrentTime();
};

onMounted(() => {
  updateTimeData();
  timeInterval = setInterval(updateTimeData, 1000);
});

onBeforeUnmount(() => {
  clearInterval(timeInterval);
});
</script>

<style lang="scss" scoped>
@import "@/assets/css/main.css";

.func-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 20px 15px;
}

.search-card {
  background-color: rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 20px 15px;
  margin-bottom: 20px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.content-row {
  display: flex;
  justify-content: center;
  align-items: flex-start; /* 顶部对齐 */
  width: 100%;
  gap: 20px; /* 增加间距 */

  @media (max-width: 1200px) {
    flex-direction: column;
    align-items: center;
  }
}

.time-weather-card {
  flex: 1; /* 占据剩余空间 */
  max-width: 800px;
  background-color: rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 10px 15px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  display: flex;
  flex-direction: column;

  @media (max-width: 1200px) {
    width: 100%;
    max-width: 100%;
  }
}

.time-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  padding: 0; /* 【移除】时间区块内边距 */

  .date {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 5px; /* 【减小】日期与时钟的间距（原10px） */
    width: 100%;
    justify-content: center;
    flex-wrap: wrap;

    .date-text {
      color: rgba(255, 255, 255, 0.9);
      font-size: 16px; /* 字体大小不变 */
    }

    .weekday {
      color: #4285f4;
      padding: 1px 6px; /* 【减小】星期标签内边距 */
      background-color: rgba(66, 133, 244, 0.2);
      border-radius: 5px;
      font-size: 14px; /* 字体大小不变 */
      font-weight: 500;
    }
  }

  .clock-container {
    width: 100%;
    display: flex;
    justify-content: center;
    padding: 0; /* 【移除】时钟容器内边距 */
    overflow: hidden;
  }

  .clock {
    font-family: 'Digital-7', sans-serif;
    font-size: 84px; /* 字体大小不变 */
    letter-spacing: 8px; /* 字间距不变 */
    color: #fff;
    font-weight: normal;
    text-shadow: 0 0 12px rgba(255, 255, 255, 0.6);
    line-height: 1; /* 保持行高为1，不增加额外高度 */
    width: 100%;
    text-align: center;
    min-width: 450px;
    padding-left: 8px;
  }
}

.weather-section {
  margin-top: 8px; /* 【减小】天气区域与时钟的间距（原12px） */
  padding-top: 8px; /* 【减小】天气区域上内边距（原12px） */
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  width: 100%;
  display: flex;
  justify-content: center;
}

/* 移动端优化 - 仅调整间距 */
@media (max-width: 768px) {
  .time-weather-card {
    max-width: 100%;
    padding: 8px 10px; /* 【减小】移动端卡片内边距 */
  }

  .time-section .clock {
    font-size: 52px; /* 字体大小不变 */
    letter-spacing: 3px; /* 字间距不变 */
    min-width: auto;
    padding-left: 3px;
  }

  .time-section .date {
    margin-bottom: 3px; /* 【进一步减小】移动端日期间距 */
  }
}

/* 小屏手机优化 */
@media (max-width: 375px) {
  .func-container {
    padding: 15px 10px;
  }

  .time-section .date {
    gap: 6px;
    margin-bottom: 2px; /* 【进一步减小】小屏日期间距 */
  }

  .date-text {
    font-size: 14px !important; /* 字体大小不变（原设置保留） */
  }

  .time-section .clock {
    font-size: 44px; /* 字体大小不变 */
    letter-spacing: 2px; /* 字间距不变 */
  }

  .weather-section {
    margin-top: 6px; /* 【进一步减小】小屏天气区域间距 */
    padding-top: 6px;
  }
}

@media (min-width: 1200px) {
  .time-section .clock {
    font-size: 96px; /* 字体大小不变 */
    letter-spacing: 10px; /* 字间距不变 */
    padding-left: 10px;
  }
}
</style>
    