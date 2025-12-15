<template>
  <div v-if="siteLinks[0]" class="links">
    <div class="line">
      <Icon size="20">
        <Link />
      </Icon>
      <span class="title">网站列表</span>
    </div>
    <!-- 网站列表 -->
    <Swiper
      v-if="siteLinks[0]"
      :modules="[Pagination, Mousewheel]"
      :slides-per-view="1"
      :space-between="40"
      :pagination="{
        el: '.swiper-pagination',
        clickable: true,
        bulletElement: 'div',
      }"
      :mousewheel="true"
    >
      <SwiperSlide v-for="(site, siteIndex) in siteLinksList" :key="siteIndex">
        <Transition name="slide-up" appear>
          <el-row class="link-all" :gutter="20">
            <el-col v-for="(item, index) in site" :span="8" :key="item">
              <Transition
                :name="index % 2 === 0 ? 'slide-right' : 'slide-left'"
                appear
                :duration="{ enter: 500 + index * 100 }"
              >
                <div
                  class="item cards glow-hover"
                  :style="index < 3 ? 'margin-bottom: 20px' : null"
                  @click="jumpLink(item)"
                >
                  <Icon size="26">
                    <component :is="siteIcon[item.icon]" />
                  </Icon>
                  <span class="name text-hidden">{{ item.name }}</span>
                </div>
              </Transition>
            </el-col>
          </el-row>
        </Transition>
      </SwiperSlide>
      <div class="swiper-pagination" />
    </Swiper>
  </div>
</template>

<script setup>
import { Icon } from "@vicons/utils";
// 可前往 https://www.xicons.org 自行挑选并在此处引入
import { Link, Blog, Atom, Cloud, Tools, CloudUploadAlt, Fire, Music, Surprise, Amilia, Meetup, Database, Airbnb } from "@vicons/fa"; // 注意使用正确的类别
import { mainStore } from "@/store";
import { Swiper, SwiperSlide } from "swiper/vue";
import { Pagination, Mousewheel } from "swiper";
import siteLinks from "@/assets/siteLinks.json";

const store = mainStore();

// 计算网站链接
const siteLinksList = computed(() => {
  const result = [];
  for (let i = 0; i < siteLinks.length; i += 6) {
    const subArr = siteLinks.slice(i, i + 6);
    result.push(subArr);
  }
  return result;
});

// 网站链接图标
const siteIcon = {
  Link,
  Blog,
  Airbnb,
  Cloud,
  Amilia,
  Meetup,
  Database,
  CloudUploadAlt,
  Fire,
  Music,
  Surprise,
  Atom,
  Tools,
};

// 链接跳转
const jumpLink = (data) => {
  if (data.name === "音乐" && store.musicClick) {
    if (typeof $openList === "function") $openList();
  } else {
    window.open(data.link, "_blank");
  }
};

onMounted(() => {
  console.log(siteLinks);
});
</script>

<style lang="scss" scoped>
.links {
  .line {
    margin: 2rem 0.25rem 1rem;
    font-size: 1.1rem;
    display: flex;
    align-items: center;
    animation: fade 0.5s;
    .title {
      margin-left: 8px;
      font-size: 1.15rem;
      text-shadow: 0 0 5px #00000050;
    }
  }
  .swiper {
    left: -10px;
    width: calc(100% + 20px);
    padding: 5px 10px 0;
    z-index: 0;
    margin-bottom: 10px; /* Add margin to ensure space for floating button */
    .swiper-slide {
      height: 100%;
    }
    .swiper-pagination {
      position: static;
      margin-top: 4px;
      margin-bottom: 20px; /* Add margin to keep pagination away from floating button */
      :deep(.swiper-pagination-bullet) {
        background-color: #fff;
        width: 18px;
        height: 4px;
        border-radius: 4px;
        transition: opacity 0.3s;
        &:hover {
          opacity: 1;
        }
      }
    }
  }
  .link-all {
    height: 220px;
    .item {
      height: 100px;
      width: 100%;
      display: flex;
      align-items: center;
      flex-direction: row;
      justify-content: center;
      padding: 0 10px;
      animation: fade 0.5s;
      position: relative;
      overflow: hidden;

      &.glow-hover {
        &::before {
          content: '';
          position: absolute;
          top: -2px;
          left: -2px;
          right: -2px;
          bottom: -2px;
          background: linear-gradient(45deg,
            #ff00cc, #333399, #00ccff, #00ffcc, #ff00cc);
          z-index: -1;
          border-radius: 8px;
          animation: gradient-border 3s linear infinite;
          background-size: 400% 400%;
          opacity: 0;
          transition: opacity 0.3s ease;
        }

        &:hover::before {
          opacity: 1;
        }
      }

      &:hover {
        transform: scale(1.05);
        background: rgb(0 0 0 / 40%);
        transition: all 0.3s ease;
        z-index: 1;
        box-shadow:
          0 8px 30px rgba(0, 0, 0, 0.3),
          0 0 20px rgba(255, 255, 255, 0.1);
      }

      &:active {
        transform: scale(0.98);
      }

      .name {
        font-size: 1.1rem;
        margin-left: 8px;
      }
      @media (min-width: 720px) and (max-width: 820px) {
        .name {
          display: none;
        }
      }
      @media (max-width: 720px) {
        height: 80px;
      }
      @media (max-width: 460px) {
        flex-direction: column;
        .name {
          font-size: 1rem;
          margin-left: 0;
          margin-top: 8px;
        }
      }
    }
    @media (max-width: 720px) {
      height: 180px;
    }
  }
}
</style>
