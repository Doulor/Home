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
        @click="openDetail(device)"
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

        <!-- 定位信息（仅手机在线时显示） -->
        <div v-if="device.device_type === 'phone' && device.status !== 'offline' && !isPlaceholder(device.location)" class="location-info">
          <span class="location-icon">&#128205;</span>
          <span class="location-text text-hidden">{{ device.location }}</span>
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
          <div class="media-progress-row">
            <el-progress
              v-if="device.media_duration"
              :percentage="mediaProgress(device)"
              :show-text="false"
              :stroke-width="3"
              color="#1db954"
            />
            <span v-if="device.media_duration" class="media-time">{{ formatMediaTime(device.media_elapsed) }} / {{ formatMediaTime(device.media_duration) }}</span>
          </div>
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

    <!-- 详情弹窗 -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="detailDevice" class="detail-overlay" @click="closeDetail">
          <div class="detail-card" :class="{ collapsing: isCollapsing }" @click.stop>
            <!-- 头部 -->
            <div class="detail-header">
              <span class="detail-device-icon">{{ detailDevice.device_type === 'phone' ? '&#128241;' : '&#128187;' }}</span>
              <div class="detail-device-info">
                <span class="detail-device-name">{{ detailDevice.device_name }}</span>
                <span :class="['detail-status-dot', detailDevice.status]"></span>
              </div>
              <span class="detail-close" @click="closeDetail">&times;</span>
            </div>

            <!-- 状态标签 -->
            <div class="detail-status-row">
              <span :class="['detail-status-badge', detailDevice.status]">{{ statusLabel(detailDevice.status) }}</span>
              <span v-if="detailDevice.custom_status" class="detail-charge">{{ chargeLabel(detailDevice.custom_status) }}</span>
              <span v-if="detailDevice.status === 'offline'" class="detail-last-seen">{{ lastSeenText(detailDevice.updated_at) }}</span>
            </div>

            <!-- 当前活动 -->
            <div v-if="detailDevice.status !== 'offline' && detailDevice.active_app" class="detail-section">
              <div class="detail-section-title">当前应用</div>
              <div class="detail-app">
                <span class="detail-app-name">{{ detailDevice.active_app }}</span>
                <span v-if="appStatusText(detailDevice.active_app)" class="detail-app-mood">{{ appStatusText(detailDevice.active_app) }}</span>
              </div>
            </div>

            <!-- 歌曲信息 -->
            <div v-if="detailDevice.media_title && !isPlaceholder(detailDevice.media_title)" class="detail-section">
              <div class="detail-section-title">正在播放</div>
              <div class="detail-media">
                <span class="detail-media-title">{{ detailDevice.media_title }}</span>
                <span v-if="detailDevice.media_detail" class="detail-media-artist">{{ detailDevice.media_detail }}</span>
              </div>
              <el-progress
                v-if="detailDevice.media_duration"
                :percentage="mediaProgress(detailDevice)"
                :show-text="false"
                :stroke-width="4"
                color="#1db954"
              />
            </div>

            <!-- 系统指标 -->
            <div v-if="detailDevice.status !== 'offline'" class="detail-section">
              <div class="detail-section-title">系统信息</div>
              <div class="detail-metrics">
                <div class="detail-metric" v-if="detailDevice.cpu_usage != null">
                  <div class="detail-metric-header">
                    <span class="detail-metric-label">CPU</span>
                    <span class="detail-metric-value">{{ Math.round(detailDevice.cpu_usage) }}%</span>
                  </div>
                  <el-progress :percentage="Math.round(detailDevice.cpu_usage)" :stroke-width="6" :show-text="false" :color="progressColor(detailDevice.cpu_usage)" />
                </div>
                <div class="detail-metric" v-if="detailDevice.memory_usage != null">
                  <div class="detail-metric-header">
                    <span class="detail-metric-label">内存</span>
                    <span class="detail-metric-value">{{ formatMemory(detailDevice.memory_used) }} / {{ formatMemory(detailDevice.memory_total) }}</span>
                  </div>
                  <el-progress :percentage="Math.round(detailDevice.memory_usage)" :stroke-width="6" :show-text="false" :color="progressColor(detailDevice.memory_usage)" />
                </div>
                <div class="detail-metric" v-if="detailDevice.battery_level != null">
                  <div class="detail-metric-header">
                    <span class="detail-metric-label">{{ detailDevice.battery_charging ? '&#9889; 电量' : '&#128267; 电量' }}</span>
                    <span class="detail-metric-value">{{ Math.round(detailDevice.battery_level) }}%</span>
                  </div>
                  <el-progress :percentage="Math.round(detailDevice.battery_level)" :stroke-width="6" :show-text="false" :color="batteryColor(detailDevice.battery_level)" />
                </div>
              </div>
              <div v-if="detailDevice.device_type === 'desktop' && detailDevice.extra?.uptime" class="detail-uptime">
                <span class="uptime-icon">&#9201;</span> 已运行 {{ formatUptime(detailDevice.extra.uptime) }}
              </div>
            </div>

            <!-- 定位信息 -->
            <div v-if="detailDevice.device_type === 'phone' && detailDevice.status !== 'offline' && !isPlaceholder(detailDevice.location)" class="detail-section">
              <div class="detail-section-title">定位</div>
              <div class="detail-location">
                <span>&#128205;</span> {{ resolvedAddresses[detailDevice.device_id] || detailDevice.location }}
              </div>
            </div>

            <!-- 底部 -->
            <div class="detail-footer">
              <span class="detail-device-type">{{ detailDevice.device_type === 'phone' ? '手机' : '桌面端' }}</span>
              <span class="detail-updated">最后更新 {{ lastSeenText(detailDevice.updated_at) }}</span>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
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
const detailDeviceId = ref(null)
const isCollapsing = ref(false)
let mediaTickTimer = null

