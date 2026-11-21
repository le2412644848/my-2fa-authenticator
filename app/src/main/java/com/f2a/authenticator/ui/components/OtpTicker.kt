package com.f2a.authenticator.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.f2a.authenticator.data.model.AuthToken
import com.f2a.authenticator.data.model.TokenType
import com.f2a.authenticator.totp.OtpEngine
import com.f2a.authenticator.ui.theme.LocalSemanticColors
import com.f2a.authenticator.ui.theme.Motion
import com.f2a.authenticator.ui.theme.Radius
import com.f2a.authenticator.ui.theme.Space
import com.f2a.authenticator.ui.theme.Type

/**
 * 纯函数计算当前/下一/上一周期验证码（供点击复制按需调用，不在组合期直接读取高频时钟）。
 */
fun computeTokenOtpCode(
    token: AuthToken,
    cycleOffset: Int = 0,
    nowMs: Long = System.currentTimeMillis()
): String {
    if (token.secretInvalid) return ""
    if (token.type == TokenType.HOTP) {
        val targetCounter = (token.counter + cycleOffset).coerceAtLeast(0L)
        return OtpEngine.generateHotp(token.secret, targetCounter, token.digits, token.algorithm)
    }
    val isSteam = token.type == TokenType.STEAM
    return when {
        cycleOffset < 0 -> OtpEngine.generatePreviousTotp(token.secret, token.period, token.digits, token.algorithm, isSteam, nowMs)
        cycleOffset > 0 -> OtpEngine.generateNextTotp(token.secret, token.period, token.digits, token.algorithm, isSteam, nowMs)
        else -> OtpEngine.generateTotp(token.secret, token.period, token.digits, token.algorithm, isSteam, nowMs)
    }
}

fun splitOtpParts(code: String): Pair<String, String> = when (code.length) {
    6 -> code.substring(0, 3) to code.substring(3, 6)
    8 -> code.substring(0, 4) to code.substring(4, 8)
    else -> code to ""
}

fun maskedOtpParts(token: AuthToken): Pair<String, String> = when {
    token.type == TokenType.STEAM -> "•••••" to ""
    token.digits == 8 -> "••••" to "••••"
    else -> "•••" to "•••"
}

/**
 * 高频时钟隔离倒计时圆环叶子组件。
 * CircularProgressIndicator 的 progress lambda 仅在 Draw 阶段读取 nowMsProvider()，0 重组；
 * 文本秒数通过 derivedStateOf 降频至 <= 1Hz。
 */
@Composable
fun OtpRingTicker(
    period: Int,
    nowMsProvider: () -> Long,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    strokeWidth: Dp = 2.75.dp
) {
    val semantic = LocalSemanticColors.current
    val remainingSeconds by remember(period, nowMsProvider) {
        derivedStateOf { OtpEngine.getRemainingSeconds(period, nowMsProvider()) }
    }
    val timerColor = when {
        remainingSeconds <= 5 -> semantic.timerDanger
        remainingSeconds <= 10 -> semantic.timerWarning
        else -> semantic.timerNormal
    }
    val dangerAlpha by animateFloatAsState(
        targetValue = if (remainingSeconds <= 5 && remainingSeconds % 2 == 1) 0.72f else 1f,
        animationSpec = tween(durationMillis = Motion.base),
        label = "ring_danger_alpha"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .graphicsLayer { alpha = dangerAlpha }
    ) {
        CircularProgressIndicator(
            progress = { OtpEngine.getRemainingProgress(period, nowMsProvider()) },
            modifier = Modifier.size(size),
            color = timerColor,
            strokeWidth = strokeWidth,
            strokeCap = StrokeCap.Round,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        )
        Text(
            text = "$remainingSeconds",
            style = Type.CodeMonoS,
            color = timerColor
        )
    }
}

/**
 * 贴边线性倒计时进度条（Draw 阶段读取 nowMsProvider()，0 重组）。
 */
