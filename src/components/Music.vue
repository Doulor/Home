<template>
<<<<<<< Updated upstream
  <!-- 音乐控制面板 -->
  <div
    class="music"
    @mouseenter="volumeShow = true"
    @mouseleave="volumeShow = false"
    v-show="store.musicOpenState"
  >
    <div class="btns">
      <span @click="openMusicList()">音乐列表</span>
      <span @click="store.musicOpenState = false">回到一言</span>
    </div>
    <div class="control">
      <go-start theme="filled" size="30" fill="#efefef" @click="changeMusicIndex(0)" />
      <div class="state" @click="changePlayState">
        <play-one theme="filled" size="50" fill="#efefef" v-show="!store.playerState" />
        <pause theme="filled" size="50" fill="#efefef" v-show="store.playerState" />
      </div>
      <go-end theme="filled" size="30" fill="#efefef" @click="changeMusicIndex(1)" />
    </div>
    <div class="menu">
      <div class="name" v-show="!volumeShow">
        <span>{{
          store.getPlayerData.name
            ? store.getPlayerData.name + " - " + store.getPlayerData.artist
            : "未播放音乐"
        }}</span>
      </div>
      <div class="volume" v-show="volumeShow">
        <div class="icon">
          <volume-mute theme="filled" size="24" fill="#efefef" v-if="volumeNum == 0" />
          <volume-small
            theme="filled"
            size="24"
            fill="#efefef"
            v-else-if="volumeNum > 0 && volumeNum < 0.7"
          />
          <volume-notice theme="filled" size="24" fill="#efefef" v-else />
=======
  <!-- 全局音乐容器 -->
  <div class="music-global">
    <!-- 迷你悬浮球 (当播放器收起时显示) -->
    <div 
      class="music-mini" 
      v-show="!isExpanded" 
      @click.stop="handleMiniClick"
      :class="{ playing: store.playerState, 'mobile-idle': isIdle && isMobile }"
      ref="musicMiniRef"
    >
      <div class="vinyl-disk">
        <div class="vinyl-cover">
           <music-one theme="filled" size="20" fill="#fff" />
>>>>>>> Stashed changes
        </div>
        <el-slider v-model="volumeNum" :show-tooltip="false" :min="0" :max="1" :step="0.01" />
      </div>
    </div>
  </div>
  <!-- 音乐列表弹窗 -->
  <Transition name="fade" mode="out-in">
    <div class="music-list" v-show="musicListShow" @click="closeMusicList()">
      <Transition name="zoom">
        <div class="list" v-show="musicListShow" @click.stop>
          <close-one
            class="close"
            theme="filled"
            size="28"
            fill="#ffffff60"
            @click="closeMusicList()"
          />
          <Player
            ref="playerRef"
            :songServer="playerData.server"
            :songType="playerData.type"
            :songId="playerData.id"
            :volume="volumeNum"
          />
        </div>
      </Transition>
    </div>
  </Transition>
</template>

<script setup>
<<<<<<< Updated upstream
=======
import { ref, reactive, onMounted, onUnmounted, nextTick, watch, computed } from 'vue';
>>>>>>> Stashed changes
import {
  GoStart,
  PlayOne,
  Pause,
  GoEnd,
  CloseOne,
  VolumeMute,
  VolumeSmall,
  VolumeNotice,
} from "@icon-park/vue-next";
import Player from "@/components/Player.vue";
import { mainStore } from "@/store";
const store = mainStore();

// 音量条数据
const volumeShow = ref(false);
const volumeNum = ref(store.musicVolume ? store.musicVolume : 0.7);

// 播放列表数据
const musicListShow = ref(false);
<<<<<<< Updated upstream
const playerRef = ref(null);
const playerData = reactive({
  server: import.meta.env.VITE_SONG_SERVER,
  type: import.meta.env.VITE_SONG_TYPE,
  id: import.meta.env.VITE_SONG_ID,
});

