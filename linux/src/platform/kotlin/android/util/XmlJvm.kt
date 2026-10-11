package android.util

/**
 * Android 的 Xml.newPullParser() 替身：S3 的 ListObjects 响应解析用。
 *
 * 直接返回 JDK 自带的 StAX 解析器，适配到 XmlPullParser 的那几个方法上
 * （上游只用到 nextTag / getText / getName / eventType）。
 */
object Xml {
    @JvmStatic
    fun newPullParser(): org.xmlpull.v1.XmlPullParser = org.kxml2.io.KXmlParser()
}
