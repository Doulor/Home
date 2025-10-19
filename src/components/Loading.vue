<template>
  <div id="loader-wrapper" :class="store.imgLoadStatus ? 'loaded' : null">
    <div class="loader">
      <!-- 星云背景 -->
      <div class="nebula nebula-1"></div>
      <div class="nebula nebula-2"></div>
      
      <div class="loader-scene">
        <!-- 月亮 -->
        <div class="moon">
          <div class="moon-crater crater-1"></div>
          <div class="moon-crater crater-2"></div>
          <div class="moon-crater crater-3"></div>
          <div class="moon-crater crater-4"></div>
          <div class="moon-crater crater-5"></div>
          <div class="moon-crater crater-6"></div>
          <div class="moon-glow"></div>
        </div>
        
        <!-- 多层次涟漪 -->
        <div class="ripple-layer">
          <div class="ripple ripple-1"></div>
          <div class="ripple ripple-2 delay-1"></div>
          <div class="ripple ripple-3 delay-2"></div>
          <div class="ripple ripple-4 delay-3"></div>
        </div>
        
        <!-- 满屏星星 -->
        <div 
          v-for="(star, index) in stars" 
          :key="index"
          class="star" 
          :class="`star-type-${star.type}`"
          :style="{
            left: `${star.x}%`,
            top: `${star.y}%`,
            animationDelay: `${star.delay}s`,
            animationDuration: `${star.duration}s`
          }"
        ></div>
        
        <!-- 流星系统 -->
        <div 
          v-for="(shootingStar, index) in shootingStars" 
          :key="'shooting-' + index"
          class="shooting-star" 
          :style="{
            left: `${shootingStar.startX}%`,
            top: `${shootingStar.startY}%`,
            '--endX': `${shootingStar.endX}%`,
            '--endY': `${shootingStar.endY}%`,
            animationDelay: `${shootingStar.delay}s`,
            animationDuration: `${shootingStar.duration}s`
          }"
        >
          <div class="shooting-star-core"></div>
          <div class="shooting-star-tail"></div>
        </div>
        
        <!-- 星座连线 -->
        <div class="constellation constellation-1"></div>
        <div class="constellation constellation-2"></div>
        
        <!-- 漂浮的星尘 -->
        <div 
          v-for="(dust, index) in starDust" 
          :key="'dust-' + index"
          class="star-dust" 
          :style="{
            left: `${dust.x}%`,
            top: `${dust.y}%`,
            animationDelay: `${dust.delay}s`,
            animationDuration: `${dust.duration}s`
          }"
        ></div>
      </div>
      
      <!-- 加载提示文字 - 简化版 -->
      <div class="loading-text">加载中</div>
      
      <!-- 真实进度指示器 -->
      <div class="progress-indicator">
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: `${realProgress}%` }"></div>
        </div>
        <div class="progress-text">{{ Math.round(realProgress) }}%</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { mainStore } from "@/store";
import { ref, onMounted, onUnmounted, watch } from "vue";

const store = mainStore();

// 状态
const stars = ref([]);
const shootingStars = ref([]);
const starDust = ref([]);
const realProgress = ref(0);

// 资源加载追踪
const loadedResources = ref(0);
const totalResources = ref(0);
let resourceCheckInterval = null;

// 生成随机星星位置
const generateStars = () => {
  const starCount = 120;
  const newStars = [];
  
  for (let i = 0; i < starCount; i++) {
    newStars.push({
      x: Math.random() * 100,
      y: Math.random() * 100,
      type: Math.floor(Math.random() * 5) + 1,
      delay: Math.random() * 6,
      duration: 2 + Math.random() * 4
    });
  }
  
  stars.value = newStars;
};

// 生成流星 - 使用真实的轨迹计算
const generateShootingStars = () => {
  const shootingStarCount = 15;
  const newShootingStars = [];
  
  for (let i = 0; i < shootingStarCount; i++) {
    // 随机选择轨迹类型：左上到右下、右上到左下、上到下等
    const trajectoryType = Math.floor(Math.random() * 4);
    
    let startX, startY, endX, endY;
    
    switch(trajectoryType) {
      case 0: // 左上到右下
        startX = -5;
        startY = Math.random() * 40;
        endX = 105;
        endY = startY + 30 + Math.random() * 40;
        break;
      case 1: // 右上到左下
        startX = 105;
        startY = Math.random() * 40;
        endX = -5;
        endY = startY + 30 + Math.random() * 40;
        break;
      case 2: // 上到下（偏左）
        startX = Math.random() * 30;
        startY = -5;
        endX = startX + 20 + Math.random() * 30;
        endY = 105;
        break;
      case 3: // 上到下（偏右）
        startX = 70 + Math.random() * 25;
        startY = -5;
        endX = startX - 20 - Math.random() * 30;
        endY = 105;
        break;
    }
    
    newShootingStars.push({
      startX,
      startY,
      endX,
      endY,
      delay: Math.random() * 25,
      duration: 1 + Math.random() * 2
    });
  }
  
  shootingStars.value = newShootingStars;
};

