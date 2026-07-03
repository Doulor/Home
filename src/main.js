import { createApp } from "vue";
import "element-plus/dist/index.css";
import "@/style/style.scss";
import App from "@/App.vue";
// 引入 pinia
import { createPinia } from "pinia";
import piniaPluginPersistedstate from "pinia-plugin-persistedstate";
// swiper
import "swiper/scss";
import "swiper/scss/pagination";

const app = createApp(App);
const pinia = createPinia();
pinia.use(piniaPluginPersistedstate);

// 一次性迁移：清理旧 localStorage 数据
const MIGRATION_VERSION = 2
const storedVersion = parseInt(localStorage.getItem("migration_version") || "0")
if (storedVersion < MIGRATION_VERSION) {
  try {
    const data = JSON.parse(localStorage.getItem("data") || "{}")
    if ("deviceStatusShow" in data) delete data.deviceStatusShow
    if ("calendarShow" in data) delete data.calendarShow
    localStorage.setItem("data", JSON.stringify(data))
  } catch {}
  localStorage.setItem("migration_version", String(MIGRATION_VERSION))
}

app.use(pinia);
app.mount("#app");

// PWA
navigator.serviceWorker.addEventListener("controllerchange", () => {
  // 弹出更新提醒
  console.log("站点已更新，刷新后生效");
  ElMessage("站点已更新，刷新后生效");
});
