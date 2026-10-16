/* eslint-disable no-undef */
import { defineConfig, loadEnv } from "vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";
import { resolve } from "path";
import { VitePWA } from "vite-plugin-pwa";
import vue from "@vitejs/plugin-vue";
import AutoImport from "unplugin-auto-import/vite";
import Components from "unplugin-vue-components/vite";
import viteCompression from "vite-plugin-compression";

// https://vitejs.dev/config/
export default ({ mode }) =>
  defineConfig({
    plugins: [
      vue(),
      AutoImport({
        imports: [
          "vue",
          {
            "element-plus": ["ElMessage"],
          },
        ],
        resolvers: [ElementPlusResolver()],
      }),
      Components({
        resolvers: [ElementPlusResolver()],
      }),
      VitePWA({
        registerType: "autoUpdate",
        workbox: {
          skipWaiting: true,
          clientsClaim: true,
          runtimeCaching: [
            {
              urlPattern: /(.*?)\.(js|css|woff2|woff|ttf)/, // js / css 静态资源缓存
              handler: "CacheFirst",
              options: {
                cacheName: "js-css-cache",
              },
            },
            {
              urlPattern: /(.*?)\.(png|jpe?g|svg|gif|bmp|psd|tiff|tga|eps)/, // 图片缓存
              handler: "CacheFirst",
              options: {
                cacheName: "image-cache",
              },
            },
          ],
        },
        manifest: {
          name: loadEnv(mode, process.cwd()).VITE_SITE_NAME,
          short_name: loadEnv(mode, process.cwd()).VITE_SITE_NAME,
          description: loadEnv(mode, process.cwd()).VITE_SITE_DES,
          display: "standalone",
          start_url: "/",
          theme_color: "#424242",
          background_color: "#424242",
          icons: [
            // 图标统一用 QQ 头像，由 scripts/fetch-qq-avatar.mjs 在构建前自动更新。
            // 腾讯接口只提供 100 / 640 两个尺寸，所以 192 和 512 都指向 640 那张
            // （浏览器按声明的尺寸缩放，Chrome 的可安装性检查需要 192 和 512 两项）。
            {
              src: "/images/icon/qq-avatar-100.jpg",
              sizes: "100x100",
              type: "image/jpeg",
            },
            {
              src: "/images/icon/qq-avatar.jpg",
              sizes: "192x192",
              type: "image/jpeg",
            },
            {
              src: "/images/icon/qq-avatar.jpg",
              sizes: "512x512",
              type: "image/jpeg",
            },
          ],
        },
      }),
      viteCompression(),
    ],
    server: {
      port: "3000",
      open: true,
      // 正确的 Bing 代理配置
      proxy: {
        "/bing-suggest": {
          target: "https://api.bing.com",
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/bing-suggest/, "/osjson.aspx"),
          // 模拟浏览器请求头，避免被 Bing 拦截
          configure: (proxy) => {
            proxy.on("proxyReq", (proxyReq) => {
              proxyReq.setHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
              );
            });
          },
        },
      },
    },
    resolve: {
      alias: [
        {
          find: "@",
          replacement: resolve(__dirname, "src"),
        },
      ],
    },
    css: {
      preprocessorOptions: {
        scss: {
          charset: false,
          additionalData: `@import "./src/style/global.scss";`,
        },
      },
    },
    build: {
      minify: "terser",
      terserOptions: {
        compress: {
          pure_funcs: ["console.log"],
        },
      },
    },
  });