<template>
  <!-- 加载 -->
  <Loading />
  <!-- 壁纸 -->
  <Background @loadComplete="loadComplete" />
  <!-- 主界面 -->
  <Transition name="fade" mode="out-in">
    <main id="main" v-if="store.imgLoadStatus">
      <div class="container" v-show="!store.backgroundShow">
        <section class="all" v-show="!store.setOpenState">
          <MainLeft v-show="!store.minimalistMode" />
          <MainRight />
          <Box v-show="store.boxOpenState" class="box-overlay" />
        </section>
        <section class="more" v-show="store.setOpenState" @click="store.setOpenState = false">
          <MoreSet />
        </section>
      </div>
      <!-- 页脚 -->
      <Transition name="fade" mode="out-in">
        <Footer v-show="!store.backgroundShow && !store.setOpenState && !store.minimalistMode" />
      </Transition>
    </main>
  </Transition>
  <!-- 移动端菜单按钮 - 移出 main 容器避免 transform 影响，仅在内容加载完成后显示 -->
  <Transition name="fade" mode="out-in">
    <Icon
      v-if="store.imgLoadStatus && isMobileSize"
      class="menu"
      size="24"
      v-show="!store.backgroundShow && !store.boxOpenState"
      @click="store.mobileOpenState = !store.mobileOpenState"
    >
      <component :is="store.mobileOpenState ? CloseSmall : HamburgerButton" />
    </Icon>
  </Transition>
  <!-- 音乐播放器 -->
  <Music v-show="!store.minimalistMode" />
</template>
<script setup>
import { helloInit, checkDays, showUpcomingCloudHello } from "@/utils/getTime.js";
import { HamburgerButton, CloseSmall } from "@icon-park/vue-next";
import { mainStore } from "@/store";
import { Icon } from "@vicons/utils";
import Loading from "@/components/Loading.vue";
import MainLeft from "@/views/Main/Left.vue";
import MainRight from "@/views/Main/Right.vue";
import Background from "@/components/Background.vue";
import Footer from "@/components/Footer.vue";
import Box from "@/views/Box/index.vue";
import MoreSet from "@/views/MoreSet/index.vue";
import Music from "@/components/Music.vue";
import cursorInit from "@/utils/cursor.js";
import config from "@/../package.json";

const store = mainStore();
const isMobileSize = ref(true); // 默认为移动端尺寸
const fallbackTimer = ref(null); // 壁纸兜底计时器，防止因加载异常导致页面不展示
const upcomingTimer = ref(null); // 最近特殊日提醒延时器

// 页面宽度
const getWidth = () => {
  store.setInnerWidth(window.innerWidth);
  // 根据窗口宽度判断是否为移动端尺寸，当宽度大于等于720px时认为是大屏，不需要切换按钮
  isMobileSize.value = window.innerWidth < 720;
};

// 加载完成事件
const hasCalledHello = ref(false); // 添加标志确保问候语只显示一次
const hasShownUpcoming = ref(false); // 仅提醒一次最近特殊日
const loadComplete = () => {
  nextTick(() => {
    // 欢迎提示 - 只调用一次
    if (!hasCalledHello.value) {
      helloInit();
      hasCalledHello.value = true;
    }

    if (!hasShownUpcoming.value && !upcomingTimer.value) {
      upcomingTimer.value = setTimeout(() => {
        const upcoming = showUpcomingCloudHello();
        if (upcoming) {
          hasShownUpcoming.value = true;
        }
        upcomingTimer.value = null;
      }, 1600); // 问候与提醒之间增加间隔
    }
    // 默哀模式
    checkDays();
  });
};

// 监听宽度变化
watch(
  () => store.innerWidth,
  (value) => {
    // 移除990px限制，允许在移动端打开时光胶囊
    // 保持原来的逻辑仅在极小屏幕（如<600px）时关闭，避免布局问题
    if (value < 600 && store.boxOpenState) {
      store.boxOpenState = false;
    }
  },
);

