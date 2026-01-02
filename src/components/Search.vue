<template>
  <div class="search-wrapper">
    <div class="search-container">
      <div class="search-box" :class="{ 'focused': isFocused }">
        <input
          type="text"
          v-model="searchText"
          placeholder="搜索内容..."
          @keyup.enter="handleSearch"
          @input="handleInput"
          class="search-input"
          ref="searchInput"
          @keydown.down="handleKeyDown('down')"
          @keydown.up="handleKeyDown('up')"
          @keydown.esc="handleEsc"
          @focus="handleFocus"
          @blur="handleBlur"
        />
        <button 
          @click="handleSearch" 
          class="search-btn"
        >
          <Search 
            theme="outline" 
            size="18" 
            fill="#fff" 
          />
        </button>
      </div>
      <div class="search-hint" v-if="searchText && !suggestions.length && isFocused && !isLoading">
        按 Enter 键或点击图标搜索
      </div>
      <!-- 加载中提示 -->
      <div class="search-hint" v-if="searchText && isLoading">
        <span class="loading-text">加载联想建议中...</span>
      </div>
      <!-- 错误提示 -->
      <div class="search-hint" v-if="searchText && suggestError && !isLoading">
        <span class="error-text">⚠️ 联想加载失败，可直接搜索</span>
      </div>
      <!-- 环境变量配置错误提示 -->
      <div class="search-hint" v-if="showEnvError && !isLoading">
        <span class="error-text">⚠️ 未配置代理地址，请检查环境变量</span>
      </div>
      <div 
        class="suggestions" 
        v-if="shouldShowSuggestions"
        @mousedown="isMouseDownOnSuggestion = true"
        @mouseup="isMouseDownOnSuggestion = false"
      >
        <div 
          v-for="(item, index) in suggestions" 
          :key="index"
          class="suggestion-item"
          @click="handleSuggestionClick(item, $event)"
          @mouseenter="hoverIndex = index"
          :class="{ 'hover': hoverIndex === index }"
        >
          {{ item }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from "vue";
import { Search } from "@icon-park/vue-next";
import { ElMessage } from "element-plus";

// 核心状态变量
const searchText = ref("");
const suggestions = ref([]);
const hoverIndex = ref(-1);
const searchInput = ref(null);
const isFocused = ref(false);
const isMouseDownOnSuggestion = ref(false);
const isLoading = ref(false); // 加载状态
const suggestError = ref(false); // 错误状态
const showEnvError = ref(false); // 环境变量配置错误提示
let debounceTimer = null;
let globalKeydownListener = null;
let abortController = null; // 用于取消请求

// 初始化时检测环境变量
onMounted(() => {
  // 开发环境不检测，生产环境检测
  if (import.meta.env.PROD) {
    const proxyUrl = import.meta.env.VITE_BING_PROXY_URL;
    if (!proxyUrl || proxyUrl.trim() === "") {
      console.warn("未配置VITE_BING_PROXY_URL环境变量，联想功能将无法使用");
      showEnvError.value = true;
    }
  }
});

import { mainStore } from "@/store";
const store = mainStore();

// 控制联想框显示
const shouldShowSuggestions = computed(() => {
  return store.searchSuggestion && isFocused.value && suggestions.value.length > 0 && !isLoading.value && !suggestError.value && !showEnvError.value;
});

// 输入处理
const handleInput = (e) => {
  if (!store.searchSuggestion) return;
  clearTimeout(debounceTimer);
  const value = e.target.value.trim();
  
  if (value.length < 2) {
    suggestions.value = [];
    suggestError.value = false;
    isLoading.value = false;
    cancelPendingRequest(); // 取消未完成的请求
    return;
  }
  
  debounceTimer = setTimeout(async () => {
    await fetchSuggestions(value);
  }, 300);
};

// 获取搜索建议（自动适配本地/线上环境）
const fetchSuggestions = async (keyword) => {
  // 重置状态
  isLoading.value = true;
  suggestError.value = false;
  suggestions.value = [];
  cancelPendingRequest(); // 取消之前的请求
  
  try {
    if (!isFocused.value) return;

    // 1. 确定请求地址（优先使用环境变量，本地开发用代理）
    const proxyUrl = import.meta.env.VITE_BING_PROXY_URL || '/bing-suggest';
    
    // 2. 检查地址有效性
    if (!proxyUrl || proxyUrl.trim() === "") {
      showEnvError.value = true;
      isLoading.value = false;
      return;
    }
    
    // 3. 创建取消控制器
    abortController = new AbortController();
    
    // 4. 发起请求
    const response = await fetch(
      `${proxyUrl}?query=${encodeURIComponent(keyword)}`,
      {
        method: "GET",
        signal: abortController.signal,
        timeout: 5000,
        headers: {
          'Accept': 'application/json'
        }
      }
    );

    // 5. 检查响应状态
    if (!response.ok) {
      throw new Error(`请求失败: ${response.status} ${response.statusText}`);
    }

    // 6. 解析响应内容（先检查是否为JSON）
    const contentType = response.headers.get('content-type');
    if (!contentType || !contentType.includes('application/json')) {
      const text = await response.text();
      throw new Error(`非JSON响应: ${text.substring(0, 100)}...`);
    }

    // 7. 解析Bing返回的建议数据
    const [, suggestionsList] = await response.json();
    
    if (Array.isArray(suggestionsList)) {
      suggestions.value = suggestionsList;
    } else {
      suggestions.value = [];
    }

  } catch (err) {
    // 忽略用户主动取消的请求错误
    if (err.name !== 'AbortError') {
      console.error("获取搜索建议失败:", err);
      suggestError.value = true;
      ElMessage.warning("联想建议加载失败，可直接搜索");
    }
  } finally {
    isLoading.value = false;
    abortController = null;
  }
};

// 取消未完成的请求
const cancelPendingRequest = () => {
  if (abortController) {
    abortController.abort();
    abortController = null;
  }
};

// 联想项点击处理
const handleSuggestionClick = (text, event) => {
  searchText.value = text;
  suggestions.value = [];
  
  nextTick(() => {
    searchInput.value?.focus();
  });
  
  if (!event.shiftKey) {
    handleSearch();
  }
};

// 搜索执行
const handleSearch = () => {
  const searchValue = searchText.value.trim();
  if (!searchValue) {
    ElMessage.info("请输入搜索内容");
    return;
  }
  // 跳转到Bing搜索结果页
  const searchUrl = `https://www.bing.com/search?q=${encodeURIComponent(searchValue)}`;
  window.open(searchUrl, "_blank");
};

// 键盘导航
const handleKeyDown = (direction) => {
  if (!suggestions.length || isLoading.value || suggestError.value) return;
  
  const len = suggestions.length;
  hoverIndex.value = direction === 'down' 
    ? (hoverIndex.value + 1) % len 
    : (hoverIndex.value - 1 + len) % len;
  
  searchText.value = suggestions.value[hoverIndex.value];
};

// ESC键处理
const handleEsc = () => {
  searchText.value = "";
  suggestions.value = [];
  hoverIndex.value = -1;
  suggestError.value = false;
  isLoading.value = false;
  cancelPendingRequest();
  searchInput.value?.blur();
  isFocused.value = false;
};

// 聚焦处理
const handleFocus = () => {
  isFocused.value = true;
  const value = searchText.value.trim();
  if (value.length >= 2) {
    fetchSuggestions(value);
  }
};

// 失焦处理
const handleBlur = () => {
  if (isMouseDownOnSuggestion.value) return;
  
  setTimeout(() => {
    isFocused.value = false;
    suggestions.value = [];
    isLoading.value = false;
    cancelPendingRequest();
  }, 200);
};

// 全局键盘监听
const setupGlobalKeyListener = () => {
  globalKeydownListener = (e) => {
    if (
      document.activeElement.tagName === 'INPUT' ||
      document.activeElement.tagName === 'TEXTAREA' ||
      document.activeElement.isContentEditable
    ) {
      return;
    }

    if (e.key === 'Backspace' && searchText.value) {
      e.preventDefault();
      searchText.value = searchText.value.slice(0, -1);
      if (searchText.value.length >= 2) {
        fetchSuggestions(searchText.value);
      } else {
        suggestions.value = [];
        suggestError.value = false;
      }
      return;
    }

    if (e.key === 'Enter' && searchText.value.trim()) {
      e.preventDefault();
      handleSearch();
      return;
    }

    if (e.key.length === 1 && !e.ctrlKey && !e.altKey && !e.metaKey) {
      // 如果是空格且当前没有搜索内容，则忽略（避免与播放暂停冲突）
      if (e.key === ' ' && !searchText.value) return;
      
      // 聚焦输入框，让浏览器原生处理输入（支持IME）
      searchInput.value.focus();
    }
  };

  document.addEventListener('keydown', globalKeydownListener);
};

// 移除全局监听
const removeGlobalKeyListener = () => {
  if (globalKeydownListener) {
    document.removeEventListener('keydown', globalKeydownListener);
  }
};

// 监听搜索文本变化
watch(searchText, (newVal) => {
  if (!newVal) {
    suggestions.value = [];
    hoverIndex.value = -1;
    suggestError.value = false;
    isLoading.value = false;
    cancelPendingRequest();
  }
});

// 组件挂载
onMounted(() => {
  setupGlobalKeyListener();
});

// 组件卸载
onUnmounted(() => {
  clearTimeout(debounceTimer);
  removeGlobalKeyListener();
  cancelPendingRequest();
});
</script>

<style lang="scss" scoped>
.search-wrapper {
  width: 100%;
  padding: 8px 0;
}

.search-container {
  width: 100%;
  position: relative;
}

.search-box {
  display: flex;
  align-items: center;
  width: 100%;
  background-color: rgba(0, 0, 0, 0.25);
  backdrop-filter: blur(10px);
  border-radius: 6px;
  padding: 4px 6px 4px 16px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);

  &:hover {
    background-color: rgba(0, 0, 0, 0.4);
    border-color: rgba(255, 255, 255, 0.2);
  }

  &.focused {
    background-color: rgba(0, 0, 0, 0.6);
    border-color: rgba(255, 255, 255, 0.3);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
  }
}

