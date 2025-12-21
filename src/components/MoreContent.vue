<template>
  <div class="memory-capsule">
    <!-- 上方：简洁的访问计数 -->
    <div class="visitor-counter">
      <div class="counter-text" @click="toggleCounterDisplay">
        <Transition name="counter-fade" mode="out-in">
          <div v-if="showUniqueCounter" :key="1" class="counter-content">
            你是路过此处的第 <span class="counter-number">{{ formattedUniqueCount }}</span> 个唯一的你
          </div>
          <div v-else :key="0" class="counter-content">
            你是飘过此处的第 <span class="counter-number">{{ formattedCount }}</span> 缕灵魂
          </div>
        </Transition>
      </div>
    </div>

    <!-- 下方：留言展示区域 -->
    <div class="message-section">
      <!-- 随机留言展示 -->
      <div class="message-display-area">
        <div v-if="currentMessage" class="message-card">
          <div class="message-content">
            {{ currentMessage.content }}
          </div>
          <div class="message-footer">
            <span class="message-time">{{ formatTime(currentMessage.created_at) }}</span>
            <button @click="getRandomMessage" class="refresh-btn" title="换一条留言">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                <path d="M23 4v6h-6M1 20v-6h6M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
              </svg>
            </button>
          </div>
        </div>
        
        <div v-else class="empty-state">
          <div class="empty-icon">💭</div>
          <p>时光胶囊中还没有留言</p>
          <p class="empty-subtitle">成为第一个留下印记的人吧</p>
        </div>
      </div>

      <!-- 留言输入触发按钮 -->
      <div class="input-trigger">
        <button 
          @click="toggleInput" 
          class="trigger-btn"
          :class="{ active: showInput }"
        >
          <span v-if="!showInput">✍️ 留下你的印记</span>
          <span v-else>收起</span>
        </button>
      </div>

      <!-- 覆盖式输入框 -->
      <div v-if="showInput" class="overlay-input">
        <div class="overlay-backdrop" @click="cancelInput"></div>
        <div class="overlay-content">
          <div class="overlay-header">
            <h3>留下时光印记</h3>
            <button @click="cancelInput" class="close-btn">×</button>
          </div>
          <textarea 
            v-model="newMessage" 
            placeholder="在这里写下你想说的话...它将被永久保存在这个时光胶囊中"
            maxlength="200"
            rows="6"
            class="overlay-textarea"
            autofocus
          ></textarea>
          <div class="overlay-actions">
            <span class="char-count">{{ newMessage.length }}/200</span>
            <div class="action-buttons">
              <button 
                @click="cancelInput" 
                class="cancel-btn"
              >
                取消
              </button>
              <button 
                @click="submitMessage" 
                :disabled="!newMessage.trim() || submitting"
                class="submit-btn-overlay"
              >
                {{ submitting ? '投递中...' : '💾 永久保存' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { createClient } from '@supabase/supabase-js'

// 使用环境变量初始化 Supabase 客户端
const supabase = createClient(
  import.meta.env.VITE_SUPABASE_URL,
  import.meta.env.VITE_SUPABASE_ANON_KEY
)

// 响应式数据
const visitorCount = ref(0)
const uniqueVisitorCount = ref(0)
const currentMessage = ref(null)
const newMessage = ref('')
const submitting = ref(false)
const showInput = ref(false)
const showUniqueCounter = ref(false) // 控制是否显示唯一访问者计数

// 格式化访问计数（如：1,234）
const formattedCount = computed(() => {
  return visitorCount.value.toLocaleString()
})

// 格式化唯一访问者计数（如：1,234）
const formattedUniqueCount = computed(() => {
  return uniqueVisitorCount.value.toLocaleString()
})

// 初始化函数
onMounted(async () => {
  await incrementVisitorCount()
  await getUniqueVisitorCount()
  await getRandomMessage()
})

// 切换计数显示
function toggleCounterDisplay() {
  showUniqueCounter.value = !showUniqueCounter.value
  // 只有切换到唯一访问者计数时才增加计数
  if (showUniqueCounter.value) {
    incrementUniqueVisitorCount()
  }
}

// 获取唯一访问者计数
async function getUniqueVisitorCount() {
  try {
    const { count, error } = await supabase
      .from('unique_visitors')
      .select('*', { count: 'exact', head: true })

    if (error) throw error

    uniqueVisitorCount.value = count
  } catch (error) {
    console.error('获取唯一访问者计数失败:', error)
  }
}

// 增加唯一访问者计数
async function incrementUniqueVisitorCount() {
  try {
    // 检查localStorage中是否有已记录的IP哈希，避免重复请求
    const storedIpHash = localStorage.getItem('unique_visitor_ip_hash')
    const currentIpHash = await generateIpHash()

    // 如果已经记录过该IP，则不再重复请求
    if (storedIpHash === currentIpHash) {
      return
    }

    // 检查该IP是否已存在
    let existingRecord = null;
    let fetchError = null;

    try {
      const { data, error } = await supabase
        .from('unique_visitors')
        .select('*')
        .eq('ip_hash', currentIpHash)
        .single()

      if (error) {
        if (error.code === 'PGRST116') {
          // PGRST116 means no rows returned, which is expected when IP doesn't exist
          existingRecord = null;
        } else {
          console.error('查询唯一访问者记录失败:', error)
          return
        }
      } else {
        existingRecord = data;
      }
    } catch (error) {
      console.error('查询唯一访问者记录时发生异常:', error)
      return
    }

    if (!existingRecord) {
      // 如果IP不存在，则插入新记录
      const { error: insertError } = await supabase
        .from('unique_visitors')
        .insert([{ ip_hash: currentIpHash }])

      if (insertError) {
        if (insertError.code === '23505') {
          // 23505是唯一约束违反错误，表示并发情况下另一请求已插入相同IP
          // 这种情况可以忽略，因为目标已经达成
          console.log('IP已存在（并发处理）')
        } else {
          console.error('插入唯一访问者记录失败:', insertError)
          // 即使插入失败也不增加本地计数
          return
        }
      } else {
        // 插入成功，更新本地计数并存储IP哈希到localStorage
        uniqueVisitorCount.value++
        localStorage.setItem('unique_visitor_ip_hash', currentIpHash)
      }
    }
  } catch (error) {
    console.error('增加唯一访问者计数失败:', error)
  }
}

// 增加访问计数
async function incrementVisitorCount() {
  try {
    // 首先尝试直接更新计数
    const { data: currentData, error: fetchError } = await supabase
      .from('visitor_count')
      .select('count')
      .single()
    
    if (fetchError) throw fetchError
    
    // 更新计数
    const newCount = (currentData.count || 0) + 1
    const { data, error } = await supabase
      .from('visitor_count')
      .update({ count: newCount })
      .eq('id', 1)
      .select()
    
    if (error) throw error
    
    visitorCount.value = newCount
    
  } catch (error) {
    console.error('更新访问计数失败:', error)
    
    // 如果更新失败，尝试使用 RPC 函数
    try {
      const { data, error: rpcError } = await supabase
        .rpc('increment_visitor_count')
      
      if (rpcError) throw rpcError
      visitorCount.value = data
    } catch (rpcError) {
      console.error('RPC 函数也失败了:', rpcError)
      // 最后尝试直接查询当前计数
      const { data } = await supabase
        .from('visitor_count')
        .select('count')
        .single()
      if (data) visitorCount.value = data.count
    }
  }
}

// 获取随机留言
async function getRandomMessage() {
  try {
    // 先获取总条数
    const { count, error: countError } = await supabase
      .from('messages')
      .select('*', { count: 'exact', head: true })
    
    if (countError) throw countError
    
    if (count === 0) {
      currentMessage.value = null
      return
    }
    
    // 随机选择一条
    const randomIndex = Math.floor(Math.random() * count)
    
    const { data, error } = await supabase
      .from('messages')
      .select('*')
      .range(randomIndex, randomIndex)
      .single()
    
    if (error) throw error
    currentMessage.value = data
    
  } catch (error) {
    console.error('获取留言失败:', error)
  }
}

// 切换输入框显示
function toggleInput() {
  showInput.value = !showInput.value
  if (!showInput.value) {
    newMessage.value = ''
  }
}

// 取消输入
function cancelInput() {
  showInput.value = false
  newMessage.value = ''
}

// 提交新留言
async function submitMessage() {
  if (!newMessage.value.trim()) return
  
  submitting.value = true
  
  try {
    const messageData = {
      content: newMessage.value.trim(),
      ip_hash: await generateIpHash(),
      user_agent: navigator.userAgent
    }
    
    const { data, error } = await supabase
      .from('messages')
      .insert([messageData])
      .select()
      .single()
    
    if (error) throw error
    
    // 提交成功
    newMessage.value = ''
    showInput.value = false
    
    // 显示新提交的留言
    currentMessage.value = data
    
    // 使用更优雅的成功提示
    showSuccess('你的印记已永久保存')
    
  } catch (error) {
    console.error('提交留言失败:', error)
    showError('保存失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

// 生成简单的 IP 哈希
async function generateIpHash() {
  try {
    // 使用第三方服务获取 IP（可选）
    const response = await fetch('https://api.ipify.org?format=json')
    const data = await response.json()
    const ip = data.ip
    
    // 哈希处理（不存储真实 IP）
    const encoder = new TextEncoder()
    const dataBuffer = encoder.encode(ip)
    const hashBuffer = await crypto.subtle.digest('SHA-256', dataBuffer)
    const hashArray = Array.from(new Uint8Array(hashBuffer))
    return hashArray.map(b => b.toString(16).padStart(2, '0')).join('').substring(0, 16)
  } catch {
    // 备用方案：使用用户代理和时间的组合
    return btoa(navigator.userAgent + Date.now()).substring(0, 16)
  }
}

// 格式化时间显示 - 智能混合方案（一周内相对时间，超过一周绝对时间）
function formatTime(timestamp) {
  const date = new Date(timestamp)
  const now = new Date()
  const diff = now - date
  
  // 1分钟内：刚刚
  if (diff < 60 * 1000) {
    return '刚刚'
  }
  
  // 1小时内：显示分钟
  if (diff < 60 * 60 * 1000) {
    const minutes = Math.floor(diff / (60 * 1000))
    return `${minutes}分钟前`
  }
  
  // 24小时内：显示小时
  if (diff < 24 * 60 * 60 * 1000) {
    const hours = Math.floor(diff / (60 * 60 * 1000))
    return `${hours}小时前`
  }
  
  // 一周内：显示天数
  if (diff < 7 * 24 * 60 * 60 * 1000) {
    const days = Math.floor(diff / (24 * 60 * 60 * 1000))
    return `${days}天前`
  }
  
  // 今年内的显示月日
  if (date.getFullYear() === now.getFullYear()) {
    return date.toLocaleDateString('zh-CN', { 
      month: 'long', 
      day: 'numeric' 
    })
  }
  
  // 跨年的显示完整日期
  return date.toLocaleDateString('zh-CN', { 
    year: 'numeric',
    month: 'long', 
    day: 'numeric' 
  })
}

// 提示函数
function showSuccess(message) {
  const toast = document.createElement('div')
  toast.textContent = message
  toast.style.cssText = `
    position: fixed;
    top: 20px;
    right: 20px;
    background: #10b981;
    color: white;
    padding: 12px 20px;
    border-radius: 6px;
    z-index: 10000;
    animation: slideIn 0.3s ease;
  `
  document.body.appendChild(toast)
  setTimeout(() => {
    document.body.removeChild(toast)
  }, 3000)
}

function showError(message) {
  const toast = document.createElement('div')
  toast.textContent = message
  toast.style.cssText = `
    position: fixed;
    top: 20px;
    right: 20px;
    background: #ef4444;
    color: white;
    padding: 12px 20px;
    border-radius: 6px;
    z-index: 10000;
    animation: slideIn 0.3s ease;
  `
  document.body.appendChild(toast)
  setTimeout(() => {
    document.body.removeChild(toast)
  }, 3000)
}
</script>

<style scoped>
.memory-capsule {
  font-family: inherit;
  color: inherit;
  position: relative;
}

/* 修复访问计数文字颜色 */
.visitor-counter {
  padding: 1rem 0;
  text-align: center;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  margin-bottom: 1.5rem;
}

.counter-text {
  font-size: 1rem;
  color: rgba(255, 255, 255, 0.8);
  font-style: italic;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}

.counter-text:hover {
  color: #fff;
}

.counter-number {
  font-weight: bold;
  color: #fff;
  font-style: normal;
  font-size: 1.1em;
  transition: all 0.3s ease;
}

.counter-text:hover .counter-number {
  text-shadow: 0 0 8px rgba(255, 255, 255, 0.5);
}

/* 计数切换动画 */
.counter-content {
  display: flex;
  justify-content: center;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}

.counter-fade-enter-active,
.counter-fade-leave-active {
  transition: all 0.2s ease;
}

.counter-fade-enter-from {
  opacity: 0;
  transform: translateY(8px) scale(0.95);
}

.counter-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px) scale(1.05);
}

/* 留言展示区域 - 修复背景突兀问题 */
.message-display-area {
  min-height: 120px;
  margin-bottom: 1rem;
}

.message-card {
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(10px);
  border-radius: 12px;
  padding: 1.5rem;
  border: 1px solid rgba(255, 255, 255, 0.1);
  transition: all 0.3s ease;
}

.message-card:hover {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(102, 126, 234, 0.3);
}

.message-content {
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.9);
  font-size: 1.1rem;
  margin-bottom: 1rem;
}

.message-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.85rem;
  color: rgba(255, 255, 255, 0.6);
}

.refresh-btn {
  background: rgba(255, 255, 255, 0.1);
  border: none;
  padding: 0.5rem;
  border-radius: 6px;
  cursor: pointer;
  color: rgba(255, 255, 255, 0.7);
  transition: all 0.2s;
}

.refresh-btn:hover {
  background: rgba(255, 255, 255, 0.2);
  color: rgba(255, 255, 255, 0.9);
}

/* 空状态样式 */
.empty-state {
  text-align: center;
  padding: 2rem;
  color: rgba(255, 255, 255, 0.6);
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(10px);
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.empty-icon {
  font-size: 2rem;
  margin-bottom: 0.5rem;
  opacity: 0.7;
}

.empty-subtitle {
  font-size: 0.9rem;
  margin-top: 0.25rem;
  color: rgba(255, 255, 255, 0.5);
}

/* 输入触发按钮 */
.input-trigger {
  text-align: center;
  margin: 1.5rem 0;
}

.trigger-btn {
  background: rgba(102, 126, 234, 0.8);
  color: white;
  border: none;
  border-radius: 24px;
  padding: 0.75rem 1.5rem;
  font-size: 0.9rem;
  cursor: pointer;
  transition: all 0.3s ease;
  backdrop-filter: blur(10px);
}

.trigger-btn:hover {
  background: rgba(102, 126, 234, 0.9);
  transform: translateY(-1px);
}

.trigger-btn.active {
  background: rgba(108, 117, 125, 0.8);
}

/* 覆盖式输入框 */
.overlay-input {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
}

.overlay-backdrop {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  backdrop-filter: blur(8px);
}

.overlay-content {
  position: relative;
  background: rgba(30, 30, 40, 0.9);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  padding: 2rem;
  width: 100%;
  max-width: 500px;
  max-height: 80vh;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.1);
  animation: modalSlideIn 0.3s ease-out;
  color: rgba(255, 255, 255, 0.9);
}

.overlay-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.overlay-header h3 {
  margin: 0;
  color: rgba(255, 255, 255, 0.9);
  font-size: 1.25rem;
  font-weight: 600;
}

.close-btn {
  background: none;
  border: none;
  font-size: 1.5rem;
  color: rgba(255, 255, 255, 0.7);
  cursor: pointer;
  padding: 0.25rem;
  border-radius: 4px;
  transition: all 0.2s;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.9);
}

