/**
 * 自动获取 QQ 头像，生成站点用到的图标文件。
 *
 * 由 package.json 的 build 脚本在 vite build 之前自动运行，也可以手动执行：
 *   pnpm avatar
 *
 * 产出：
 *   public/images/icon/qq-avatar.jpg      640×640  主页左侧大 Logo
 *   public/images/icon/qq-avatar-100.jpg  100×100  浏览器标签页 favicon
 *
 * 换号码：改下面的 QQ_NUMBER（或用环境变量 QQ_NUMBER 覆盖）。
 *
 * 设计原则：这个脚本**永远不会让构建失败**。
 * 接口挂了 / 没网 / 返回默认头像时，都会保留已有文件并正常退出。
 */
import { mkdir, writeFile } from "node:fs/promises"
import { existsSync, statSync } from "node:fs"
import path from "node:path"

const QQ_NUMBER = process.env.QQ_NUMBER || "2737855297"

const TARGETS = [
  { size: 640, file: "public/images/icon/qq-avatar.jpg", label: "主页 Logo" },
  { size: 100, file: "public/images/icon/qq-avatar-100.jpg", label: "favicon" },
]

// 号码取不到头像时，接口会返回一张约 1KB 的默认图。低于这个大小就认为不是真头像。
const MIN_BYTES = 2048

/** 同一尺寸的两个接口互为备份 */
function endpoints(size) {
  return [
    `https://q1.qlogo.cn/g?b=qq&nk=${QQ_NUMBER}&s=${size}`,
    `https://q.qlogo.cn/headimg_dl?dst_uin=${QQ_NUMBER}&spec=${size}`,
  ]
}

function sizeOf(file) {
  try {
    return statSync(file).size
  } catch {
    return 0
  }
}

let failed = 0

for (const { size, file, label } of TARGETS) {
  const out = path.resolve(file)
  let done = false
  let lastError = ""

  for (const url of endpoints(size)) {
    try {
      const res = await fetch(url, { signal: AbortSignal.timeout(15000) })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)

      const buf = Buffer.from(await res.arrayBuffer())
      if (buf.length < MIN_BYTES) {
        throw new Error(`返回的像是默认头像（只有 ${buf.length} 字节）`)
      }

      await mkdir(path.dirname(out), { recursive: true })
      await writeFile(out, buf)
      console.log(`[qq-avatar] ${label} 已更新 → ${file}（${size}×${size}，${buf.length} 字节）`)
      done = true
      break
    } catch (e) {
      lastError = e.message
    }
  }

  if (!done) {
    failed++
    const kept = sizeOf(out)
    console.warn(
      `[qq-avatar] ${label} 获取失败（${lastError}）。` +
        (kept > 0
          ? `沿用已有文件（${kept} 字节），构建继续。`
          : `而且本地还没有 ${file}，页面上会显示裂图！`),
    )
  }
}

if (failed === 0) {
  console.log(`[qq-avatar] 全部完成（QQ ${QQ_NUMBER}）`)
}
process.exit(0)
