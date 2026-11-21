package com.f2a.authenticator.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.f2a.authenticator.data.model.AuthToken
import com.f2a.authenticator.data.model.Tag
import com.f2a.authenticator.ui.state.IssuerGroupUi
import com.f2a.authenticator.ui.theme.IconSize
import com.f2a.authenticator.ui.theme.LocalSemanticColors
import com.f2a.authenticator.ui.theme.Motion
import com.f2a.authenticator.ui.theme.Radius
import com.f2a.authenticator.ui.theme.Space
import com.f2a.authenticator.ui.theme.Type

@Composable
fun IssuerAccordionCard(
    group: IssuerGroupUi,
    allTags: List<Tag>,
    clipboardClearSeconds: Int,
    modifier: Modifier = Modifier,
    privacyMasked: Boolean = false,
    nowMsProvider: () -> Long,
    onToggleExpand: () -> Unit,
    onAccountClick: (AuthToken) -> Unit,
    onTogglePin: (String) -> Unit,
    onRequestDelete: (AuthToken) -> Unit,
    onAdvanceHotp: (AuthToken) -> Unit,
    onCopied: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val semantic = LocalSemanticColors.current
    val expanded = group.isExpanded

    val brandGradient = remember(group.issuer) { getBrandGradient(group.issuer) }
    val avatarLabel = remember(group.issuer, group.accounts) {
        issuerAvatarLabel(group.issuer, group.accounts.firstOrNull()?.accountName ?: "")
    }
    val headerEnvColors = remember(group.accounts, semantic) {
        group.accounts.map { it.tenantEnv }.filter { it.isNotBlank() }.distinct().take(4)
            .map { env -> getEnvColors(env, semantic).second }
    }

    val rotationState by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = Motion.base, easing = Motion.EaseStandard),
        label = "arrow_tween"
    )

    val borderColor = if (expanded) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    } else {
        semantic.borderSubtle
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.L, vertical = Space.HS)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(Radius.L)
            ),
        shape = RoundedCornerShape(Radius.L),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (semantic.isDark) 0.dp else 0.5.dp
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { stateDescription = if (expanded) "已展开" else "已收起" }
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleExpand()
                    }
                    .padding(horizontal = Space.L, vertical = Space.ML),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(Radius.M))
                        .background(brandGradient)
                        .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(Radius.M)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = avatarLabel,
                        style = Type.AvatarCard,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(Space.M))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = group.issuer.ifBlank { "未知平台" },
                            style = Type.TitleCard,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (group.hasPinned) {
                            Spacer(modifier = Modifier.width(Space.XS))
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "含置顶账号",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${group.accounts.size} 个账户凭据",
                            style = Type.BodyS,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (!expanded && headerEnvColors.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(Space.S))
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.XS)) {
                                headerEnvColors.forEach { envColor ->
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(envColor)
                                    )
                                }
                            }
                        }
                    }
                }

                when {
                    group.isAllHotp -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radius.XS))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = Space.S, vertical = Space.XS)
                        ) {
                            Text(
                                text = "HOTP",
                                style = Type.Label,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    group.hasMixedPeriods -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radius.XS))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = Space.S, vertical = Space.XS)
                        ) {
                            Text(
                                text = "混合周期",
                                style = Type.Label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        OtpRingTicker(
                            period = group.headerPeriod,
                            nowMsProvider = nowMsProvider
                        )
                    }
                }

                Spacer(modifier = Modifier.width(Space.S))

                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier
                        .rotate(rotationState)
                        .size(IconSize.M),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = tween(Motion.slow, easing = Motion.EaseStandard)
                ) + fadeIn(
                    animationSpec = tween(Motion.base, easing = Motion.EaseStandard)
                ),
                exit = shrinkVertically(
                    animationSpec = tween(Motion.slow, easing = Motion.EaseStandard)
                ) + fadeOut(
                    animationSpec = tween(Motion.fast, easing = Motion.EaseStandard)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                        .padding(bottom = Space.XS)
                ) {
                    HorizontalDivider(color = semantic.borderSubtle)

                    group.accounts.forEachIndexed { index, token ->
                        key(token.id) {
                            AccountItemRow(
                                token = token,
                                allTags = allTags,
                                clipboardClearSeconds = clipboardClearSeconds,
                                privacyMasked = privacyMasked,
                                nowMsProvider = nowMsProvider,
                                onClick = { onAccountClick(token) },
                                onTogglePin = { onTogglePin(token.id) },
                                onRequestDelete = { onRequestDelete(token) },
                                onAdvanceHotp = { onAdvanceHotp(token) },
                                onCopied = { onCopied(token.id) }
                            )
                        }
                        if (index < group.accounts.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = Space.L),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                            )
                        }
                    }
                }
            }
        }
    }
}
