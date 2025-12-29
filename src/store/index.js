import { defineStore } from "pinia";

export const mainStore = defineStore("main", {
  state: () => {
    return {
  imgLoadStatus: true, // 壁纸加载状态，默认开启以避免界面被遮挡
      innerWidth: null, // 当前窗口宽度
      coverType: "0", // 壁纸种类
      siteStartShow: false, // 建站日期显示
      musicClick: false, // 音乐链接是否跳转
      musicIsOk: false, // 音乐是否加载完成
      musicVolume: 0.7, // 音乐音量;
      musicOpenState: false, // 音乐面板开启状态
      backgroundShow: false, // 壁纸展示状态
      boxOpenState: false, // 盒子开启状态
      mobileOpenState: false, // 移动端开启状态
      mobileFuncState: false, // 移动端功能区开启状态
      setOpenState: false, // 设置页面开启状态
      playerState: false, // 当前播放状态
      playerTitle: "未播放", // 当前播放歌曲名
      playerArtist: "未知艺术家", // 当前播放歌手名
      playerLrc: "暂无歌词", // 当前播放歌词
      playerLrcShow: true, // 是否显示底栏歌词
      footerBlur: true, // 底栏模糊
      playerAutoplay: false, // 是否自动播放
      playerLoop: "all", // 循环播放 "all", "one", "none"
      playerOrder: "list", // 循环顺序 "list", "random"
      playList: [], // 播放列表
      currentSongIndex: 0, // 当前播放索引
      randomLockIds: [], // 随机播放锁定歌曲ID列表
    };
  },
  getters: {
    // 获取歌词
    getPlayerLrc(state) {
      return state.playerLrc;
    },
    // 获取歌曲信息
    getPlayerData(state) {
      return {
        name: state.playerTitle,
        artist: state.playerArtist,
      };
    },
    // 获取页面宽度
    getInnerWidth(state) {
      return state.innerWidth;
    },
  },
  actions: {
    // 更改当前页面宽度
    setInnerWidth(value) {
      this.innerWidth = value;
      if (value >= 720) {
        this.mobileOpenState = false;
        this.mobileFuncState = false;
      }
    },
    // 更改播放状态
    setPlayerState(value) {
      this.playerState = value;
    },
    // 更改歌词
    setPlayerLrc(value) {
      this.playerLrc = value;
    },
    // 更改歌曲数据
    setPlayerData(title, artist) {
      this.playerTitle = title;
      this.playerArtist = artist;
    },
    // 更改壁纸加载状态
    setImgLoadStatus(value) {
      this.imgLoadStatus = value;
    },
    // 设置播放列表
    setPlayList(list) {
      this.playList = list;
    },
    // 设置当前播放索引
    setCurrentSongIndex(index) {
      this.currentSongIndex = index;
    }
  },
  persist: {
    key: "data",
    storage: window.localStorage,
    paths: [
      "coverType",
      "musicVolume",
      "siteStartShow",
      "musicClick",
      "playerLrcShow",
      "footerBlur",
      "playerAutoplay",
      "playerLoop",
      "playerOrder",
      "currentSongIndex",
      "randomLockIds"
    ],
  },
});
