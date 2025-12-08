<template>
  <div :class="store.mobileOpenState ? 'right' : 'right hidden'">
    <!-- 移动端 Logo -->
    <div class="logo text-hidden" @click="store.mobileFuncState = !store.mobileFuncState">
      <span class="bg">{{ siteUrl[0] }}</span>
      <span class="sm">.{{ siteUrl[1] }}</span>
    </div>
    <!-- 功能区 -->
    <Func />
    <!-- 时光胶囊入口 -->
    <div class="time-capsule-entry" @click="openTimeCapsule">
      <Icon size="20">
        <HourglassFull theme="two-tone" size="24" :fill="['#efefef', '#00000020']" />
      </Icon>
      <span class="title">时光胶囊</span>
    </div>
    <!-- 网站链接 -->
    <Link />
  </div>
</template>

<script setup>
import { mainStore } from "@/store";
import { Icon } from "@vicons/utils";
import { HourglassFull } from "@icon-park/vue-next";
import Func from "@/views/Func/index.vue";
import Link from "@/components/Links.vue";
const store = mainStore();

// 站点链接
const siteUrl = computed(() => {
  const url = import.meta.env.VITE_SITE_URL;
  if (!url) return "imsyy.top".split(".");
  // 判断协议前缀
  if (url.startsWith("http://") || url.startsWith("https://")) {
    const urlFormat = url.replace(/^(https?:\/\/)/, "");
    return urlFormat.split(".");
  }
  return url.split(".");
});

// 打开时光胶囊
const openTimeCapsule = () => {
  store.boxOpenState = true;
};
</script>

<style lang="scss" scoped>
.right {
  // flex: 1 0 0%;
  width: 50%;
  margin-left: 0.75rem;
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
  }

  .time-capsule-entry {
    display: flex;
    align-items: center;
    padding: 1rem 0.25rem;
    font-size: 1.1rem;
    margin: 1rem 0;
    cursor: pointer;
    transition: all 0.3s;

    &:hover {
      opacity: 0.8;
    }

    .title {
      margin-left: 8px;
      font-size: 1.15rem;
      text-shadow: 0 0 5px #00000050;
    }
  }

  @media (max-width: 720px) {
    margin-left: 0;
    width: 100%;
    padding-bottom: 80px; /* 为底部菜单按钮留出空间 */
    &.hidden {
      display: none;
    }
  }
}
</style>
