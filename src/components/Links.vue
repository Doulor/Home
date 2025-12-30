<template>
  <div class="links">
    <div class="line">
      <Icon size="20">
        <Link />
      </Icon>
      <span class="title">网站列表</span>
      <Editor
        theme="outline"
        size="18"
        fill="#fff"
        class="edit-btn"
        @click="dialogVisible = true"
      />
    </div>
    <!-- 网站列表 -->
    <Swiper
      v-if="siteLinks[0]"
      :modules="[Pagination, Mousewheel]"
      :slides-per-view="1"
      :space-between="40"
      :pagination="{
        el: '.swiper-pagination',
        clickable: true,
        bulletElement: 'div',
      }"
      :mousewheel="true"
      class="link-swiper"
      @sliderMove="disableTooltip"
      @slideChangeTransitionStart="disableTooltip"
      @transitionEnd="enableTooltip"
    >
      <SwiperSlide v-for="(page, index) in siteLinksList" :key="index">
        <div class="link-grid">
          <el-tooltip
            v-for="(item, i) in page"
            :key="i"
            :content="item.description || item.name"
            placement="top"
            :show-after="500"
            popper-class="link-tooltip"
            :disabled="tooltipDisabled"
          >
            <div class="link-card" @click="jumpLink(item)">
              <div class="icon-wrapper">
                <Icon size="28" v-if="siteIcon[item.icon]">
                  <component :is="siteIcon[item.icon]" />
                </Icon>
                <img
                  v-else-if="item.icon && item.icon.startsWith('http')"
                  :src="item.icon"
                  class="icon-img"
                />
                <span v-else class="icon-text">{{ item.icon }}</span>
              </div>
              <span class="name text-hidden">{{ item.name }}</span>
            </div>
          </el-tooltip>
        </div>
      </SwiperSlide>
      <div class="swiper-pagination" />
    </Swiper>
    <div v-else class="empty-site">
      <Icon size="40">
        <Link />
      </Icon>
      <span>暂无网站链接</span>
      <el-button type="primary" size="small" @click="resetToDefault" class="reset-btn"
        >加载默认</el-button
      >
    </div>

    <!-- 编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      title="编辑网站列表"
      width="600px"
      class="link-dialog"
      :append-to-body="true"
    >
      <el-form :model="form" label-width="60px">
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="网站名称" />
        </el-form-item>
        <el-form-item label="链接">
          <el-input v-model="form.link" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="图标">
          <el-input
            v-model="form.icon"
            placeholder="图标名 / Emoji / URL"
          />
          <div class="icon-tip">
            支持: 1. 内置图标名 (如 Blog, Cloud) 2. Emoji (如 🏠) 3. 图片链接
            (http...)
          </div>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" placeholder="网站描述" />
        </el-form-item>
        <div class="form-actions">
          <el-button type="primary" @click="saveLink">
            {{ isEditing ? "保存修改" : "添加链接" }}
          </el-button>
          <el-button @click="resetForm">重置表单</el-button>
        </div>
      </el-form>

      <el-divider>已添加列表</el-divider>

      <div class="link-list">
        <div
          v-for="(item, index) in store.siteLinks"
          :key="index"
          class="link-list-item"
        >
          <div class="left">
            <Icon size="20" v-if="siteIcon[item.icon]">
              <component :is="siteIcon[item.icon]" />
            </Icon>
            <img
              v-else-if="item.icon && item.icon.startsWith('http')"
              :src="item.icon"
              class="list-icon-img"
            />
            <span v-else class="list-icon-text">{{ item.icon }}</span>
            <span class="link-name">{{ item.name }}</span>
          </div>
          <div class="actions">
            <el-button
              v-if="index !== 0"
              size="small"
              @click="editLink(index)">编辑</el-button>
            <el-button
              v-if="index !== 0"
              size="small"
              type="danger"
              @click="deleteLink(index)"
              >删除</el-button
            >
          </div>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button type="warning" @click="resetToDefault">恢复默认</el-button>
          <el-button @click="dialogVisible = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { Icon } from "@vicons/utils";
import {
  Link,
  Blog,
  Atom,
  Cloud,
  Tools,
  Blogger,
  Fire,
  Music,
  Surprise,
  Amilia,
  Meetup,
  Database,
  Mountain,
  Dochub,
} from "@vicons/fa";
import { Editor } from "@icon-park/vue-next";
import { mainStore } from "@/store";
import { Swiper, SwiperSlide } from "swiper/vue";
import { Pagination, Mousewheel } from "swiper";
import "swiper/css";
import "swiper/css/pagination";
import siteLinksData from "@/assets/siteLinks.json";

const store = mainStore();
const dialogVisible = ref(false);
const isEditing = ref(false);
const editIndex = ref(-1);

