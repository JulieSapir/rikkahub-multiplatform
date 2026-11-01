package android.webkit

import java.io.InputStream

/** 上游 WebViewLocalAssets 用它描述拦截下来的本地资源；桌面端由 CEF 的资源处理器读出来返回给页面 */
class WebResourceResponse(
    val mimeType: String,
    val encoding: String?,
    val data: InputStream,
)
