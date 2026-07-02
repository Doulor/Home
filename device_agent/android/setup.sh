#!/bin/bash
# Termux 设备状态采集 Agent 安装脚本
# 使用方法: bash setup.sh

echo "=== 设备状态 Agent 安装 ==="
echo ""

# 更新包管理器
echo "[1/5] 更新包管理器..."
pkg update -y

# 安装依赖
echo "[2/5] 安装 Python 和 API 工具..."
pkg install -y python termux-api curl

# 安装 Python 依赖
echo "[3/5] 安装 Python 依赖..."
pip install supabase requests

# 创建配置文件
echo "[4/5] 创建配置文件..."
mkdir -p ~/device_agent
cat > ~/device_agent/config.py << 'EOF'
SUPABASE_URL = "https://你的项目URL.supabase.co"
SERVICE_ROLE_KEY = "你的 service_role key"
DEVICE_ID = "android-phone"
DEVICE_NAME = "Android"
EOF

# 复制 agent 脚本到 home 目录
echo "[5/5] 安装完成！"
echo ""
echo "=== 下一步 ==="
echo "1. 运行: python ~/device_agent/agent.py"
echo "2. 按 Ctrl+C 停止"
echo "3. 后台运行: nohup python ~/device_agent/agent.py > /dev/null 2>&1 &"
