<template>
  <div class="search-container">
    <div class="search-box">
      <input
        type="text"
        v-model="searchText"
        placeholder="搜索内容..."
        @keyup.enter="handleSearch"
        class="search-input"
      />
      <button @click="handleSearch" class="search-btn">
        <Search theme="filled" size="18" fill="#efefef" />
      </button>
    </div>
    <div class="search-hint" v-if="searchText">
      按 Enter 键或点击图标搜索
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { Search } from "@icon-park/vue-next";

const searchText = ref("");

// 修改为Bing搜索引擎（核心改动）
const handleSearch = () => {
  if (!searchText.value.trim()) return;
  // Bing搜索链接格式：https://www.bing.com/search?q=关键词
  const searchUrl = `https://www.bing.com/search?q=${encodeURIComponent(searchText.value)}`;
  window.open(searchUrl, "_blank");
};
</script>

<style lang="scss" scoped>
/* 样式部分保持不变 */
.search-container {
  width: 100%;
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
  background-color: rgba(255, 255, 255, 0.15);
  color: #fff;
  font-size: 1rem;
  outline: none;
  transition: background-color 0.3s;

  &::placeholder {
    color: rgba(255, 255, 255, 0.6);
  }

  &:focus {
    background-color: rgba(255, 255, 255, 0.25);
  }
}

.search-btn {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 4px;
  background-color: rgba(255, 255, 255, 0.15);
  cursor: pointer;
  transition: background-color 0.3s;

  &:hover {
    background-color: rgba(255, 255, 255, 0.25);
  }
}

.search-hint {
  margin-top: 8px;
  font-size: 0.85rem;
  color: rgba(255, 255, 255, 0.6);
  text-align: right;
}
</style>
