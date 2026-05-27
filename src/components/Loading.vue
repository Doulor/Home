<template>
  <div id="loader-wrapper" :class="{ loaded: loaderFinished }">
    <div class="loader">
      <div class="sky-grain"></div>
      <div class="nebula nebula-1"></div>
      <div class="nebula nebula-2"></div>
      <div class="nebula nebula-3"></div>
      <div class="aurora aurora-1"></div>
      <div class="aurora aurora-2"></div>

      <div class="loader-scene">
        <div class="moon-orbit">
          <svg class="orbit-svg" viewBox="0 0 240 240" aria-hidden="true">
            <circle class="orbit-path orbit-path-1" cx="120" cy="120" r="108" pathLength="1" />
            <ellipse class="orbit-path orbit-path-2" cx="120" cy="120" rx="82" ry="30" pathLength="1" />
          </svg>
          <div class="orbit-ring orbit-ring-1"></div>
          <div class="orbit-ring orbit-ring-2"></div>
          <span class="orbit-spark spark-1"></span>
          <span class="orbit-spark spark-2"></span>
          <span class="orbit-spark spark-3"></span>
        </div>

        <div class="moon">
          <div class="moon-shine"></div>
          <div class="moon-shadow"></div>
          <div class="moon-crater crater-1"></div>
          <div class="moon-crater crater-2"></div>
          <div class="moon-crater crater-3"></div>
          <div class="moon-crater crater-4"></div>
          <div class="moon-crater crater-5"></div>
          <div class="moon-crater crater-6"></div>
          <div class="moon-glow"></div>
        </div>

        <div class="ripple-layer">
          <div class="ripple ripple-1"></div>
          <div class="ripple ripple-2 delay-1"></div>
          <div class="ripple ripple-3 delay-2"></div>
          <div class="ripple ripple-4 delay-3"></div>
        </div>

        <div
          v-for="(star, index) in stars"
          :key="index"
          class="star"
          :class="`star-type-${star.type}`"
          :style="{
            left: `${star.x}%`,
            top: `${star.y}%`,
            animationDelay: `${star.delay}s`,
            animationDuration: `${star.duration}s`,
            '--star-size': `${star.size}px`,
            '--star-glow-size': `${star.glow}px`,
            '--star-opacity': star.opacity,
            '--star-dim-opacity': star.dimOpacity,
            '--star-soft-opacity': star.softOpacity,
            '--drift-x': `${star.driftX}px`,
            '--drift-y': `${star.driftY}px`,
            '--star-color': star.color,
          }"
        ></div>

        <div
          v-for="(shootingStar, index) in shootingStars"
          :key="'shooting-' + index"
          class="shooting-star"
          :style="{
            left: `${shootingStar.startX}%`,
            top: `${shootingStar.startY}%`,
            animationDelay: `${shootingStar.delay}s`,
            animationDuration: `${shootingStar.duration}s`,
            '--travel-x': `${shootingStar.travelX}vw`,
            '--travel-y': `${shootingStar.travelY}vh`,
            '--shooting-angle': `${shootingStar.angle}rad`,
            '--shooting-length': `${shootingStar.length}px`,
          }"
        >
          <div class="shooting-star-tail"></div>
          <div class="shooting-star-core"></div>
        </div>

        <div class="constellation constellation-1">
          <span class="constellation-node node-1"></span>
          <span class="constellation-node node-2"></span>
          <span class="constellation-node node-3"></span>
          <span class="constellation-node node-4"></span>
          <span class="constellation-line line-1"></span>
          <span class="constellation-line line-2"></span>
          <span class="constellation-line line-3"></span>
        </div>
        <div class="constellation constellation-2">
          <span class="constellation-node node-1"></span>
          <span class="constellation-node node-2"></span>
          <span class="constellation-node node-3"></span>
          <span class="constellation-node node-4"></span>
          <span class="constellation-line line-1"></span>
          <span class="constellation-line line-2"></span>
          <span class="constellation-line line-3"></span>
        </div>

        <div
          v-for="(dust, index) in starDust"
          :key="'dust-' + index"
          class="star-dust"
          :style="{
            left: `${dust.x}%`,
            top: `${dust.y}%`,
            animationDelay: `${dust.delay}s`,
            animationDuration: `${dust.duration}s`,
            '--dust-size': `${dust.size}px`,
            '--float-x': `${dust.floatX}px`,
            '--float-y': `${dust.floatY}px`,
          }"
        ></div>
      </div>

      <div class="loading-text">
        <span>加载中</span>
        <span class="loading-dot"></span>
        <span class="loading-dot"></span>
        <span class="loading-dot"></span>
      </div>

      <div class="progress-indicator">
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: `${realProgress}%` }">
            <span class="progress-comet"></span>
          </div>
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

const stars = ref([]);
const shootingStars = ref([]);
const starDust = ref([]);
const realProgress = ref(0);
const loaderFinished = ref(false);

const loadedResources = ref(0);
const totalResources = ref(0);
let resourceCheckInterval = null;
let loaderFinishTimer = null;
let startTime;

const starColors = ["#ffffff", "#eaf2ff", "#d8e4ff", "#c9d7ff", "#f5fbff"];

