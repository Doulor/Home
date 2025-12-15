<template>
  <!-- 社交链接 -->
  <div class="social">
    <div class="link">
      <TransitionGroup name="slide-up" tag="div">
        <a
          v-for="(item, index) in socialLinks"
          :key="item.name"
          :href="item.url"
          target="_blank"
          class="social-icon"
          @mouseenter="socialTip = item.tip"
          @mouseleave="socialTip = '通过这里联系我吧'"
        >
          <img class="icon" :src="item.icon" height="24" />
        </a>
      </TransitionGroup>
    </div>
    <span class="tip">{{ socialTip }}</span>
  </div>
</template>

<script setup>
import socialLinks from "@/assets/socialLinks.json";

// 社交链接提示
const socialTip = ref("通过这里联系我吧");
</script>

<style lang="scss" scoped>
.social {
  margin-top: 1rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 460px;
  width: 100%;
  height: 42px;
  background-color: transparent;
  border-radius: 6px;
  backdrop-filter: blur(0);
  animation: fade 0.5s;
  transition:
    background-color 0.3s,
    backdrop-filter 0.3s;
  @media (max-width: 840px) {
    max-width: 100%;
    justify-content: center;
    .link {
      justify-content: space-evenly !important;
      width: 90%;
    }
    .tip {
      display: none !important;
    }
  }

  .link {
    display: flex;
    align-items: center;
    justify-content: center;
    a {
      display: inherit;
      .icon {
        margin: 0 12px;
        transition: transform 0.3s, filter 0.3s;
        filter: brightness(0.8);
        &:hover {
          transform: scale(1.2);
          filter: brightness(1.2);
          transition: transform 0.3s, filter 0.3s;
        }
        &:active {
          transform: scale(1);
        }
      }
    }
  }
  .social-icon {
    position: relative;
    &::after {
      content: '';
      position: absolute;
      top: 50%;
      left: 50%;
      width: 0;
      height: 0;
      background: rgba(255, 255, 255, 0.2);
      border-radius: 50%;
      transform: translate(-50%, -50%);
      transition: width 0.4s, height 0.4s;
    }
    &:hover::after {
      width: 30px;
      height: 30px;
    }
  }
  .tip {
    display: none;
    margin-right: 12px;
    animation: fade 0.5s;
  }
  @media (min-width: 768px) {
    &:hover {
      background-color: #00000040;
      backdrop-filter: blur(5px);
      .tip {
        display: block;
      }
    }
  }
}
</style>
