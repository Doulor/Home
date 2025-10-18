<template>
  <div class="more-content">
    <!-- 仅保留小窗口显示，移除全屏功能 -->
    <div 
      class="capsule-container"
      :class="{ 'active': isActive }"
    >
      <!-- 未激活时的提示 -->
      <div v-if="!isActive" class="placeholder" @click="activate">
        <p>点击进入时光胶囊内容</p>
      </div>

      <!-- 激活后显示网页（仅小窗口） -->
      <div v-else class="content-wrapper">
        <!-- 移除全屏按钮 -->
        <div class="scroll-wrapper">
          <iframe 
            src="http://39.97.183.32:7500/"
            width="100%" 
            height="100%" 
            frameborder="0"
            class="embedded-iframe"
          ></iframe>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';

// 仅保留激活状态控制（无需全屏状态）
const isActive = ref(false);

// 激活网页显示
const activate = () => {
  isActive.value = true;
};
</script>

<style lang="scss" scoped>
.more-content {
  margin-top: 30px;
  width: 100%;
  display: flex;
  justify-content: center;
  padding: 0 16px;
}

// 胶囊容器基础样式（仅小窗口）
.capsule-container {
  width: 100%;
  max-width: 900px;
  min-height: 320px; /* 未激活时的高度（已优化） */
  background-color: #00000040; /* 灰色透明背景框 */
  backdrop-filter: blur(10px);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  padding: 14px;
  transition: all 0.3s ease;
  cursor: pointer;
  overflow: hidden; /* 严格限制内容不溢出 */
}

// 未激活提示样式（保持优化后的状态）
.placeholder {
  width: 100%;
  height: 100%;
  min-height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255, 255, 255, 0.7);
  font-size: 16px;
}

// 激活后样式（仅小窗口，无全屏）
.capsule-container.active {
  cursor: default;
  min-height: 420px; /* 激活后高度（控制背景框不超出） */
  padding: 16px;
}

// 滚动容器（确保内容在小窗口内滚动）
.scroll-wrapper {
  width: 100%;
  height: 100%; /* 充满激活后的容器高度 */
  overflow: auto; /* 内容过长时内部滚动 */
  border-radius: 4px;
  background-color: #121212;
}

// iframe样式（适配小窗口）
.embedded-iframe {
  width: 100%;
  height: 100%;
  min-height: 380px; /* 匹配激活后容器高度，避免空白 */
}

// 滚动条样式（保持美观）
.scroll-wrapper::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

.scroll-wrapper::-webkit-scrollbar-track {
  background: transparent;
}

.scroll-wrapper::-webkit-scrollbar-thumb {
  background-color: rgba(255, 255, 255, 0.3);
  border-radius: 3px;
}

// 移动端适配
@media (max-width: 768px) {
  .capsule-container {
    padding: 10px;
    min-height: 300px;
  }

  .placeholder {
    min-height: 300px;
  }

  .capsule-container.active {
    min-height: 380px;
  }

  .embedded-iframe {
    min-height: 340px;
  }
}
</style>