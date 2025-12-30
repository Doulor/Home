<template>
  <div :class="store.backgroundShow ? 'cover show' : 'cover'">
    <img
      v-show="store.imgLoadStatus"
      :src="bgUrl"
      class="bg"
      alt="cover"
      @load="imgLoadComplete"
      @error="imgLoadError"
    />
    <div :class="store.backgroundShow ? 'gray hidden' : 'gray'" />
    <Transition name="fade" mode="out-in">
      <a
        v-if="store.backgroundShow"
        class="down"
        :href="bgUrl"
        target="_blank"
      >
        下载壁纸
      </a>
    </Transition>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onBeforeUnmount, h } from "vue";
import { mainStore } from "@/store";
import { Error } from "@icon-park/vue-next";

const store = mainStore();
const bgUrl = ref(null);
const imgTimeout = ref(null);
const emit = defineEmits(["loadComplete"]);

// 错误重试次数
const retryCount = ref(0);
const MAX_RETRY = 5;

// 壁纸随机数
const getBgRandom = () => Math.floor(Math.random() * 20 + 1);

// 更换壁纸链接
const changeBg = (type) => {
  let newBgUrl;
  const randomNum = getBgRandom();
  
  if (type == 0) {
    newBgUrl = `/images/background${randomNum}.jpg`;
  } else if (type == 1) {
    newBgUrl = "https://api.dujin.org/bing/1920.php";
  } else {
    newBgUrl = `/images/background${randomNum}.jpg`;
  }

  console.log("设置壁纸URL:", newBgUrl);
  bgUrl.value = newBgUrl;
};

// 图片加载完成
const imgLoadComplete = () => {
  console.log("图片加载完成");
  retryCount.value = 0; // 重置重试计数
  if (!store.imgLoadStatus) {
    store.setImgLoadStatus(true);
  }
  emit("loadComplete");
};

// 图片显示失败
const imgLoadError = () => {
  console.error("壁纸加载失败：", bgUrl.value);
  
  if (retryCount.value < MAX_RETRY) {
    retryCount.value++;
    console.log(`正在尝试第 ${retryCount.value} 次重试...`);
    
    // 如果是必应壁纸失败，尝试切换回本地随机壁纸
    if (store.coverType == 1) {
      const randomNum = getBgRandom();
      bgUrl.value = `/images/background${randomNum}.jpg`;
    } else {
      // 如果是本地壁纸失败，尝试换一个随机数
      const randomNum = getBgRandom();
      bgUrl.value = `/images/background${randomNum}.jpg`;
    }
  } else {
    // 超过最大重试次数，使用保底壁纸
    console.error("超过最大重试次数，使用保底壁纸");
    bgUrl.value = "/images/background1.jpg";
    
    ElMessage({
      message: "壁纸加载失败，已切换至默认",
      type: "error",
      icon: h(Error, {
        theme: "filled",
        fill: "#efefef",
      }),
    });

    // 即使彻底失败也要让加载界面消失，防止卡死
    if (!store.imgLoadStatus) {
      store.setImgLoadStatus(true);
    }
    emit("loadComplete");
  }
};

// 监听壁纸切换
watch(
  () => store.coverType,
  (value) => {
    retryCount.value = 0;
    changeBg(value);
  },
);

let loadTimeout;

onMounted(() => {
  // 加载壁纸
  changeBg(store.coverType);

  // 超时保护
  loadTimeout = setTimeout(() => {
    if (!store.imgLoadStatus) {
      console.warn("壁纸加载超时，强制进入主页");
      store.setImgLoadStatus(true);
      emit("loadComplete");
    }
  }, 5000); // 增加到5秒，给慢网络一点时间
});

onBeforeUnmount(() => {
  clearTimeout(imgTimeout.value);
  if (loadTimeout) {
    clearTimeout(loadTimeout);
  }
});
</script>

<style lang="scss" scoped>
.cover {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  transition: 0.25s;
  z-index: -1;

  &.show {
    z-index: 1;
  }

  .bg {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    height: 100%;
    object-fit: cover;
    backface-visibility: hidden;
    filter: blur(20px) brightness(0.3);
    transition:
      filter 0.3s,
      transform 0.3s;
    animation: fade-blur-in 0.8s cubic-bezier(0.25, 0.46, 0.45, 0.94) forwards;
    animation-delay: 0.45s;
  }
  .gray {
    opacity: 1;
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    height: 100%;
    background-image: radial-gradient(rgba(0, 0, 0, 0) 0, rgba(0, 0, 0, 0.5) 100%),
      radial-gradient(rgba(0, 0, 0, 0) 33%, rgba(0, 0, 0, 0.3) 166%);

    transition: 1.5s;
    &.hidden {
      opacity: 0;
      transition: 1.5s;
    }
  }
  .down {
    font-size: 16px;
    color: white;
    position: absolute;
    bottom: 30px;
    left: 0;
    right: 0;
    margin: 0 auto;
    display: block;
    padding: 20px 26px;
    border-radius: 8px;
    background-color: #00000030;
    width: 120px;
    height: 30px;
    display: flex;
    justify-content: center;
    align-items: center;
    &:hover {
      transform: scale(1.05);
      background-color: #00000060;
    }
    &:active {
      transform: scale(1);
    }
  }
}
</style>
