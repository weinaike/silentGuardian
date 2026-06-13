#!/bin/bash
sed -i '' '/<\/resources>/i\
\    <string name="home_sleep_mode_title">睡眠安息模式</string>\
\    <string name="home_sleep_mode_desc">请放下手机，好好休息</string>\
\    <string name="home_sleep_mode_status">当前时段已被锁定</string>\
' app/src/main/res/values/strings.xml
