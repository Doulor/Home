<template>
  <div class="device-status">
    <div class="status-header">
      <span class="header-icon">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="2" y="3" width="20" height="14" rx="2" ry="2"/>
          <line x1="8" y1="21" x2="16" y2="21"/>
          <line x1="12" y1="17" x2="12" y2="21"/>
        </svg>
      </span>
      <span class="header-text">设备状态</span>
      <span v-if="isAnyDeviceOnline" class="online-dot"></span>
    </div>

    <div class="device-cards">
      <div
        v-for="device in devices"
        :key="device.device_id"
        class="device-card"
        :class="device.status"
      >
        <!-- 设备头部 -->
        <div class="device-header">
          <span class="device-icon">{{ device.device_type === 'phone' ? '&#128241;' : '&#128187;' }}</span>
          <span class="device-name">{{ device.device_name }}</span>
          <span :class="['status-dot', device.status]"></span>
        </div>

        <!-- 状态标签 -->
        <div class="status-row">
          <span class="status-label">{{ statusLabel(device.status) }}</span>
          <span v-if="device.custom_status" class="custom-status">{{ chargeLabel(device.custom_status) }}</span>
          <span v-if="device.status === 'offline'" class="last-seen">{{ lastSeenText(device.updated_at) }}</span>
        </div>

        <!-- 当前活动应用 -->
        <div v-if="device.status !== 'offline' && device.active_app" class="active-app">
          <span class="app-indicator">&#9654;</span>
          <span class="app-name text-hidden">{{ device.active_app }}</span>
          <span v-if="appStatusText(device.active_app)" class="app-mood text-hidden">{{ appStatusText(device.active_app) }}</span>
        </div>

        <!-- 开机时长（仅电脑在线时显示） -->
        <div v-if="device.device_type === 'desktop' && device.status !== 'offline' && device.extra?.uptime" class="uptime-info">
          <span class="uptime-icon">&#9201;</span>
          <span class="uptime-text">已运行 {{ formatUptime(device.extra.uptime) }}</span>
        </div>

        <!-- 媒体信息 -->
        <div v-if="device.media_title && !isPlaceholder(device.media_title)" class="media-info">
          <div class="media-text">
            <span class="media-title text-hidden">{{ device.media_title }}</span>
            <span v-if="device.media_detail" class="media-detail text-hidden">{{ device.media_detail }}</span>
          </div>
          <el-progress
            v-if="device.media_duration"
            :percentage="mediaProgress(device)"
            :show-text="false"
            :stroke-width="3"
            color="#1db954"
          />
        </div>

        <!-- 系统指标 -->
        <div v-if="device.status !== 'offline'" class="system-stats">
          <div class="stat" v-if="device.cpu_usage != null">
            <span class="stat-label">CPU</span>
            <el-progress
              :percentage="Math.round(device.cpu_usage)"
              :stroke-width="5"
              :show-text="false"
              :color="progressColor(device.cpu_usage)"
            />
            <span class="stat-value">{{ Math.round(device.cpu_usage) }}%</span>
          </div>
          <div class="stat" v-if="device.memory_usage != null">
            <span class="stat-label">RAM</span>
            <el-progress
              :percentage="Math.round(device.memory_usage)"
              :stroke-width="5"
              :show-text="false"
              :color="progressColor(device.memory_usage)"
            />
            <span class="stat-value">{{ formatMemory(device.memory_used) }}</span>
          </div>
          <div class="stat" v-if="device.battery_level != null">
            <span class="stat-label">{{ device.battery_charging ? '&#9889;' : '&#128267;' }}</span>
            <el-progress
              :percentage="Math.round(device.battery_level)"
              :stroke-width="5"
              :show-text="false"
              :color="batteryColor(device.battery_level)"
            />
            <span class="stat-value">{{ Math.round(device.battery_level) }}%</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { createClient } from '@supabase/supabase-js'
import { appStatusText } from '@/assets/appStatusMap'

const monitorSupabase = createClient(
  import.meta.env.VITE_MONITOR_URL,
  import.meta.env.VITE_MONITOR_KEY
)

const devices = ref([])
const realtimeChannel = ref(null)
let staleCheckTimer = null
const now = ref(Date.now())
let nowTimer = null

