English | [简体中文](./README.md)

<p>
<strong><h2>Doulor's Homepage</h2></strong>
A feature-rich personal browser homepage, heavily refactored from <a href="https://github.com/imsyy/home">imsyy/home</a>
</p>

![Doulor's Homepage](/screenshots/main.jpg)

### Differences from Upstream

This project is forked from [imsyy/home](https://github.com/imsyy/home). The upstream has been unmaintained for a long time with limited features. I've done a major refactoring and added many new capabilities:

#### New Features

- **Animated Loading Screen** — Multi-layered CSS animation with moon, orbital rings, shooting stars, constellations, stardust, nebula, and aurora
- **Minimalist Mode** — One-click distraction-free mode, keeping only time, weather, and search bar
- **Calendar Component** — Cloud calendar events (Chinese holidays/memorial days) and personal local events, with multi-date support and expand animation
- **Time Capsule** — Day/week/month/year elapsed progress bars
- **Guestbook System** — Supabase-powered message board with floating message bubbles
- **Visitor Counter** — Total and unique visitor tracking via Supabase
- **Bing Search Suggestions** — Smart search bar with Bing autocomplete, keyboard navigation, and global hotkeys
- **Custom Wallpaper Management** — IndexedDB-based wallpaper system with local upload, URL import, rename, lock/unlock for random selection, and delete
- **Custom Music Player** — Replaces upstream APlayer with a fully custom player supporting local file upload, network URL import, and playlist lock for random play
- **Multi-API Weather Fallback** — AMap → QWeather → OpenMeteo automatic failover
- **Mobile Menu** — Floating hamburger menu on mobile devices (< 720px)
- **Horizontal Scroll with Mouse Wheel** — Website links grid supports mouse wheel horizontal scrolling
- **PWA Support** — Offline access and installable to desktop

#### Improvements

- Music player replaced from APlayer + MetingJS to a fully custom implementation, removing the mainland China restriction
- Weather API upgraded from single-source to tri-source automatic failover
- Wallpapers increased from a few to 20 built-in + custom upload management
- Global style overhaul with more animation details and visual polish
- Comprehensive mobile adaptation

### Live Demo

- [Doulor's Homepage](https://Doulor.cn)

### Features

- [x] Moon-themed loading animation
- [x] Site description & Hitokoto quotes
- [x] Date and time
- [x] Live weather (tri-API auto fallback)
- [x] Time capsule (day/week/month/year)
- [x] Custom music player (local/network)
- [x] Bing search suggestions
- [x] Cloud + private calendar
- [x] Guestbook system
- [x] Visitor counter
- [x] Minimalist mode
- [x] Wallpaper management
- [x] Social links
- [x] Website shortcuts
- [x] PWA offline support
- [x] Mobile adaptation

### Deployment

#### Auto Deploy (GitHub Actions)

After forking the repo, go to the `Actions` page and enable workflows. Each push triggers an automatic build, producing a downloadable static file archive ready for deployment.

#### Manual Deploy

**Requirements:** Node.js > 16.16.0, npm > 8.15.0

```bash
# Install pnpm
npm install -g pnpm

# Install dependencies
pnpm install

# Development preview
pnpm dev

# Build
pnpm build
```

After building, upload the files from the **`dist`** directory to your server, or use platforms like `Vercel` for one-click import and auto deploy.

#### Docker Deploy

```bash
docker build -t home .
docker run -p 12445:12445 -d home
```

### Configuration

Configuration files are in `.env` and `.env.local` at the project root:

```bash
# .env — Basic site info
VITE_SITE_NAME = "Doulorの主页"
VITE_SITE_ANTHOR = "Doulor"
VITE_SITE_KEYWORDS = "Doulor,homepage"
VITE_SITE_DES = "A simple homepage"
VITE_SITE_URL = "Doulor.cn"
VITE_SITE_START = "2025-9-18"

# .env.local — Sensitive keys (create this file yourself)
VITE_WEATHER_KEY = ""       # AMap Web Service Key
VITE_QWEATHER_KEY = ""      # QWeather Key (optional, fallback)
VITE_SUPABASE_URL = ""      # Supabase project URL
VITE_SUPABASE_ANON_KEY = "" # Supabase anonymous key
```

#### Weather

Weather supports tri-API auto failover: AMap → QWeather → OpenMeteo (no key required). At minimum, configure the AMap key.

Go to [AMap Console](https://console.amap.com/dev/index), create a `Web Service` type Key, and fill it in `VITE_WEATHER_KEY` in `.env.local`.

#### Music Player

The music player has been replaced with a custom implementation supporting:
- Local MP3 file upload (stored in IndexedDB)
- Network URL import
- Playlist locking (random play only selects from locked tracks)
- Mini floating disc and expandable player card

#### Website Shortcuts

Configure in `src/assets/siteLinks.json`:

```json
{
  "icon": "Blog",
  "name": "Blog",
  "link": "https://blog.imsyy.top/"
}
```

Browse icons at [xicons](https://www.xicons.org) and import them in `src/components/Links/index.vue`.

#### Social Links

Configure in `src/assets/socialLinks.json`.

#### Cloud Calendar Events

The `date` field in `src/assets/calendarCloudEvents.json` supports comma-separated multiple ISO dates:

```json
{
  "title": "Spring Festival",
  "date": "2025-01-29, 2026-02-17"
}
```

- If the current year is explicitly listed, the file date is used
- Otherwise, the month/day is auto-mapped to the current year

#### Wallpapers

The `public/images` directory contains 20 default wallpapers. You can also upload or import custom wallpapers via the settings panel — they are stored in IndexedDB.

#### Site Icons

Replace icons in `public/images/icon`.

### Tech Stack

- [Vue 3](https://vuejs.org/) — Frontend framework
- [Vite](https://vitejs.dev/) — Build tool
- [Pinia](https://pinia.vuejs.org/) — State management
- [Element Plus](https://element-plus.org/) — UI component library
- [Supabase](https://supabase.com/) — Backend (guestbook, visitor counter)
- [IconPark](https://iconpark.oceanengine.com/official) — Icon library
- [xicons](https://xicons.org/) — Icon library
- [Sass](https://sass-lang.com/) — CSS preprocessor
- [PWA](https://developer.mozilla.org/docs/Web/Progressive_web_apps) — Offline support

### APIs Used

- [Hitokoto](https://hitokoto.cn/)
- [AMap (Gaode)](https://lbs.amap.com/)
- [QWeather](https://dev.qweather.com/)
- [OpenMeteo](https://open-meteo.com/)
- [XiaoWai API](https://api.aixiaowai.cn)
- [BoTian API](https://api.btstu.cn/doc/sjbz.php)

### Acknowledgements

This project is based on [imsyy/home](https://github.com/imsyy/home). Thanks to the original author for the initial work.

### Star History

[![Star History Chart](https://api.star-history.com/svg?repos=imsyy/home&type=Date)](https://star-history.com/#imsyy/home&Date)

<a title="CDN" target="_blank" href="https://cdnjs.com/"><img src="https://img.shields.io/badge/CDN-Cloudflare-blue"></a>&nbsp;<a title="Copyright" target="_blank" href="https://Doulor.cn/"><img src="https://img.shields.io/badge/Copyright%20%C2%A9%202025--2026-Doulor-red"></a>