.overlay-textarea {
  width: 100%;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 12px;
  padding: 1.25rem;
  font-family: inherit;
  font-size: 1rem;
  resize: vertical;
  transition: all 0.2s;
  background: rgba(255, 255, 255, 0.05);
  color: rgba(255, 255, 255, 0.9);
  min-height: 150px;
  line-height: 1.5;
}

.overlay-textarea:focus {
  outline: none;
  border-color: rgba(102, 126, 234, 0.6);
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
  background: rgba(255, 255, 255, 0.08);
}

.overlay-textarea::placeholder {
  color: rgba(255, 255, 255, 0.5);
}

.overlay-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 1.5rem;
}

.char-count {
  font-size: 0.85rem;
  color: rgba(255, 255, 255, 0.6);
}

.action-buttons {
  display: flex;
  gap: 0.75rem;
}

.cancel-btn {
  background: none;
  border: 1px solid rgba(220, 53, 69, 0.7);
  color: rgba(220, 53, 69, 0.9);
  border-radius: 8px;
  padding: 0.75rem 1.5rem;
  font-size: 0.9rem;
  cursor: pointer;
  transition: all 0.2s;
  font-weight: 500;
}

.cancel-btn:hover {
  background: rgba(220, 53, 69, 0.9);
  color: white;
}

