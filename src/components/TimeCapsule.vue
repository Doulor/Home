<template>
  <div class="time-capsule">
    <div class="title">
      <hourglass-full theme="two-tone" size="24" :fill="['#efefef', '#00000020']" />
      <span>时光胶囊</span>
    </div>
    <span class="text">今日已经度过了&nbsp;{{ timeData.day.elapsed }}&nbsp;小时</span>
    <el-progress :text-inside="true" :stroke-width="20" :percentage="timeData.day.pass" />
    <span class="text">本周已经度过了&nbsp;{{ timeData.week.elapsed }}&nbsp;天</span>
    <el-progress :text-inside="true" :stroke-width="20" :percentage="timeData.week.pass" />
    <span class="text">本月已经度过了&nbsp;{{ timeData.month.elapsed }}&nbsp;天</span>
    <el-progress :text-inside="true" :stroke-width="20" :percentage="timeData.month.pass" />
    <span class="text">今年已经度过了&nbsp;{{ timeData.year.elapsed }}&nbsp;个月</span>
    <el-progress :text-inside="true" :stroke-width="20" :percentage="timeData.year.pass" />
    <div v-if="startDate?.length >= 4 && store.siteStartShow">
      <span class="text" v-html="startDateText" />
      <!-- <el-progress
        :show-text="false"
        :indeterminate="true"
        :stroke-width="6"
        :percentage="80"
        :duration="2"
      /> -->
    </div>
  </div>
</template>

<script setup>
import { HourglassFull } from "@icon-park/vue-next";
import { getTimeCapsule, siteDateStatistics } from "@/utils/getTime.js";
import { mainStore } from "@/store";
const store = mainStore();

// 进度条数据
const timeData = ref(getTimeCapsule());
const startDate = ref(import.meta.env.VITE_SITE_START);
const startDateText = ref(null);
const timeInterval = ref(null);

onMounted(() => {
  timeInterval.value = setInterval(() => {
    timeData.value = getTimeCapsule();
    if (startDate.value) startDateText.value = siteDateStatistics(new Date(startDate.value));
  }, 1000);
});

onBeforeUnmount(() => {
  clearInterval(timeInterval.value);
});
</script>

<style lang="scss" scoped>
.time-capsule {
  width: 100%;
  .title {
    display: flex;
    flex-direction: row;
    align-items: center;
    margin: 0.2rem 0 1.5rem;
    font-size: 1.1rem;
    .i-icon {
      display: flex;
      justify-content: center;
      align-items: center;
      margin-right: 6px;
    }
  }
  .text {
    display: block;
    margin: 1rem 0rem 0.5rem 0rem;
    font-size: 0.95rem;
  }
}

/* 移动端适配 */
@media (max-width: 768px) {
  .time-capsule {
    padding: 0.5rem;
    
    .title {
      margin: 0.1rem 0 1rem;
      font-size: 1rem;
      
      .i-icon {
        margin-right: 4px;
      }
    }
    
    .text {
      margin: 0.8rem 0rem 0.4rem 0rem;
      font-size: 0.85rem;
      line-height: 1.4;
    }
    
    /* 调整 Element UI 进度条在移动端的显示 */
    :deep(.el-progress) {
      margin: 0.3rem 0;
    }
    
    :deep(.el-progress-bar) {
      padding-right: 0;
    }
    
    :deep(.el-progress__text) {
      font-size: 0.75rem;
    }
  }
}

/* 超小屏手机适配 */
@media (max-width: 480px) {
  .time-capsule {
    padding: 0.25rem;
    
    .title {
      font-size: 0.95rem;
      margin: 0 0 0.8rem;
    }
    
    .text {
      font-size: 0.8rem;
      margin: 0.6rem 0rem 0.3rem 0rem;
    }
    
    :deep(.el-progress__text) {
      font-size: 0.7rem;
    }
  }
}
</style>