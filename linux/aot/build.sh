#!/usr/bin/env bash
# 桌面端 GraalVM native-image 构建：一条命令出可用的原生包。
#
#   ./linux/aot/build.sh              # 全量：类路径 → 元数据 → 编译 → 打包
#   ./linux/aot/build.sh --refresh    # 重新采集可达性元数据（改了反射面时用）
#
# 中间产物在 linux/build/aot，成品在 build/output/rikka-native。
#
# 四件不查就一定会踩的事（全部本机实测）：
#  1. 要 GraalVM for JDK 25。21/24 上连纯 java2d 离屏程序都会在 libawt.so 的 JNI_OnLoad 里
#     FatalError「Could not allocate library name」。
#  2. 元数据必须把类同时写进 jni 段。只写 reflection 的话界面画得出来，但 AWT 的 XToolkit 线程
#     FindClass("java/lang/Thread") 抛 NoClassDefFoundError，鼠标点击永远进不到应用。
#  3. libjawt.so 内联不进可执行文件（AWT 是 dlopen("<java.home>/lib/libjawt.so")，
#     构建期烘 java.home 会让 native-image 自己崩），所以成品带一个 lib/ 和自定位启动脚本。
#  4. agent 只记录真正执行过的代码，采集只画到首屏的话，首屏之外的功能会在运行时缺项。
#     所以采集时带 -Drikka.aot.warmup=true，让 linux/.../desktop/AotWarmup.kt 把文件选择框
#     （Swing 按类名反射装 LookAndFeel）和内嵌 Web 服务各走一遍。
#  5. 默认只带采集时用过的 locale，界面会「不报错但长得不一样」：实测日期分组在 JVM 上是
#     「10月 11」、原生包上变成「Oct 11」。所以 -H:+IncludeAllLocales（成品 +35M；
#     换 -H:IncludeLocales=zh,en 能省回来，但界面语言再多的就会又静默退回英文）。
#     时区不用管：native-image 默认已带全（IncludeAllTimeZones 已废弃）。
#
# MPTODO: 添加 deb/appimage/flatpak 目标
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK="$REPO/linux/build/aot"
OUT="${OUT:-$REPO/build/output/rikka-native}"
GRAAL="${GRAAL_HOME:-/opt/graalvm-jdk-25.0.4+7.1}"
GRADLE_ARGS=(--configure-on-demand)
# 沙箱里直连 Maven Central 不通时才需要镜像 init 脚本，有就带上
# !!!开发环境测试用，Actions编译应该不会有这个问题
MIRROR_INIT="${MIRROR_INIT:-/tmp/central-mirror.gradle}"
[[ -f "$MIRROR_INIT" ]] && GRADLE_ARGS+=(-I "$MIRROR_INIT")
META="$WORK/reachability-metadata.json"
APP_MAIN=me.rerere.rikkahub.linux.MainKt
JVM_ARGS=(-Dskiko.renderApi=SOFTWARE -Djava.awt.headless=false)

log() { printf '\033[1;36m[aot]\033[0m %s\n' "$*"; }

# 采集元数据必须真的把界面画出来，所以这里总是自己起一个独占编号的 Xvfb：
# 绝不沿用环境里继承来的 DISPLAY（那通常是别人的桌面）。
start_display() {
    for n in $(seq 91 140); do
        [[ -e "/tmp/.X11-unix/X$n" ]] && continue
        Xvfb ":$n" -screen 0 1400x900x24 -nolisten tcp >"$WORK/xvfb.log" 2>&1 &
        XVFB_PID=$!
        export DISPLAY=":$n"
        sleep 3
        log "Use private display $DISPLAY"
        return 0
    done
    echo "Can't find available number for display, set AOT_DISPLAY to bypass" >&2
    exit 1
}

stop_display() { [[ -n "${XVFB_PID:-}" ]] && kill "$XVFB_PID" 2>/dev/null || true; }

# 采集进程在任何退出路径上都要收掉：窗口起不来时 exit 1、后面编译失败、Ctrl+C 都会漏掉
# 一个占着显存和 X 连接的 JVM。
cleanup() {
    [[ -n "${COLLECT_PID:-}" ]] && { kill -TERM "$COLLECT_PID" 2>/dev/null || true; wait "$COLLECT_PID" 2>/dev/null || true; }
    stop_display
}
trap cleanup EXIT

window_up() { xwininfo -root -tree 2>/dev/null | grep -q XCanvasPeer; }

mkdir -p "$WORK"

log "1/4 Compile and export runtime class path"
(cd "$REPO" && ./gradlew :linux:writeAotClasspath "${GRADLE_ARGS[@]}")
CP="$(cat "$WORK/classpath.txt")"

