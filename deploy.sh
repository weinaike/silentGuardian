#!/bin/bash

# ==========================================================
# SilentGuardian - 一键部署发布脚本
# 1. 提取 build.gradle.kts 中的版本号
# 2. 编译 Release APK
# 3. SCP 上传到阿里云服务器
# 4. 更新 gitee_release 中的 update_config.json
# 5. Push 到 Gitee
# ==========================================================

# 颜色高亮
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}>>> 1. 读取版本信息...${NC}"
VERSION_NAME=$(grep 'versionName = ' app/build.gradle.kts | awk -F'"' '{print $2}')
VERSION_CODE=$(grep 'versionCode = ' app/build.gradle.kts | awk '{print $3}')

if [ -z "$VERSION_NAME" ] || [ -z "$VERSION_CODE" ]; then
    echo -e "${RED}读取版本信息失败！${NC}"
    exit 1
fi

echo "当前版本号: v$VERSION_NAME (Code: $VERSION_CODE)"

echo -e "\n${GREEN}>>> 2. 开始构建 Release APK...${NC}"
gradle assembleRelease

APK_PATH="app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}构建失败：未找到 APK 文件 ($APK_PATH)！${NC}"
    exit 1
fi

echo -e "\n${GREEN}>>> 3. 上传 APK 到阿里云服务器...${NC}"
# 注意: ssh -o ServerAliveInterval=60 admin@47.237.161.121
TARGET_APK_NAME="SilentGuardian_v${VERSION_NAME}.apk"
scp -o StrictHostKeyChecking=no -o BatchMode=yes -o ServerAliveInterval=60 "$APK_PATH" admin@47.237.161.121:/home/admin/gost/brand/apk/$TARGET_APK_NAME

if [ $? -ne 0 ]; then
    echo -e "${RED}APK 上传失败！请检查服务器连接。${NC}"
    exit 1
fi

echo -e "\n${GREEN}>>> 4. 更新配置文件...${NC}"
CONFIG_FILE="gitee_release/update_config.json"
# 简单的文本替换更新 versionCode 和 versionName
if [ -f "$CONFIG_FILE" ]; then
    sed -i '' -E "s/\"latestVersionCode\": [0-9]+/\"latestVersionCode\": ${VERSION_CODE}/" "$CONFIG_FILE"
    sed -i '' -E "s/\"latestVersionName\": \"[^\"]+\"/\"latestVersionName\": \"${VERSION_NAME}\"/" "$CONFIG_FILE"
    sed -i '' -E "s|\"downloadUrl\": \"[^\"]+\"|\"downloadUrl\": \"https://www.yes-tek.com/asset/apk/${TARGET_APK_NAME}\"|" "$CONFIG_FILE"
    echo "配置文件已更新 (含版本与下载链接)。"
else
    echo -e "${RED}未找到 $CONFIG_FILE！请确认 gitee_release 子模块已正确初始化。${NC}"
    exit 1
fi

echo -e "\n${GREEN}>>> 5. 提交并推送配置到 Gitee...${NC}"
cd gitee_release || exit 1
git add update_config.json

# 检查是否有改动
if git diff-index --quiet HEAD --; then
    echo "配置文件无内容变动，跳过推送。"
else
    git commit -m "chore: release version v$VERSION_NAME"
    # 使用 token 推送
    git push origin master
    if [ $? -ne 0 ]; then
        echo -e "${RED}推送到 Gitee 失败！${NC}"
        exit 1
    fi
    echo "配置更新成功推送至 Gitee！"
fi

cd ..

echo -e "\n${GREEN}✅ 部署全部完成！(v$VERSION_NAME)${NC}"
