package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Download01
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.ui.toast.ToastType
import org.koin.compose.koinInject

/**
 * Android 端用的是 image-viewer 那个 AAR（com.jvziyaoyao.scale，只有 Android 产物）做双指缩放，
 * 桌面端换成 HorizontalPager + zoomimage 的 CoilZoomAsyncImage：翻页、保存图片、Esc 关闭这些行为
 * 都保留，滚轮/拖拽缩放由 zoomimage 接管（telephoto 没有 JVM 产物，见 build.gradle.kts 的说明）。
 */
@Composable
fun ImagePreviewDialog(
    images: List<String>,
    onDismissRequest: () -> Unit,
    initialPage: Int = 0,
    actions: @Composable RowScope.(page: Int) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val filesManager: FilesManager = koinInject()
    val toaster = LocalToaster.current
    val pagerState = rememberPagerState(initialPage = initialPage) { images.size }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f))) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { index ->
                CoilZoomAsyncImage(
                    model = images[index],
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${images.size}",
                    color = Color.White,
                    modifier = Modifier.background(
                        Color.DarkGray.copy(alpha = 0.6f),
                        RoundedCornerShape(12.dp),
                    ).padding(horizontal = 10.dp, vertical = 4.dp),
                )
                actions(pagerState.currentPage)
                IconButton(onClick = {
                    scope.launch {
                        runCatching {
                            toaster.show("正在保存")
                            filesManager.saveMessageImage(context, images[pagerState.currentPage])
                            toaster.show(message = "已保存图片", type = ToastType.Success)
                        }.onFailure {
                            it.printStackTrace()
                            toaster.show(message = it.toString(), type = ToastType.Error)
                        }
                    }
                }) {
                    Icon(HugeIcons.Download01, null, tint = Color.White)
                }
            }
        }
    }
}