// 开启播放列表
const openMusicList = () => {
  musicListShow.value = true;
  playerRef.value.toggleList();
=======
const showUrlInput = ref(false);
const showSpaceTip = ref(false); // 空格提示气泡
const playList = ref([]);
const currentUrl = ref("");
const volumeNum = ref(store.musicVolume);
const isMobile = computed(() => store.innerWidth < 720);
const isIdle = ref(false);
let idleTimer = null;

// 进度条
const currentTime = ref(0);
const duration = ref(0);
const isDragging = ref(false);

const urlForm = reactive({ name: "", artist: "", url: "" });

// 悬浮球点击处理
const handleMiniClick = () => {
  if (isMobile.value && isIdle.value) {
    // 移动端闲置状态下，先唤醒
    isIdle.value = false;
    clearTimeout(idleTimer);
    // 等待动画完成后再展开 (300ms)
    setTimeout(() => {
      isExpanded.value = true;
    }, 300);
  } else {
    // 正常展开
    isExpanded.value = true;
  }
};

// 点击外部关闭播放器
const handleClickOutside = (event) => {
  if (isExpanded.value) {
    // 如果点击的是播放器内部，不做处理
    if (musicPlayerRef.value && musicPlayerRef.value.contains(event.target)) return;
    // 如果点击的是悬浮球，不做处理
    if (musicMiniRef.value && musicMiniRef.value.contains(event.target)) return;
    // 如果点击的是播放列表弹窗，不做处理
    if (musicListShow.value) return;
    
    isExpanded.value = false;
  }
>>>>>>> Stashed changes
};

// 关闭播放列表
const closeMusicList = () => {
  musicListShow.value = false;
  playerRef.value.toggleList();
};

// 音乐播放暂停
const changePlayState = () => {
  playerRef.value.playToggle();
};

<<<<<<< Updated upstream
// 音乐上下曲
const changeMusicIndex = (type) => {
  playerRef.value.changeSong(type);
};

onMounted(() => {
  // 空格键事件
  window.addEventListener("keydown", (e) => {
    if (!store.musicIsOk) {
      return ;
=======
  // 随机提示逻辑 (30%概率) - 仅在非移动端显示
  if (!isMobile.value && !store.spaceTipSeen && Math.random() < 0.8) {
    showSpaceTip.value = true;
    // 8秒后自动消失
    setTimeout(() => {
      showSpaceTip.value = false;
    }, 8000);
  }

  // 移动端闲置检测
  if (isMobile.value) {
    resetIdleTimer();
  }

  await loadPlayList();

  // 如果列表为空，自动加载预设音乐
  if (playList.value.length === 0) {
    await loadDefaultMusic();
  }

  if (audioRef.value) audioRef.value.volume = volumeNum.value;
  
  // 随机播放逻辑
  if (playList.value.length > 0) {
    // 获取随机池
    let pool = playList.value;
    if (store.randomLockIds.length > 0) {
      const locked = playList.value.filter(s => store.randomLockIds.includes(s.id));
      if (locked.length > 0) pool = locked;
>>>>>>> Stashed changes
    }
    if (e.code == "Space") {
      changePlayState();
    }
  });
  // 挂载方法至 window
  window.$openList = openMusicList;
});

<<<<<<< Updated upstream
// 监听音量变化
watch(
  () => volumeNum.value,
  (value) => {
    store.musicVolume = value;
    playerRef.value.changeVolume(store.musicVolume);
  },
);
</script>

<style lang="scss" scoped>
.music {
  width: 100%;
  height: 100%;
  background: #00000040;
=======
onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown);
  document.removeEventListener('click', handleClickOutside);
  if (isMobile.value) {
    clearTimeout(idleTimer);
  }
  // document.removeEventListener('click', handleGlobalClick);
});

// 闲置计时器重置
const resetIdleTimer = () => {
  if (!isMobile.value || isExpanded.value) return;
  isIdle.value = false;
  clearTimeout(idleTimer);
  idleTimer = setTimeout(() => {
    isIdle.value = true;
  }, 2000);
};

// 监听展开状态，处理闲置计时器
watch(isExpanded, (val) => {
  if (isMobile.value) {
    if (val) {
      clearTimeout(idleTimer);
      isIdle.value = false;
    } else {
      resetIdleTimer();
    }
  }
});

// 监听移动端状态变化
watch(isMobile, (val) => {
  if (val) {
    resetIdleTimer();
  } else {
    clearTimeout(idleTimer);
    isIdle.value = false;
  }
});

// 核心逻辑
const loadPlayList = async () => {
  try {
    playList.value = await getAllSongs();
    store.setPlayList(playList.value);
  } catch (e) { console.error(e); }
};

const loadDefaultMusic = async () => {
  try {
    const modules = import.meta.glob('@/assets/music/*.{mp3,flac,wav,m4a}', { eager: true, as: 'url' });
    let count = 0;
    for (const path in modules) {
      const url = modules[path];
      // 从文件名解析歌名和歌手 (假设格式: 歌手 - 歌名.mp3 或 歌名.mp3)
      const fileName = path.split('/').pop().replace(/\.[^/.]+$/, "");
      let artist = "本地音乐";
      let name = fileName;
      
      if (fileName.includes(" - ")) {
        const parts = fileName.split(" - ");
        artist = parts[0];
        name = parts.slice(1).join(" - ");
      }
      
      await addSong({ name, artist, url, created: new Date() });
      count++;
    }
    if (count > 0) {
      await loadPlayList();
      ElMessage.success(`已加载 ${count} 首预设音乐`);
      // 加载第一首
      if (playList.value.length > 0) {
        loadSong(0);
      }
    } else {
      ElMessage.warning("未找到预设音乐文件");
    }
  } catch (e) {
    console.error(e);
    ElMessage.error("加载失败");
  }
};

const loadSong = (index) => {
  if (index < 0 || index >= playList.value.length) return;
  const song = playList.value[index];
  store.setCurrentSongIndex(index);
  store.setPlayerData(song.name, song.artist);
  currentTime.value = 0;
  duration.value = 0;
  currentUrl.value = song.blob ? URL.createObjectURL(song.blob) : song.url;
};

const playSong = (index) => {
  loadSong(index);
  nextTick(() => {
    audioRef.value.play();
    store.setPlayerState(true);
    closeMusicList(); // 选中歌曲后自动关闭列表
  });
};

const togglePlay = () => {
  if (!audioRef.value) return;
  if (audioRef.value.paused) {
    audioRef.value.play();
    store.setPlayerState(true);
  } else {
    audioRef.value.pause();
    store.setPlayerState(false);
  }
};

const changeSong = (dir) => {
  let newIndex = store.currentSongIndex + dir;
  if (newIndex < 0) newIndex = playList.value.length - 1;
  if (newIndex >= playList.value.length) newIndex = 0;
  playSong(newIndex);
};

const onEnded = () => {
  store.setPlayerState(false);
  if (store.playerLoop === 'one') {
    audioRef.value.play();
    store.setPlayerState(true);
  } else {
    changeSong(1);
  }
};

const onError = () => {
  if (store.playerState) ElMessage.error("播放出错");
  store.setPlayerState(false);
};

const onLoadedMetadata = () => {
  if (audioRef.value) duration.value = audioRef.value.duration;
};

const onTimeUpdate = () => {
  if (!isDragging.value && audioRef.value) currentTime.value = audioRef.value.currentTime;
};

const onProgressChange = (val) => {
  isDragging.value = false;
  if (audioRef.value) audioRef.value.currentTime = val;
};

const changeVolume = (val) => {
  if (audioRef.value) audioRef.value.volume = val;
  store.musicVolume = val;
};

const formatTime = (t) => {
  if (!t) return '00:00';
  const m = Math.floor(t / 60);
  const s = Math.floor(t % 60);
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
};

// 列表操作
const openMusicList = () => musicListShow.value = true;
const closeMusicList = () => musicListShow.value = false;

const triggerFileInput = () => fileInput.value.click();
const handleFileChange = async (e) => {
  const files = e.target.files;
  if (!files || !files.length) return;
  let count = 0;
  for (let i = 0; i < files.length; i++) {
    const file = files[i];
    const name = file.name.replace(/\.[^/.]+$/, "");
    try {
      await addSong({ name, artist: "本地音乐", blob: file, created: new Date() });
      count++;
    } catch (e) {}
  }
  if (count > 0) {
    await loadPlayList();
    ElMessage.success(`已添加 ${count} 首`);
    if (playList.value.length === count) loadSong(0);
  }
  e.target.value = "";
};

const addUrlSong = async () => {
  if (!urlForm.name || !urlForm.url) return ElMessage.warning("请填写完整");
  try {
    await addSong({ name: urlForm.name, artist: urlForm.artist || "网络", url: urlForm.url, created: new Date() });
    await loadPlayList();
    ElMessage.success("添加成功");
    showUrlInput.value = false;
    urlForm.name = ""; urlForm.artist = ""; urlForm.url = "";
    if (playList.value.length === 1) loadSong(0);
  } catch (e) { ElMessage.error("添加失败"); }
};

const removeSong = async (id) => {
  await deleteSong(id);
  await loadPlayList();
  if (playList.value.length === 0) {
    audioRef.value.pause();
    store.setPlayerState(false);
    store.setPlayerData(null, null);
    currentUrl.value = "";
  } else if (store.currentSongIndex >= playList.value.length) {
    store.setCurrentSongIndex(0);
    loadSong(0);
  } else {
    loadSong(store.currentSongIndex);
  }
};

const clearAll = () => {
  ElMessageBox.confirm(
    '确定要清空播放列表吗？',
    '提示',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
      customClass: 'music-message-box',
    }
  ).then(async () => {
    await clearSongs();
    await loadPlayList();
    audioRef.value.pause();
    store.setPlayerState(false);
    store.setPlayerData(null, null);
    currentUrl.value = "";
  }).catch(() => {});
};

