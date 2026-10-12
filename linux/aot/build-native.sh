#!/usr/bin/env bash
# 用 GraalVM native-image 把桌面端编成原生可执行文件。
#
# 三个不查就一定会踩的点（都是本机实测出来的）：
#  1. 必须 GraalVM 25。21.0.12 与 24.0.2 上连纯 java2d 离屏程序都会在 libawt.so 的
#     JNI_OnLoad 里 FatalError「Could not allocate library name」；25 改成动态链接后才起得来。
#  2. 可达性元数据要跑一遍 agent 采集，而且必须把类同时写进 jni 段：只写 reflection 的话
#     界面能画出来，但 AWT 的 XToolkit 线程 FindClass("java/lang/Thread") 会抛
#     NoClassDefFoundError，鼠标点击永远进不到应用（表现为「能看不能点」）。
#  3. 运行时要给 -Djava.home，否则 sun.awt.FontConfiguration 报「java.home property not set」。
#     home方法：新建lib目录，往里面丢个任意jdk版本的libjawt.so（只要8kb）
#
# 用法：
#   ./aot/build-native.sh collect   # 用 JVM 跑一遍 app 并采集元数据（需要 DISPLAY）
#   ./aot/build-native.sh build     # 编原生可执行文件（约 7 分钟、需要 ~9G 堆）
#   ./aot/build-native.sh run       # 跑起来
set -euo pipefail

MODULE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRAAL_HOME="${GRAAL_HOME:-/opt/graalvm-jdk-25.0.4+7.1}"
OUT_BIN="${OUT_BIN:-/tmp/rikka-native}"
META_DIR="${META_DIR:-/tmp/rikka-native-metadata}"
CP_FILE=/tmp/rikka-native-classpath.txt
MIRROR_INIT="${MIRROR_INIT:-/tmp/central-mirror.gradle}"
RUN_JVM_ARGS=(-Dskiko.renderApi=SOFTWARE -Djava.awt.headless=false)

# native-image 的类路径：从一次 gradle run 的进程命令行里抓 -cp 后面那段
classpath() {
    if [[ ! -s "$CP_FILE" ]]; then
        echo "先生成 classpath：跑一次 :linux:run，再执行 $(dirname "$0")/capture-classpath.sh" >&2
        exit 1
    fi
    cat "$CP_FILE"
}

case "${1:-}" in
collect)
    rm -rf "$META_DIR" && mkdir -p "$META_DIR"
    "$GRAAL_HOME/bin/java" "${RUN_JVM_ARGS[@]}" \
        -agentlib:native-image-agent=config-output-dir="$META_DIR",config-write-period-secs=30 \
        -cp "$(classpath)" me.rerere.rikkahub.linux.MainKt
    ;;
build)
    python3 - "$META_DIR" <<'PY'
import json, sys, pathlib
p = pathlib.Path(sys.argv[1]) / 'reachability-metadata.json'
m = json.loads(p.read_text())
ref, jni = m.get('reflection', []), m.setdefault('jni', [])
have = {e.get('type') for e in jni}
for e in ref:
    t = e.get('type')
    if not t or t in have:
        continue
    jni.append({'type': t, 'allDeclaredConstructors': True, 'allPublicConstructors': True,
                'allDeclaredMethods': True, 'allPublicMethods': True,
                'allDeclaredFields': True, 'allPublicFields': True, 'allDeclaredClasses': True})
    have.add(t)
p.write_text(json.dumps(m, indent=1))
print(f'jni 段补齐：{len(jni)} 条')
PY
    (cd "$MODULE_DIR" && "$GRAAL_HOME/bin/native-image" -J-Xmx9g \
        -H:ConfigurationFileDirectories="$META_DIR" \
        --no-fallback -march=compatibility -Djava.awt.headless=false \
        -cp "$(classpath)" me.rerere.rikkahub.linux.MainKt -o "$OUT_BIN")
    ;;
run)
    (cd "$MODULE_DIR" && exec "$OUT_BIN" "-Djava.home=$GRAAL_HOME" "${RUN_JVM_ARGS[@]}")
    ;;
*)
    echo "用法: $0 collect|build|run" >&2
    exit 2
    ;;
esac
