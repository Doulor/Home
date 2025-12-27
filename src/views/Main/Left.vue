<template>
  <div :class="store.mobileOpenState ? 'left hidden' : 'left'">
    <Message />
    <SocialLinks />
    <Calendar />
  </div>
</template>

<script setup>
import { mainStore } from "@/store";
import Message from "@/components/Message.vue";
import SocialLinks from "@/components/SocialLinks.vue";
import Calendar from "@/components/Calendar.vue";
const store = mainStore();
</script>

<style lang="scss" scoped>
.left {
  // flex: 1 0 0%;
  width: 50%;
  margin-right: 10px;
  transform: translateY(32px);
  margin-top: 2.5rem;
  padding-bottom: 80px; /* Add padding to avoid content being hidden behind the floating menu button */
  &.hidden {
    display: none;
  }
  @media (max-width: 720px) {
    margin-right: 0;
    width: 100%;
    padding-bottom: 80px; /* Maintain padding on mobile */
    transform: translateY(24px);
    margin-top: 2rem;
  }
}
</style>