.submit-btn-overlay {
  background: rgba(102, 126, 234, 0.8);
  color: white;
  border: none;
  border-radius: 8px;
  padding: 0.75rem 1.5rem;
  font-size: 0.9rem;
  cursor: pointer;
  transition: all 0.2s;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.submit-btn-overlay:hover:not(:disabled) {
  background: rgba(102, 126, 234, 0.9);
  transform: translateY(-1px);
}

.submit-btn-overlay:disabled {
  background: rgba(204, 204, 204, 0.3);
  cursor: not-allowed;
  transform: none;
}

/* 动画效果 */
@keyframes modalSlideIn {
  from {
    opacity: 0;
    transform: translateY(-20px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .overlay-content {
    padding: 1.5rem;
    margin: 1rem;
  }
  
  .overlay-header h3 {
    font-size: 1.1rem;
  }
  
  .overlay-textarea {
    padding: 1rem;
    min-height: 120px;
  }
  
  .action-buttons {
    flex-direction: column;
    width: 100%;
  }
  
  .cancel-btn,
  .submit-btn-overlay {
    width: 100%;
    justify-content: center;
  }
  
  .overlay-actions {
    flex-direction: column;
    gap: 1rem;
    align-items: stretch;
  }
  
  .char-count {
    text-align: center;
  }
}

/* Toast 动画 */
@keyframes slideIn {
  from {
    transform: translateX(100%);
    opacity: 0;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}
</style>