const detailDevice = computed(() => {
  if (!detailDeviceId.value) return null
  return devices.value.find(d => d.device_id === detailDeviceId.value) || null
})

function openDetail(device) {
  detailDeviceId.value = device.device_id
  isCollapsing.value = false
}

function closeDetail() {
  isCollapsing.value = true
  setTimeout(() => {
    detailDeviceId.value = null
    isCollapsing.value = false
  }, 280)
}

function handleEsc(e) {
  if (e.key === 'Escape' && detailDeviceId.value) closeDetail()
}

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
    // 初始加载时解析已有位置
    data.forEach(d => {
      if (d.location && !isPlaceholder(d.location)) {
        resolveLocation(d.device_id, d.location)
      }
    })
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
          // 解析新位置
          if (payload.new.location && !isPlaceholder(payload.new.location)) {
            resolveLocation(payload.new.device_id, payload.new.location)
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

function formatMediaTime(seconds) {
  if (!seconds || seconds <= 0) return '00:00'
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0')
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
  document.addEventListener('keydown', handleEsc)
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
  document.removeEventListener('keydown', handleEsc)
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
    cursor: pointer;

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

    .location-info {
      display: flex;
      align-items: center;
      gap: 4px;
      margin-top: 0.25rem;
      font-size: 0.7rem;
      color: rgba(255, 255, 255, 0.45);
      .location-icon {
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

      .media-progress-row {
        display: flex;
        align-items: center;
        gap: 6px;

        :deep(.el-progress) {
          flex: 1;
        }

        .media-time {
          font-size: 0.65rem;
          color: rgba(255, 255, 255, 0.4);
          white-space: nowrap;
          min-width: 80px;
          text-align: right;
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

@keyframes zoom-in {
  from {
    opacity: 0;
    transform: scale(0.9);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes zoom-out {
  from {
    opacity: 1;
    transform: scale(1);
  }
  to {
    opacity: 0;
    transform: scale(0.9);
  }
}
</style>

<style lang="scss">
/* 详情弹窗 - 非 scoped，因为 Teleport 到 body */
.detail-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(10px);
  z-index: 1000;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 1rem;
}

.detail-card {
  background: rgba(20, 20, 30, 0.95);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  padding: 1.5rem;
  width: 100%;
  max-width: 420px;
  max-height: 85vh;
  overflow-y: auto;
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.4);
  color: rgba(255, 255, 255, 0.9);
  animation: zoom-in 0.3s cubic-bezier(0.25, 1, 0.5, 1) forwards;

  &.collapsing {
    animation: zoom-out 0.28s cubic-bezier(0.5, 0, 0.75, 0) forwards;
  }

  @media (max-width: 768px) {
    max-width: 95vw;
    max-height: 86vh;
    padding: 1.2rem;
  }

  &::-webkit-scrollbar { width: 4px; }
  &::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.15); border-radius: 2px; }
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 1rem;

  .detail-device-icon {
    font-size: 2rem;
  }
  .detail-device-info {
    display: flex;
    align-items: center;
    gap: 8px;
    flex: 1;
    .detail-device-name {
      font-size: 1.2rem;
      font-weight: 700;
      color: #fff;
    }
    .detail-status-dot {
      width: 12px;
      height: 12px;
      border-radius: 50%;
      &.online { background: #43b581; }
      &.away { background: #faa61a; }
      &.idle { background: #747f8d; }
      &.offline { background: #747f8d; }
    }
  }
  .detail-close {
    font-size: 1.6rem;
    cursor: pointer;
    color: rgba(255, 255, 255, 0.5);
    transition: color 0.2s;
    line-height: 1;
    &:hover { color: #fff; }
  }
}

.detail-status-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 1.2rem;

  .detail-status-badge {
    font-size: 0.8rem;
    padding: 3px 10px;
    border-radius: 12px;
    font-weight: 500;
    &.online { background: rgba(67, 181, 129, 0.2); color: #43b581; }
    &.away { background: rgba(250, 166, 26, 0.2); color: #faa61a; }
    &.idle { background: rgba(116, 127, 141, 0.2); color: #747f8d; }
    &.offline { background: rgba(116, 127, 141, 0.2); color: #747f8d; }
  }
  .detail-charge {
    font-size: 0.8rem;
    color: rgba(255, 255, 255, 0.6);
  }
  .detail-last-seen {
    font-size: 0.75rem;
    color: rgba(255, 255, 255, 0.35);
    margin-left: auto;
  }
}

.detail-section {
  margin-bottom: 1.2rem;
  padding: 0.8rem;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 10px;

  .detail-section-title {
    font-size: 0.7rem;
    color: rgba(255, 255, 255, 0.35);
    text-transform: uppercase;
    letter-spacing: 1px;
    margin-bottom: 0.6rem;
  }
}

.detail-app {
  .detail-app-name {
    font-size: 1rem;
    font-weight: 600;
    color: rgba(255, 255, 255, 0.9);
    display: block;
  }
  .detail-app-mood {
    font-size: 0.85rem;
    color: rgba(255, 255, 255, 0.5);
    font-style: italic;
    margin-top: 2px;
    display: block;
  }
}

.detail-media {
  margin-bottom: 0.5rem;
  .detail-media-title {
    font-size: 1rem;
    font-weight: 600;
    color: rgba(255, 255, 255, 0.9);
    display: block;
  }
  .detail-media-artist {
    font-size: 0.8rem;
    color: rgba(255, 255, 255, 0.5);
    display: block;
    margin-top: 2px;
  }
}

.detail-metrics {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;

  .detail-metric {
    .detail-metric-header {
      display: flex;
      justify-content: space-between;
      margin-bottom: 4px;
      .detail-metric-label {
        font-size: 0.8rem;
        color: rgba(255, 255, 255, 0.6);
      }
      .detail-metric-value {
        font-size: 0.8rem;
        color: rgba(255, 255, 255, 0.7);
        font-weight: 500;
      }
    }
    :deep(.el-progress) {
      .el-progress-bar__outer {
        background-color: rgba(255, 255, 255, 0.08);
      }
    }
  }
}

.detail-uptime {
  margin-top: 0.6rem;
  font-size: 0.8rem;
  color: rgba(255, 255, 255, 0.45);
}

.detail-location {
  font-size: 0.85rem;
  color: rgba(255, 255, 255, 0.7);
}

.detail-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 0.8rem;
  border-top: 1px solid rgba(255, 255, 255, 0.06);

  .detail-device-type {
    font-size: 0.7rem;
    color: rgba(255, 255, 255, 0.3);
    background: rgba(255, 255, 255, 0.05);
    padding: 2px 8px;
    border-radius: 4px;
  }
  .detail-updated {
    font-size: 0.7rem;
    color: rgba(255, 255, 255, 0.3);
  }
}
</style>
