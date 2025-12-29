<template>
  <!-- 全局音乐容器 -->
  <div class="music-global">
    <!-- 迷你悬浮球 (当播放器收起时显示) -->
    <div 
      class="music-mini" 
      v-show="!isExpanded" 
      @click="isExpanded = true"
      :class="{ playing: store.playerState }"
    >
      <div class="vinyl-disk">
        <div class="vinyl-cover">
           <music-one theme="filled" size="20" fill="#fff" />
        </div>
      </div>
    </div>

    <!-- 展开的播放器卡片 -->
    <Transition name="slide-up">
      <div class="music-player" v-show="isExpanded">
        <!-- 顶部栏 -->
        <div class="header">
          <div class="info">
            <span class="name" :title="store.getPlayerData.name">{{ store.getPlayerData.name || '未播放' }}</span>
            <span class="artist">{{ store.getPlayerData.artist || '---' }}</span>
          </div>
          <div class="ops">
            <down theme="filled" size="20" fill="#fff" class="op-btn" @click="isExpanded = false" title="收起" />
          </div>
        </div>

        <!-- 进度条 -->
        <div class="progress-bar">
          <span class="time">{{ formatTime(currentTime) }}</span>
          <el-slider 
            v-model="currentTime" 
            :max="duration" 
            :show-tooltip="false" 
            @change="onProgressChange" 
            @input="isDragging = true"
            size="small"
          />
          <span class="time">{{ formatTime(duration) }}</span>
        </div>

        <!-- 控制区 -->
        <div class="controls">
          <div class="main-ctrl">
            <go-start theme="filled" size="28" fill="#fff" class="ctrl-btn" @click="changeSong(-1)" />
            <div class="play-btn" @click="togglePlay">
              <play-one theme="filled" size="40" fill="#fff" v-show="!store.playerState" />
              <pause theme="filled" size="40" fill="#fff" v-show="store.playerState" />
            </div>
            <go-end theme="filled" size="28" fill="#fff" class="ctrl-btn" @click="changeSong(1)" />
          </div>
          
          <div class="sub-ctrl">
            <!-- 音量 -->
            <div class="volume-wrap">
              <volume-small theme="filled" size="20" fill="#fff" class="vol-icon" />
              <el-slider v-model="volumeNum" :min="0" :max="1" :step="0.01" @input="changeVolume" class="vol-slider" />
            </div>
            <!-- 列表按钮 -->
            <list theme="filled" size="22" fill="#fff" class="list-btn" @click="openMusicList" title="播放列表" />
          </div>
        </div>
      </div>
    </Transition>

    <!-- 播放列表弹窗 (独立层级) -->
    <Transition name="fade">
      <div class="music-list-modal" v-show="musicListShow" @click="closeMusicList">
        <div class="list-panel" @click.stop>
          <div class="panel-header">
            <h3>播放列表 ({{ playList.length }})</h3>
            <close theme="outline" size="24" fill="#fff" class="close-btn" @click="closeMusicList" />
          </div>
          
          <div class="panel-tools">
            <div class="tool-btn" @click="triggerFileInput">
              <folder-open theme="outline" size="16" />
              <span>本地上传</span>
            </div>
            <div class="tool-btn" @click="showUrlInput = true">
              <link-one theme="outline" size="16" />
              <span>网络链接</span>
            </div>
            <div class="tool-btn danger" @click="clearAll">
              <delete theme="outline" size="16" />
              <span>清空列表</span>
            </div>
          </div>

          <div class="song-list">
            <div v-if="playList.length === 0" class="empty">
              <music theme="outline" size="48" fill="#ffffff80" />
              <p>暂无歌曲</p>
            </div>
            <ul v-else>
              <li 
                v-for="(song, index) in playList" 
                :key="song.id"
                :class="{ active: index === store.currentSongIndex }"
                @click="playSong(index)"
              >
                <span class="index">{{ index + 1 }}</span>
                <div class="info">
                  <div class="name">{{ song.name }}</div>
                  <div class="artist">{{ song.artist }}</div>
                </div>
                <delete theme="filled" size="16" fill="#ff4d4f" class="del-btn" @click.stop="removeSong(song.id)" />
              </li>
            </ul>
          </div>
        </div>
      </div>
    </Transition>

    <!-- 隐藏元素 -->
    <audio
      ref="audioRef"
      :src="currentUrl"
      @ended="onEnded"
      @error="onError"
      @timeupdate="onTimeUpdate"
      @loadedmetadata="onLoadedMetadata"
    ></audio>
    <input type="file" ref="fileInput" accept="audio/*" multiple style="display: none" @change="handleFileChange" />

    <!-- URL弹窗 -->
    <el-dialog v-model="showUrlInput" title="添加网络音乐" width="400px" append-to-body center class="music-dialog" :show-close="false">
      <el-form :model="urlForm" label-width="50px" class="music-form">
        <el-form-item label="歌名">
          <el-input v-model="urlForm.name" placeholder="请输入歌名" />
        </el-form-item>
        <el-form-item label="歌手">
          <el-input v-model="urlForm.artist" placeholder="请输入歌手" />
        </el-form-item>
        <el-form-item label="链接">
          <el-input v-model="urlForm.url" placeholder="http(s)://..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showUrlInput = false" class="cancel-btn">取消</el-button>
          <el-button type="primary" @click="addUrlSong" class="confirm-btn">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick, watch } from 'vue';