const toggleLock = (id) => {
  const index = store.randomLockIds.indexOf(id);
  if (index === -1) {
    store.randomLockIds.push(id);
  } else {
    store.randomLockIds.splice(index, 1);
  }
};

watch(() => store.musicVolume, (val) => {
  volumeNum.value = val;
  if (audioRef.value) audioRef.value.volume = val;
});
</script>

<style lang="scss" scoped>
/* 迷你悬浮球 - 黑胶唱片风格 */
.music-mini {
  position: fixed;
  bottom: 20px;
  left: 20px;
  width: 48px;
  height: 48px;
  background: #1a1a1a;
  border-radius: 50%;
  display: flex;
  justify-content: center;
  align-items: center;
  cursor: pointer;
  z-index: 9998;
  box-shadow: 0 4px 12px rgba(0,0,0,0.5);
  border: 2px solid rgba(255,255,255,0.1);
  
  // 开场动画
  transform: scale(1.2);
  transition: all 0.3s;
  animation: fade-blur-main-in 0.65s cubic-bezier(0.25, 0.46, 0.45, 0.94) forwards;
  animation-delay: 0.5s;
  
  &:hover { transform: scale(1.1); }
  
  .vinyl-disk {
    width: 100%;
    height: 100%;
    border-radius: 50%;
    background: repeating-radial-gradient(
      #111 0, 
      #111 2px, 
      #222 3px, 
      #222 4px
    );
    display: flex;
    justify-content: center;
    align-items: center;
    
    .vinyl-cover {
      width: 24px;
      height: 24px;
      background: #333;
      border-radius: 50%;
      display: flex;
      justify-content: center;
      align-items: center;
      border: 1px solid rgba(255,255,255,0.2);
    }
  }
  
  &.playing .vinyl-disk {
    animation: rotate 4s linear infinite;
  }
  
  /* 移动端闲置状态 */
  &.mobile-idle {
    left: -24px;
    opacity: 0.5;
    pointer-events: auto; /* 确保仍可点击 */
  }
}

