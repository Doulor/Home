<template>
  <!-- 基本信息 -->
  <div class="message">
    <!-- Logo -->
    <Transition name="fade" appear>
      <div class="logo">
        <img class="logo-img" :src="siteLogo" alt="logo" />
        <div :class="{ name: true, 'text-hidden': true, long: siteUrl[0].length >= 6 }">
          <span class="bg">{{ siteUrl[0] }}</span>
          <span class="sm">.{{ siteUrl[1] }}</span>
        </div>
      </div>
    </Transition>
    <!-- 简介 -->
    <Transition name="slide-up" appear>
      <div class="description cards glow-hover" @click="changeBox">
        <div class="content">
          <Icon size="16">
            <QuoteLeft />
          </Icon>
          <div class="text">
            <p>{{ descriptionText.hello }}</p>
            <p>{{ descriptionText.text }}</p>
          </div>
          <Icon size="16">
            <QuoteRight />
          </Icon>
        </div>
      </div>
    </Transition>
</template>

<script setup>
import { Icon } from "@vicons/utils";
import { QuoteLeft, QuoteRight } from "@vicons/fa";
import { Error } from "@icon-park/vue-next";
import { mainStore } from "@/store";
const store = mainStore();

// 主页站点logo
const siteLogo = import.meta.env.VITE_SITE_MAIN_LOGO;
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

// 简介区域文字
const descriptionText = reactive({
  hello: import.meta.env.VITE_DESC_HELLO,
  text: import.meta.env.VITE_DESC_TEXT,
});

// 切换右侧功能区
const changeBox = () => {
  store.boxOpenState = !store.boxOpenState;
};

// 监听状态变化
watch(
  () => store.boxOpenState,
  (value) => {
    if (value) {
      descriptionText.hello = import.meta.env.VITE_DESC_HELLO_OTHER;
      descriptionText.text = import.meta.env.VITE_DESC_TEXT_OTHER;
    } else {
      descriptionText.hello = import.meta.env.VITE_DESC_HELLO;
      descriptionText.text = import.meta.env.VITE_DESC_TEXT;
    }
  },
);
</script>

<style lang="scss" scoped>
.message {
  .logo {
    display: flex;
    flex-direction: row;
    align-items: center;
    animation: fade 0.5s;
    max-width: 460px;
    .logo-img {
      border-radius: 50%;
      width: 120px;
    }
    .name {
      width: 100%;
      padding-left: 22px;
      transform: translateY(-8px);
      font-family: "Pacifico-Regular";

      .bg {
        font-size: 5rem;
      }

      .sm {
        margin-left: 6px;
        font-size: 2rem;
        @media (min-width: 720px) and (max-width: 789px) {
          display: none;
        }
      }
    }
    @media (max-width: 768px) {
      .logo-img {
        width: 100px;
      }
      .name {
        height: 128px;
        .bg {
          font-size: 4.5rem;
        }
      }
    }

    @media (max-width: 720px) {
      max-width: 100%;
    }
  }

  .description {
    padding: 1rem;
    margin-top: 3.5rem;
    max-width: 460px;
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
      transform: scale(1.02);
      background: rgb(0 0 0 / 45%) !important;
      transition: all 0.3s ease;
      z-index: 1;
      box-shadow:
        0 8px 30px rgba(0, 0, 0, 0.3),
        0 0 20px rgba(255, 255, 255, 0.1);
    }

    .content {
      display: flex;
      justify-content: space-between;

      .text {
        margin: 0.75rem 1rem;
        line-height: 2rem;
        margin-right: auto;

        p {
          &:nth-of-type(1) {
            font-family: "Pacifico-Regular";
          }
        }
      }

      .xicon:nth-of-type(2) {
        align-self: flex-end;
      }
    }
    @media (max-width: 720px) {
      max-width: 100%;
      /* 恢复移动端点击功能 */
      pointer-events: auto;
    }
  }
  @media (max-width: 390px) {
    .logo {
      flex-direction: column;
      .logo-img {
        display: none;
      }
      .name {
        margin-left: 0;
        height: auto;
        transform: none;
        text-align: center;
        .bg {
          font-size: 3.5rem;
        }
        .sm {
          font-size: 1.4rem;
        }
      }
    }
    .description {
      margin-top: 2.5rem;
    }
  }
}
</style>
