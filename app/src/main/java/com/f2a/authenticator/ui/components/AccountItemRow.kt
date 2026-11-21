package com.f2a.authenticator.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.f2a.authenticator.data.model.AuthToken
import com.f2a.authenticator.data.model.Tag
import com.f2a.authenticator.data.model.TokenType
import com.f2a.authenticator.ui.theme.IconSize
import com.f2a.authenticator.ui.theme.LocalSemanticColors
import com.f2a.authenticator.ui.theme.Motion
import com.f2a.authenticator.ui.theme.Radius
import com.f2a.authenticator.ui.theme.Space
import com.f2a.authenticator.ui.theme.Touch
import com.f2a.authenticator.ui.theme.Type
import com.f2a.authenticator.utils.SecureClipboard
import kotlinx.coroutines.delay

@Composable
fun AccountItemRow(
    token: AuthToken,
    allTags: List<Tag>,
    clipboardClearSeconds: Int,
    privacyMasked: Boolean = false,
    nowMsProvider: () -> Long,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onRequestDelete: () -> Unit,
    onAdvanceHotp: () -> Unit,
    onCopied: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val semantic = LocalSemanticColors.current

    val envColors = remember(token.tenantEnv, semantic) { getEnvColors(token.tenantEnv, semantic) }
    var showNextCodePreview by rememberSaveable(token.id) { mutableStateOf(false) }
    var copiedFeedback by remember(token.id) { mutableStateOf(false) }
    var tempRevealed by remember(token.id, privacyMasked) { mutableStateOf(false) }
    var menuExpanded by remember(token.id) { mutableStateOf(false) }

    val effectiveMasked = privacyMasked && !tempRevealed

    LaunchedEffect(copiedFeedback) {
        if (copiedFeedback) {
            delay(Motion.hold)
            copiedFeedback = false
        }
    }

    LaunchedEffect(tempRevealed) {
        if (tempRevealed) {
            delay(10_000L)
            tempRevealed = false
        }
    }

    val matchedTagsWithColor = remember(token.tagIds, allTags) {
        allTags.filter { token.tagIds.contains(it.id) }
            .map { it to parseHexColor(it.colorHex) }
    }

    val copyCode: () -> Unit = {
        if (!token.secretInvalid) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            val code = computeTokenOtpCode(
                token = token,
                cycleOffset = if (showNextCodePreview) 1 else 0,
                nowMs = nowMsProvider()
            )
            SecureClipboard.copy(context, code, clipboardClearSeconds)
            copiedFeedback = true
            onCopied()
        }
    }

    val codeBgColor by animateColorAsState(
        targetValue = if (copiedFeedback) semantic.success.copy(alpha = 0.14f) else Color.Transparent,
        animationSpec = tween(durationMillis = Motion.base),
        label = "code_copy_bg"
    )
    val pulseScale by animateFloatAsState(
        targetValue = if (copiedFeedback) 1.02f else 1f,
        animationSpec = tween(durationMillis = Motion.fast),
        label = "code_copy_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.L, vertical = Space.M)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 账号名是识别凭据的主信息，独占首行；标签与环境徽标降为次行，空间不足时由它们收缩
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (token.isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "已置顶",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(Space.XS))
                    }
                    Text(
                        text = token.accountName,
                        style = Type.BodyM,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val firstTag = matchedTagsWithColor.firstOrNull()
                if (firstTag != null || token.tenantEnv.isNotBlank()) {
                    Spacer(modifier = Modifier.height(Space.XXS))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (token.tenantEnv.isNotBlank()) {
                            val (badgeBg, badgeText) = envColors
                            Box(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .clip(RoundedCornerShape(Radius.XS))
                                    .background(badgeBg)
                                    .padding(horizontal = Space.HS, vertical = Space.XXS)
                            ) {
                                Text(
                                    text = token.tenantEnv,
                                    color = badgeText,
                                    style = Type.Label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(Space.HS))
                        }
                        if (firstTag != null) {
                            val (tag, tagColor) = firstTag
                            Box(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .clip(RoundedCornerShape(Radius.XS))
                                    .background(tagColor.copy(alpha = 0.16f))
                                    .padding(horizontal = Space.HS, vertical = Space.XXS)
                            ) {
                                Text(
                                    text = tag.name,
                                    color = accessibleTagTextColor(tagColor, semantic.isDark),
                                    style = Type.Caption,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (matchedTagsWithColor.size > 1) {
                            Spacer(modifier = Modifier.width(Space.HS))
                            Text(
                                text = "+${matchedTagsWithColor.size - 1}",
                                style = Type.Caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (privacyMasked && !token.secretInvalid) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        tempRevealed = !tempRevealed
                    },
                    modifier = Modifier.size(Touch.MinTarget)
                ) {
                    Icon(
                        imageVector = if (tempRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (tempRevealed) "隐藏验证码" else "临时显示验证码 10 秒",
                        tint = if (tempRevealed) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        },
                        modifier = Modifier.size(IconSize.M)
                    )
                }
            }

            if (token.type != TokenType.HOTP) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showNextCodePreview = !showNextCodePreview
                    },
                    modifier = Modifier.size(Touch.MinTarget)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = if (showNextCodePreview) "切回当前周期" else "预览下一周期验证码",
                        tint = if (showNextCodePreview) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        },
                        modifier = Modifier.size(IconSize.M)
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(Touch.MinTarget)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "账号快捷操作",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IconSize.M)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (token.isPinned) "取消置顶" else "置顶该账号") },
                        leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onTogglePin()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("查看与编辑详情") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("移入回收站", color = semantic.errorText) },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = semantic.errorText)
                        },
                        onClick = {
                            menuExpanded = false
                            onRequestDelete()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Space.XS))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 点击验证码区域直接一键复制
            Box(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    }
                    .clip(RoundedCornerShape(Radius.S))
                    .background(codeBgColor)
                    .semantics {
                        role = Role.Button
                        contentDescription = "点击直接复制验证码"
                    }
                    .clickable(enabled = !token.secretInvalid, onClick = copyCode)
                    .padding(vertical = Space.XS, horizontal = Space.XS)
            ) {
                OtpRowCodeDisplay(
                    token = token,
                    showNextCodePreview = showNextCodePreview,
                    isMasked = effectiveMasked,
                    nowMsProvider = nowMsProvider
                )
            }

            Spacer(modifier = Modifier.width(Space.S))

            if (token.type == TokenType.HOTP && !token.secretInvalid) {
                FilledTonalButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAdvanceHotp()
                    },
                    shape = RoundedCornerShape(Radius.M),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = Space.MS, vertical = Space.HS),
                    modifier = Modifier.heightIn(min = Touch.MinTarget)
                ) {
                    Text("下一", style = Type.BodyS, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.width(Space.HS))
            }

            FilledTonalButton(
                onClick = copyCode,
                enabled = !token.secretInvalid,
                shape = RoundedCornerShape(Radius.M),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (copiedFeedback) {
                        semantic.success.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                contentPadding = PaddingValues(horizontal = Space.M, vertical = Space.HS),
                modifier = Modifier.heightIn(min = Touch.MinTarget)
            ) {
                AnimatedContent(
                    targetState = copiedFeedback,
                    transitionSpec = {
                        fadeIn(tween(Motion.fast, easing = Motion.EaseStandard)) togetherWith
                            fadeOut(tween(Motion.fast, easing = Motion.EaseStandard))
                    },
                    label = "copy_anim"
                ) { isCopied ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = if (isCopied) semantic.success else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(IconSize.XS)
                        )
                        Spacer(modifier = Modifier.width(Space.XS))
                        Text(
                            text = if (isCopied) "已复制" else "复制",
                            style = Type.BodyS,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isCopied) semantic.success else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        if (token.type != TokenType.HOTP && !token.secretInvalid) {
            Spacer(modifier = Modifier.height(Space.HS))
            OtpLinearProgressBar(
                period = token.period,
                nowMsProvider = nowMsProvider,
                barHeight = 2.dp
            )
        }

        if (token.note.isNotBlank()) {
            Spacer(modifier = Modifier.height(Space.HS))
            Text(
                text = "备忘: ${token.note}",
                style = Type.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