const form = reactive({
  name: "",
  link: "",
  icon: "",
  description: "",
});

const tooltipDisabled = ref(false);
const disableTooltip = () => (tooltipDisabled.value = true);
const enableTooltip = () => (tooltipDisabled.value = false);

// 计算网站链接
const siteLinks = computed(() => store.siteLinks);
const siteLinksList = computed(() => {
  const result = [];
  const chunkSize = 8; // 每页显示8个
  for (let i = 0; i < siteLinks.value.length; i += chunkSize) {
    result.push(siteLinks.value.slice(i, i + chunkSize));
  }
  return result;
});

// 网站链接图标
const siteIcon = {
  Link,
  Blog,
  Mountain,
  Cloud,
  Amilia,
  Meetup,
  Database,
  Blogger,
  Fire,
  Music,
  Surprise,
  Atom,
  Tools,
  Dochub,
};

// 链接跳转
const jumpLink = (data) => {
  if (data.name === "音乐" && store.musicClick) {
    if (typeof $openList === "function") $openList();
  } else {
    window.open(data.link, "_blank");
  }
};

// 编辑逻辑
const resetForm = () => {
  form.name = "";
  form.link = "";
  form.icon = "";
  form.description = "";
  isEditing.value = false;
  editIndex.value = -1;
};

const saveLink = () => {
  if (!form.name || !form.link) {
    ElMessage.warning("名称和链接不能为空");
    return;
  }
  const newLink = { ...form };
  if (isEditing.value && editIndex.value > -1) {
    store.siteLinks[editIndex.value] = newLink;
    ElMessage.success("修改成功");
  } else {
    store.siteLinks.push(newLink);
    ElMessage.success("添加成功");
  }
  resetForm();
};

const editLink = (index) => {
  const item = store.siteLinks[index];
  form.name = item.name;
  form.link = item.link;
  form.icon = item.icon;
  form.description = item.description || "";
  isEditing.value = true;
  editIndex.value = index;
};

const deleteLink = (index) => {
  if (index === 0) {
    ElMessage.warning("第一个博客链接无法删除");
    return;
  }
  store.siteLinks.splice(index, 1);
  ElMessage.success("删除成功");
  
  if (store.siteLinks.length === 1) {
    ElMessageBox.confirm("只剩下一个基础链接，是否恢复默认列表？", "提示", {
      confirmButtonText: "恢复默认",
      cancelButtonText: "保持现状",
      type: "info",
      customClass: "link-dialog",
    })
      .then(() => {
        store.siteLinks = JSON.parse(JSON.stringify(siteLinksData));
        ElMessage.success("已恢复默认");
      })
      .catch(() => {});
  }
};

const resetToDefault = () => {
  ElMessageBox.confirm("确定要恢复默认列表吗？自定义数据将丢失。", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
    customClass: "link-dialog",
  }).then(() => {
    store.siteLinks = JSON.parse(JSON.stringify(siteLinksData));
    ElMessage.success("已恢复默认");
  });
};
</script>

<style lang="scss" scoped>
.links {
  .line {
    margin: 2rem 0.25rem 1rem;
    font-size: 1.1rem;
    display: flex;
    align-items: center;
    animation: fade 0.5s;
    .title {
      margin-left: 8px;
      font-size: 1.15rem;
      text-shadow: 0 0 5px #00000050;
    }
    .edit-btn {
      margin-left: 12px;
      cursor: pointer;
      opacity: 0.6;
      transition: opacity 0.3s;
      transform: translateY(3px);
      &:hover {
        opacity: 1;
      }
    }
  }

  .link-swiper {
    position: relative;
    padding-bottom: 20px;
    animation: fade 0.5s;
    
    .swiper-pagination {
      position: absolute;
      bottom: 0;
      left: 0;
      width: 100%;
      display: flex;
      justify-content: center;
      align-items: center;
      
      :deep(.swiper-pagination-bullet) {
        background-color: #fff;
        width: 20px;
        height: 4px;
        border-radius: 4px;
        transition: opacity 0.3s;
        opacity: 0.4;
        margin: 0 4px;
        
        &.swiper-pagination-bullet-active {
          opacity: 1;
        }
        
        &:hover {
          opacity: 0.8;
        }
      }
    }
  }

  .link-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    grid-template-rows: repeat(2, 1fr);
    gap: 15px;
    padding: 10px 5px;
    min-height: 230px;

    .link-card {
      height: 100px;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      background: rgba(0, 0, 0, 0.2);
      border-radius: 12px;
      cursor: pointer;
      transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
      border: 1px solid rgba(255, 255, 255, 0.05);
      backdrop-filter: blur(5px);

      &:hover {
        transform: translateY(-5px);
        background: rgba(0, 0, 0, 0.4);
        border-color: rgba(255, 255, 255, 0.15);
        box-shadow: 0 8px 16px rgba(0, 0, 0, 0.2);
        
        .icon-wrapper {
          transform: scale(1.1);
        }
      }

      &:active {
        transform: scale(0.98);
      }

      .icon-wrapper {
        display: flex;
        align-items: center;
        justify-content: center;
        margin-bottom: 8px;
        transition: transform 0.3s;
        height: 32px;
        width: 32px;
      }

      .name {
        font-size: 0.9rem;
        color: #eee;
        text-align: center;
        width: 100%;
        padding: 0 5px;
      }

      .icon-img {
        width: 28px;
        height: 28px;
        border-radius: 6px;
      }
      
      .icon-text {
        font-size: 24px;
      }
    }
  }

  @media (max-width: 720px) {
    .line {
      margin: 0.55rem 0.25rem 0.5rem;
    }
    .link-grid {
      grid-template-columns: repeat(4, 1fr);
      gap: 10px;
      min-height: 200px;
      
      .link-card {
        height: 90px;
        .name {
          font-size: 0.85rem;
        }
      }
    }
  }
  
  @media (max-width: 480px) {
    .link-grid {
      grid-template-columns: repeat(3, 1fr);
    }
  }
  
  .empty-site {
    height: 220px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 10px;
    color: #eee;
    animation: fade 0.5s;
    
    span {
      font-size: 1.1rem;
    }
  }
}