const randomBetween = (min, max) => min + Math.random() * (max - min);

const generateStars = () => {
  const starCount = window.innerWidth < 768 ? 95 : 165;
  const newStars = [];

  for (let i = 0; i < starCount; i++) {
    const depth = Math.random();
    const opacity = randomBetween(0.35, 0.9);

    newStars.push({
      x: Math.random() * 100,
      y: Math.random() * 100,
      type: Math.floor(Math.random() * 5) + 1,
      delay: Math.random() * 5,
      duration: randomBetween(2.2, 5.8),
      size: randomBetween(1, 2.6 + depth * 1.2),
      glow: randomBetween(5, 11),
      opacity: opacity.toFixed(2),
      dimOpacity: (opacity * 0.45).toFixed(2),
      softOpacity: (opacity * 0.7).toFixed(2),
      driftX: randomBetween(-18, 18) * (0.4 + depth),
      driftY: randomBetween(-14, 14) * (0.4 + depth),
      color: starColors[Math.floor(Math.random() * starColors.length)],
    });
  }

  stars.value = newStars;
};

const generateShootingStars = () => {
  const shootingStarCount = window.innerWidth < 768 ? 8 : 13;
  const newShootingStars = [];

  for (let i = 0; i < shootingStarCount; i++) {
    const trajectoryType = Math.floor(Math.random() * 4);
    let startX;
    let startY;
    let endX;
    let endY;

    switch (trajectoryType) {
      case 0:
        startX = randomBetween(-12, 18);
        startY = randomBetween(-8, 35);
        endX = randomBetween(82, 112);
        endY = startY + randomBetween(24, 52);
        break;
      case 1:
        startX = randomBetween(82, 112);
        startY = randomBetween(-8, 34);
        endX = randomBetween(-12, 18);
        endY = startY + randomBetween(24, 54);
        break;
      case 2:
        startX = randomBetween(8, 42);
        startY = randomBetween(-12, 8);
        endX = startX + randomBetween(22, 52);
        endY = randomBetween(78, 112);
        break;
      default:
        startX = randomBetween(58, 96);
        startY = randomBetween(-12, 10);
        endX = startX - randomBetween(22, 56);
        endY = randomBetween(78, 112);
        break;
    }

    const travelX = endX - startX;
    const travelY = endY - startY;

    newShootingStars.push({
      startX,
      startY,
      travelX,
      travelY,
      angle: Math.atan2(travelY, travelX),
      delay: Math.random() * 16,
      duration: randomBetween(2.2, 4.2),
      length: randomBetween(110, 210),
    });
  }

  shootingStars.value = newShootingStars;
};

const generateStarDust = () => {
  const dustCount = window.innerWidth < 768 ? 24 : 42;
  const newDust = [];

  for (let i = 0; i < dustCount; i++) {
    newDust.push({
      x: Math.random() * 100,
      y: Math.random() * 100,
      delay: Math.random() * 10,
      duration: randomBetween(12, 26),
      size: randomBetween(1.5, 4),
      floatX: randomBetween(-80, 80),
      floatY: randomBetween(-160, -70),
    });
  }

  starDust.value = newDust;
};

const calculateResourceProgress = () => {
  const images = document.images;
  totalResources.value = images.length;
  loadedResources.value = 0;

  if (totalResources.value === 0) {
    realProgress.value = 100;
    return;
  }

  const markResourceLoaded = () => {
    loadedResources.value = Math.min(loadedResources.value + 1, totalResources.value);
  };

  Array.from(images).forEach((img) => {
    if (img.complete) {
      markResourceLoaded();
    } else {
      img.addEventListener("load", markResourceLoaded, { once: true });
      img.addEventListener("error", markResourceLoaded, { once: true });
    }
  });
};

const updateRealProgress = () => {
  if (totalResources.value > 0) {
    const resourceProgress = (loadedResources.value / totalResources.value) * 84;
    const timeProgress = Math.min(16, ((Date.now() - startTime) / 2600) * 16);

    realProgress.value = Math.min(resourceProgress + timeProgress, 100);
  } else {
    const elapsed = Date.now() - startTime;
    realProgress.value = Math.min((elapsed / 2000) * 100, 100);
  }
};

onMounted(() => {
  startTime = Date.now();
  generateStars();
  generateShootingStars();
  generateStarDust();

  calculateResourceProgress();

  resourceCheckInterval = setInterval(updateRealProgress, 80);
});

onUnmounted(() => {
  if (resourceCheckInterval) {
    clearInterval(resourceCheckInterval);
  }
  if (loaderFinishTimer) {
    clearTimeout(loaderFinishTimer);
  }
});

