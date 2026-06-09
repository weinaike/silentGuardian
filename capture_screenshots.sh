#!/bin/bash

# 确保 screenshots 目录存在
mkdir -p screenshots

# 定义页面名称和对应的文件名
pages=(
  "1. 隐私协议弹窗界面 (五、隐私协议与首次启动)"
  "2. 权限引导页面 (六、权限引导配置)"
  "3. 主控看板页面 (七、主控看板)"
  "4. 应用选择管理页面 (八、应用选择管理)"
  "5. 使用时长限额设置页面 (九、使用时长限额设置)"
  "6. PIN码设置/验证页面 (十、PIN码设置与验证)"
  "7. 设备管理员激活页面 (十一、设备管理员与防卸载)"
  "8. 应用更新检查页面 (十二、应用更新检查)"
  "9. 隐私政策查看页面 (十三、隐私政策查看)"
)

filenames=(
  "1_privacy_policy_popup.png"
  "2_permission_guide.png"
  "3_main_dashboard.png"
  "4_app_selection_management.png"
  "5_time_limit_setting.png"
  "6_pin_setting_validation.png"
  "7_device_admin_activation.png"
  "8_app_update_check.png"
  "9_privacy_policy_view.png"
)

echo "开始截图流程，请根据提示在手机上打开对应页面并按回车键..."

for i in "${!pages[@]}"; do
  echo ""
  echo "👉 请在手机上打开：${pages[$i]}"
  read -p "准备好后，请按回车键继续 (Press Enter to capture)..."
  
  filename="${filenames[$i]}"
  echo "正在截图..."
  adb shell screencap -p "/sdcard/$filename"
  adb pull "/sdcard/$filename" "./screenshots/$filename"
  adb shell rm "/sdcard/$filename"
  echo "✅ 已保存至 ./screenshots/$filename"
done

echo ""
echo "🎉 所有截图已完成！请查看当前目录下的 screenshots 文件夹。"
