package com.f2a.authenticator.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.f2a.authenticator.ui.theme.IconSize
import com.f2a.authenticator.ui.theme.LocalSemanticColors
import com.f2a.authenticator.ui.theme.Motion
import com.f2a.authenticator.ui.theme.Radius
import com.f2a.authenticator.ui.theme.Space
import com.f2a.authenticator.ui.theme.Type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 复制反馈统一 Snackbar 与剪贴板剩余时间浮条工具。
 */
object CopyFeedback {

    fun buildMessage(clipboardClearSeconds: Int): String {
        return if (clipboardClearSeconds <= 0) {
            "已复制（自动清除已关闭）"
        } else {
            "已复制 · $clipboardClearSeconds 秒后自动清除"
        }
    }

    /**
     * 开启自动清除时，倒计时浮条与按钮的“已复制”状态已构成完整反馈，
     * 再弹 Snackbar 只会与系统剪贴板浮层重叠；仅在无浮条时才需要它。
     */
    fun needsSnackbar(clipboardClearSeconds: Int): Boolean = clipboardClearSeconds <= 0

    fun show(
        snackbarHostState: SnackbarHostState,
        scope: CoroutineScope,
        clipboardClearSeconds: Int
    ) {
        if (!needsSnackbar(clipboardClearSeconds)) return
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(buildMessage(clipboardClearSeconds))
        }
    }
}

@Composable
fun ClipboardCountdownBanner(
    remainingSeconds: Int,
    onClearNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalSemanticColors.current
    AnimatedVisibility(
        visible = remainingSeconds > 0,
        enter = expandVertically(tween(Motion.base, easing = Motion.EaseStandard)) +
            fadeIn(tween(Motion.base)),
        exit = shrinkVertically(tween(Motion.fast, easing = Motion.EaseStandard)) +
            fadeOut(tween(Motion.fast))
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = Space.L, vertical = Space.XS)
                .clip(RoundedCornerShape(Radius.M))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(Radius.M)
                )
                .padding(start = Space.M, end = Space.XS, top = Space.XXS, bottom = Space.XXS),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ContentPaste,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconSize.XS)
            )
            Spacer(modifier = Modifier.width(Space.S))
            Text(
                text = "剪贴板含敏感凭据 · ${remainingSeconds}s 后清除",
                style = Type.BodyS,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClearNow) {
                Icon(
                    imageVector = Icons.Default.CleaningServices,
                    contentDescription = null,
                    tint = semantic.timerNormal,
                    modifier = Modifier.size(IconSize.XS)
                )
                Spacer(modifier = Modifier.width(Space.XS))
                Text(
                    text = "立即清空",
                    style = Type.Label,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
