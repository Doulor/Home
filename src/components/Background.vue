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
import { getAllBgs, addBg } from "@/utils/bgDb";

const store = mainStore();
const bgUrl = ref(null);
const imgTimeout = ref(null);
const emit = defineEmits(["loadComplete"]);

// 错误重试次数
const retryCount = ref(0);
const MAX_RETRY = 5;

const defaultBgNames = [
  "天空之境上的钢琴", "黄昏小溪", "落日拱门", "蔚蓝银河", "云海草地",
  "黄昏光晕", "栏杆下的午后云影", "Online", "穿透云层的烟花", "月光下的阶梯小镇",
  "烟花易逝", "路口的猫", "云影列车", "彩云步道", "天空之境上的风车",
  "耀阳下的阶梯小镇", "田野", "西海紫云", "树影星河", "星轨山川"
];

// 初始化壁纸列表
const initBgList = async () => {
  try {
    let list = await getAllBgs();
    if (list.length === 0) {
      // 初始化默认壁纸
      for (let i = 1; i <= 20; i++) {
        await addBg({
          name: defaultBgNames[i - 1] || `默认壁纸 ${i}`,
          url: `/images/background${i}.jpg`,
          type: 'local',
          created: new Date()
        });
      }
      list = await getAllBgs();
    }
    
    // 处理 Blob URL
    store.bgList = list.map(item => {
      if (item.blob) {
        return { ...item, url: URL.createObjectURL(item.blob) };
      }
      return item;
    });
  } catch (e) {
    console.error("壁纸加载失败:", e);
    // 兜底
    if (!store.bgList || store.bgList.length === 0) {
      const list = [];
      for (let i = 1; i <= 20; i++) {
        list.push({
          id: i,
          name: defaultBgNames[i - 1] || `默认壁纸 ${i}`,
          url: `/images/background${i}.jpg`,
          type: 'local'
        });
      }
      store.bgList = list;
    }
  }
};

// 更换壁纸链接
const changeBg = async () => {
  // 确保列表已初始化
  if (!store.bgList || store.bgList.length === 0) {
    await initBgList();
  }

  // 筛选可用列表 (如果有锁定，只在锁定中随机)
  let list = store.bgList;
  if (store.bgLockIds && store.bgLockIds.length > 0) {
    list = list.filter(item => store.bgLockIds.includes(item.id));
  }
  
  // 如果筛选后为空（比如锁定的都被删了），则重置为全部
  if (list.length === 0) {
    list = store.bgList;
  }

  if (list.length === 0) {
     // 极端情况：列表全空
     bgUrl.value = "/images/background1.jpg";
     return;
  }

  const randomIndex = Math.floor(Math.random() * list.length);
  const item = list[randomIndex];
  bgUrl.value = item.url;

  console.log("设置壁纸URL:", item.url);
};

onMounted(async () => {
  await initBgList();
  changeBg();
});

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
    changeBg();
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

.el-dialog__footer .el-button--default {
  background: #222 !important;
  color: #fff !important;
  border: 1px solid #444 !important;
}

.el-popover .el-button--default {
  background: #222 !important;
  color: #fff !important;
  border: 1px solid #444 !important;
}
</style>
