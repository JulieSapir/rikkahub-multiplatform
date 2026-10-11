package android.annotation

/**
 * Android lint 的抑制注解。桌面端不跑 lint，这里只需要让带注解的上游文件能编译。
 */
@Retention(AnnotationRetention.SOURCE)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.TYPEALIAS,
    AnnotationTarget.EXPRESSION,
    AnnotationTarget.FIELD,
)
annotation class SuppressLint(vararg val value: String)
