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
#  6. AWT 的 native 库会在任意一次 X11 事件里 FindClass 我们没跑到的类，漏一个「AWT-XAWT」
#     线程直接死掉（实测：复制文本后别的程序来取剪贴板 → XRootWindow 找不到，事件循环停摆）。
#     所以 java.desktop 的 sun.awt / sun.datatransfer / java.awt.datatransfer 整包登记进 jni。
#     这三条静态规则连同采集结果一起放在 build/aot/derived 里，agent 的原始输出不改写。
#  7. Swing 与剪贴板还有两处「不报错就等着崩」：UIManager 按名字反射装 *UI（没采集到的控件会抛
#     no ComponentUI class，出错对话框就是这么把进程带走的），以及 AWT 用 java.io 序列化搬运
#     selection 内容（漏登记时复制「成功」但别的应用什么也取不到）。规则见 3/4 步那段 python。
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
# 派生配置单独放一个目录，和 agent 采集的原始配置一起交给 native-image（两份都会被读）
GEN_DIR="$WORK/derived"
GEN="$GEN_DIR/reachability-metadata.json"
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

log "3/4 Generate derived config and compile"
rm -rf "$GEN_DIR"; mkdir -p "$GEN_DIR"
python3 - "$META" "$GEN" "$CP" "$GRAAL" <<'PY'
import json, subprocess, sys, zipfile, pathlib
agent_path, gen_path, cp, graal = sys.argv[1], pathlib.Path(sys.argv[2]), sys.argv[3], sys.argv[4]
agent = json.loads(pathlib.Path(agent_path).read_text())   # agent 的原始采集，只读不改
gen = {'reflection': [], 'jni': [], 'serialization': [], 'resources': []}
FLAGS = {'allDeclaredConstructors': True, 'allPublicConstructors': True, 'allDeclaredMethods': True,
         'allPublicMethods': True, 'allDeclaredFields': True, 'allPublicFields': True,
         'allDeclaredClasses': True}
ref, jni, ser, res = gen['reflection'], gen['jni'], gen['serialization'], gen['resources']
ref_by, jni_by = {}, {}

def want_reflect(cls):
    if cls not in ref_by:
        e = {'type': cls, **FLAGS}; ref.append(e); ref_by[cls] = e

def want_jni(cls):
    if cls not in jni_by:
        e = {'type': cls, **FLAGS}; jni.append(e); jni_by[cls] = e

# 三条「静态可枚举」的规则，不靠采集跑到；agent 那份保持原样，派生配置单独成文件，
# 这样每次构建都是重新推导，不会像就地改写那样把上一轮的合成条目再喂给下一轮。
#
# 1) @Serializable：kotlinx 拿序列化器有两条路——读 X.Companion 字段再调 serializer()，
#    或 Class.forName("X$$serializer") 取 INSTANCE，两条都是反射，所以 X / X$Companion /
#    X$$serializer 三个都要登记成员。漏掉的表现是「接口本该回 400 却变成 500」：
#    StatusPages 回 ErrorResponse 时拿不到它的序列化器。判据 = jar 里存在 Xxx$$serializer.class。
#    （只写 serialization 段没用，那一段是给 java.io 序列化用的。）
SERIALIZABLE = '$$serializer.class'
CLIPBOARD_SERIALIZABLE = (
    'java.lang.String', 'java.lang.Integer', 'java.lang.Long', 'java.lang.Boolean',
    'java.net.URI', 'java.net.URL', 'java.io.File',
    'java.awt.datatransfer.DataFlavor', 'java.awt.datatransfer.MimeType',
    'java.util.ArrayList', 'java.util.LinkedList', 'java.util.Vector',
    'java.util.Arrays$ArrayList', 'java.util.Collections$UnmodifiableList',
    'java.util.Collections$UnmodifiableRandomAccessList', 'java.util.Collections$EmptyList',
)
for jar in cp.split(':'):
    if not jar.endswith('.jar'):
        continue
    try:
        names = zipfile.ZipFile(jar).namelist()
    except (OSError, zipfile.BadZipFile):
        continue
    for n in names:
        if not n.endswith(SERIALIZABLE):
            continue
        t = n[: -len(SERIALIZABLE)].split('$')[0].replace('/', '.')
        ser.append({'type': t})
        for cls in (t, t + '$Companion', t + '$$serializer'):
            want_reflect(cls)