// 获取初始数据
async function fetchDevices() {
  try {
    // 先标记过时设备为离线
    await monitorSupabase.rpc('mark_stale_devices_offline')
  } catch {
    // 函数可能不存在，忽略错误
  }

  const { data, error } = await monitorSupabase
    .from('device_status')
    .select('*')
    .order('device_type')

  if (!error && data) {
    devices.value = data
  }
}

// 订阅 Realtime 变更
function subscribeRealtime() {
  realtimeChannel.value = monitorSupabase
    .channel('device_status_changes')
    .on(
      'postgres_changes',
      { event: '*', schema: 'public', table: 'device_status' },
      (payload) => {
        if (payload.eventType === 'UPDATE' || payload.eventType === 'INSERT') {
          const idx = devices.value.findIndex(d => d.device_id === payload.new.device_id)
          if (idx >= 0) {
            devices.value[idx] = { ...devices.value[idx], ...payload.new }
          } else {
            devices.value.push(payload.new)
          }
        } else if (payload.eventType === 'DELETE') {
          devices.value = devices.value.filter(d => d.device_id !== payload.old.device_id)
        }
      }
    )
    .subscribe()
}

// 计算属性
const isAnyDeviceOnline = computed(() =>
  devices.value.some(d => d.status === 'online' || d.status === 'away')
)

// 辅助函数
function statusLabel(status) {
  const map = { online: '在线', away: '离开', idle: '闲置', offline: '离线' }
  return map[status] || status
}

function mediaProgress(device) {
  if (!device.media_duration || device.media_duration === 0) return 0
  return Math.round((device.media_elapsed / device.media_duration) * 100)
}

function formatMemory(mb) {
  if (!mb) return '-'
  return mb >= 1024 ? `${(mb / 1024).toFixed(1)}G` : `${Math.round(mb)}M`
}

function progressColor(val) {
  if (val > 90) return '#f56c6c'
  if (val > 70) return '#e6a23c'
  return '#409eff'
}

function isPlaceholder(val) {
  if (!val) return true
  return val.startsWith('{') && val.endsWith('}')
}

function batteryColor(val) {
  if (val <= 20) return '#f56c6c'
  if (val <= 50) return '#e6a23c'
  return '#67c23a'
}

function chargeLabel(val) {
  if (!val) return ''
  const map = {
    '打开': '充电中',
    '关闭': '未充电',
    'charging': '充电中',
    'discharging': '未充电',
    'full': '已充满',
  }
  return map[val.toLowerCase()] || map[val] || val
}

function lastSeenText(updated_at) {
  if (!updated_at) return ''
  const diff = Math.floor((now.value - new Date(updated_at).getTime()) / 1000)
  if (diff < 60) return '刚刚在线'
  if (diff < 3600) return Math.floor(diff / 60) + ' 分钟前在线'
  if (diff < 86400) return Math.floor(diff / 3600) + ' 小时前在线'
  return Math.floor(diff / 86400) + ' 天前在线'
}

