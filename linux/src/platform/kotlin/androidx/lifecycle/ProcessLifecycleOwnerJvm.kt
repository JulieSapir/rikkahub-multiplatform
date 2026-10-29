package androidx.lifecycle

/**
 * Android 用 ProcessLifecycleOwner 判断 App 在前台还是后台，桌面端对应“窗口有没有获得焦点”。
 *
 * 由窗口在焦点变化时调用 [setForeground]；上游只读 lifecycle，所以这里维持同一个实例，
 * 观察者注册时立刻补发当前状态，和 Android 的行为一致。
 */
class ProcessLifecycleOwner private constructor() {

    val lifecycle: Lifecycle = DesktopLifecycle()

    fun setForeground(foreground: Boolean) {
        (lifecycle as DesktopLifecycle).setForeground(foreground)
    }

    companion object {
        private val instance = ProcessLifecycleOwner()

        @JvmStatic
        fun get(): ProcessLifecycleOwner = instance

        fun setForeground(foreground: Boolean) = instance.setForeground(foreground)
    }
}

private class DesktopLifecycle : Lifecycle() {

    private val observers = mutableListOf<LifecycleObserver>()
    private var foreground = true

    /** KMP 版的 Lifecycle 不实现 LifecycleOwner，回调要的那个引用单独给一个壳。 */
    private val owner = object : LifecycleOwner {
        override val lifecycle: Lifecycle get() = this@DesktopLifecycle
    }

    override val currentState: State
        get() = if (foreground) State.RESUMED else State.CREATED

    override fun addObserver(observer: LifecycleObserver) {
        // 与 LifecycleRegistry 一致：新观察者要补发到当前状态（RESUMED = CREATE→START→RESUME）
        val events = synchronized(observers) {
            observers += observer
            if (foreground) {
                listOf(Event.ON_CREATE, Event.ON_START, Event.ON_RESUME)
            } else {
                listOf(Event.ON_CREATE)
            }
        }
        events.forEach { dispatch(observer, it) }
    }

    override fun removeObserver(observer: LifecycleObserver) {
        synchronized(observers) { observers -= observer }
    }

    fun setForeground(value: Boolean) {
        val changed = synchronized(observers) {
            if (foreground == value) false else {
                foreground = value
                true
            }
        }
        if (!changed) return
        val events = if (value) listOf(Event.ON_START, Event.ON_RESUME) else listOf(Event.ON_PAUSE, Event.ON_STOP)
        val targets = synchronized(observers) { observers.toList() }
        targets.forEach { observer -> events.forEach { event -> dispatch(observer, event) } }
    }

    private fun dispatch(observer: LifecycleObserver, event: Event) {
        (observer as? LifecycleEventObserver)?.onStateChanged(source = owner, event = event)
    }
}
