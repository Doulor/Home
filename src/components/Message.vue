<template>
  <!-- 基本信息 -->
  <div class="message">
    <!-- 留言气泡 -->
    <Transition name="fade-slide-up">
      <div v-if="bubbleShow" class="message-bubble" @click="bubbleShow = false">
        <div class="bubble-content">
          <span class="text">{{ bubbleContent }}</span>
        </div>
      </div>
    </Transition>

    <!-- Logo -->
    <div class="logo">
      <img class="logo-img" :src="siteLogo" alt="logo" />
      <div :class="{ name: true, 'text-hidden': true, long: siteUrl[0].length >= 6 }">
        <span class="bg">{{ siteUrl[0] }}</span>
        <span class="sm">.{{ siteUrl[1] }}</span>
      </div>
    </div>

    <!-- 简介 -->
    <div class="description cards" @click="changeBox">
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
  </div>
</template>

<script setup>
import { Icon } from "@vicons/utils";
import { QuoteLeft, QuoteRight } from "@vicons/fa";
import { createClient } from "@supabase/supabase-js";

import { mainStore } from "@/store";
const store = mainStore();

// 留言气泡逻辑
const bubbleShow = ref(false);
const bubbleContent = ref("");

onMounted(async () => {
  // 检查开关和概率 (30% 概率出现)
  if (!store.messageBubbleShow || Math.random() > 0.3) return;

  try {
    const supabase = createClient(
      import.meta.env.VITE_SUPABASE_URL,
      import.meta.env.VITE_SUPABASE_ANON_KEY
    );

    // 获取最新的一条留言（按 id 倒序）
    const { data, error } = await supabase
      .from("messages")
      .select("content")
      .order("id", { ascending: false })
      .limit(1)
      .single();

    if (error || !data?.content) return;

    // 显示气泡
    bubbleContent.value = data.content;
    bubbleShow.value = true;

    // 10秒后自动消失
    setTimeout(() => {
      bubbleShow.value = false;
    }, 10000);
  } catch (err) {
    console.error("Message bubble error:", err);
  }
});

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
  position: relative;

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
      max-width: 100%;
      margin-top: 14px; /* 移动端下移 Doulor 文案 */
      }

      .sm {
        margin-left: 6px;
        font-size: 2rem;
        @media (min-width: 720px) and (max-width: 789px) {
          display: none;
        }
      }
    }

    @media (min-width: 720px) {
      .name {
        .bg {
          font-size: 4.2rem; // 调小为4.2rem
        }

        .sm {
          font-size: 2rem; // 调小为2rem
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
    @media (max-width: 720px) {
      .description {
        margin-top: 2.75rem; /* 稍微上移简介块 */
      }
    }
  }

  .message-bubble {
    position: absolute;
    top: -70px;
    left: 140px;
    z-index: 10;
    cursor: pointer;
    animation: float 4s ease-in-out infinite;

    .bubble-content {
      position: relative;
      background: rgba(0, 0, 0, 0.6);
      backdrop-filter: blur(10px);
      padding: 12px 16px;
      border-radius: 12px;
      border: 1px solid rgba(255, 255, 255, 0.1);
      width: 260px;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);

      .text {
        font-size: 14px;
        color: #fff;
        line-height: 1.4;
        display: -webkit-box;
        -webkit-line-clamp: 3;
        line-clamp: 3;
        -webkit-box-orient: vertical;
        overflow: hidden;
        text-shadow: 0 1px 2px rgba(0, 0, 0, 0.5);
      }

      /* 小三角 - 指向下方 */
      &::after {
        content: "";
        position: absolute;
        bottom: -8px;
        left: 20px;
        width: 0;
        height: 0;
        border-top: 8px solid rgba(0, 0, 0, 0.6);
        border-left: 8px solid transparent;
        border-right: 8px solid transparent;
      }
    }

    /* 移动端隐藏 */
    @media (max-width: 1200px) {
      display: none;
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