if [[ ! -s "$META" || "${1:-}" == "--refresh" ]]; then
    log "2/4 Use native-image agent to collect metadata"
    rm -f "$META"
    start_display
    "$GRAAL/bin/java" "${JVM_ARGS[@]}" -Drikka.aot.warmup=true \
        -agentlib:native-image-agent=config-output-dir="$WORK" \
        -cp "$CP" "$APP_MAIN" >"$WORK/collect.log" 2>&1 &
    COLLECT_PID=$!
    for _ in $(seq 1 240); do window_up && break; sleep 1; done
    window_up || { echo "Window not start up during 240s! $WORK/collect.log" >&2; tail -20 "$WORK/collect.log"; exit 1; }
    # 首屏之后再等一段，覆盖只有交互后才会走到的代码路径
    sleep 45
    # 实测 SIGTERM 约 2s 走完 JVM shutdown 钩子，agent 的元数据正是退出时整体落盘，
    # 所以这里只要优雅终止并回收，不需要定时落盘也不需要强杀。
    kill -TERM "$COLLECT_PID" 2>/dev/null || true
    wait "$COLLECT_PID" 2>/dev/null || true
    COLLECT_PID=
    stop_display
    [[ -s "$META" ]] || { echo "agent haven't write metadata! $WORK/collect.log" >&2; exit 1; }
else
    log "2/4 Reuse current metadata（--refresh to bypass）"
fi

log "3/4 Complete jni and compile"
python3 - "$META" "$CP" <<'PY'
import json, sys, zipfile, pathlib
meta_path, cp = pathlib.Path(sys.argv[1]), sys.argv[2]
m = json.loads(meta_path.read_text())

# agent 只记录采集时真正碰到的东西，所以「静态可枚举」的两类不靠采集，直接按全量登记：
#  - @Serializable：判据是 classpath 里存在 Xxx$$serializer（编译器插件生成的），
#    Ktor 的 content-negotiation 运行期按类型反射找它，缺一个就是一条 500；
#  - 资源：我们自己按路径读 classpath 的三棵树（前端产物、R 资源、android assets）。
SERIALIZABLE_SUFFIX = '$$serializer.class'
serial, resources = m.setdefault('serialization', []), m.setdefault('resources', [])
have_ser = {e.get('type') for e in serial}
for jar in cp.split(':'):
    if not jar.endswith('.jar'):
        continue
    try:
        names = zipfile.ZipFile(jar).namelist()
    except (OSError, zipfile.BadZipFile):
        continue
    for n in names:
        if n.endswith(SERIALIZABLE_SUFFIX):
            t = n[: -len(SERIALIZABLE_SUFFIX)].split('$')[0].replace('/', '.')
            if t not in have_ser:
                serial.append({'type': t}); have_ser.add(t)
seen_res = {e.get('glob') for e in resources}
for g in ('static/**', 'rikka-res/**', 'assets/**'):
    if g not in seen_res:
        resources.append({'glob': g}); seen_res.add(g)

# AWT 的 XToolkit 线程要 FindClass，只写 reflection 段会「能看不能点」，所以同步进 jni 段
ref, jni = m.get('reflection', []), m.setdefault('jni', [])
have = {e.get('type') for e in jni}
for e in ref:
    t = e.get('type')
    if t and t not in have:
        jni.append({'type': t, 'allDeclaredConstructors': True, 'allPublicConstructors': True,
                    'allDeclaredMethods': True, 'allPublicMethods': True,
                    'allDeclaredFields': True, 'allPublicFields': True, 'allDeclaredClasses': True})
        have.add(t)
meta_path.write_text(json.dumps(m, indent=1))
print(f'      reflection {len(ref)} / jni {len(jni)} / serialization {len(serial)} / resources {len(resources)}')
PY

rm -rf "$WORK/image" && mkdir -p "$WORK/image"
(cd "$REPO/linux" && "$GRAAL/bin/native-image" -J-Xmx9g \
    -H:ConfigurationFileDirectories="$WORK" \
    -H:+IncludeAllLocales \
    --no-fallback -march=compatibility -Djava.awt.headless=false \
    -H:Path="$WORK/image" -cp "$CP" "$APP_MAIN" -o rikka-native) | tail -3

log "4/4 Packaging → $OUT"
rm -rf "$OUT" && mkdir -p "$OUT/lib"
cp "$WORK/image/rikka-native" "$WORK/image/"*.so "$OUT/"
cp "$GRAAL/lib/libjawt.so" "$OUT/lib/"

# WARNING: 产物脚本不要有中文，规避潜在风险
cat > "$OUT/rikka-native.sh" <<'EOF'
#!/usr/bin/env bash
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$DIR/rikka-native" -Djava.home="$DIR" "$@"
EOF
chmod +x "$OUT/rikka-native.sh"

log "All done! $OUT/rikka-native.sh（$(du -sh "$OUT" | cut -f1)）"
