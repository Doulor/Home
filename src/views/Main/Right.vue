<template>
  <div :class="['right', !store.mobileOpenState ? 'hidden' : '', store.minimalistMode ? 'minimalist' : '']">
    <!-- 移动端 Logo -->
    <div class="logo text-hidden" @click="store.mobileFuncState = !store.mobileFuncState" v-show="!store.minimalistMode">
      <span class="bg">{{ siteUrl[0] }}</span>
      <span class="sm">.{{ siteUrl[1] }}</span>
    </div>
    <!-- 功能区 -->
    <Func />
    <!-- 网站链接 -->
    <Link v-show="!store.minimalistMode" />
    <div class="minimalist-time" v-if="store.minimalistMode && store.minimalistTimeVisible">
      <span class="time-text">{{ clockTime.hour }}:{{ clockTime.minute }}:{{ clockTime.second }}</span>
      <span class="date-text">{{ clockTime.year }}-{{ clockTime.month }}-{{ clockTime.day }} · {{ clockTime.weekday }}</span>
    </div>
    <div class="minimalist-weather" v-if="store.minimalistMode && store.minimalistWeatherVisible">
      <Weather />
    </div>
    
    <!-- 极简模式退出按钮 (右下角触发) -->
    <div class="exit-minimalist-trigger" v-if="store.minimalistMode">
      <div class="exit-minimalist" @click="store.minimalistMode = false">
        <setting-two theme="filled" size="24" fill="#ffffff60" />
        <span>退出极简模式</span>
      </div>
    </div>

    <!-- 极简模式进入按钮 (右下角触发) -->
    <div class="enter-minimalist-trigger" v-if="!store.minimalistMode && store.minimalistEntryVisible">
      <div class="enter-minimalist" @click="store.minimalistMode = true">
        <setting-two theme="filled" size="24" fill="#ffffff60" />
        <span>进入极简模式</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onBeforeUnmount } from "vue";
import { SettingTwo } from "@icon-park/vue-next";
import { mainStore } from "@/store";
import Func from "@/views/Func/index.vue";
import Link from "@/components/Links.vue";
import Weather from "@/components/Weather.vue";
import { getCurrentTime } from "@/utils/getTime";
const store = mainStore();

const clockTime = ref(getCurrentTime());
let clockTimer = null;

const updateClock = () => {
  clockTime.value = getCurrentTime();
};

onMounted(() => {
  updateClock();
  clockTimer = setInterval(updateClock, 1000);
});

onBeforeUnmount(() => {
  if (clockTimer) {
    clearInterval(clockTimer);
    clockTimer = null;
  }
});

const siteUrl = computed(() => {
  const url = import.meta.env.VITE_SITE_URL;
  if (!url) return "imsyy.top".split(".");
  if (url.startsWith("http://") || url.startsWith("https://")) {
    const urlFormat = url.replace(/^(https?:\/\/)/, "");
    return urlFormat.split(".");
  }
  return url.split(".");
});
</script>

<style lang="scss" scoped>
.right {
  width: 50%;
  margin-left: 0.75rem;
  padding-bottom: 80px;
  position: relative;
  .logo {
    width: 100%;
    font-family: "Pacifico-Regular";
    font-size: 1.75rem;
    position: fixed;
    top: 6%;
    left: 0;
    text-align: center;
    transition: transform 0.3s;
    animation: fade 0.5s;
    &:active {
      transform: scale(0.95);
    }
    @media (min-width: 720px) {
      display: none;
    }
    @media (max-width: 720px) {
      display: none;
    }
  }
  @media (max-width: 720px) {
    &.hidden {
      display: none;
    }
    margin-left: 0;
    width: 100%;
    padding-bottom: 80px;
    padding-top: 1.5rem;
    margin-top: -60px;
  }
  &.minimalist {
    margin-left: 0;
    width: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    padding-bottom: 60vh;
    @media (max-width: 720px) {
      margin-top: 0;
      padding-top: 0;
    }
    :deep(.func-container) {
      width: 100%;
      display: flex;
      justify-content: center;
      align-items: center;
    }
    :deep(.search-card) {
      width: 100%;
      max-width: 600px;
      margin: 0 auto;
      background-color: transparent;
      padding: 0;
      box-shadow: none;
      backdrop-filter: none;
    }
    .minimalist-time {
      margin-bottom: 20px;
      text-align: center;
      color: rgba(255, 255, 255, 0.8);
      transition: all 0.3s;
      .time-text {
        display: block;
        font-size: 2.5rem;
        font-weight: 600;
        letter-spacing: 0.04em;
      }
      .date-text {
        display: block;
        font-size: 0.9rem;
        margin-top: 4px;
        color: rgba(255, 255, 255, 0.6);
      }
    }
    .minimalist-weather {
      position: fixed;
      top: 40px;
      right: 40px;
      left: auto;
      width: auto;
      display: flex;
      justify-content: flex-end;
      z-index: 10;
      transition: all 0.3s;
      
      :deep(.weather-container) {
        width: auto;
        align-items: flex-end;
      }
      :deep(.weather-card) {
        align-items: flex-end;
      }
      :deep(.city-update) {
        justify-content: flex-end;
        width: auto;
      }
      :deep(.weather-detail) {
        justify-content: flex-end;
      }
      @media (max-width: 720px) {
        top: 20px;
        right: 20px;
        left: auto;
      }
    }

  }
}
.exit-minimalist-trigger {
  position: fixed;
  bottom: 0;
  right: 0;
  width: 180px;
  height: 100px;
  z-index: 9999;
  pointer-events: auto;
  .exit-minimalist {
    position: absolute;
    bottom: 20px;
    right: 20px;
    display: flex;
    align-items: center;
    gap: 6px;
    cursor: pointer;
    color: rgba(255, 255, 255, 0.6);
    font-size: 14px;
    transition: all 0.3s;
    background: rgba(0, 0, 0, 0.2);
    padding: 8px 16px;
    border-radius: 20px;
    backdrop-filter: blur(10px);
    opacity: 0;
    transform: translateY(20px);
    &:hover {
      color: #fff;
      background: rgba(0, 0, 0, 0.4);
      :deep(.i-icon) {
        fill: #fff !important;
      }
    }
  }
  &:hover {
    .exit-minimalist {
      opacity: 1;
      transform: translateY(0);
    }
  }
}
.enter-minimalist-trigger {
  position: fixed;
  bottom: 0;
  right: 0;
  width: 180px;
  height: 100px;
  z-index: 9999;
  pointer-events: auto;
  .enter-minimalist {
    position: absolute;
    bottom: 20px;
    right: 20px;
    display: flex;
    align-items: center;
    gap: 6px;
    cursor: pointer;
    color: rgba(255, 255, 255, 0.6);
    font-size: 14px;
    transition: all 0.3s;
    background: rgba(0, 0, 0, 0.2);
    padding: 8px 16px;
    border-radius: 20px;
    backdrop-filter: blur(10px);
    opacity: 0;
    transform: translateY(20px);
    &:hover {
      color: #fff;
      background: rgba(0, 0, 0, 0.4);
      :deep(.i-icon) {
        fill: #fff !important;
      }
    }
  }
  &:hover {
    .enter-minimalist {
      opacity: 1;
      transform: translateY(0);
    }
  }
}
</style>