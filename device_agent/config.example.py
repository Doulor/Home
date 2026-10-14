# 复制此文件为 config.py，填入你的 Supabase 密钥
# 注意: secret key 是敏感信息，不要提交到 Git（config.py 已在 .gitignore 中排除）

# Home-Monitor 项目（device_status 表所在项目）
# 必须和网页端 .env.local 里的 VITE_MONITOR_URL 一致
SUPABASE_URL = "https://kphhahpdaqdwcbvhvseo.supabase.co"

# 在 Supabase Dashboard → Project Settings → API Keys 获取
# 新格式是 sb_secret_ 开头（旧的 service_role JWT 也仍可用）
SERVICE_ROLE_KEY = "你的 secret key（sb_secret_ 开头）"
