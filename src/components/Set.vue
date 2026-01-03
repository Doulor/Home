<template>
  <div class="setting">
    <el-collapse class="collapse" v-model="activeName" accordion>
      <el-collapse-item title="个性壁纸" name="1">
        <div class="bg-set">
          <el-button type="primary" @click="bgSetShow = true" class="bg-btn">
            管理壁纸列表
          </el-button>
        </div>
      </el-collapse-item>
      <el-collapse-item title="个性化调整" name="2">
        <div class="item">
          <span class="text">建站日期显示</span>
          <el-switch
            v-model="siteStartShow"
            inline-prompt
            :active-icon="CheckSmall"
            :inactive-icon="CloseSmall"
          />
        </div>
        <div class="item">
          <span class="text">音乐点击是否打开面板</span>
          <el-switch
            v-model="musicClick"
            inline-prompt
            :active-icon="CheckSmall"
            :inactive-icon="CloseSmall"
          />
        </div>
        <div class="item">
          <span class="text">底栏歌词显示</span>
          <el-switch
            v-model="playerLrcShow"
            inline-prompt
            :active-icon="CheckSmall"
            :inactive-icon="CloseSmall"
          />
        </div>
        <div class="item">
          <span class="text">底栏背景模糊</span>
          <el-switch
            v-model="footerBlur"
            inline-prompt
            :active-icon="CheckSmall"
            :inactive-icon="CloseSmall"
          />
        </div>
      </el-collapse-item>
      <el-collapse-item title="播放器配置" name="3">
        <div class="item">
          <span class="text">随机播放</span>
          <el-switch
            v-model="playerOrder"
            inline-prompt
            :active-icon="CheckSmall"
            :inactive-icon="CloseSmall"
            active-value="random"
            inactive-value="list"
          />
        </div>
        <div class="item">
          <span class="text">循环模式</span>
          <el-radio-group v-model="playerLoop" size="small" text-color="#FFFFFF">
            <el-radio value="all" border>列表</el-radio>
            <el-radio value="one" border>单曲</el-radio>
            <el-radio value="none" border>不循环</el-radio>
          </el-radio-group>
        </div>
      </el-collapse-item>
      <el-collapse-item title="组件功能" name="4">
        <div class="item">
          <span class="text">搜索联想 (Bing)</span>
          <el-switch
            v-model="searchSuggestion"
            inline-prompt
            :active-icon="CheckSmall"
            :inactive-icon="CloseSmall"
          />
        </div>
        <div class="item">
          <span class="text">留言气泡显示(30%概率)</span>
          <el-switch
            v-model="messageBubbleShow"
            inline-prompt
            :active-icon="CheckSmall"
            :inactive-icon="CloseSmall"
          />
        </div>
        <div class="item">
          <span class="text">右下角极简模式入口</span>
          <el-switch
            v-model="minimalistEntryVisible"
            inline-prompt
            :active-icon="CheckSmall"
            :inactive-icon="CloseSmall"
          />
        </div>
        <div class="item">
          <span class="text">极简模式显示时间</span>
          <el-switch
            v-model="minimalistTimeVisible"
            inline-prompt
            :active-icon="CheckSmall"
            :inactive-icon="CloseSmall"
          />
        </div>
        <div class="item">
          <span class="text">极简模式显示天气</span>
          <el-switch
            v-model="minimalistWeatherVisible"
            inline-prompt
            :active-icon="CheckSmall"
            :inactive-icon="CloseSmall"
          />
        </div>
      </el-collapse-item>
    </el-collapse>
    <!-- 壁纸管理弹窗 -->
    <Transition name="fade">
      <div class="set-modal" v-show="bgSetShow" @click="bgSetShow = false">
        <div class="set-panel" @click.stop>
          <div class="panel-header">
            <h3>壁纸管理 ({{ store.bgList ? store.bgList.length : 0 }})</h3>
            <close-small theme="outline" size="24" fill="#fff" class="close-btn" @click="bgSetShow = false" />
          </div>
          
          <div class="panel-tools">
             <div class="tool-btn" @click="triggerFileInput">
               <folder-open theme="outline" size="16" />
               <span>本地上传</span>
             </div>
             <el-input v-model="addBgUrl" placeholder="输入壁纸链接" size="small" style="flex: 1; margin: 0 10px;" />
             <el-button type="primary" size="small" @click="addBg">添加</el-button>
          </div>

          <div class="bg-list">
            <div v-if="!store.bgList || store.bgList.length === 0" class="empty">
              <picture theme="outline" size="48" fill="#ffffff80" />
              <p>暂无壁纸</p>
              <el-button type="primary" plain class="load-btn" @click="resetBg">重载预设壁纸</el-button>
            </div>
            <div v-else v-for="item in store.bgList" :key="item.id" class="bg-item">
              <div class="bg-info">
                <span class="name">{{ item.name }}</span>
                <span class="url">{{ item.url || '本地图片' }}</span>
              </div>
              <div class="bg-ops">
                <edit theme="outline" size="18" fill="#fff" class="op-btn" @click="editBgName(item)" />
                <component
                  :is="store.bgLockIds.includes(item.id) ? Lock : Unlock"
                  theme="outline"
                  size="18"
                  :fill="store.bgLockIds.includes(item.id) ? '#3B82F6' : '#fff'"
                  class="op-btn"
                  @click="toggleLock(item.id)"
                />
                <delete theme="outline" size="18" fill="#fff" class="op-btn" @click="deleteBg(item.id)" />
              </div>
            </div>
          </div>
          <input type="file" ref="fileInput" accept="image/*" multiple style="display: none" @change="handleFileChange" />
        </div>
      </div>
    </Transition>
    <!-- 重命名弹窗 -->
  </div>
