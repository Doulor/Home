<template>
  <div class="search-wrapper">
    <div class="search-container">
      <div class="search-box">
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
          :class="{ 'search-input--focused': isFocused }"
        />
        <button 
          @click="handleSearch" 
          class="search-btn"
          :class="{ 'search-btn--focused': isFocused }"
        >
          <Search 
            theme="filled" 
            size="18" 
            fill="#efefef" 
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
let debounceTimer = null;
let globalKeydownListener = null;
let abortController = null; // 用于取消请求

// 控制联想框显示
const shouldShowSuggestions = computed(() => {
  return isFocused.value && suggestions.value.length > 0 && !isLoading.value && !suggestError.value;
});

// 输入处理
const handleInput = (e) => {
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

// 获取搜索建议（通过本地代理请求）
const fetchSuggestions = async (keyword) => {
  // 重置状态
  isLoading.value = true;
  suggestError.value = false;
  suggestions.value = [];
  cancelPendingRequest(); // 取消之前的请求
  
  try {
    if (!isFocused.value) return;

    // 创建取消控制器
    abortController = new AbortController();
    
    // 使用本地代理地址 /bing-suggest（由vite.config.js配置转发）
    const response = await fetch(
      `/bing-suggest?query=${encodeURIComponent(keyword)}`,
      {
        method: "GET",
        signal: abortController.signal,
        timeout: 5000
      }
    );

    if (!response.ok) {
      throw new Error(`请求失败: ${response.status}`);
    }

    // 解析Bing返回的建议数据
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
      e.preventDefault();
      if (!isFocused.value) {
        isFocused.value = true;
      }
      searchText.value += e.key;
      if (searchText.value.length >= 2) {
        fetchSuggestions(searchText.value);
      }
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
  gap: 8px;
  width: 100%;
}

.search-input {
  flex: 1;
  padding: 10px 16px;
  border: none;
  border-radius: 4px;
  background-color: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.8);
  font-size: 1rem;
  outline: none;
  transition: all 0.3s;

  &::placeholder {
    color: rgba(255, 255, 255, 0.5);
  }
}

.search-input--focused {
  background-color: rgba(255, 255, 255, 0.15);
  color: #fff;
}

.search-btn {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 4px;
  background-color: rgba(255, 255, 255, 0.1);
  cursor: pointer;
  transition: all 0.3s;
  display: flex;
  align-items: center;
  justify-content: center;

  &:hover {
    background-color: rgba(255, 255, 255, 0.15);
  }
}

.search-btn--focused {
  background-color: rgba(64, 158, 255, 0.3);
  border: 1px solid rgba(64, 158, 255, 0.5);

  &:hover {
    background-color: rgba(64, 158, 255, 0.4);
  }
}

.search-hint {
  margin-top: 8px;
  font-size: 0.85rem;
  color: rgba(255, 255, 255, 0.6);
  text-align: right;
}

.loading-text {
  color: #409eff;
}

.error-text {
  color: #ff4d4f;
}

.suggestions {
  position: absolute;
  top: 50px;
  left: 0;
  right: 0;
  background-color: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(0, 0, 0, 0.2);
  border-radius: 6px;
  padding: 8px 0;
  z-index: 100;
  backdrop-filter: blur(12px);
  max-height: 300px;
  overflow-y: auto;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.suggestion-item {
  padding: 10px 16px;
  cursor: pointer;
  transition: background-color 0.2s;
  color: rgba(255, 255, 255, 1);
  text-align: left;
  font-size: 0.95rem;
  font-weight: 500;

  &.hover {
    background-color: rgba(255, 255, 255, 0.18);
    color: #fff;
  }
}

/* 滚动条样式 */
.suggestions::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

.suggestions::-webkit-scrollbar-track {
  background: transparent;
}

.suggestions::-webkit-scrollbar-thumb {
  background-color: rgba(255, 255, 255, 0.3);
  border-radius: 3px;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .search-input {
    font-size: 0.9rem;
    padding: 8px 12px;
  }

  .search-btn {
    width: 36px;
    height: 36px;
  }

  .suggestions {
    top: 44px;
  }

  .suggestion-item {
    padding: 8px 12px;
    font-size: 0.9rem;
  }
}
</style>