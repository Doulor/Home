<template>
  <div class="more-content">
    <div class="iframe-container">
      <!-- 添加滚动容器包裹iframe，避免内容溢出 -->
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
</template>

<style lang="scss" scoped>
.more-content {
  margin-top: 30px;
  width: 100%;
  // 限制整体最大高度，与时光胶囊所在容器高度协调
  max-height: 600px; 
  display: flex;
  justify-content: center;
  padding: 0 16px;
  // 避免容器自身溢出
  overflow: hidden;
}

.iframe-container {
  width: 100%;
  max-width: 900px;
  background-color: #00000040;
  backdrop-filter: blur(10px);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  padding: 16px;
  transition: transform 0.3s;
  // 容器高度继承父元素，避免无限制扩展
  height: 100%;

  &:hover {
    transform: scale(1.01);
  }
}

// 滚动容器：限制高度并处理溢出
.scroll-wrapper {
  width: 100%;
  // 减去内边距后的可用高度（父容器padding 16px*2）
  height: calc(100% - 32px); 
  overflow: auto;  // 内容超出时显示滚动条
  border-radius: 4px;
}

.embedded-iframe {
  min-height: 400px;  // 保证最小高度，避免内容过短时容器塌陷
}

// 滚动条样式统一（与全局风格匹配）
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
  .more-content {
    margin-top: 20px;
    max-height: 500px;  // 移动端降低最大高度
  }
  
  .iframe-container {
    padding: 12px;
  }
  
  .scroll-wrapper {
    height: calc(100% - 24px);  // 对应移动端内边距调整
  }
  
  .embedded-iframe {
    min-height: 350px;
  }
}
</style>