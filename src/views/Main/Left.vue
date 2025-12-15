<template>
  <div :class="store.mobileOpenState ? 'left hidden' : 'left'" v-if="!store.mobileOpenState">
    <Transition name="fade" appear>
      <Message />
    </Transition>
    <Transition name="slide-up" :duration="{ enter: 800, leave: 300 }" appear>
      <SocialLinks />
    </Transition>
  </div>
</template>

<script setup>
import { mainStore } from "@/store";
import Message from "@/components/Message.vue";
import SocialLinks from "@/components/SocialLinks.vue";
const store = mainStore();
</script>

<style lang="scss" scoped>
.left {
  // flex: 1 0 0%;
  width: 50%;
  margin-right: 10px;
  transform: translateY(20px);
  padding-bottom: 80px; /* Add padding to avoid content being hidden behind the floating menu button */
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2rem;

  &.hidden {
    display: none;
  }

  @media (max-width: 720px) {
    margin-right: 0;
    width: 100%;
    padding-bottom: 80px; /* Maintain padding on mobile */
  }
}
</style>