// 生成星尘
const generateStarDust = () => {
  const dustCount = 25;
  const newDust = [];
  
  for (let i = 0; i < dustCount; i++) {
    newDust.push({
      x: Math.random() * 100,
      y: Math.random() * 100,
      delay: Math.random() * 10,
      duration: 10 + Math.random() * 20
    });
  }
  
  starDust.value = newDust;
};

// 计算页面资源加载进度
const calculateResourceProgress = () => {
  const images = document.images;
  totalResources.value = images.length;
  loadedResources.value = 0;

  if (totalResources.value === 0) {
    realProgress.value = 100;
    return;
  }

  Array.from(images).forEach(img => {
    if (img.complete) {
      loadedResources.value++;
    } else {
      img.addEventListener('load', () => {
        loadedResources.value++;
      });
      img.addEventListener('error', () => {
        loadedResources.value++;
      });
    }
  });
};

// 更新真实进度
const updateRealProgress = () => {
  if (totalResources.value > 0) {
    const resourceProgress = (loadedResources.value / totalResources.value) * 80;
    const timeProgress = Math.min(20, (Date.now() - startTime) / 2500 * 20);
    
    realProgress.value = Math.min(resourceProgress + timeProgress, 100);
  } else {
    const elapsed = Date.now() - startTime;
    realProgress.value = Math.min(elapsed / 2000 * 100, 100);
  }
};

let startTime;

onMounted(() => {
  startTime = Date.now();
  generateStars();
  generateShootingStars();
  generateStarDust();
  
  calculateResourceProgress();
  
  resourceCheckInterval = setInterval(updateRealProgress, 100);
});

onUnmounted(() => {
  if (resourceCheckInterval) {
    clearInterval(resourceCheckInterval);
  }
});

watch(() => store.imgLoadStatus, (newVal) => {
  if (newVal) {
    realProgress.value = 100;
    if (resourceCheckInterval) {
      clearInterval(resourceCheckInterval);
    }
  }
});
</script>

