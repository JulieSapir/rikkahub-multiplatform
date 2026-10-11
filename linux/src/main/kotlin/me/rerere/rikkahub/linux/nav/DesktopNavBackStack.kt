package me.rerere.rikkahub.linux.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * navigation3 的 desktop 变体只给了 `rememberNavBackStack(config: SavedStateConfiguration, vararg keys)`
 * 这一个重载，Android 端写法是 `rememberNavBackStack(startScreen)`。
 *
 * 桌面端没有“进程被杀后恢复导航栈”这回事（窗口关掉就是进程退出），所以直接建栈、不接 savedstate 序列化。
 * 起始屏只当初始值用，不能当 remember 的 key：RouteActivity 的 startScreen 是
 * `Screen.Chat(id = Uuid.random())`，一旦把 key 带上，任何让根作用域重组的改动（换主题、设置变更
 * 触发 settings 重新发射）都会把随机 id 当新 key，整个导航栈被重建，用户就被弹回主页。
 */
@Composable
fun desktopRememberNavBackStack(vararg navKeys: NavKey): NavBackStack<NavKey> =
    remember { NavBackStack(*navKeys) }