</template>

<script setup>
import { CheckSmall, CloseSmall, SuccessPicture, Lock, Unlock, Delete, FolderOpen, Picture, Edit } from "@icon-park/vue-next";
import { mainStore } from "@/store";
import { storeToRefs } from "pinia";
import { ref, h, reactive, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { addBg as addBgDb, deleteBg as deleteBgDb, clearBgs, getAllBgs, updateBg as updateBgDb } from "@/utils/bgDb";

const store = mainStore();
const bgSetShow = ref(false);
const addBgUrl = ref("");
const fileInput = ref(null);

const defaultBgNames = [
  "天空之境上的钢琴", "黄昏小溪", "落日拱门", "蔚蓝银河", "云海草地",
  "黄昏光晕", "栏杆下的午后云影", "Online", "穿透云层的烟花", "月光下的阶梯小镇",
  "烟花易逝", "路口的猫", "云影列车", "彩云步道", "天空之境上的风车",
  "耀阳下的阶梯小镇", "田野", "西海紫云", "树影星河", "星轨山川"
];

// 刷新列表
const refreshList = async () => {
  try {
    let list = await getAllBgs();
    store.bgList = list.map(item => {
      if (item.blob) {
        return { ...item, url: URL.createObjectURL(item.blob) };
      }
      return item;
    });
  } catch (e) {
    console.error(e);
  }
};

// 添加网络壁纸
const addBg = async () => {
  if (!addBgUrl.value) return;
  try {
    await addBgDb({
      name: `自定义壁纸`,
      url: addBgUrl.value,
      type: 'custom',
      created: new Date()
    });
    addBgUrl.value = "";
    await refreshList();
    ElMessage.success("添加成功");
  } catch (e) {
    ElMessage.error("添加失败");
  }
};

// 本地上传
const triggerFileInput = () => fileInput.value.click();
const handleFileChange = async (e) => {
  const files = e.target.files;
  if (!files || !files.length) return;
  let count = 0;
  for (let i = 0; i < files.length; i++) {
    const file = files[i];
    const name = file.name.replace(/\.[^/.]+$/, "");
    try {
      await addBgDb({
        name: name,
        url: "", // Blob doesn't need URL stored
        blob: file,
        type: 'local',
        created: new Date()
      });
      count++;
    } catch (e) {}
  }
  if (count > 0) {
    await refreshList();
    ElMessage.success(`已添加 ${count} 张壁纸`);
  }
  e.target.value = "";
};

// 删除壁纸
const deleteBg = async (id) => {
  try {
    await deleteBgDb(id);
    store.bgLockIds = store.bgLockIds.filter(lockId => lockId !== id);
    await refreshList();
  } catch (e) {
    ElMessage.error("删除失败");
  }
};

// 锁定/解锁
const toggleLock = (id) => {
  if (store.bgLockIds.includes(id)) {
    store.bgLockIds = store.bgLockIds.filter(lockId => lockId !== id);
  } else {
    store.bgLockIds.push(id);
  }
};

// 重置预设
const resetBg = async () => {
  try {
    await clearBgs();
    for (let i = 1; i <= 20; i++) {
      await addBgDb({
        name: defaultBgNames[i - 1] || `默认壁纸 ${i}`,
        url: `/images/background${i}.jpg`,
        type: 'local',
        created: new Date()
      });
    }
    store.bgLockIds = [];
    await refreshList();
    ElMessage.success("已重置预设壁纸");
  } catch (e) {
    ElMessage.error("重置失败");
  }
};

// 打开重命名弹窗
const editBgName = (item) => {
  ElMessageBox.prompt("请输入新的名称", "修改名称", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    inputValue: item.name,
    customClass: "music-message-box",
    showCancelButton: false,
    inputPattern: /\S/,
    inputErrorMessage: "名称不能为空",
  })
    .then(async ({ value }) => {
      try {
        const list = await getAllBgs();
        const original = list.find(i => i.id === item.id);
        if (original) {
          original.name = value;
          await updateBgDb(original);
          await refreshList();
          ElMessage.success("修改成功");
        }
      } catch (e) {
        ElMessage.error("修改失败");
      }
    })
    .catch(() => {});
};
const {
  coverType,
  siteStartShow,
  musicClick,
  playerLrcShow,
  footerBlur,
  playerOrder,
  playerLoop,
  searchSuggestion,
  messageBubbleShow,
  minimalistMode,
  minimalistTimeVisible,
  minimalistWeatherVisible,
  minimalistEntryVisible,
} = storeToRefs(store);