watch(
  () => store.imgLoadStatus,
  (newVal) => {
    if (newVal) {
      realProgress.value = 100;
      if (resourceCheckInterval) {
        clearInterval(resourceCheckInterval);
      }
      loaderFinishTimer = setTimeout(() => {
        loaderFinished.value = true;
      }, 1450);
    }
  },
);
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
    radial-gradient(circle at 18% 16%, rgba(95, 122, 255, 0.22) 0%, transparent 32%),
    radial-gradient(circle at 78% 74%, rgba(141, 86, 255, 0.18) 0%, transparent 34%),
    radial-gradient(circle at 52% 46%, rgba(48, 102, 204, 0.2) 0%, transparent 42%),
    linear-gradient(135deg, #06091c 0%, #0c1533 46%, #050816 100%);
  isolation: isolate;

  .loader {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    perspective: 900px;
    transform-style: preserve-3d;

    .sky-grain {
      position: absolute;
      inset: 0;
      z-index: 1;
      pointer-events: none;
      opacity: 0.18;
      background-image:
        radial-gradient(circle, rgba(255, 255, 255, 0.26) 0 1px, transparent 1px),
        radial-gradient(circle, rgba(154, 183, 255, 0.16) 0 1px, transparent 1px);
      background-position: 0 0, 22px 28px;
      background-size: 46px 46px, 72px 72px;
      animation: sky-grain-drift 18s linear infinite;
      mix-blend-mode: screen;
    }

    .nebula,
    .aurora {
      position: absolute;
      pointer-events: none;
      will-change: transform, opacity;
    }

    .nebula {
      z-index: 0;
      border-radius: 50%;
      filter: blur(48px);
      opacity: 0.22;
      animation: nebula-float 22s ease-in-out infinite alternate;

      &.nebula-1 {
        width: 360px;
        height: 360px;
        top: 8%;
        left: 8%;
        background: radial-gradient(circle, rgba(139, 92, 246, 0.64), transparent 68%);
      }

      &.nebula-2 {
        width: 480px;
        height: 480px;
        right: 7%;
        bottom: 6%;
        background: radial-gradient(circle, rgba(37, 99, 235, 0.48), transparent 70%);
        animation-delay: -9s;
      }

      &.nebula-3 {
        width: 280px;
        height: 280px;
        left: 50%;
        top: 58%;
        background: radial-gradient(circle, rgba(45, 212, 191, 0.18), transparent 72%);
        animation-delay: -15s;
      }
    }

    .aurora {
      z-index: 2;
      width: 80vw;
      height: 26vh;
      border-radius: 999px;
      filter: blur(22px);
      opacity: 0.24;
      transform-origin: center;
      mix-blend-mode: screen;

      &.aurora-1 {
        top: 18%;
        left: -18%;
        background: linear-gradient(90deg, transparent, rgba(77, 171, 247, 0.42), rgba(167, 139, 250, 0.35), transparent);
        animation: aurora-wave 12s ease-in-out infinite;
      }

      &.aurora-2 {
        right: -20%;
        bottom: 20%;
        background: linear-gradient(90deg, transparent, rgba(129, 140, 248, 0.36), rgba(45, 212, 191, 0.2), transparent);
        animation: aurora-wave 15s ease-in-out infinite reverse;
      }
    }

    .loader-scene {
      position: relative;
      z-index: 4;
      width: 100%;
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
      transform-style: preserve-3d;

      .moon-orbit {
        position: absolute;
        width: 240px;
        height: 240px;
        z-index: 9;
        border-radius: 50%;
        opacity: 0;
        transform: scale(0.05) rotateX(64deg) rotateZ(-18deg);
        animation:
          orbit-birth 1.72s cubic-bezier(0.12, 0.82, 0.16, 1) forwards,
          orbit-drift 9s cubic-bezier(0.42, 0, 0.24, 1) 1.72s infinite;
        will-change: transform, opacity, filter;

        .orbit-svg {
          position: absolute;
          inset: 0;
          overflow: visible;
          transform: rotate(-28deg);
        }

        .orbit-path {
          fill: none;
          opacity: 0;
          stroke-linecap: round;
          stroke-dasharray: 1;
          stroke-dashoffset: 1;
          filter: drop-shadow(0 0 12px rgba(169, 211, 255, 0.72));
          animation: orbit-path-draw 1.26s cubic-bezier(0.16, 0.82, 0.22, 1) forwards;
        }

        .orbit-path-1 {
          stroke: rgba(235, 245, 255, 0.82);
          stroke-width: 1.4;
          animation-delay: 0.82s;
        }

        .orbit-path-2 {
          stroke: rgba(140, 190, 255, 0.46);
          stroke-width: 1;
          transform: rotate(-18deg);
          transform-origin: 120px 120px;
          animation-delay: 1.02s;
        }

        .orbit-ring {
          position: absolute;
          inset: 0;
          border-radius: 50%;
          border: 1px solid rgba(204, 222, 255, 0.16);
          opacity: 0;
          --ring-transform-start: scale(0.7);
          --ring-transform-mid: scale(1.08);
          --ring-transform-end: scale(1);
          transform: var(--ring-transform-start);
          box-shadow: inset 0 0 32px rgba(143, 184, 255, 0.08);
          animation: orbit-ring-settle 0.9s ease-out 1.08s forwards;
        }

        .orbit-ring-2 {
          inset: 26px;
          --ring-transform-start: rotateX(62deg) rotateZ(-26deg) scale(0.72);
          --ring-transform-mid: rotateX(62deg) rotateZ(-26deg) scale(1.06);
          --ring-transform-end: rotateX(62deg) rotateZ(-26deg) scale(1);
          transform: var(--ring-transform-start);
          border-color: rgba(255, 255, 255, 0.1);
          animation-delay: 1.18s;
        }

        .orbit-spark {
          position: absolute;
          width: 5px;
          height: 5px;
          border-radius: 50%;
          background: #fff;
          opacity: 0;
          box-shadow: 0 0 16px rgba(255, 255, 255, 0.88), 0 0 28px rgba(110, 168, 255, 0.6);
          animation:
            orbit-spark-birth 1.4s cubic-bezier(0.2, 0.86, 0.24, 1) 0.78s forwards,
            orbit-spark-pulse 1.6s ease-in-out 2.16s infinite;
        }

        .spark-1 {
          top: 16px;
          left: 56px;
        }

        .spark-2 {
          right: 24px;
          top: 116px;
          animation-delay: 0.96s, 2.34s;
        }

        .spark-3 {
          left: 66px;
          bottom: 24px;
          animation-delay: 1.12s, 2.5s;
        }
      }

      .moon {
        position: relative;
        z-index: 12;
        width: 128px;
        height: 128px;
        overflow: visible;
        border-radius: 50%;
        background:
          radial-gradient(circle at 28% 24%, #ffffff 0%, #f8fbff 18%, transparent 35%),
          radial-gradient(circle at 68% 72%, rgba(142, 153, 171, 0.34) 0%, transparent 34%),
          radial-gradient(circle at 50% 50%, #f7f8fb 0%, #d7dce7 54%, #b8bfcc 100%);
        box-shadow:
          0 0 46px rgba(255, 255, 255, 0.9),
          0 0 92px rgba(154, 199, 255, 0.56),
          0 0 150px rgba(80, 121, 255, 0.34),
          inset 15px -18px 30px rgba(101, 112, 132, 0.28),
          inset -12px 15px 24px rgba(255, 255, 255, 0.62);
        animation:
          moon-birth 1.42s cubic-bezier(0.08, 0.84, 0.18, 1) forwards,
          moon-breathe 5.4s ease-in-out 1.42s infinite;
        transform: translate3d(0, 0, 28px) scale(0.035);
        transform-style: preserve-3d;
        will-change: transform, box-shadow, opacity, filter;

        &::before,
        &::after {
          position: absolute;
          content: "";
          inset: 50%;
          z-index: -2;
          border-radius: 50%;
          pointer-events: none;
          transform: translate(-50%, -50%) scale(0);
        }

        &::before {
          width: 340px;
          height: 340px;
          background: conic-gradient(from -90deg, transparent 0deg, rgba(255, 255, 255, 0.95) 42deg, rgba(107, 183, 255, 0.28) 96deg, transparent 150deg);
          filter: blur(3px);
          animation: moon-flash-ring 1.16s cubic-bezier(0.18, 0.8, 0.2, 1) 0.08s forwards;
        }

        &::after {
          width: 18px;
          height: 18px;
          background: #fff;
          box-shadow: 0 0 24px 12px rgba(255, 255, 255, 0.78), 0 0 78px 34px rgba(116, 174, 255, 0.48);
          animation: moon-core-burst 1.08s cubic-bezier(0.13, 0.82, 0.16, 1) forwards;
        }

        .moon-shine,
        .moon-shadow,
        .moon-glow {
          position: absolute;
          border-radius: 50%;
          pointer-events: none;
        }

        .moon-shine {
          inset: 8px 18px 36px 18px;
          z-index: 3;
          background: radial-gradient(circle at 34% 16%, rgba(255, 255, 255, 0.72), transparent 64%);
          opacity: 0;
          transform: rotate(-18deg) translateX(-22px) scaleX(0.25);
          animation:
            moon-shine-birth 0.86s cubic-bezier(0.18, 0.78, 0.22, 1) 0.62s forwards,
            moon-shine-sweep 4.6s ease-in-out 1.48s infinite;
        }

        .moon-shadow {
          inset: 0;
          z-index: 2;
          background: radial-gradient(circle at 84% 74%, rgba(28, 34, 56, 0.32), transparent 52%);
          opacity: 0;
          mix-blend-mode: multiply;
          animation: moon-detail-fade 0.7s ease-out 0.74s forwards;
        }

        .moon-crater {
          position: absolute;
          z-index: 4;
          border-radius: 50%;
          opacity: 0;
          transform: scale(0.35);
          background:
            radial-gradient(circle at 35% 30%, rgba(255, 255, 255, 0.24), transparent 35%),
            radial-gradient(circle, rgba(126, 135, 154, 0.42), rgba(85, 92, 112, 0.18));
          box-shadow: inset 3px 3px 8px rgba(62, 70, 92, 0.28), inset -2px -2px 6px rgba(255, 255, 255, 0.34);
          animation: moon-crater-birth 0.74s cubic-bezier(0.2, 0.84, 0.22, 1) forwards;
        }

        .crater-1 {
          width: 19px;
          height: 19px;
          top: 22px;
          left: 25px;
          animation-delay: 0.72s;
        }

        .crater-2 {
          width: 12px;
          height: 12px;
          top: 47px;
          left: 58px;
          animation-delay: 0.84s;
        }

        .crater-3 {
          width: 10px;
          height: 10px;
          top: 72px;
          left: 36px;
          animation-delay: 0.94s;
        }

        .crater-4 {
          width: 15px;
          height: 15px;
          top: 38px;
          left: 16px;
          animation-delay: 1s;
        }

        .crater-5 {
          width: 8px;
          height: 8px;
          top: 82px;
          left: 70px;
          animation-delay: 1.08s;
        }

        .crater-6 {
          width: 17px;
          height: 17px;
          top: 54px;
          left: 86px;
          animation-delay: 1.16s;
        }

        .moon-glow {
          inset: -42px;
          z-index: -1;
          background:
            radial-gradient(circle, rgba(255, 255, 255, 0.32) 0%, rgba(123, 177, 255, 0.2) 36%, transparent 72%);
          opacity: 0;
          animation:
            moon-glow-birth 1.1s cubic-bezier(0.14, 0.76, 0.2, 1) 0.38s forwards,
            moon-glow-pulse 3.8s ease-in-out 1.48s infinite;
          filter: blur(1px);
        }
      }

      .ripple-layer {
        position: absolute;
        inset: 0;
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 7;
        pointer-events: none;
        transform: rotateX(58deg);

        .ripple {
          position: absolute;
          width: 132px;
          height: 132px;
          border-radius: 50%;
          border: 1px solid rgba(207, 224, 255, 0.16);
          opacity: 0;
          transform: scale3d(0.72, 0.72, 1);
          box-shadow: 0 0 28px rgba(112, 168, 255, 0.08);
          animation: ripple-effect 4.8s cubic-bezier(0.2, 0.72, 0.18, 1) 1.2s infinite;
          will-change: transform, opacity;
        }

        .ripple-2 {
          border-color: rgba(172, 199, 255, 0.12);
        }

        .ripple-3 {
          border-color: rgba(124, 162, 255, 0.1);
        }

        .ripple-4 {
          border-color: rgba(105, 127, 255, 0.08);
        }

        .delay-1 {
          animation-delay: 2.4s;
        }

        .delay-2 {
          animation-delay: 3.6s;
        }

        .delay-3 {
          animation-delay: 4.8s;
        }
      }

      .star {
        position: absolute;
        z-index: 4;
        width: var(--star-size);
        height: var(--star-size);
        border-radius: 50%;
        background: var(--star-color);
        opacity: var(--star-opacity);
        animation-name: star-twinkle;
        animation-timing-function: ease-in-out;
        animation-iteration-count: infinite;
        box-shadow: 0 0 var(--star-glow-size) rgba(255, 255, 255, 0.82);
        will-change: transform, opacity, filter;

        &::before,
        &::after {
          position: absolute;
          content: "";
          inset: 50% auto auto 50%;
          width: calc(var(--star-size) * 4);
          height: 1px;
          border-radius: 999px;
          background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.68), transparent);
          transform: translate(-50%, -50%) scaleX(0);
          opacity: 0;
          animation: star-cross 3.8s ease-in-out infinite;
          animation-delay: inherit;
        }

        &::after {
          --cross-rotation: 90deg;
        }

        &.star-type-3,
        &.star-type-5 {
          filter: drop-shadow(0 0 7px rgba(181, 210, 255, 0.8));
        }
      }

      .shooting-star {
        position: absolute;
        z-index: 8;
        width: var(--shooting-length);
        height: 4px;
        opacity: 0;
        transform-origin: right center;
        animation-name: shooting-star-trajectory;
        animation-timing-function: cubic-bezier(0.16, 0.86, 0.34, 1);
        animation-iteration-count: infinite;
        will-change: transform, opacity, filter;

        .shooting-star-core {
          position: absolute;
          top: 50%;
          right: 0;
          width: 5px;
          height: 5px;
          border-radius: 50%;
          background: #fff;
          box-shadow: 0 0 10px 3px rgba(255, 255, 255, 0.85), 0 0 22px rgba(129, 197, 255, 0.78);
          transform: translateY(-50%);
        }

        .shooting-star-tail {
          position: absolute;
          top: 50%;
          right: 2px;
          width: 100%;
          height: 2px;
          border-radius: 999px;
          background: linear-gradient(90deg, transparent, rgba(116, 174, 255, 0.1), rgba(214, 231, 255, 0.75), #fff);
          transform: translateY(-50%);
          filter: blur(0.2px);
        }
      }

      .constellation {
        position: absolute;
        z-index: 5;
        width: 170px;
        height: 112px;
        opacity: 0.38;
        animation: constellation-drift 9s ease-in-out infinite;
        will-change: transform, opacity;

        .constellation-node {
          position: absolute;
          width: 5px;
          height: 5px;
          border-radius: 50%;
          background: rgba(255, 255, 255, 0.94);
          box-shadow: 0 0 12px rgba(255, 255, 255, 0.78);
        }

        .constellation-line {
          position: absolute;
          height: 1px;
          border-radius: 999px;
          background: linear-gradient(90deg, transparent, rgba(190, 214, 255, 0.4), transparent);
          transform-origin: left center;
          animation: constellation-line-glow 3.2s ease-in-out infinite;
        }

        .node-1 {
          left: 10px;
          top: 32px;
        }

        .node-2 {
          left: 68px;
          top: 12px;
        }

        .node-3 {
          left: 118px;
          top: 54px;
        }

        .node-4 {
          left: 154px;
          top: 84px;
        }

        .line-1 {
          left: 14px;
          top: 35px;
          width: 62px;
          transform: rotate(-19deg);
        }

        .line-2 {
          left: 72px;
          top: 15px;
          width: 70px;
          transform: rotate(40deg);
          animation-delay: -0.8s;
        }

        .line-3 {
          left: 120px;
          top: 57px;
          width: 48px;
          transform: rotate(40deg);
          animation-delay: -1.6s;
        }

        &.constellation-1 {
          top: 17%;
          left: 13%;
        }

        &.constellation-2 {
          right: 12%;
          bottom: 24%;
          transform: scale(0.86) rotate(12deg);
          animation-delay: -4s;
        }
      }

      .star-dust {
        position: absolute;
        z-index: 6;
        width: var(--dust-size);
        height: var(--dust-size);
        border-radius: 50%;
        background: rgba(255, 255, 255, 0.42);
        box-shadow: 0 0 12px rgba(169, 205, 255, 0.58);
        opacity: 0;
        animation: star-dust-float linear infinite;
        will-change: transform, opacity;
      }
    }

    .loading-text {
      position: absolute;
      bottom: 122px;
      z-index: 20;
      display: flex;
      align-items: center;
      gap: 5px;
      color: rgba(255, 255, 255, 0.9);
      font-size: 18px;
      letter-spacing: 0.28em;
      text-shadow: 0 0 14px rgba(255, 255, 255, 0.48), 0 0 28px rgba(129, 197, 255, 0.36);
      animation: loading-text-float 2.8s ease-in-out infinite;

      .loading-dot {
        width: 4px;
        height: 4px;
        margin-left: -2px;
        border-radius: 50%;
        background: currentColor;
        animation: loading-dot 1.2s ease-in-out infinite;

        &:nth-child(3) {
          animation-delay: 0.16s;
        }

        &:nth-child(4) {
          animation-delay: 0.32s;
        }
      }
    }

    .progress-indicator {
      position: absolute;
      bottom: 80px;
      z-index: 20;
      display: flex;
      flex-direction: column;
      align-items: center;
      width: min(250px, 58vw);

      .progress-bar {
        position: relative;
        width: 100%;
        height: 6px;
        overflow: hidden;
        border: 1px solid rgba(255, 255, 255, 0.18);
        border-radius: 999px;
        background: rgba(255, 255, 255, 0.12);
        box-shadow: inset 0 0 12px rgba(0, 0, 0, 0.28), 0 0 22px rgba(93, 149, 255, 0.16);
        backdrop-filter: blur(8px);

        &::before {
          position: absolute;
          content: "";
          inset: 0;
          background: linear-gradient(110deg, transparent 0%, rgba(255, 255, 255, 0.35) 50%, transparent 100%);
          transform: translateX(-120%);
          animation: progress-sheen 1.8s ease-in-out infinite;
        }

        .progress-fill {
          position: relative;
          height: 100%;
          min-width: 8px;
          overflow: visible;
          border-radius: inherit;
          background: linear-gradient(90deg, #60a5fa, #a78bfa 54%, #f0f9ff);
          box-shadow: 0 0 14px rgba(96, 165, 250, 0.75), 0 0 28px rgba(167, 139, 250, 0.42);
          transition: width 0.18s cubic-bezier(0.22, 0.76, 0.28, 1);
        }

        .progress-comet {
          position: absolute;
          top: 50%;
          right: -5px;
          width: 12px;
          height: 12px;
          border-radius: 50%;
          background: #fff;
          box-shadow: 0 0 18px rgba(255, 255, 255, 0.95), 0 0 30px rgba(120, 190, 255, 0.8);
          transform: translateY(-50%);
        }
      }

      .progress-text {
        margin-top: 10px;
        color: rgba(255, 255, 255, 0.82);
        font-size: 13px;
        letter-spacing: 0.12em;
        text-shadow: 0 0 12px rgba(129, 197, 255, 0.48);
      }
    }
  }

  &.loaded {
    opacity: 0;
    visibility: hidden;
    transition: opacity 0.9s ease, visibility 0.9s ease;
    pointer-events: none;

    .loader-scene,
    .loading-text,
    .progress-indicator {
      opacity: 0;
      transform: translateY(-10px) scale(0.98);
      transition: opacity 0.45s ease, transform 0.45s ease;
    }
  }
}

@keyframes sky-grain-drift {
  0% {
    transform: translate3d(0, 0, 0);
  }

  100% {
    transform: translate3d(-72px, 46px, 0);
  }
}

@keyframes aurora-wave {
  0%,
  100% {
    transform: translate3d(0, 0, 0) rotate(-10deg) scaleX(1);
    opacity: 0.16;
  }

  50% {
    transform: translate3d(12vw, 3vh, 0) rotate(-3deg) scaleX(1.18);
    opacity: 0.32;
  }
}

@keyframes nebula-float {
  0% {
    transform: translate3d(0, 0, 0) scale(1);
  }

  100% {
    transform: translate3d(48px, -34px, 0) scale(1.12);
  }
}

@keyframes orbit-birth {
  0% {
    opacity: 0;
    filter: brightness(2.8) blur(12px);
    transform: scale(0.05) rotateX(64deg) rotateZ(-18deg);
  }

  38% {
    opacity: 1;
    filter: brightness(2.2) blur(4px);
    transform: scale(0.28) rotateX(64deg) rotateZ(110deg);
  }

  74% {
    opacity: 1;
    filter: brightness(1.4) blur(0.6px);
    transform: scale(1.08) rotateX(64deg) rotateZ(260deg);
  }

  100% {
    opacity: 1;
    filter: brightness(1) blur(0);
    transform: scale(1) rotateX(64deg) rotateZ(342deg);
  }
}

@keyframes orbit-drift {
  0% {
    opacity: 1;
    transform: rotate(342deg) rotateX(64deg) rotateZ(-18deg);
  }

  100% {
    opacity: 1;
    transform: rotate(702deg) rotateX(64deg) rotateZ(-18deg);
  }
}

@keyframes orbit-path-draw {
  0%,
  12% {
    opacity: 0;
    stroke-dashoffset: 1;
    stroke-width: 0.2;
  }

  24% {
    opacity: 1;
  }

  74% {
    stroke-dashoffset: 0;
    stroke-width: 2.2;
  }

  100% {
    opacity: 0.3;
    stroke-dashoffset: 0;
  }
}

@keyframes orbit-ring-settle {
  0% {
    opacity: 0;
    filter: brightness(2.2);
    transform: var(--ring-transform-start);
  }

  62% {
    opacity: 0.72;
    filter: brightness(1.8);
    transform: var(--ring-transform-mid);
  }

  100% {
    opacity: 1;
    filter: brightness(1);
    transform: var(--ring-transform-end);
  }
}

@keyframes orbit-spark-birth {
  0% {
    opacity: 0;
    transform: scale(0) rotate(0deg);
  }

  44% {
    opacity: 1;
    transform: scale(1.9) rotate(180deg);
  }

  100% {
    opacity: 0.7;
    transform: scale(1) rotate(360deg);
  }
}

@keyframes orbit-spark-pulse {
  0%,
  100% {
    opacity: 0.55;
    transform: scale(0.8);
  }

  50% {
    opacity: 1;
    transform: scale(1.35);
  }
}

@keyframes moon-birth {
  0% {
    opacity: 0;
    filter: brightness(2.4) blur(12px);
    transform: translate3d(0, 0, 28px) rotate(-28deg) scale(0.035);
  }

  24% {
    opacity: 1;
    filter: brightness(2.8) blur(7px);
    transform: translate3d(0, 0, 70px) rotate(-20deg) scale(0.18);
  }

  58% {
    opacity: 1;
    filter: brightness(1.75) blur(1.2px);
    transform: translate3d(0, -2px, 48px) rotate(8deg) scale(1.1);
  }

  78% {
    filter: brightness(1.28) blur(0);
    transform: translate3d(0, 1px, 34px) rotate(-3deg) scale(0.97);
  }

  100% {
    opacity: 1;
    filter: brightness(1) blur(0);
    transform: translate3d(0, 0, 28px) rotate(-1deg) scale(1);
  }
}

@keyframes moon-core-burst {
  0% {
    opacity: 1;
    transform: translate(-50%, -50%) scale(0.16);
  }

  36% {
    opacity: 1;
    transform: translate(-50%, -50%) scale(1.7);
  }

  100% {
    opacity: 0;
    transform: translate(-50%, -50%) scale(5.8);
  }
}

@keyframes moon-flash-ring {
  0% {
    opacity: 0;
    transform: translate(-50%, -50%) rotate(-120deg) scale(0.05);
  }

  22% {
    opacity: 0.92;
  }

  72% {
    opacity: 0.56;
    transform: translate(-50%, -50%) rotate(210deg) scale(0.86);
  }

  100% {
    opacity: 0;
    transform: translate(-50%, -50%) rotate(300deg) scale(1.08);
  }
}

@keyframes moon-detail-fade {
  0% {
    opacity: 0;
  }

  100% {
    opacity: 1;
  }
}

@keyframes moon-shine-birth {
  0% {
    opacity: 0;
    transform: rotate(-18deg) translateX(-22px) scaleX(0.25);
  }

  62% {
    opacity: 1;
    transform: rotate(-18deg) translateX(12px) scaleX(1.15);
  }

  100% {
    opacity: 0.72;
    transform: rotate(-18deg) translateX(-3px) scaleX(1);
  }
}

@keyframes moon-crater-birth {
  0% {
    opacity: 0;
    transform: scale(0.35);
  }

  76% {
    opacity: 0.86;
    transform: scale(1.18);
  }

  100% {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes moon-glow-birth {
  0% {
    opacity: 0;
    transform: scale(0.12);
  }

  58% {
    opacity: 1;
    transform: scale(1.2);
  }

  100% {
    opacity: 0.52;
    transform: scale(0.95);
  }
}

@keyframes moon-breathe {
  0%,
  100% {
    transform: translate3d(0, 0, 28px) rotate(-1deg) scale(1);
    box-shadow:
      0 0 46px rgba(255, 255, 255, 0.9),
      0 0 92px rgba(154, 199, 255, 0.56),
      0 0 150px rgba(80, 121, 255, 0.34),
      inset 15px -18px 30px rgba(101, 112, 132, 0.28),
      inset -12px 15px 24px rgba(255, 255, 255, 0.62);
  }

  50% {
    transform: translate3d(0, -8px, 38px) rotate(1deg) scale(1.045);
    box-shadow:
      0 0 58px rgba(255, 255, 255, 0.98),
      0 0 118px rgba(154, 199, 255, 0.66),
      0 0 178px rgba(80, 121, 255, 0.42),
      inset 18px -20px 32px rgba(101, 112, 132, 0.24),
      inset -14px 18px 26px rgba(255, 255, 255, 0.7);
  }
}

@keyframes moon-shine-sweep {
  0%,
  100% {
    opacity: 0.54;
    transform: rotate(-18deg) translateX(-3px);
  }

  50% {
    opacity: 0.86;
    transform: rotate(-18deg) translateX(6px);
  }
}

@keyframes moon-glow-pulse {
  0%,
  100% {
    opacity: 0.48;
    transform: scale(0.95);
  }

  50% {
    opacity: 0.88;
    transform: scale(1.12);
  }
}

@keyframes ripple-effect {
  0% {
    opacity: 0.72;
    transform: scale3d(0.72, 0.72, 1);
  }

  70% {
    opacity: 0.16;
  }

  100% {
    opacity: 0;
    transform: scale3d(4.4, 4.4, 1);
  }
}

@keyframes star-twinkle {
  0%,
  100% {
    opacity: var(--star-dim-opacity);
    transform: translate3d(0, 0, 0) scale(0.86);
  }

  40% {
    opacity: var(--star-opacity);
    transform: translate3d(calc(var(--drift-x) * 0.36), calc(var(--drift-y) * 0.36), 0) scale(1.28);
  }

  72% {
    opacity: var(--star-soft-opacity);
    transform: translate3d(var(--drift-x), var(--drift-y), 0) scale(1);
  }
}

@keyframes star-cross {
  0%,
  58%,
  100% {
    opacity: 0;
    transform: translate(-50%, -50%) rotate(var(--cross-rotation, 0deg)) scaleX(0);
  }

  68% {
    opacity: 0.72;
    transform: translate(-50%, -50%) rotate(var(--cross-rotation, 0deg)) scaleX(1);
  }
}

@keyframes shooting-star-trajectory {
  0%,
  63%,
  100% {
    opacity: 0;
    filter: blur(0);
    transform: translate3d(0, 0, 0) rotate(var(--shooting-angle)) scaleX(0.36);
  }

  68% {
    opacity: 1;
    filter: blur(0.1px);
    transform: translate3d(calc(var(--travel-x) * 0.12), calc(var(--travel-y) * 0.12), 0) rotate(var(--shooting-angle)) scaleX(0.85);
  }

  82% {
    opacity: 0;
    filter: blur(0.8px);
    transform: translate3d(var(--travel-x), var(--travel-y), 0) rotate(var(--shooting-angle)) scaleX(1.08);
  }
}

@keyframes constellation-drift {
  0%,
  100% {
    opacity: 0.26;
    transform: translate3d(0, 0, 0) scale(1);
  }

  50% {
    opacity: 0.48;
    transform: translate3d(12px, -10px, 0) scale(1.04);
  }
}

@keyframes constellation-line-glow {
  0%,
  100% {
    opacity: 0.4;
  }

  50% {
    opacity: 1;
  }
}

@keyframes star-dust-float {
  0% {
    opacity: 0;
    transform: translate3d(0, 0, 0) scale(0.5);
  }

  14% {
    opacity: 0.48;
  }

  80% {
    opacity: 0.34;
  }

  100% {
    opacity: 0;
    transform: translate3d(var(--float-x), var(--float-y), 0) scale(1.18);
  }
}

@keyframes loading-text-float {
  0%,
  100% {
    transform: translateY(0);
  }

  50% {
    transform: translateY(-5px);
  }
}

@keyframes loading-dot {
  0%,
  100% {
    opacity: 0.28;
    transform: translateY(0) scale(0.75);
  }

  50% {
    opacity: 1;
    transform: translateY(-4px) scale(1.15);
  }
}

@keyframes progress-sheen {
  0% {
    transform: translateX(-120%);
  }

  100% {
    transform: translateX(120%);
  }
}

@media (max-width: 720px) {
  #loader-wrapper {
    .loader {
      .loader-scene {
        .moon-orbit {
          width: 204px;
          height: 204px;
        }

        .moon {
          width: 112px;
          height: 112px;
        }

        .constellation {
          opacity: 0.28;
          transform: scale(0.76);
        }

        .constellation-1 {
          left: 4%;
        }

        .constellation-2 {
          right: 4%;
        }
      }
    }
  }
}

@media (prefers-reduced-motion: reduce) {
  #loader-wrapper {
    *,
    *::before,
    *::after {
      animation-duration: 0.001ms !important;
      animation-iteration-count: 1 !important;
      transition-duration: 0.001ms !important;
    }
  }
}
</style>
