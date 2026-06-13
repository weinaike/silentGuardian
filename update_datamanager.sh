#!/bin/bash
sed -i '' '/fun setSpecificDayLimit/i\
\    fun getSpecificDayLimit(dayOfWeek: Int): Int {\
\        return kv.decodeInt("limit_daily_day_$dayOfWeek", -1)\
\    }\
' app/src/main/java/com/yestek/silentguardian/manager/DataManager.kt
