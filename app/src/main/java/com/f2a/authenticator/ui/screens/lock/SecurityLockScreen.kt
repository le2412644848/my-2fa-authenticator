package com.f2a.authenticator.ui.screens.lock

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.f2a.authenticator.security.PinConfigResult
import com.f2a.authenticator.security.SecurityLockManager
import com.f2a.authenticator.ui.state.VaultAuthState
import com.f2a.authenticator.ui.theme.IconSize
import com.f2a.authenticator.ui.theme.LocalSemanticColors
import com.f2a.authenticator.ui.theme.Motion
import com.f2a.authenticator.ui.theme.Radius
import com.f2a.authenticator.ui.theme.Space
import com.f2a.authenticator.ui.theme.Touch
import com.f2a.authenticator.ui.theme.Type
import kotlinx.coroutines.delay

@Composable
fun SecurityLockScreen(
    authState: VaultAuthState,
    onSetupPin: (pin: String, onResult: (PinConfigResult) -> Unit) -> Unit,
    onVerifyPin: (pin: String, onResult: (SecurityLockManager.VerifyResult) -> Unit) -> Unit,
    onBiometricUnlock: (FragmentActivity, onError: (String) -> Unit) -> Unit,
    onRefreshLockout: () -> Long
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val haptic = LocalHapticFeedback.current
    val semantic = LocalSemanticColors.current

    BackHandler {
        (context as? Activity)?.moveTaskToBack(true)
    }

    // 【安全红线】PIN 输入状态严禁使用 rememberSaveable，仅存内存态
    var inputPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isSettingConfirmStep by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var lockoutSeconds by remember(authState.lockoutSeconds) {
        mutableLongStateOf(authState.lockoutSeconds)
    }
    var shakeTrigger by remember { mutableIntStateOf(0) }
    val shakeOffset = remember { Animatable(0f) }
    var autoPromptedBiometric by remember { mutableStateOf(false) }

    val isConfigured = authState.isPinConfigured
    val remainingAttempts = authState.remainingAttempts
    val isLockedOut = lockoutSeconds > 0L
    val hasError = errorMessage.isNotEmpty()
    val canUseBiometric = isConfigured && authState.isBiometricEnabled && authState.isBiometricAvailable && !isLockedOut

    LaunchedEffect(canUseBiometric) {
        if (canUseBiometric && !autoPromptedBiometric && activity != null) {
            autoPromptedBiometric = true
            onBiometricUnlock(activity) { err ->
                if (err.isNotBlank()) errorMessage = err
            }
        }
    }

    LaunchedEffect(lockoutSeconds) {
        if (lockoutSeconds > 0L) {
            while (lockoutSeconds > 0L) {
                delay(1000L)
                lockoutSeconds = onRefreshLockout()
            }
            errorMessage = ""
        }
    }

    LaunchedEffect(shakeTrigger) {
        if (shakeTrigger > 0) {
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = Motion.errorShake
                    0f at 0
                    (-8f) at 40
                    8f at 120
                    (-8f) at 200
                    8f at 280
                    0f at 320
                }
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        val header: @Composable () -> Unit = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = Space.XL)
            ) {
                val warnAttempts = isConfigured && remainingAttempts <= 3
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            if (warnAttempts || isLockedOut) {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = if (warnAttempts || isLockedOut) {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, semantic.borderSubtle, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (warnAttempts || isLockedOut) Icons.Default.Warning else Icons.Default.Shield,
                            contentDescription = "保险库已锁定",
                            tint = if (warnAttempts || isLockedOut) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(IconSize.LockShield)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Space.XL))

                Text(
                    text = if (!isConfigured) {
                        if (isSettingConfirmStep) "再次确认安全密码 (2/2)" else "设置独立保险库主密码 (1/2)"
                    } else {
                        "极盾保险库"
                    },
                    style = Type.TitlePage,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(Space.S))

                Text(
                    text = when {
                        !isConfigured -> "与手机锁屏彻底独立 · 离线 PBKDF2 硬件绑定"
                        isLockedOut -> "多次尝试错误，防线冷却中：$lockoutSeconds 秒"
                        remainingAttempts <= 3 -> "警告：仅剩 $remainingAttempts 次尝试机会，继续错误将触发物理自毁"
                        else -> "请输入 6 位专属密码解锁您的 2FA 凭据"
                    },
                    style = Type.BodyM,
                    color = if (warnAttempts || isLockedOut) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    fontWeight = if (warnAttempts || isLockedOut) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = Space.L)
                )

                if (authState.missingPinAnomaly) {
                    Spacer(modifier = Modifier.height(Space.M))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(Radius.M),
                        modifier = Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                            RoundedCornerShape(Radius.M)
                        )
                    ) {
                        Text(
                            text = "检测到本地已有加密凭据但主密码配置缺失，请立即设置新主密码以保护数据安全。",
                            style = Type.BodyS,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(Space.M)
                        )
                    }
                }

                if (hasError) {
                    Spacer(modifier = Modifier.height(Space.MS))
                    Text(
                        text = errorMessage,
                        color = semantic.errorText,
                        style = Type.BodyS,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(Space.XXL))

                val currentLength = if (!isConfigured && isSettingConfirmStep) confirmPin.length else inputPin.length
                val activeColor = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                Row(
                    modifier = Modifier
                        .offset { IntOffset(shakeOffset.value.dp.roundToPx(), 0) }
                        .semantics(mergeDescendants = true) {
                            contentDescription = "已输入 $currentLength 位，共 6 位"
                        },
                    horizontalArrangement = Arrangement.spacedBy(Space.XL - Space.XXS)
                ) {
                    repeat(6) { index ->
                        val filled = index < currentLength
                        val dotScale by animateFloatAsState(
                            targetValue = if (filled) 1.15f else 1f,
                            animationSpec = tween(durationMillis = Motion.fast, easing = Motion.EaseStandard),
                            label = "pin_dot_scale"
                        )
                        val dotColor by animateColorAsState(
                            targetValue = if (filled) activeColor else Color.Transparent,
                            animationSpec = tween(durationMillis = Motion.fast),
                            label = "pin_dot_color"
                        )
                        Box(
                            modifier = Modifier
                                .size(Space.ML)
                                .graphicsLayer {
                                    scaleX = dotScale
                                    scaleY = dotScale
                                }
                                .clip(CircleShape)
                                .background(dotColor)
                                .border(
                                    width = 1.5.dp,
                                    color = if (filled) activeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    shape = CircleShape
                                )
                        )
                    }
                }

                if (!isConfigured && isSettingConfirmStep) {
                    Spacer(modifier = Modifier.height(Space.M))
                    TextButton(
                        onClick = {
                            inputPin = ""
                            confirmPin = ""
                            isSettingConfirmStep = false
                            errorMessage = ""
                        },
                        modifier = Modifier.heightIn(min = Touch.MinTarget)
                    ) {
                        Text("返回重新设置第一步", style = Type.BodyS)
                    }
                }
            }
        }

        val keypad: @Composable () -> Unit = {
            Column(
                modifier = Modifier
                    .widthIn(max = KeypadMaxWidth)
                    .fillMaxWidth()
                    .padding(top = Space.L, bottom = Space.L)
                    .alpha(if (isLockedOut || isVerifying) 0.4f else 1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.M)
            ) {
                val numRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("", "0", "DEL")
                )

                val onDigit: (String) -> Unit = { key ->
                    if (!isLockedOut && !isVerifying) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        errorMessage = ""
                        if (!isConfigured) {
                            if (!isSettingConfirmStep) {
                                if (inputPin.length < 6) {
                                    inputPin += key
                                    if (inputPin.length == 6) {
                                        isSettingConfirmStep = true
                                    }
                                }
                            } else {
                                if (confirmPin.length < 6) {
                                    confirmPin += key
                                    if (confirmPin.length == 6) {
                                        if (inputPin == confirmPin) {
                                            val candidate = inputPin
                                            isVerifying = true
                                            onSetupPin(candidate) { res ->
                                                isVerifying = false
                                                when (res) {
                                                    is PinConfigResult.Ok -> {
                                                        inputPin = ""
                                                        confirmPin = ""
                                                    }
                                                    is PinConfigResult.WeakPin -> {
                                                        errorMessage = "密码过于简单，请勿使用全同、连续或重复模式数字"
                                                        inputPin = ""
                                                        confirmPin = ""
                                                        isSettingConfirmStep = false
                                                        shakeTrigger++
                                                    }
                                                    else -> {
                                                        errorMessage = "请输入合法的 6 位纯数字密码"
                                                        inputPin = ""
                                                        confirmPin = ""
                                                        isSettingConfirmStep = false
                                                        shakeTrigger++
                                                    }
                                                }
                                            }
                                        } else {
                                            errorMessage = "两次输入的密码不一致，请重新输入"
                                            inputPin = ""
                                            confirmPin = ""
                                            isSettingConfirmStep = false
                                            shakeTrigger++
                                        }
                                    }
                                }
                            }
                        } else {
                            if (inputPin.length < 6) {
                                inputPin += key
                                if (inputPin.length == 6) {
                                    val candidate = inputPin
                                    isVerifying = true
                                    onVerifyPin(candidate) { res ->
                                        isVerifying = false
                                        when (res) {
                                            is SecurityLockManager.VerifyResult.Success,
                                            is SecurityLockManager.VerifyResult.DuressTriggered -> {
                                                inputPin = ""
                                            }
                                            is SecurityLockManager.VerifyResult.WrongPin -> {
                                                errorMessage = "密码错误，还可尝试 ${res.remaining} 次"
                                                inputPin = ""
                                                shakeTrigger++
                                            }
                                            is SecurityLockManager.VerifyResult.LockedOut -> {
                                                errorMessage = "连续多次错误，已启动防御冻结"
                                                lockoutSeconds = res.waitSeconds
                                                inputPin = ""
                                                shakeTrigger++
                                            }
                                            is SecurityLockManager.VerifyResult.SelfDestructed -> {
                                                errorMessage = "安全阈值耗尽，已执行物理自毁"
                                                inputPin = ""
                                            }
                                            is SecurityLockManager.VerifyResult.NotConfigured -> {
                                                inputPin = ""
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val onDelete: () -> Unit = {
                    if (!isLockedOut && !isVerifying) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (!isConfigured && isSettingConfirmStep) {
                            if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                        } else {
                            if (inputPin.isNotEmpty()) inputPin = inputPin.dropLast(1)
                        }
                        errorMessage = ""
                    }
                }

                numRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { key ->
                            when {
                                key.isEmpty() -> Spacer(modifier = Modifier.size(IconSize.LockKey))
                                key == "DEL" -> LockKey(
                                    enabled = !isLockedOut && !isVerifying,
                                    onClick = onDelete
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                                        contentDescription = "删除一位",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                else -> LockKey(
                                    enabled = !isLockedOut && !isVerifying,
                                    onClick = { onDigit(key) }
                                ) {
                                    Text(
                                        text = key,
                                        style = Type.KeypadDigit,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                if (canUseBiometric && activity != null) {
                    Spacer(modifier = Modifier.height(Space.XS))
                    OutlinedButton(
                        onClick = {
                            onBiometricUnlock(activity) { err ->
                                if (err.isNotBlank()) errorMessage = err
                            }
                        },
                        shape = RoundedCornerShape(Radius.M),
                        modifier = Modifier.heightIn(min = Touch.MinTarget)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "生物识别解锁",
                            modifier = Modifier.size(IconSize.M)
                        )
                        Spacer(modifier = Modifier.width(Space.S))
                        Text("指纹 / 面容解锁", style = Type.BodyM, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            if (maxWidth > maxHeight && maxHeight < CompactHeightBreakpoint) {
                // 横屏：竖排会把键盘挤出屏幕，改为提示区与键盘左右分栏
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Space.XXL),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) { header() }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) { keypad() }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Space.XXL, vertical = Space.XL),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    header()
                    keypad()
                }
            }
        }
    }
}

private val KeypadMaxWidth = 360.dp
private val CompactHeightBreakpoint = 480.dp

@Composable
private fun LockKey(
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val semantic = LocalSemanticColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(durationMillis = Motion.fast),
        label = "key_scale"
    )
    val bgColor by animateColorAsState(
        targetValue = if (pressed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = Motion.fast),
        label = "key_bg"
    )

    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = Modifier
            .size(IconSize.LockKey)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(1.dp, semantic.borderSubtle, CircleShape),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = bgColor
        )
    ) {
        content()
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