// 监听极简模式开启
watch(minimalistMode, (val) => {
  if (val) {
    store.boxOpenState = false;
    store.setOpenState = false;
  }
});

// 默认选中项
const activeName = ref("1");

// 壁纸切换
const radioChange = () => {
  ElMessage({
    message: "壁纸更换成功",
    icon: h(SuccessPicture, {
      theme: "filled",
      fill: "#efefef",
    }),
  });
};
</script>

<style lang="scss" scoped>
.setting {
  .collapse {
    border-radius: 8px;
    --el-collapse-content-bg-color: #ffffff10;
    border-color: transparent;
    overflow: hidden;

    :deep(.el-collapse-item__header) {
      background-color: #ffffff30;
      color: #fff;
      font-size: 15px;
      padding-left: 18px;
      border-color: transparent;
    }

    :deep(.el-collapse-item__wrap) {
      border-color: transparent;

      .el-collapse-item__content {
        padding: 20px;
        .item {
          display: flex;
          align-items: center;
          justify-content: space-between;
          flex-wrap: wrap;
          font-size: 14px;
          .el-switch__core {
            border-color: transparent;
            background-color: #ffffff30;
          }
          .el-radio-group {
            .el-radio {
              margin: 2px 10px 2px 0;
              border-radius: 5px;

              &:last-child {
                margin-right: 0;
              }
            }
          }
        }
        .el-radio-group {
          justify-content: space-between;

          .el-radio {
            margin: 10px 16px;
            background: #ffffff26;
            border: 2px solid transparent;
            border-radius: 8px;

            .el-radio__label {
              color: #fff;
            }

            .el-radio__inner {
              background: #ffffff06 !important;
              border: 2px solid #eeeeee !important;
            }

            &.is-checked {
              background: #ffffff06 !important;
              border: 2px solid #eeeeee !important;
            }

            .is-checked {
              .el-radio__inner {
                background-color: #ffffff30 !important;
                border-color: #fff !important;
              }

              & + .el-radio__label {
                color: #fff !important;
              }
            }
          }
        }
      }
    }
  }

  .set-modal {
    position: fixed;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: rgba(0, 0, 0, 0.5);
    backdrop-filter: blur(5px);
    z-index: 999;
    display: flex;
    justify-content: center;
    align-items: center;

    .set-panel {
      width: 80%;
      max-width: 500px;
      height: 70%;
      background: rgba(0, 0, 0, 0.8);
      border-radius: 12px;
      padding: 20px;
      display: flex;
      flex-direction: column;
      border: 1px solid rgba(255, 255, 255, 0.1);

      .panel-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 20px;
        color: #fff;
        
        .close-btn {
          cursor: pointer;
          &:hover { transform: scale(1.1); }
        }
      }

      .panel-tools {
        display: flex;
        align-items: center;
        margin-bottom: 15px;
        
        .tool-btn {
          display: flex;
          align-items: center;
          gap: 6px;
          background: rgba(255,255,255,0.1);
          padding: 5px 12px;
          border-radius: 4px;
          cursor: pointer;
          font-size: 12px;
          color: #fff;
          height: 24px;
          transition: 0.2s;

          &:hover { background: rgba(255,255,255,0.2); }
        }
      }

      .bg-list {
        flex: 1;
        overflow-y: auto;
        
        /* 滚动条样式 */
        &::-webkit-scrollbar { width: 4px; }
        &::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.2); border-radius: 2px; }

        .empty {
          height: 100%;
          display: flex;
          flex-direction: column;
          justify-content: center;
          align-items: center;
          color: rgba(255,255,255,0.3);
          gap: 10px;
          .load-btn {
            margin-top: 10px;
            background: transparent;
            border: 1px solid rgba(255, 255, 255, 0.3);
            color: #fff;
            &:hover {
              background: rgba(255, 255, 255, 0.1);
              border-color: #fff;
            }
          }
        }

        .bg-item {
          display: flex;
          justify-content: space-between;
          align-items: center;
          padding: 10px;
          border-bottom: 1px solid rgba(255, 255, 255, 0.1);
          transition: background 0.3s;
          
          &:hover {
            background: rgba(255, 255, 255, 0.1);
          }

          .bg-info {
            display: flex;
            flex-direction: column;
            max-width: 70%;
            
            .name { color: #fff; font-size: 14px; }
            .url { color: #aaa; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
          }

          .bg-ops {
            display: flex;
            gap: 10px;
            
            .op-btn {
              cursor: pointer;
              &:hover { transform: scale(1.1); }
            }
          }
        }
      }
    }
  }
}
</style>

