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
          <MainLeft />
          <MainRight v-show="!store.boxOpenState" />
          <Box v-show="store.boxOpenState" />
        </section>
        <section class="more" v-show="store.setOpenState" @click="store.setOpenState = false">
          <MoreSet />
        </section>
      </div>
      <!-- 页脚 -->
      <Transition name="fade" mode="out-in">
        <Footer v-show="!store.backgroundShow && !store.setOpenState" />
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
</template>
<script setup>
import { helloInit, checkDays } from "@/utils/getTime.js";
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
import cursorInit from "@/utils/cursor.js";
import config from "@/../package.json";

const store = mainStore();
const isMobileSize = ref(true); // 默认为移动端尺寸

// 页面宽度
const getWidth = () => {
  store.setInnerWidth(window.innerWidth);
  // 根据窗口宽度判断是否为移动端尺寸
  isMobileSize.value = window.innerWidth < 1201;
};

// 加载完成事件
const loadComplete = () => {
  nextTick(() => {
    // 欢迎提示
    helloInit();
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
  // 自定义鼠标
  cursorInit();

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
  const title1 = "無名の主页";
  const title2 = `
 _____ __  __  _______     ____     __
|_   _|  \\/  |/ ____\\ \\   / /\\ \\   / /
  | | | \\  / | (___  \\ \\_/ /  \\ \\_/ /
  | | | |\\/| |\\___ \\  \\   /    \\   /
 _| |_| |  | |____) |  | |      | |
|_____|_|  |_|_____/   |_|      |_|`;
  const content = `\n\n版本: ${config.version}\n主页: ${config.home}\nGithub: ${config.github}`;
  console.info(`%c${title1} %c${title2} %c${content}`, styleTitle1, styleTitle2, styleContent);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", getWidth);
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
  border-radius: 12px;  /* 增加圆角 */
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
  @media (min-width: 1201px) {
    display: none !important;  /* 在大屏幕上隐藏，因为左右两部分已经同时可见 */
  }
  @media (max-width: 1200px) {
    display: flex !important;  /* 在中等屏幕及以下显示 */
  }
}
</style>