function formatUptime(seconds) {
  if (!seconds) return ''
  const d = Math.floor(seconds / 86400)
  const h = Math.floor((seconds % 86400) / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  if (d > 0) return d + ' 天 ' + h + ' 小时'
  if (h > 0) return h + ' 小时 ' + m + ' 分钟'
  return m + ' 分钟'
}

onMounted(() => {
  fetchDevices()
  subscribeRealtime()
  // 每 30 秒检查一次过时设备，自动标记离线
  staleCheckTimer = setInterval(async () => {
    try {
      await monitorSupabase.rpc('mark_stale_devices_offline')
      // 刷新列表以反映状态变化
      const { data, error } = await monitorSupabase
        .from('device_status')
        .select('*')
        .order('device_type')
      if (!error && data) {
        devices.value = data
      }
    } catch {
      // 忽略错误
    }
  }, 30000)
  // 每 30 秒更新一次当前时间，用于"最后在线"显示
  nowTimer = setInterval(() => {
    now.value = Date.now()
  }, 30000)
})

onBeforeUnmount(() => {
  if (realtimeChannel.value) {
    monitorSupabase.removeChannel(realtimeChannel.value)
  }
  if (staleCheckTimer) clearInterval(staleCheckTimer)
  if (nowTimer) clearInterval(nowTimer)
})
</script>

<style lang="scss" scoped>
.device-status {
  margin-top: 1.2rem;
  max-width: 460px;
  width: 100%;
  animation: fade 0.5s;

  @media (max-width: 840px) {
    max-width: 100%;
  }

  .status-header {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 0.6rem;
    font-size: 0.95rem;
    color: rgba(255, 255, 255, 0.85);

    .header-icon {
      display: flex;
      align-items: center;
      color: rgba(255, 255, 255, 0.7);
    }

    .header-text {
      font-weight: 600;
    }

    .online-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #43b581;
      animation: pulse-dot 2s infinite;
      margin-left: auto;
    }
  }

  .device-cards {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
  }

  .device-card {
    background: rgba(255, 255, 255, 0.05);
    backdrop-filter: blur(10px);
    border-radius: 10px;
    padding: 0.7rem 0.9rem;
    border: 1px solid rgba(255, 255, 255, 0.08);
    transition: all 0.3s ease;

    &:hover {
      background: rgba(255, 255, 255, 0.08);
    }

    &.offline {
      opacity: 0.45;
    }

    .device-header {
      display: flex;
      align-items: center;
      gap: 8px;

      .device-icon {
        font-size: 1.05rem;
      }
      .device-name {
        font-weight: 600;
        font-size: 0.9rem;
        color: rgba(255, 255, 255, 0.9);
      }
      .status-dot {
        width: 10px;
        height: 10px;
        border-radius: 50%;
        margin-left: auto;
        &.online { background: #43b581; }
        &.away { background: #faa61a; }
        &.idle { background: #747f8d; }
        &.offline { background: #747f8d; }
      }
    }

    .status-row {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-top: 2px;

      .status-label {
        font-size: 0.75rem;
        color: rgba(255, 255, 255, 0.45);
      }

      .custom-status {
        font-size: 0.75rem;
        color: rgba(255, 255, 255, 0.6);
        font-style: italic;
      }

      .last-seen {
        font-size: 0.7rem;
        color: rgba(255, 255, 255, 0.35);
        margin-left: auto;
      }
    }

    .uptime-info {
      display: flex;
      align-items: center;
      gap: 4px;
      margin-top: 0.25rem;
      font-size: 0.7rem;
      color: rgba(255, 255, 255, 0.45);
      .uptime-icon {
        font-size: 0.7rem;
      }
    }

    .active-app {
      display: flex;
      align-items: center;
      gap: 5px;
      margin-top: 0.35rem;
      font-size: 0.8rem;
      color: rgba(255, 255, 255, 0.7);
      .app-indicator {
        color: #43b581;
        font-size: 0.65rem;
      }
      .app-mood {
        font-size: 0.7rem;
        color: rgba(255, 255, 255, 0.45);
        font-style: italic;
      }
    }

    .media-info {
      margin-top: 0.4rem;
      padding: 0.45rem 0.5rem;
      background: rgba(0, 0, 0, 0.2);
      border-radius: 6px;

      .media-text {
        display: flex;
        flex-direction: column;
        gap: 1px;
        margin-bottom: 4px;
        .media-title {
          font-size: 0.8rem;
          font-weight: 500;
          color: rgba(255, 255, 255, 0.85);
        }
        .media-detail {
          font-size: 0.7rem;
          color: rgba(255, 255, 255, 0.5);
        }
      }

      :deep(.el-progress) {
        .el-progress-bar__outer {
          background-color: rgba(255, 255, 255, 0.1);
        }
      }
    }

    .system-stats {
      display: flex;
      gap: 0.6rem;
      margin-top: 0.45rem;

      .stat {
        flex: 1;
        display: flex;
        align-items: center;
        gap: 4px;
        .stat-label {
          font-size: 0.65rem;
          color: rgba(255, 255, 255, 0.45);
          min-width: 26px;
        }
        .stat-value {
          font-size: 0.65rem;
          color: rgba(255, 255, 255, 0.55);
          min-width: 30px;
          text-align: right;
        }
        :deep(.el-progress) {
          flex: 1;
          .el-progress-bar__outer {
            background-color: rgba(255, 255, 255, 0.08);
          }
        }
      }
    }
  }

  // 文本溢出省略
  .text-hidden {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}
</style>
