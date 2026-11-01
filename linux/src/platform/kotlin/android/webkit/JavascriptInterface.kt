package android.webkit

/** 标出暴露给页面脚本的方法；桌面端的 CEF 桥（linux/web/CefWebView.kt）按它找可调用的方法 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class JavascriptInterface
