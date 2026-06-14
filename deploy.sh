#!/bin/bash

# ==========================================================
# SilentGuardian - 一键部署发布脚本
# 1. 提取 build.gradle.kts 中的版本号
# 2. 编译 Release APK
# 3. SCP 上传 APK 到部署服务器
# 4. 生成 update_config.json 并上传到服务器
#
# [Failsafe] 服务器凭证从环境变量读取，避免把生产服务器信息
# 硬编码进公开仓库。配置方式任选其一：
#   (a) cp .env.deploy.example .env.deploy && 填好后 source 它
#   (b) 在 ~/.zshrc 中 export SG_DEPLOY_HOST / SG_DEPLOY_PATH
# ==========================================================

# 颜色高亮
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

# 自动加载 gitignored 的 .env.deploy（如果存在）
if [ -f ".env.deploy" ]; then
    set -a
    source .env.deploy
    set +a
fi

# 校验服务器凭证环境变量
if [ -z "$SG_DEPLOY_HOST" ] || [ -z "$SG_DEPLOY_PATH" ]; then
    echo -e "${RED}错误：缺少服务器凭证环境变量。${NC}"
    echo -e "请在 .env.deploy（参考 .env.deploy.example）或 shell 中设置："
    echo -e "  ${YELLOW}SG_DEPLOY_HOST${NC}    SSH 目标主机，例如 admin@your.server.com"
    echo -e "  ${YELLOW}SG_DEPLOY_PATH${NC}    服务器上 APK 存放目录的绝对路径"
    exit 1
fi

echo -e "${GREEN}>>> 1. 读取版本信息...${NC}"
VERSION_NAME=$(grep 'versionName = ' app/build.gradle.kts | awk -F'"' '{print $2}')
VERSION_CODE=$(grep 'versionCode = ' app/build.gradle.kts | awk '{print $3}')

if [ -z "$VERSION_NAME" ] || [ -z "$VERSION_CODE" ]; then
    echo -e "${RED}读取版本信息失败！${NC}"
    exit 1
fi

echo "当前版本号: v$VERSION_NAME (Code: $VERSION_CODE)"

echo -e "\n${GREEN}>>> 2. 开始构建 Release APK 与 AAB...${NC}"
gradle assembleRelease
gradle bundleRelease

APK_PATH="app/build/outputs/apk/release/app-release.apk"
AAB_PATH="app/build/outputs/bundle/release/app-release.aab"
if [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}构建失败：未找到 APK 文件 ($APK_PATH)！${NC}"
    exit 1
fi

echo -e "\n${GREEN}>>> 3. 上传 APK 到部署服务器...${NC}"
TARGET_APK_NAME="SilentGuardian.apk"
REMOTE_HOST="$SG_DEPLOY_HOST"
REMOTE_APK_DIR="$SG_DEPLOY_PATH"
DOWNLOAD_BASE_URL="https://www.yes-tek.com/assets/apk"

scp -o StrictHostKeyChecking=no -o BatchMode=yes -o ServerAliveInterval=60 \
    "$APK_PATH" "${REMOTE_HOST}:${REMOTE_APK_DIR}/${TARGET_APK_NAME}"

if [ $? -ne 0 ]; then
    echo -e "${RED}APK 上传失败！请检查服务器连接或 SG_DEPLOY_HOST / SG_DEPLOY_PATH 是否正确。${NC}"
    exit 1
fi
echo "APK 已上传: ${REMOTE_HOST}:${REMOTE_APK_DIR}/${TARGET_APK_NAME}"

echo -e "\n${GREEN}>>> 4. 更新并上传本地 update_config.json...${NC}"

LOCAL_CONFIG="update_config.json"
if [ ! -f "$LOCAL_CONFIG" ]; then
    echo -e "${RED}未找到本地 $LOCAL_CONFIG 文件！请先在根目录创建。${NC}"
    exit 1
fi

# 更新本地配置文件的版本信息和下载链接
sed -i '' -E "s/\"latestVersionCode\": [0-9]+/\"latestVersionCode\": ${VERSION_CODE}/" "$LOCAL_CONFIG"
sed -i '' -E "s/\"latestVersionName\": \"[^\"]+\"/\"latestVersionName\": \"${VERSION_NAME}\"/" "$LOCAL_CONFIG"
sed -i '' -E "s|\"downloadUrl\": \"[^\"]+\"|\"downloadUrl\": \"${DOWNLOAD_BASE_URL}/${TARGET_APK_NAME}\"|" "$LOCAL_CONFIG"

echo "更新后的 $LOCAL_CONFIG 内容："
cat "$LOCAL_CONFIG"

scp -o StrictHostKeyChecking=no -o BatchMode=yes -o ServerAliveInterval=60 \
    "$LOCAL_CONFIG" "${REMOTE_HOST}:${REMOTE_APK_DIR}/update_config.json"

if [ $? -ne 0 ]; then
    echo -e "${RED}update_config.json 上传失败！${NC}"
    exit 1
fi

# 强制修复远程文件权限为全局可读，彻底解决 Nginx 403 问题
ssh -o StrictHostKeyChecking=no -o BatchMode=yes "${REMOTE_HOST}" "chmod 644 ${REMOTE_APK_DIR}/update_config.json"
echo "update_config.json 已上传至 ${REMOTE_APK_DIR}/update_config.json"
echo "对外访问地址: ${DOWNLOAD_BASE_URL}/update_config.json"

# ================= 新增：上传 ad_config.json =================
LOCAL_AD_CONFIG="ad_config.json"
if [ -f "$LOCAL_AD_CONFIG" ]; then
    echo -e "\n${GREEN}>>> 5. 上传本地 ad_config.json...${NC}"
    scp -o StrictHostKeyChecking=no -o BatchMode=yes -o ServerAliveInterval=60 \
        "$LOCAL_AD_CONFIG" "${REMOTE_HOST}:${REMOTE_APK_DIR}/ad_config.json"
    
    if [ $? -eq 0 ]; then
        ssh -o StrictHostKeyChecking=no -o BatchMode=yes "${REMOTE_HOST}" "chmod 644 ${REMOTE_APK_DIR}/ad_config.json"
        echo "ad_config.json 已上传至 ${REMOTE_APK_DIR}/ad_config.json"
        echo "对外访问地址: ${DOWNLOAD_BASE_URL}/ad_config.json"
    else
        echo -e "${RED}ad_config.json 上传失败！${NC}"
    fi
else
    echo -e "\n${RED}未找到本地 $LOCAL_AD_CONFIG 文件，跳过上传广告配置。${NC}"
fi

echo -e "\n${GREEN}✅ 部署全部完成！(v$VERSION_NAME)${NC}"