.search-input {
  flex: 1;
  padding: 8px 0;
  border: none;
  background: transparent;
  color: #fff;
  font-size: 15px;
  outline: none;
  min-width: 0;

  &::placeholder {
    color: rgba(255, 255, 255, 0.4);
    transition: color 0.3s;
  }

  &:focus::placeholder {
    color: rgba(255, 255, 255, 0.2);
  }
}

.search-btn {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 6px;
  background-color: rgba(255, 255, 255, 0.1);
  cursor: pointer;
  transition: all 0.3s;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-left: 8px;
  flex-shrink: 0;

  &:hover {
    background-color: rgba(255, 255, 255, 0.25);
    transform: scale(1.05);
  }
  
  &:active {
    transform: scale(0.95);
  }
}

.search-hint {
  margin-top: 8px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
  text-align: center;
  text-shadow: 0 1px 2px rgba(0,0,0,0.5);
}

.loading-text {
  color: #409eff;
}

.error-text {
  color: #ff4d4f;
}

.suggestions {
  position: absolute;
  top: 54px;
  left: 0;
  right: 0;
  background-color: rgba(30, 30, 30, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  padding: 6px;
  z-index: 100;
  backdrop-filter: blur(20px);
  max-height: 300px;
  overflow-y: auto;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
  animation: slide-down 0.2s ease-out;
}

@keyframes slide-down {
  from { opacity: 0; transform: translateY(-10px); }
  to { opacity: 1; transform: translateY(0); }
}

.suggestion-item {
  padding: 10px 14px;
  cursor: pointer;
  transition: all 0.2s;
  color: rgba(255, 255, 255, 0.8);
  text-align: left;
  font-size: 14px;
  border-radius: 8px;
  display: flex;
  align-items: center;

  &::before {
    content: "";
    display: inline-block;
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background-color: rgba(255, 255, 255, 0.2);
    margin-right: 10px;
    transition: background-color 0.2s;
  }

  &.hover, &:hover {
    background-color: rgba(255, 255, 255, 0.1);
    color: #fff;
    padding-left: 18px;
    
    &::before {
      background-color: #409eff;
    }
  }
}

/* 滚动条样式 */
.suggestions::-webkit-scrollbar {
  width: 4px;
}

.suggestions::-webkit-scrollbar-track {
  background: transparent;
}

.suggestions::-webkit-scrollbar-thumb {
  background-color: rgba(255, 255, 255, 0.2);
  border-radius: 2px;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .search-box {
    padding: 4px 6px 4px 14px;
  }
  
  .search-input {
    font-size: 14px;
  }

  .search-btn {
    width: 30px;
    height: 30px;
  }

  .suggestions {
    top: 48px;
  }
}
</style>
