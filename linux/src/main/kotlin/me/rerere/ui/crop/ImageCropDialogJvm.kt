package me.rerere.ui.crop

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.RefreshCcw
import me.rerere.hugeicons.stroke.RotateCwSquare
import me.rerere.hugeicons.stroke.Tick02

/**
 * 全屏的图片裁剪：拖动、缩放图片，拖框的边角调整范围，可以旋转 90°。
 * 与上游的差异（原文件绑 DialogWindowProvider / ui 模块资源，已 exclude）：
 * - 对话框没有系统窗口可拿，跳过状态栏明暗调整；
 * - 文案用内联英文（上游从 me.rerere.ui.R 读，桌面端没有那个资源表）；
 * - 回正动画用 spring 顶替 motionScheme。
 *
 * @param image 要裁剪的图片
 * @param onConfirm 裁好的图片，长边不超过 [maxResultSize]，由调用方负责存下来
 * @param aspectRatio 固定的宽高比（宽 / 高），比如头像传 1f；为空时可以自由裁剪，也可以在底部选比例
 */
@Composable
fun ImageCropDialog(
    image: Uri,
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit,
    aspectRatio: Float? = null,
    maxResultSize: Int = 4096,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    var state by remember(image) { mutableStateOf<ImageCropState?>(null) }
    var cropping by remember { mutableStateOf(false) }
    val refitSpec: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    LaunchedEffect(image, aspectRatio, maxResultSize) {
        val loaded = loadCropImage(context, image, maxResultSize)
        if (loaded != null) {
            state = ImageCropState(loaded.bitmap, loaded.preview, aspectRatio)
        } else {
            Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
            currentOnDismiss()
        }
    }

    fun confirm(current: ImageCropState) {
        if (cropping) return
        cropping = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.Default) { current.crop(maxResultSize) }
            }.onSuccess(onConfirm).onFailure {
                Toast.makeText(context, "Failed to crop image", Toast.LENGTH_SHORT).show()
            }
            cropping = false
        }
    }

    Dialog(
        onDismissRequest = { if (!cropping) onDismiss() },
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                val current = state
                CropTopBar(
                    state = current,
                    cropping = cropping,
                    onClose = onDismiss,
                    onConfirm = { current?.let(::confirm) },
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (current != null) {
                        ImageCropper(
                            state = current,
                            refitSpec = refitSpec,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(text = "Loading…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (current != null && current.lockedRatio == null) {
                    CropAspectBar(state = current)
                }
            }
        }
    }
}

// 关闭、旋转、重置和确认
@Composable
private fun CropTopBar(
    state: ImageCropState?,
    cropping: Boolean,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose, enabled = !cropping) {
            Icon(HugeIcons.Cancel01, contentDescription = "Cancel")
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { state?.rotate() }, enabled = state != null && !cropping) {
            Icon(HugeIcons.RotateCwSquare, contentDescription = "Rotate")
        }
        IconButton(onClick = { state?.reset() }, enabled = state?.modified == true && !cropping) {
            Icon(HugeIcons.RefreshCcw, contentDescription = "Reset")
        }
        Spacer(Modifier.weight(1f))
        FilledIconButton(onClick = onConfirm, enabled = state != null && !cropping) {
            Icon(HugeIcons.Tick02, contentDescription = "OK")
        }
    }
}

// 没有固定比例时，底部可以切换裁剪框的比例
@Composable
private fun CropAspectBar(state: ImageCropState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CropAspect.Options.forEach { aspect ->
            ToggleButton(
                checked = state.aspect == aspect,
                onCheckedChange = { state.selectAspect(aspect) },
                colors = ToggleButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Text(
                    text = when (aspect) {
                        CropAspect.Free -> "Free"
                        CropAspect.Original -> "Original"
                        is CropAspect.Ratio -> "${aspect.width}:${aspect.height}"
                    },
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