<style lang="scss" scoped>
#loader-wrapper {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 999;
  overflow: hidden;
  background: 
    radial-gradient(ellipse at 20% 20%, rgba(31, 58, 147, 0.4) 0%, transparent 50%),
    radial-gradient(ellipse at 80% 80%, rgba(13, 71, 161, 0.3) 0%, transparent 50%),
    radial-gradient(ellipse at 40% 40%, rgba(26, 35, 126, 0.2) 0%, transparent 50%),
    linear-gradient(135deg, #0a0f2b 0%, #0f1c3f 50%, #090e1f 100%);
  
  .loader {
    width: 100%;
    height: 100%;
    position: absolute;
    top: 0;
    left: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    
    // 星云背景
    .nebula {
      position: absolute;
      border-radius: 50%;
      filter: blur(40px);
      opacity: 0.15;
      animation: nebula-float 30s ease-in-out infinite alternate;
      
      &.nebula-1 {
        width: 300px;
        height: 300px;
        top: 10%;
        left: 10%;
        background: radial-gradient(circle, rgba(94, 53, 177, 0.6) 0%, transparent 70%);
        animation-delay: 0s;
      }
      
      &.nebula-2 {
        width: 400px;
        height: 400px;
        bottom: 10%;
        right: 10%;
        background: radial-gradient(circle, rgba(26, 35, 126, 0.5) 0%, transparent 70%);
        animation-delay: 15s;
      }
    }
    
    .loader-scene {
      position: relative;
      width: 100%;
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
      
      // 月亮优化
      .moon {
        width: 120px;
        height: 120px;
        background: 
          radial-gradient(circle at 30% 30%, #fff 0%, #e8e8e8 20%, #c8c8c8 100%),
          radial-gradient(circle at 70% 20%, rgba(255, 255, 255, 0.9) 0%, transparent 30%);
        border-radius: 50%;
        box-shadow: 
          0 0 80px rgba(255, 255, 255, 0.8),
          0 0 120px rgba(100, 150, 255, 0.5),
          0 0 160px rgba(66, 133, 244, 0.3),
          inset 0 0 30px rgba(255, 255, 255, 0.9);
        z-index: 10;
        animation: moon-glow 6s ease-in-out infinite alternate;
        position: relative;
        
        // 月亮陨石坑
        .moon-crater {
          position: absolute;
          background: rgba(160, 160, 160, 0.4);
          border-radius: 50%;
          box-shadow: inset 0 0 8px rgba(0, 0, 0, 0.3);
          
          &.crater-1 {
            width: 18px;
            height: 18px;
            top: 20px;
            left: 25px;
          }
          &.crater-2 {
            width: 12px;
            height: 12px;
            top: 45px;
            left: 55px;
          }
          &.crater-3 {
            width: 10px;
            height: 10px;
            top: 65px;
            left: 35px;
          }
          &.crater-4 {
            width: 14px;
            height: 14px;
            top: 35px;
            left: 15px;
          }
          &.crater-5 {
            width: 8px;
            height: 8px;
            top: 75px;
            left: 65px;
          }
          &.crater-6 {
            width: 16px;
            height: 16px;
            top: 50px;
            left: 80px;
          }
        }
        
        // 月亮光晕
        .moon-glow {
          position: absolute;
          top: -20px;
          left: -20px;
          width: 160px;
          height: 160px;
          border-radius: 50%;
          background: radial-gradient(circle, rgba(255, 255, 255, 0.2) 0%, transparent 70%);
          z-index: -1;
          animation: moon-glow-pulse 4s ease-in-out infinite alternate;
        }
      }
      
      // 涟漪效果
      .ripple-layer {
        position: absolute;
        width: 100%;
        height: 100%;
        display: flex;
        align-items: center;
        justify-content: center;
        
        .ripple {
          position: absolute;
          border-radius: 50%;
          animation: ripple-effect 5s linear infinite;
          
          &.ripple-1 {
            width: 120px;
            height: 120px;
            border: 1px solid rgba(255, 255, 255, 0.15);
          }
          &.ripple-2 {
            width: 120px;
            height: 120px;
            border: 1px solid rgba(200, 220, 255, 0.1);
          }
          &.ripple-3 {
            width: 120px;
            height: 120px;
            border: 1px solid rgba(150, 180, 255, 0.08);
          }
          &.ripple-4 {
            width: 120px;
            height: 120px;
            border: 1px solid rgba(100, 150, 255, 0.05);
          }
          
          &.delay-1 {
            animation-delay: 1.25s;
          }
          &.delay-2 {
            animation-delay: 2.5s;
          }
          &.delay-3 {
            animation-delay: 3.75s;
          }
        }
      }
      
      // 星星 - 多种类型
      .star {
        position: absolute;
        border-radius: 50%;
        animation: twinkle 4s ease-in-out infinite;
        
        &.star-type-1 {
          width: 1px;
          height: 1px;
          background: #fff;
          box-shadow: 0 0 3px 1px rgba(255, 255, 255, 0.8);
        }
        &.star-type-2 {
          width: 1.5px;
          height: 1.5px;
          background: #fff;
          box-shadow: 0 0 4px 1px rgba(255, 255, 255, 0.9);
        }
        &.star-type-3 {
          width: 2px;
          height: 2px;
          background: #fff;
          box-shadow: 0 0 5px 1px rgba(255, 255, 255, 1);
        }
        &.star-type-4 {
          width: 1px;
          height: 1px;
          background: #e0e0ff;
          box-shadow: 0 0 3px 1px rgba(200, 220, 255, 0.8);
        }
        &.star-type-5 {
          width: 1.5px;
          height: 1.5px;
          background: #c0c0ff;
          box-shadow: 0 0 4px 1px rgba(180, 200, 255, 0.9);
        }
      }
      
      // 流星效果 - 使用CSS变量控制轨迹
      .shooting-star {
        position: absolute;
        width: 100px;
        height: 2px;
        animation: shooting-star-trajectory 3s linear infinite;
        opacity: 0;
        z-index: 5;
        
        .shooting-star-core {
          position: absolute;
          width: 4px;
          height: 4px;
          background: #fff;
          border-radius: 50%;
          box-shadow: 0 0 8px 2px rgba(255, 255, 255, 0.8);
          top: -1px;
          left: 0;
        }
        
        .shooting-star-tail {
          position: absolute;
          width: 96px;
          height: 1px;
          background: linear-gradient(90deg, 
            rgba(255,255,255,0.8) 0%, 
            rgba(255,255,255,0.4) 50%, 
            rgba(255,255,255,0) 100%);
          top: 0.5px;
          left: 4px;
        }
      }
      
      // 星座连线
      .constellation {
        position: absolute;
        border: 1px solid rgba(255, 255, 255, 0.1);
        
        &.constellation-1 {
          width: 80px;
          height: 60px;
          top: 20%;
          left: 20%;
          border-radius: 30% 70% 70% 30% / 30% 30% 70% 70%;
          animation: constellation-pulse 8s ease-in-out infinite;
        }
        
        &.constellation-2 {
          width: 100px;
          height: 40px;
          bottom: 30%;
          right: 25%;
          border-radius: 60% 40% 30% 70% / 60% 30% 70% 40%;
          animation: constellation-pulse 10s ease-in-out infinite reverse;
        }
      }
      
      // 漂浮星尘
      .star-dust {
        position: absolute;
        width: 3px;
        height: 3px;
        background: rgba(255, 255, 255, 0.3);
        border-radius: 50%;
        animation: star-dust-float 15s linear infinite;
        opacity: 0;
      }
    }
    
    // 简化版加载文字
    .loading-text {
      position: absolute;
      bottom: 120px;
      color: #fff;
      font-size: 18px;
      opacity: 0.8;
      z-index: 20;
      text-shadow: 0 0 10px rgba(255, 255, 255, 0.5);
    }
    
    // 真实进度指示器
    .progress-indicator {
      position: absolute;
      bottom: 80px;
      z-index: 20;
      display: flex;
      flex-direction: column;
      align-items: center;
      width: 200px;
      
      .progress-bar {
        width: 100%;
        height: 4px;
        background: rgba(255, 255, 255, 0.2);
        border-radius: 2px;
        overflow: hidden;
        
        .progress-fill {
          height: 100%;
          background: linear-gradient(90deg, #64b5f6, #bb86fc);
          border-radius: 2px;
          transition: width 0.3s ease;
          box-shadow: 0 0 10px rgba(100, 181, 246, 0.7);
        }
      }
      
      .progress-text {
        margin-top: 8px;
        color: #fff;
        font-size: 14px;
        opacity: 0.8;
      }
    }
  }
  
  &.loaded {
    opacity: 0;
    visibility: hidden;
    transition: all 1s ease-out;
    pointer-events: none;
    
    .loader {
      .loader-scene,
      .loading-text,
      .progress-indicator {
        opacity: 0;
        transition: opacity 0.5s ease-out;
      }
    }
  }
}

// 月亮光晕动画
@keyframes moon-glow {
  0%, 100% {
    box-shadow: 
      0 0 80px rgba(255, 255, 255, 0.8),
      0 0 120px rgba(100, 150, 255, 0.5),
      0 0 160px rgba(66, 133, 244, 0.3),
      inset 0 0 30px rgba(255, 255, 255, 0.9);
    transform: scale(1) rotate(0deg);
  }
  50% {
    box-shadow: 
      0 0 100px rgba(255, 255, 255, 0.9),
      0 0 140px rgba(100, 150, 255, 0.6),
      0 0 180px rgba(66, 133, 244, 0.4),
      inset 0 0 35px rgba(255, 255, 255, 1);
    transform: scale(1.05) rotate(0.5deg);
  }
}

// 月亮光晕脉冲
@keyframes moon-glow-pulse {
  0%, 100% {
    opacity: 0.5;
    transform: scale(1);
  }
  50% {
    opacity: 0.8;
    transform: scale(1.1);
  }
}

// 涟漪动画
@keyframes ripple-effect {
  0% {
    transform: scale(1);
    opacity: 1;
  }
  100% {
    transform: scale(4);
    opacity: 0;
  }
}

// 星星闪烁动画
@keyframes twinkle {
  0%, 100% {
    opacity: 0.3;
    transform: scale(1);
  }
  25% {
    opacity: 0.7;
    transform: scale(1.1);
  }
  50% {
    opacity: 1;
    transform: scale(1.2);
  }
  75% {
    opacity: 0.5;
    transform: scale(1.05);
  }
}

// 流星轨迹动画 - 使用CSS变量
@keyframes shooting-star-trajectory {
  0% {
    transform: translate(0, 0);
    opacity: 0;
  }
  5% {
    opacity: 1;
  }
  15% {
    transform: translate(calc(var(--endX) - var(--startX)), calc(var(--endY) - var(--startY)));
    opacity: 0;
  }
  100% {
    transform: translate(calc(var(--endX) - var(--startX)), calc(var(--endY) - var(--startY)));
    opacity: 0;
  }
}

// 星云浮动
@keyframes nebula-float {
  0% {
    transform: translate(0, 0) scale(1);
  }
  100% {
    transform: translate(50px, -30px) scale(1.1);
  }
}

// 星座脉冲
@keyframes constellation-pulse {
  0%, 100% {
    opacity: 0.1;
  }
  50% {
    opacity: 0.3;
  }
}

// 星尘漂浮
@keyframes star-dust-float {
  0% {
    transform: translateY(0) translateX(0);
    opacity: 0;
  }
  10% {
    opacity: 0.3;
  }
  90% {
    opacity: 0.3;
  }
  100% {
    transform: translateY(-100px) translateX(50px);
    opacity: 0;
  }
}
</style>