@Composable
fun OtpLinearProgressBar(
    period: Int,
    nowMsProvider: () -> Long,
    modifier: Modifier = Modifier,
    barHeight: Dp = 2.5.dp
) {
    val semantic = LocalSemanticColors.current
    val remainingSeconds by remember(period, nowMsProvider) {
        derivedStateOf { OtpEngine.getRemainingSeconds(period, nowMsProvider()) }
    }
    val timerColor = when {
        remainingSeconds <= 5 -> semantic.timerDanger
        remainingSeconds <= 10 -> semantic.timerWarning
        else -> semantic.timerNormal
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
    ) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(
            color = trackColor,
            size = size,
            cornerRadius = radius
        )
        val progress = OtpEngine.getRemainingProgress(period, nowMsProvider()).coerceIn(0f, 1f)
        if (progress > 0f) {
            drawRoundRect(
                color = timerColor,
                size = Size(width = size.width * progress, height = size.height),
                cornerRadius = radius
            )
        }
    }
}

/**
 * 列表账号行验证码叶子组件。
 * 支持隐私遮罩、8 位码自适应字号与逐位 TalkBack 播报。
 */
@Composable
fun OtpRowCodeDisplay(
    token: AuthToken,
    showNextCodePreview: Boolean,
    modifier: Modifier = Modifier,
    isMasked: Boolean = false,
    nowMsProvider: () -> Long
) {
    val semantic = LocalSemanticColors.current
    val currentStep by remember(token.period, token.type, token.counter, nowMsProvider) {
        derivedStateOf {
            if (token.type == TokenType.HOTP) token.counter
            else OtpEngine.getCurrentStep(token.period, 0L, nowMsProvider())
        }
    }
    val remainingSeconds by remember(token.period, token.type, nowMsProvider) {
        derivedStateOf {
            if (token.type == TokenType.HOTP) 0
            else OtpEngine.getRemainingSeconds(token.period, nowMsProvider())
        }
    }
    val timerColor = if (token.type == TokenType.HOTP) {
        semantic.timerNormal
    } else {
        when {
            remainingSeconds <= 5 -> semantic.timerDanger
            remainingSeconds <= 10 -> semantic.timerWarning
            else -> semantic.timerNormal
        }
    }

    val displayCode = remember(
        token.secret,
        currentStep,
        token.counter,
        token.type,
        token.digits,
        token.algorithm,
        token.secretInvalid,
        showNextCodePreview
    ) {
        if (token.secretInvalid) ""
        else if (token.type == TokenType.HOTP) {
            val target = if (showNextCodePreview) token.counter + 1 else token.counter
            OtpEngine.generateHotp(token.secret, target, token.digits, token.algorithm)
        } else {
            val step = if (showNextCodePreview) currentStep + 1 else currentStep
            if (token.type == TokenType.STEAM) {
                OtpEngine.generateSteamCode(token.secret, step)
            } else {
                OtpEngine.generateOtp(token.secret, step, token.digits, token.algorithm)
            }
        }
    }

    val (part1, part2) = if (isMasked) maskedOtpParts(token) else splitOtpParts(displayCode)
    val spokenDigits = remember(displayCode, isMasked) {
        if (isMasked) "已隐藏，点击复制或显码" else displayCode.toCharArray().joinToString(" ")
    }
    val codeStyle = if (displayCode.length >= 8 || token.digits >= 8) Type.CodeRowCompact else Type.CodeRow

    Row(
        modifier = modifier.semantics {
            contentDescription = when {
                token.secretInvalid -> "密钥损坏，请重新导入"
                isMasked -> "动态验证码 $spokenDigits"
                token.type == TokenType.HOTP -> "动态验证码 $spokenDigits"
                else -> "动态验证码 $spokenDigits，剩余 $remainingSeconds 秒"
            }
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (token.secretInvalid) {
            Text(
                text = "⚠ 密钥损坏，请重新导入",
                style = Type.BodyM,
                fontWeight = FontWeight.Bold,
                color = semantic.errorText
            )
        } else {
            val codeColor = when {
                isMasked -> MaterialTheme.colorScheme.onSurfaceVariant
                showNextCodePreview -> MaterialTheme.colorScheme.secondary
                else -> timerColor
            }
            Text(
                text = part1,
                style = codeStyle,
                maxLines = 1,
                softWrap = false,
                color = codeColor
            )
            if (part2.isNotEmpty()) {
                Spacer(modifier = Modifier.width(if (token.digits >= 8) Space.HS else Space.S))
                Text(
                    text = part2,
                    style = codeStyle,
                    maxLines = 1,
                    softWrap = false,
                    color = codeColor
                )
            }
            if (showNextCodePreview) {
                Spacer(modifier = Modifier.width(Space.S))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.XS))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = Space.HS, vertical = Space.XXS)
                ) {
                    Text(
                        text = "下期",
                        style = Type.Caption,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

/**
 * 详情页大字 Hero 验证码与倒计时隔离组件（支持隐私遮罩与 8 位窄距自适应）。
 */
@Composable
fun OtpHeroCodeDisplay(
    token: AuthToken,
    cycleOffset: Int,
    modifier: Modifier = Modifier,
    isMasked: Boolean = false,
    nowMsProvider: () -> Long
) {
    val semantic = LocalSemanticColors.current
    val currentStep by remember(token.period, token.type, token.counter, nowMsProvider) {
        derivedStateOf {
            if (token.type == TokenType.HOTP) token.counter
            else OtpEngine.getCurrentStep(token.period, 0L, nowMsProvider())
        }
    }
    val remainingSeconds by remember(token.period, token.type, nowMsProvider) {
        derivedStateOf {
            if (token.type == TokenType.HOTP) 0
            else OtpEngine.getRemainingSeconds(token.period, nowMsProvider())
        }
    }
    val timerColor = if (token.type == TokenType.HOTP) {
        semantic.timerNormal
    } else {
        when {
            remainingSeconds <= 5 -> semantic.timerDanger
            remainingSeconds <= 10 -> semantic.timerWarning
            else -> semantic.timerNormal
        }
    }

    val currentOtp = remember(
        token.secret,
        currentStep,
        cycleOffset,
        token.counter,
        token.type,
        token.digits,
        token.algorithm,
        token.secretInvalid
    ) {
        if (token.secretInvalid) ""
        else if (token.type == TokenType.HOTP) {
            OtpEngine.generateHotp(token.secret, token.counter, token.digits, token.algorithm)
        } else {
            val step = (currentStep + cycleOffset).coerceAtLeast(0L)
            if (token.type == TokenType.STEAM) {
                OtpEngine.generateSteamCode(token.secret, step)
            } else {
                OtpEngine.generateOtp(token.secret, step, token.digits, token.algorithm)
            }
        }
    }

    val (part1, part2) = if (isMasked) maskedOtpParts(token) else splitOtpParts(currentOtp)
    val spokenDigits = remember(currentOtp, isMasked) {
        if (isMasked) "已遮罩隐藏" else currentOtp.toCharArray().joinToString(" ")
    }
    val heroStyle = if (currentOtp.length >= 8 || token.digits >= 8) Type.CodeHeroCompact else Type.CodeHero

    if (token.secretInvalid) {
        Text(
            text = "密钥损坏，无法生成验证码\n请重新导入该凭据",
            style = Type.TitleSection,
            color = semantic.errorText,
            textAlign = TextAlign.Center,
            modifier = modifier
        )
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.semantics {
                contentDescription = when {
                    isMasked -> "动态验证码 $spokenDigits"
                    token.type == TokenType.HOTP -> "动态验证码 $spokenDigits"
                    else -> "动态验证码 $spokenDigits，剩余 $remainingSeconds 秒"
                }
            }
        ) {
            val codeColor = when {
                isMasked -> MaterialTheme.colorScheme.onSurfaceVariant
                cycleOffset != 0 -> MaterialTheme.colorScheme.secondary
                else -> timerColor
            }
            Text(
                text = part1,
                style = heroStyle,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
                color = codeColor
            )
            if (part2.isNotEmpty()) {
                Spacer(modifier = Modifier.width(if (token.digits >= 8) Space.S else Space.M))
                Text(
                    text = part2,
                    style = heroStyle,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    color = codeColor
                )
            }
        }
    }
}
