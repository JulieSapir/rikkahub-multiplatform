package androidx.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract

/**
 * 上游从 LocalContext 把环境强转成 ComponentActivity 拿 intentRegistry / setResult。
 * 桌面端没有 Activity，这里给一个能过类型检查的空壳：注册出来的启动器直接执行契约，
 * setResult 之类的回调按“取消”处理。
 */
open class ComponentActivity : Activity() {

    val intentRegistry: ActivityResultRegistry = ActivityResultRegistry()

    fun setResult(resultCode: Int, data: Intent? = null) = Unit

    /** 桌面端没有运行时权限，永远不会“需要解释”。 */
    fun shouldShowRequestPermissionRationale(permission: String): Boolean = false

    companion object {
        fun findActivity(context: Context): ComponentActivity? = context as? ComponentActivity
    }
}

/** 与 Android 同名：register(key, contract, callback) 返回一个可 launch 的句柄。 */
class ActivityResultRegistry {
    fun <I, O> register(
        key: String?,
        contract: ActivityResultContract<I, O>,
        callback: (O) -> Unit,
    ): ActivityResultLauncher<I> = object : ActivityResultLauncher<I> {
        override fun launch(input: I) = contract.execute(android.content.Context(), input, callback)

        override fun dispose() = Unit
    }

    fun unregister(key: String?) = Unit
}

typealias ActivityResultCallback = (ActivityResult) -> Unit