<style lang="scss">
.music-message-box {
  background: rgba(30, 30, 30, 0.8) !important;
  backdrop-filter: blur(20px);
  border-radius: 16px !important;
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 12px 48px rgba(0, 0, 0, 0.6) !important;
  padding-bottom: 20px !important;

  .el-message-box__header {
    .el-message-box__title {
      color: #fff !important;
    }
    .el-message-box__close {
      color: rgba(255, 255, 255, 0.6) !important;
      &:hover { color: #fff !important; }
    }
  }

  .el-message-box__content {
    color: rgba(255, 255, 255, 0.8) !important;
  }

  .el-message-box__btns {
    display: flex;
    flex-direction: row;
    justify-content: center;
    gap: 12px;

    .el-button {
      border: none;
      margin: 0 !important;
      &--default {
        background: #eee !important;
        border: 1px solid #ccc !important;
        color: #000000 !important;
        
        // 强制覆盖所有子元素颜色
        span, i, svg, path {
          color: #000000 !important;
          fill: #000000 !important;
        }

        &:hover {
          background: #fff !important;
          color: #000000 !important;
          span, i, svg, path {
            color: #000000 !important;
            fill: #000000 !important;
          }
        }
      }
      &--primary {
        background: #409eff !important;
        color: #fff !important;
        span {
          color: #fff !important;
        }
        &:hover {
          background: #66b1ff !important;
        }
      }
    }
  }
}
</style>