# 2) 我们自己按路径读 classpath 的三棵树：前端产物、R 资源、android assets；
#    再加 Swing/AWT 自己的资源（LookAndFeel 的语言包与图标，按组件名反射加载）。
for g in ('static/**', 'rikka-res/**', 'assets/**',
          'com/sun/swing/internal/plaf/**', 'com/sun/java/swing/plaf/**',
          'javax/swing/plaf/**', 'sun/awt/resources/**'):
    res.append({'glob': g})

# 3) X11 剪贴板：AWT 是把 Transferable 的数据用 java.io 序列化后放进 selection 传给别的程序的
#    （sun.awt.datatransfer.TransferableProxy.getTransferData → ObjectOutputStream），
#    而这条路 agent 不会记（实测：采集里剪贴板 round-trip=true，serialization 段仍是 0），
#    GraalVM 25 也没有「全部登记」开关。漏了的表现很阴：复制不报错，但别的应用来取什么也没有。
#    文本会牵动的类型按判据列全（应用侧只有文本剪贴板，没有 ImageSelection）。
for t in CLIPBOARD_SERIALIZABLE:
    ser.append({'type': t})

# 4) AWT：libawt_xawt.so 在任意一次 X11 事件里都可能 FindClass 我们没跑到的类，漏一个
#    「AWT-XAWT」线程就直接死掉（实测复制文本后别的程序取剪贴板：
#    NoClassDefFoundError: sun/awt/X11/XRootWindow，事件循环停摆）。这类没有单点判据，
#    整包登记 java.desktop 的 AWT/数据搬运命名空间。
listing = subprocess.run([graal + '/bin/jimage', 'list', graal + '/lib/modules'],
                         capture_output=True, text=True, check=True).stdout
for line in listing.splitlines():
    n = line.strip()
    if n.endswith('.class') and n.startswith(('sun/awt/', 'sun/datatransfer/', 'java/awt/datatransfer/')):
        want_jni(n[:-6].replace('/', '.'))

# 5) Swing 的 LookAndFeel：UIManager 按组件类型名反射实例化对应的 *UI，agent 只记采集时构造过的
#    那一种控件（实测 JFileChooser 被采集记上了，出错对话框用的 JOptionPane 没有，于是
#    「no ComponentUI class for …JOptionPane」直接带走进程）。判据可静态枚举：
#    java.desktop 里各 LAF 包的 *UI / *LookAndFeel 全登记。
for line in listing.splitlines():
    n = line.strip()
    if ('$' not in n and n.startswith(('javax/swing/plaf/metal/', 'javax/swing/plaf/basic/',
                                       'com/sun/java/swing/plaf/gtk/', 'com/sun/java/swing/plaf/motif/',
                                       'com/sun/java/swing/plaf/nimbus/'))
            and (n.endswith('UI.class') or n.endswith('LookAndFeel.class'))):
        cls = n[:-6].replace('/', '.')
        want_reflect(cls); want_jni(cls)

# 6) 剪贴板搬运「java 序列化对象」这一路，AWT 会用 java.rmi.MarshalledObject 包一层；
#    没登记时是 NoSuchMethodException: java.rmi.MarshalledObject.<init>(java.lang.Object)。
want_reflect('java.rmi.MarshalledObject')
# 采集记过的类同时喂给 reflection 和 jni：只写 reflection 的话 XToolkit 线程
# FindClass("java/lang/Thread") 会抛 NoClassDefFoundError，表现为「能看不能点」。
for e in list(agent.get('reflection', [])) + list(agent.get('jni', [])):
    t = e.get('type')
    if t:
        want_reflect(t); want_jni(t)
gen_path.write_text(json.dumps(gen, indent=1))
print(f'      derived: reflection {len(ref)} / jni {len(jni)} / serialization {len(ser)} / resources {len(res)}')
PY

rm -rf "$WORK/image" && mkdir -p "$WORK/image"
(cd "$REPO/linux" && "$GRAAL/bin/native-image" -J-Xmx9g \
    -H:ConfigurationFileDirectories="$WORK,$GEN_DIR" \
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