import {
  MusicOne, PlayOne, Pause, GoStart, GoEnd, 
  VolumeSmall, List, CloseOne, Close, Down,
  FolderOpen, LinkOne, Delete, Music
} from "@icon-park/vue-next";
import { mainStore } from "@/store";
import { addSong, getAllSongs, deleteSong, clearSongs } from "@/utils/musicDb";
import { ElMessage, ElMessageBox } from "element-plus";
// import defaultMusic from "@/assets/defaultMusic.json";

const store = mainStore();
const audioRef = ref(null);
const fileInput = ref(null);
const isExpanded = ref(false); // 默认收起
const musicListShow = ref(false);
const showUrlInput = ref(false);
const playList = ref([]);
const currentUrl = ref("");
const volumeNum = ref(store.musicVolume);

// 进度条
const currentTime = ref(0);
const duration = ref(0);
const isDragging = ref(false);

const urlForm = reactive({ name: "", artist: "", url: "" });

// 初始化
onMounted(async () => {
  await loadPlayList();
  if (audioRef.value) audioRef.value.volume = volumeNum.value;
  if (playList.value.length > 0) loadSong(store.currentSongIndex);
  window.$openList = openMusicList;
});

// 核心逻辑
const loadPlayList = async () => {
  try {
    playList.value = await getAllSongs();
    // 如果列表为空，加载本地预设音乐
    if (playList.value.length === 0) {
      const modules = import.meta.glob('@/assets/music/*.{mp3,flac,wav,m4a}', { eager: true, as: 'url' });
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
      }
      playList.value = await getAllSongs();
    }
    store.setPlayList(playList.value);
  } catch (e) { console.error(e); }
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
  transition: transform 0.3s;
  
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
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 展开的播放器 */
.music-player {
  position: fixed;
  bottom: 20px;
  left: 20px;
  width: 320px;
  background: rgba(0, 0, 0, 0.8);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  padding: 16px;
  z-index: 9999;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #fff;

  .header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
    .info {
      flex: 1;
      overflow: hidden;
      .name { display: block; font-size: 15px; font-weight: bold; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
      .artist { display: block; font-size: 12px; opacity: 0.6; margin-top: 2px; }
    }
    .ops {
      display: flex;
      gap: 10px;
      .op-btn { cursor: pointer; opacity: 0.7; &:hover { opacity: 1; } }
    }
  }

  .progress-bar {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;
    .time { font-size: 12px; opacity: 0.6; width: 35px; text-align: center; }
    .el-slider {
      flex: 1;
      --el-slider-main-bg-color: #fff;
      --el-slider-runway-bg-color: rgba(255, 255, 255, 0.2);
      --el-slider-button-size: 12px;
    }
  }

  .controls {
    .main-ctrl {
      display: flex;
      justify-content: center;
      align-items: center;
      gap: 24px;
      margin-bottom: 16px;
      .ctrl-btn { cursor: pointer; opacity: 0.8; &:hover { opacity: 1; transform: scale(1.1); } transition: 0.2s; }
      .play-btn { cursor: pointer; &:hover { transform: scale(1.1); } transition: 0.2s; }
    }
    .sub-ctrl {
      display: flex;
      justify-content: space-between;
      align-items: center;
      .volume-wrap {
        display: flex;
        align-items: center;
        gap: 8px;
        width: 120px;
        .vol-slider { flex: 1; --el-slider-main-bg-color: #fff; --el-slider-runway-bg-color: rgba(255, 255, 255, 0.2); --el-slider-button-size: 10px; }
      }
      .list-btn { cursor: pointer; opacity: 0.8; &:hover { opacity: 1; } }
    }
  }
}

/* 播放列表弹窗 - 暗黑磨砂风格 */
.music-list-modal {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: rgba(0, 0, 0, 0.6);
  z-index: 10000;
  display: flex;
  justify-content: center;
  align-items: center;
  
  .list-panel {
    width: 400px;
    height: 500px;
    background: rgba(30, 30, 30, 0.9);
    backdrop-filter: blur(20px);
    border-radius: 16px;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    box-shadow: 0 12px 48px rgba(0,0,0,0.5);
    border: 1px solid rgba(255,255,255,0.1);
    color: #fff;
    
    .panel-header {
      padding: 16px;
      border-bottom: 1px solid rgba(255,255,255,0.1);
      display: flex;
      justify-content: space-between;
      align-items: center;
      h3 { margin: 0; font-size: 16px; color: #fff; }
      .close-btn { cursor: pointer; opacity: 0.7; &:hover { opacity: 1; } }
    }
    
    .panel-tools {
      padding: 12px 16px;
      background: rgba(255,255,255,0.05);
      display: flex;
      gap: 10px;
      
      .tool-btn {
        flex: 1;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 6px;
        background: rgba(255,255,255,0.1);
        padding: 8px 0;
        border-radius: 8px;
        cursor: pointer;
        font-size: 12px;
        transition: 0.2s;
        
        &:hover { background: rgba(255,255,255,0.2); }
        
        &.danger {
          color: #ff4d4f;
          background: rgba(255, 77, 79, 0.1);
          &:hover { background: rgba(255, 77, 79, 0.2); }
        }
      }
    }
    
    .song-list {
      flex: 1;
      overflow-y: auto;
      
      /* 滚动条样式 */
      &::-webkit-scrollbar { width: 4px; }
      &::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.2); border-radius: 2px; }
      
      .empty {
        height: 100%;
        display: flex;
        flex-direction: column;
        justify-content: center;
        align-items: center;
        color: rgba(255,255,255,0.3);
        gap: 10px;
      }
      ul {
        list-style: none;
        padding: 0;
        margin: 0;
        li {
          display: flex;
          align-items: center;
          padding: 12px 16px;
          border-bottom: 1px solid rgba(255,255,255,0.05);
          cursor: pointer;
          transition: 0.2s;
          
          &:hover { background: rgba(255,255,255,0.08); }
          &.active { 
            background: rgba(255,255,255,0.12); 
            .index, .name { color: #409eff; font-weight: bold; } 
          }
          
          .index { width: 30px; color: rgba(255,255,255,0.4); font-size: 12px; }
          .info {
            flex: 1;
            min-width: 0;
            .name { font-size: 14px; color: #fff; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
            .artist { font-size: 12px; color: rgba(255,255,255,0.5); margin-top: 2px; }
          }
          .del-btn { opacity: 0; transition: 0.2s; }
          &:hover .del-btn { opacity: 1; }
        }
      }
    }
  }
}

/* 动画 */
.slide-up-enter-active, .slide-up-leave-active { transition: all 0.3s ease; }
.slide-up-enter-from, .slide-up-leave-to { transform: translateY(20px); opacity: 0; }
.fade-enter-active, .fade-leave-active { transition: opacity 0.3s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>

<style lang="scss">
.music-dialog {
  background: rgba(30, 30, 30, 0.8) !important;
  backdrop-filter: blur(20px);
  border-radius: 16px !important;
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 12px 48px rgba(0, 0, 0, 0.6) !important;

  .el-dialog__header {
    margin-right: 0 !important;
    border-bottom: 1px solid rgba(255, 255, 255, 0.1);
    .el-dialog__title {
      color: #fff !important;
      font-size: 16px;
    }
  }

  .el-dialog__body {
    padding: 20px !important;
    
    .el-form-item__label {
      color: rgba(255, 255, 255, 0.8) !important;
    }
    
    .el-input__wrapper {
      background-color: rgba(255, 255, 255, 0.1) !important;
      box-shadow: none !important;
      border: 1px solid rgba(255, 255, 255, 0.1);
      transition: 0.3s;
      
      &:hover, &.is-focus {
        background-color: rgba(255, 255, 255, 0.15) !important;
        border-color: rgba(255, 255, 255, 0.3);
      }
      
      .el-input__inner {
        color: #fff !important;
        &::placeholder {
          color: rgba(255, 255, 255, 0.3);
        }
      }
    }
  }

  .el-dialog__footer {
    border-top: 1px solid rgba(255, 255, 255, 0.1);
    padding: 16px !important;
    
    .el-button {
      border: none;
      &.cancel-btn {
        background: rgba(255, 255, 255, 0.1);
        color: #fff;
        &:hover { background: rgba(255, 255, 255, 0.2); }
      }
      &.confirm-btn {
        background: #409eff;
        color: #fff;
        &:hover { background: #66b1ff; }
      }
    }
  }
}

.music-message-box {
  background: rgba(30, 30, 30, 0.8) !important;
  backdrop-filter: blur(20px);
  border-radius: 16px !important;
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 12px 48px rgba(0, 0, 0, 0.6) !important;
  padding-bottom: 20px !important;
  
  .el-message-box__header {
    .el-message-box__title {
      color: #fff !important;
    }
    .el-message-box__close {
      color: rgba(255, 255, 255, 0.6) !important;
      &:hover { color: #fff !important; }
    }
  }
  
  .el-message-box__content {
    color: rgba(255, 255, 255, 0.8) !important;
  }
  
  .el-message-box__btns {
    .el-button {
      border: none;
      &--default {
        background: transparent !important;
        border: 1px solid rgba(255, 255, 255, 0.2) !important;
        color: #fff !important;
        &:hover { 
          background: rgba(255, 255, 255, 0.1) !important;
          border-color: rgba(255, 255, 255, 0.3) !important;
          color: #fff !important;
        }
      }
      &--primary {
        background: #409eff;
        color: #fff;
        &:hover { background: #66b1ff; }
      }
    }
  }
}
</style>