onMounted(() => {
  // 初始状态设置
  store.backgroundShow = false;
  store.setOpenState = false;
  store.boxOpenState = false;

  // 自定义鼠标
  cursorInit();

  // 兜底：若壁纸加载或事件异常，强制在 5s 后展示主界面，避免空白屏
  fallbackTimer.value = setTimeout(() => {
    if (!store.imgLoadStatus) {
      store.setImgLoadStatus(true);
      loadComplete();
      console.warn("壁纸加载异常，已自动跳过等待");
    }
  }, 5000);

  // 屏蔽右键
  document.oncontextmenu = () => {
    ElMessage({
      message: "为了浏览体验，本站禁用右键",
      grouping: true,
      duration: 2000,
    });
    return false;
  };

  // 鼠标中键事件
  window.addEventListener("mousedown", (event) => {
    if (event.button == 1) {
      store.backgroundShow = !store.backgroundShow;
      ElMessage({
        message: `已${store.backgroundShow ? "开启" : "退出"}壁纸展示状态`,
        grouping: true,
      });
    }
  });

  // 监听当前页面宽度
  getWidth();
  window.addEventListener("resize", getWidth);

  // 控制台输出
  const styleTitle1 = "font-size: 20px;font-weight: 600;color: rgb(244,167,89);";
  const styleTitle2 = "font-size:12px;color: rgb(244,167,89);";
  const styleContent = "color: rgb(30,152,255);";
  const title1 = "Doulor的主页";
  const title2 = `
 _____              _            
|  __ \\            | |           
| |  | | ___  _   _| | ___  _ __ 
| |  | |/ _ \\| | | | |/ _ \\| '__|
| |__| | (_) | |_| | | (_) | |   
|_____/ \\___/ \\__,_|_|\\___/|_|   
`;
  const content = `\n\n版本: ${config.version}\n主页: ${config.home}\nGithub: ${config.github}`;
  console.info(`%c${title1} %c${title2} %c${content}`, styleTitle1, styleTitle2, styleContent);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", getWidth);
  if (fallbackTimer.value) {
    clearTimeout(fallbackTimer.value);
  }
  if (upcomingTimer.value) {
    clearTimeout(upcomingTimer.value);
  }
});
</script>

<style lang="scss" scoped>
#main {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  transform: scale(1.2);
  transition: transform 0.3s;
  animation: fade-blur-main-in 0.65s cubic-bezier(0.25, 0.46, 0.45, 0.94) forwards;
  animation-delay: 0.5s;
  .container {
    width: 100%;
    height: 100vh;
    margin: 0 auto;
    .all {
      width: 100%;
      height: 100%;
      padding: 0 0.75rem;
      display: flex;
      flex-direction: row;
      justify-content: center;
      align-items: center;
    }
    .more {
      position: fixed;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background-color: #00000080;
      backdrop-filter: blur(20px);
      z-index: 2;
      animation: fade 0.5s;
    }
    @media (max-width: 1200px) {
      padding: 0 2vw;
    }
  }
}

.box-overlay {
  position: absolute;
  right: 0.75rem;
  width: 50%;
  height: 100%;
  display: flex;
  align-items: center;
  z-index: 10; /* 确保覆盖在其他内容之上 */
  @media (max-width: 720px) {
    position: relative;
    width: 100%;
    right: 0;
    margin-left: 0;
  }
}
</style>

<style lang="scss">
/* 全局样式，确保菜单按钮在所有情况下都正确显示且不受main容器transform影响 */
.menu {
  position: fixed;
  display: flex;
  justify-content: center;
  align-items: center;
  top: auto;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%); /* 使用transform居中而不是计算left值 */
  width: 70px;  /* 增加按钮宽度 */
  height: 70px;  /* 增加按钮高度 */
  padding: 18px;  /* 增加内边距，使背景更大 */
  background: rgb(0 0 0 / 20%);
  backdrop-filter: blur(10px);
  border-radius: 4px;  /* 进一步减小圆角，更方一些 */
  transition: transform 0.3s;
  animation: fade 0.5s;
  z-index: 2000;  // 提高z-index to ensure it's above all other elements
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  &:active {
    transform: translateX(-50%) scale(0.95); /* 保持居中同时添加缩放效果 */
  }
  .i-icon {
    transform: translateY(2px);
  }
  @media (min-width: 720px) {
    display: none !important;  /* 在大屏幕上隐藏，因为左右两部分已经同时可见 */
  }
  @media (max-width: 719px) {
    display: flex !important;  /* 在中等屏幕及以下显示 */
  }
}
</style>
