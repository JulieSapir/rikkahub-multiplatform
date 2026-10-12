#!/usr/bin/env bash
# 从一次 :linux:run 的进程里抓出运行时 classpath，存给 build-native.sh 用。
# 先手动把 app 跑起来（gradle :linux:run），再在另一个终端执行本脚本。
set -euo pipefail
PID="$(pgrep -f 'linux/build/libs/linux.jar' | head -1)"
[[ -z "$PID" ]] && { echo "没有正在运行的桌面端进程，先跑 ./gradlew :linux:run" >&2; exit 1; }
tr '\0' '\n' < "/proc/$PID/cmdline" | awk '/^-cp$/{getline; print}' > /tmp/rikka-native-classpath.txt
echo "已写入 /tmp/rikka-native-classpath.txt（$(wc -c < /tmp/rikka-native-classpath.txt) 字节）"
