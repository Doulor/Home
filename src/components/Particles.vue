<template>
  <div class="particle-container" v-if="enabled">
    <div 
      v-for="particle in particles" 
      :key="particle.id"
      class="particle"
      :class="[
        `particle-${particle.size}`, 
        `particle-${particle.color}`
      ]"
      :style="{
        left: `${particle.x}%`,
        top: `${particle.y}%`,
        animationDelay: `${particle.delay}s`,
        animationDuration: `${particle.duration}s`
      }"
    ></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue';

const props = defineProps({
  enabled: {
    type: Boolean,
    default: true
  },
  count: {
    type: Number,
    default: 30
  }
});

// 粒子数组
const particles = ref([]);

// 颜色选项
const colors = ['blue', 'purple', 'pink'];
// 大小选项
const sizes = ['sm', 'md', 'lg'];

// 生成随机粒子
const generateParticles = () => {
  const newParticles = [];
  
  for (let i = 0; i < props.count; i++) {
    newParticles.push({
      id: i,
      x: Math.random() * 100,
      y: Math.random() * 100,
      size: sizes[Math.floor(Math.random() * sizes.length)],
      color: colors[Math.floor(Math.random() * colors.length)],
      delay: Math.random() * 5,
      duration: 5 + Math.random() * 10
    });
  }
  
  particles.value = newParticles;
};

onMounted(() => {
  if (props.enabled) {
    generateParticles();
  }
});

// 当属性变化时重新生成粒子
watch(() => props.count, () => {
  if (props.enabled) {
    generateParticles();
  }
});
</script>

<style lang="scss" scoped>
// 样式在 particles.scss 中定义
</style>