.icon-tip {
  font-size: 12px;
  color: #999;
  line-height: 1.5;
  margin-top: 5px;
}

.link-list {
  max-height: 300px;
  overflow-y: auto;
  margin-top: 20px;
  border: 1px solid #eee;
  border-radius: 4px;
  padding: 10px;
  
  .link-list-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 10px;
    border-bottom: 1px solid #f5f5f5;
    
    &:last-child {
      border-bottom: none;
    }
    
    .left {
      display: flex;
      align-items: center;
      gap: 10px;
      
      .list-icon-img {
        width: 20px;
        height: 20px;
        border-radius: 2px;
      }
      .list-icon-text {
        font-size: 18px;
      }
    }
  }
}

.form-actions {
  display: flex;
  gap: 10px;
}
</style>

<style lang="scss">
.link-tooltip {
  background: rgba(0, 0, 0, 0.6) !important;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
  color: #fff !important;
  border-radius: 8px !important;
  padding: 8px 12px !important;
  
  .el-popper__arrow {
    display: none !important;
  }
}

.link-dialog {
  background: rgba(20, 20, 20, 0.9) !important;
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 16px !important;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5) !important;

  .el-dialog__header, .el-message-box__header {
    margin-right: 0;
    border-bottom: 1px solid rgba(255, 255, 255, 0.1);
    padding: 20px;
    
    .el-dialog__title, .el-message-box__title {
      color: #fff;
      font-size: 1.2rem;
    }
    .el-dialog__close, .el-message-box__close {
      color: #fff;
      &:hover {
        color: #409eff;
      }
    }
  }

  .el-dialog__body, .el-message-box__content {
    padding: 20px;
    color: #fff;
  }

  .el-message-box__message {
    color: #fff;
  }

  .el-form-item__label {
    color: #ccc;
  }

  .el-input__wrapper {
    background-color: rgba(255, 255, 255, 0.05);
    box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.1) inset;
    
    &.is-focus {
      box-shadow: 0 0 0 1px #409eff inset !important;
    }
    
    .el-input__inner {
      color: #fff;
    }
  }

  .el-button {
    background: transparent;
    border: 1px solid rgba(255, 255, 255, 0.2);
    color: #fff;
    
    &:hover {
      background: rgba(255, 255, 255, 0.1);
      border-color: rgba(255, 255, 255, 0.3);
      color: #fff;
    }

    &.el-button--primary {
      background: #409eff;
      border-color: #409eff;
      color: #fff;
      
      &:hover {
        background: #66b1ff;
        border-color: #66b1ff;
      }
    }

    &.el-button--danger {
      background: #f56c6c;
      border-color: #f56c6c;
      color: #fff;
      
      &:hover {
        background: #f78989;
        border-color: #f78989;
      }
    }
    
    &.el-button--warning {
      background: #e6a23c;
      border-color: #e6a23c;
      color: #fff;
      
      &:hover {
        background: #ebb563;
        border-color: #ebb563;
      }
    }
  }

  .el-divider__text {
    background-color: transparent;
    color: #aaa;
  }
  
  .el-divider {
    border-top-color: rgba(255, 255, 255, 0.1);
  }

  .link-list {
    border-color: rgba(255, 255, 255, 0.1);
    
    .link-list-item {
      border-bottom-color: rgba(255, 255, 255, 0.05);
      
      &:hover {
        background: rgba(255, 255, 255, 0.05);
      }
    }
  }
}
</style>
