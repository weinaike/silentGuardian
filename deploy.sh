#!/bin/bash

# ==========================================================
# SilentGuardian - 一键部署发布脚本
# 1. 提取 build.gradle.kts 中的版本号
# 2. 编译 Release APK
# 3. SCP 上传 APK 到部署服务器
# 4. 生成 update_config.json 并上传到服务器
# 5. 上传 ad_config.json（若存在）
# 6. [可选] 同步 GitHub Release 并挂载签名 APK 附件
#
# 用法：
#   bash deploy.sh             # 仅部署到服务器
#   bash deploy.sh --github    # 部署 + 同步 GitHub Release
#   SG_RELEASE_GITHUB=1 bash deploy.sh   # 同上
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

# 解析命令行参数：--github 启用 GitHub Release 同步（等价于 SG_RELEASE_GITHUB=1）
DO_GITHUB_RELEASE=0
for arg in "$@"; do
    [ "$arg" = "--github" ] && DO_GITHUB_RELEASE=1
done
[ "${SG_RELEASE_GITHUB:-0}" = "1" ] && DO_GITHUB_RELEASE=1

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

# ================= 可选：同步 GitHub Release =================
# 触发：bash deploy.sh --github  或  SG_RELEASE_GITHUB=1 bash deploy.sh
# 作用：在 GitHub 上为 v$VERSION_NAME 创建 Release 并挂载签名 APK 附件，
#       Release 说明复用 update_config.json 中的双语 changelog（单一信息源）。
# Token 来源（按优先级）：
#   1. 环境变量 SG_GITHUB_TOKEN
#   2. git credential helper（与 git push 共用凭证，无需额外配置）
# [Failsafe] 幂等设计：重复执行不会因 tag/release/asset 已存在而报错。
if [ "$DO_GITHUB_RELEASE" = "1" ]; then
    echo -e "\n${GREEN}>>> 6. 同步 GitHub Release...${NC}"

    if ! command -v jq >/dev/null 2>&1; then
        echo -e "${RED}跳过 GitHub Release：缺少 jq（brew install jq 后重试）。${NC}"
    else
        TAG="v${VERSION_NAME}"
        ASSET_NAME="SilentGuardian-v${VERSION_NAME}.apk"

        # 从 origin 远程地址推导 owner/repo，避免硬编码
        REMOTE_URL=$(git remote get-url origin)
        REPO=$(printf '%s' "$REMOTE_URL" | sed -E 's#.*github\.com[:/]([^/]+/[^/.]+).*#\1#')

        # 获取 token：优先环境变量，回退到 git 凭证（与 git push 同源）
        if [ -n "$SG_GITHUB_TOKEN" ]; then
            GH_TOKEN="$SG_GITHUB_TOKEN"
        else
            GH_TOKEN=$(printf 'protocol=https\nhost=github.com\n\n' | git credential fill 2>/dev/null | sed -n 's/^password=//p')
        fi

        if [ -z "$GH_TOKEN" ] || [ -z "$REPO" ]; then
            echo -e "${RED}跳过 GitHub Release：无法获取 token 或仓库名。${NC}"
            echo -e "  设置 ${YELLOW}SG_GITHUB_TOKEN${NC}，或确保 git push 凭证可用。"
        else
            API="https://api.github.com/repos/$REPO"
            GH_HDR=( -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github+json" )

            # 复用 update_config.json 的双语 changelog 作为 Release 说明
            CHANGELOG=$(jq -r '.updateMessage // ""' "$LOCAL_CONFIG")
            RELEASE_BODY=$(printf '%s\n\n---\n📎 在下方 Assets 中下载 %s 直接安装。\n📎 Download %s from the Assets below to install.' \
                "$CHANGELOG" "$ASSET_NAME" "$ASSET_NAME")

            # 1) 检查该 tag 是否已有 Release（幂等）
            REL_ID=$(curl -s "${GH_HDR[@]}" "$API/releases/tags/$TAG" | jq -r '.id // empty' 2>/dev/null)

            if [ -n "$REL_ID" ]; then
                echo "Release 已存在（id=$REL_ID），复用并刷新说明/附件。"
                BODY_JSON=$(jq -cn --arg name "$TAG" --arg body "$RELEASE_BODY" \
                    '{name:$name, body:$body, make_latest:"true"}')
                curl -s "${GH_HDR[@]}" -X PATCH "$API/releases/$REL_ID" -d "$BODY_JSON" >/dev/null
            else
                echo "创建 Release $TAG（GitHub 会在当前 HEAD 自动打 tag）..."
                HEAD_SHA=$(git rev-parse HEAD)
                BODY_JSON=$(jq -cn \
                    --arg tag "$TAG" --arg target "$HEAD_SHA" \
                    --arg name "$TAG" --arg body "$RELEASE_BODY" \
                    '{tag_name:$tag, target_commitish:$target, name:$name, body:$body, draft:false, prerelease:false, make_latest:"true"}')
                REL_ID=$(curl -s "${GH_HDR[@]}" -X POST "$API/releases" -d "$BODY_JSON" | jq -r '.id // empty' 2>/dev/null)
                if [ -z "$REL_ID" ]; then
                    echo -e "${RED}Release 创建失败！请检查 token 是否有 repo 权限。${NC}"
                fi
            fi

            # 2) 上传 APK 附件（同名旧附件先删除以保证可覆盖）
            if [ -n "$REL_ID" ]; then
                OLD_ASSET=$(curl -s "${GH_HDR[@]}" "$API/releases/$REL_ID/assets" \
                    | jq -r --arg name "$ASSET_NAME" '.[] | select(.name==$name) | .id' 2>/dev/null)
                if [ -n "$OLD_ASSET" ]; then
                    echo "覆盖旧附件 id=$OLD_ASSET..."
                    curl -s "${GH_HDR[@]}" -X DELETE "$API/releases/assets/$OLD_ASSET" >/dev/null
                fi

                echo "上传 $ASSET_NAME ..."
                ASSET_URL=$(curl -s "${GH_HDR[@]}" -X POST \
                    -H "Content-Type: application/vnd.android.package-archive" \
                    --data-binary "@$APK_PATH" \
                    "https://uploads.github.com/repos/$REPO/releases/$REL_ID/assets?name=$ASSET_NAME" \
                    | jq -r '.browser_download_url // empty' 2>/dev/null)

                if [ -n "$ASSET_URL" ]; then
                    echo "APK 已上传: $ASSET_URL"
                    echo "Release 页面: https://github.com/$REPO/releases/tag/$TAG"
                    # 同步本地 tag 引用（API 在远端建 tag，fetch 让本地可见）
                    git fetch --tags origin >/dev/null 2>&1 || true
                else
                    echo -e "${RED}APK 附件上传失败！${NC}"
                fi
            fi
        fi
    fi
fi

if [ "$DO_GITHUB_RELEASE" = "1" ]; then
    echo -e "\n${GREEN}✅ 部署全部完成（含 GitHub Release）！(v$VERSION_NAME)${NC}"
else
    echo -e "\n${GREEN}✅ 部署全部完成！(v$VERSION_NAME)${NC}"
    echo -e "${YELLOW}提示：加 --github 可同步发不到 GitHub Release。${NC}"
fi
