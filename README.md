简体中文 | [English](./README_EN.md)

<p>
<strong><h2>Doulorの主页</h2></strong>
一个功能丰富的个人浏览器主页，基于 <a href="https://github.com/imsyy/home">imsyy/home</a> 大规模重构而来
</p>

![Doulorの主页](/screenshots/main.jpg)

### 与原项目的区别

本项目 fork 自 [imsyy/home](https://github.com/imsyy/home)，原项目长期未维护，且功能较为单一。我在原项目基础上进行了大规模重构，新增了大量功能和优化：

#### 新增功能

- **全新加载动画** — 包含月球、轨道环、流星、星座、星尘、星云、极光等多层 CSS 动画的载入画面
- **极简模式** — 一键切换至无干扰模式，仅保留时间、天气和搜索栏
- **日历组件** — 支持云端日历事件（中国传统节日/纪念日）与个人本地事件，支持多日期标注与展开动画
- **时光进度条** — 展示当日/本周/本月/本年已过去的时间百分比
- **留言板系统** — 基于 Supabase 的留言功能，留言以气泡形式展示在页面上
- **访客统计** — 基于 Supabase 的总访问量和独立访客统计
- **必应搜索建议** — 搜索栏支持必应联想词、键盘导航、全局快捷键
- **自定义壁纸管理** — 基于 IndexedDB 的壁纸系统，支持本地上传、URL 导入、重命名、锁定/解锁随机切换、删除
- **自定义音乐播放器** — 替代原项目的 APlayer，支持本地文件上传、网络 URL 导入、播放列表锁定随机播放
- **天气多源回退** — 高德 → 和风天气 → OpenMeteo 三 API 自动故障转移
- **移动端菜单** — 移动端悬浮菜单按钮，优化小屏体验
- **鼠标滚轮横向滚动** — 网站链接区域支持鼠标滚轮横向滑动
- **PWA 支持** — 支持离线访问，可安装到桌面

#### 优化改进

- 音乐播放器从 APlayer + MetingJS 替换为完全自定义实现，解除仅限中国大陆的限制
- 天气 API 从单源改为三源自动故障转移，保证可用性
- 壁纸从固定几张增加到 20 张内置 + 自定义上传管理
- 全局样式重构，更多动画细节和视觉优化
- 全面支持移动端适配

### 在线演示

- [Doulorの主页](https://Doulor.cn)

### 功能一览

- [x] 月球主题载入动画
- [x] 站点简介与一言（Hitokoto）
- [x] 日期及时间
- [x] 实时天气（三 API 自动回退）
- [x] 时光进度条（日/周/月/年）
- [x] 自定义音乐播放器（本地/网络）
- [x] 必应搜索建议
- [x] 云端 + 私有日历
- [x] 留言板系统
- [x] 访客统计
- [x] 极简模式
- [x] 壁纸自定义管理
- [x] 社交链接
- [x] 网站快捷链接
- [x] PWA 离线支持
- [x] 移动端适配

### 部署

#### 自动部署（GitHub Actions）

Fork 仓库后，前往 `Actions` 页面，开启工作流。每次推送后自动构建，生成可直接部署的静态文件压缩包。

![步骤1](/screenshots/step1.jpg)
![步骤2](/screenshots/step2.jpg)

#### 手动部署

**环境要求：** Node.js > 16.16.0，npm > 8.15.0

```bash
# 安装 pnpm
npm install -g pnpm

# 安装依赖
pnpm install

# 开发预览
pnpm dev

# 构建
pnpm build
```

构建完成后，静态资源在 **`dist` 目录** 中生成，可上传至服务器，或使用 `Vercel` 等平台一键导入自动部署。

#### Docker 部署

```bash
docker build -t home .
docker run -p 12445:12445 -d home
```

### 配置

配置文件位于项目根目录的 `.env` 和 `.env.local`：

```bash
# .env — 站点基本信息
VITE_SITE_NAME = "Doulorの主页"
VITE_SITE_ANTHOR = "Doulor"
VITE_SITE_KEYWORDS = "Doulor,个人主页"
VITE_SITE_DES = "一个默默无闻的主页"
VITE_SITE_URL = "Doulor.cn"
VITE_SITE_START = "2025-9-18"

# .env.local — 敏感信息（需自行创建）
VITE_WEATHER_KEY = ""       # 高德开放平台 Web 服务 Key
VITE_QWEATHER_KEY = ""      # 和风天气 Key（可选，作为备选）
VITE_SUPABASE_URL = ""      # Supabase 项目 URL
VITE_SUPABASE_ANON_KEY = "" # Supabase 匿名 Key
```

#### 天气配置

天气支持三 API 自动故障转移：高德开放平台 → 和风天气 → OpenMeteo（无需 Key）。至少配置高德 Key 即可正常使用。

前往 [高德开放平台控制台](https://console.amap.com/dev/index) 创建 `Web 服务` 类型的 Key，填入 `.env.local` 的 `VITE_WEATHER_KEY`。

#### 音乐播放器

音乐播放器已替换为自定义实现，支持：
- 本地 MP3 文件上传（存储在 IndexedDB）
- 网络 URL 导入
- 播放列表锁定（锁定后随机播放只从锁定曲目中选择）
- 迷你悬浮球和展开式播放卡片

#### 网站快捷链接

在 `src/assets/siteLinks.json` 中配置：

```json
{
  "icon": "Blog",
  "name": "博客",
  "link": "https://blog.imsyy.top/"
}
```

图标名称可前往 [xicons](https://www.xicons.org) 挑选，并在 `src/components/Links/index.vue` 中引入。

#### 社交链接

在 `src/assets/socialLinks.json` 中配置。

#### 云端日历事件

`src/assets/calendarCloudEvents.json` 的 `date` 字段支持逗号分隔的多个 ISO 日期：

```json
{
  "title": "春节",
  "date": "2025-01-29, 2026-02-17"
}
```

- 若当年在列表中已写明日期，则按文件显示
- 若当年未明确标注，则按「月/日」自动映射

#### 网站背景

`public/images` 目录下有 20 张默认壁纸。你也可以在设置面板中上传或导入自定义壁纸，壁纸存储在 IndexedDB 中。

#### 网站图标

在 `public/images/icon` 中替换网站图标。

### 技术栈

- [Vue 3](https://cn.vuejs.org/) — 前端框架
- [Vite](https://vitejs.cn/) — 构建工具
- [Pinia](https://pinia.vuejs.org/zh/) — 状态管理
- [Element Plus](https://element-plus.org/) — UI 组件库
- [Supabase](https://supabase.com/) — 后端服务（留言板、访客统计）
- [IconPark](https://iconpark.oceanengine.com/official) — 图标库
- [xicons](https://xicons.org/) — 图标库
- [Sass](https://sass-lang.com/) — CSS 预处理
- [PWA](https://developer.mozilla.org/docs/Web/Progressive_web_apps) — 离线支持

### 使用的 API

- [Hitokoto 一言](https://hitokoto.cn/)
- [高德开放平台](https://lbs.amap.com/)
- [和风天气](https://dev.qweather.com/)
- [OpenMeteo](https://open-meteo.com/)
- [小歪 API](https://api.aixiaowai.cn)
- [搏天 API](https://api.btstu.cn/doc/sjbz.php)

### 鸣谢

本项目基于 [imsyy/home](https://github.com/imsyy/home) 重构，感谢原作者的初始工作。

### Star History

[![Star History Chart](https://api.star-history.com/svg?repos=imsyy/home&type=Date)](https://star-history.com/#imsyy/home&Date)

<a title="CDN" target="_blank" href="https://cdnjs.com/"><img src="https://img.shields.io/badge/CDN-Cloudflare-blue"></a>&nbsp;<a title="Copyright" target="_blank" href="https://Doulor.cn/"><img src="https://img.shields.io/badge/Copyright%20%C2%A9%202025--2026-Doulor-red"></a>