/* 提示气泡 */
.music-tip-bubble {
  position: fixed;
  bottom: 80px;
  left: 20px;
  background: rgba(0, 0, 0, 0.8);
  color: #fff;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 12px;
  z-index: 9999;
  pointer-events: none;
>>>>>>> Stashed changes
  backdrop-filter: blur(10px);
  border-radius: 6px;
  padding: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-direction: column;
  animation: fade 0.5s;
  .btns {
    display: flex;
    align-items: center;
    margin-bottom: 6px;
    span {
      background: #ffffff26;
      padding: 2px 8px;
      border-radius: 6px;
      margin: 0px 6px;
      text-overflow: ellipsis;
      overflow-x: hidden;
      white-space: nowrap;
      &:hover {
        background: #ffffff4d;
      }
    }
  }
  .control {
    display: flex;
    flex-direction: row;
    align-items: center;
    justify-content: space-evenly;
    width: 100%;
    .state {
      .i-icon {
        width: 50px;
        height: 50px;
        display: block;
      }
    }
    .i-icon {
      width: 36px;
      height: 36px;
      display: flex;
      border-radius: 6px;
      align-items: center;
      justify-content: center;
      border-radius: 6px;
      transform: scale(1);
      &:hover {
        background: #ffffff33;
      }
      &:active {
        transform: scale(0.95);
      }
    }
  }
  .menu {
    height: 26px;
    width: 100%;
    line-height: 26px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    .name {
      width: 100%;
      text-align: center;
      text-overflow: ellipsis;
      overflow-x: hidden;
      white-space: nowrap;
      animation: fade 0.3s;
    }
    .volume {
      width: 100%;
      padding: 0 12px;
      display: flex;
      align-items: center;
      flex-direction: row;
      animation: fade 0.3s;
      .icon {
        margin-right: 12px;
        span {
          width: 24px;
          height: 24px;
          display: block;
        }
      }
      :deep(*) {
        transition: none;
      }
      :deep(.el-slider__button) {
        transition: 0.3s;
      }
      .el-slider {
        margin-right: 12px;
        --el-slider-main-bg-color: #efefef;
        --el-slider-runway-bg-color: #ffffff40;
        --el-slider-button-size: 16px;
      }
    }
  }
}
.music-list {
  position: fixed;
  top: 0;
  left: 0;
  margin: auto;
  width: 100%;
  height: 100%;
  background-color: #00000080;
  backdrop-filter: blur(20px);
  z-index: 1;
  .list {
    position: absolute;
    display: flex;
    align-items: center;
    justify-content: center;
    top: calc(50% - 300px);
    left: calc(50% - 320px);
    width: 640px;
    height: 600px;
    background-color: #ffffff66;
    border-radius: 6px;
    z-index: 999;
    @media (max-width: 720px) {
      left: calc(50% - 45%);
      width: 90%;
    }
    .close {
      position: absolute;
      top: 12px;
      right: 12px;
      width: 28px;
      height: 28px;
      display: block;
      &:hover {
        transform: scale(1.2);
      }
      &:active {
        transform: scale(0.95);
      }
    }
  }
}

// 弹窗动画
.zoom-enter-active {
  animation: zoom 0.4s ease-in-out;
}
.zoom-leave-active {
  animation: zoom 0.3s ease-in-out reverse;
}
@keyframes zoom {
  0% {
    opacity: 0;
    transform: scale(0) translateY(-600px);
  }
  100% {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}
</style>
