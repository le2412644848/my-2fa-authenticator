package com.f2a.authenticator.ui.screens.detail

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.f2a.authenticator.data.model.AuthToken
import com.f2a.authenticator.data.model.Tag
import com.f2a.authenticator.data.model.TokenType
import com.f2a.authenticator.data.model.Workspace
import com.f2a.authenticator.ui.components.ClipboardCountdownBanner
import com.f2a.authenticator.ui.components.CopyFeedback
import com.f2a.authenticator.ui.components.OtpHeroCodeDisplay
import com.f2a.authenticator.ui.components.OtpLinearProgressBar
import com.f2a.authenticator.ui.components.OtpRingTicker
import com.f2a.authenticator.ui.components.accessibleTagTextColor
import com.f2a.authenticator.ui.components.computeTokenOtpCode
import com.f2a.authenticator.ui.components.getBrandGradient
import com.f2a.authenticator.ui.components.getEnvColors
import com.f2a.authenticator.ui.components.issuerAvatarLabel
import com.f2a.authenticator.ui.components.parseHexColor
import com.f2a.authenticator.ui.theme.IconSize
import com.f2a.authenticator.ui.theme.LocalSemanticColors
import com.f2a.authenticator.ui.theme.Motion
import com.f2a.authenticator.ui.theme.Radius
import com.f2a.authenticator.ui.theme.Space
import com.f2a.authenticator.ui.theme.Touch
import com.f2a.authenticator.ui.theme.Type
import com.f2a.authenticator.ui.theme.contentMaxWidth
import com.f2a.authenticator.ui.utils.QrBitmapRenderer
import com.f2a.authenticator.utils.ClipboardGuard
import com.f2a.authenticator.utils.SecureClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TokenDetailScreen(
    token: AuthToken,
    allTags: List<Tag>,
    allWorkspaces: List<Workspace>,
    clipboardClearSeconds: Int,
    privacyMaskedDefault: Boolean = false,
    nowMsProvider: () -> Long,
    onBuildExportUri: (AuthToken) -> String? = { null },
    onBack: () -> Unit,
    onUpdateToken: (AuthToken) -> Unit,
    onAdvanceHotp: (AuthToken) -> Unit,
    onRecordTokenUsed: (String) -> Unit,
    onDeleteToTrash: (String) -> Unit,
    onReimportClick: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val semantic = LocalSemanticColors.current
    val snackbarHostState = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()
    val clipboardRemainingSeconds by ClipboardGuard.remainingSeconds.collectAsState()

    var copiedFeedback by remember { mutableStateOf(false) }
    var cycleOffset by rememberSaveable(token.id) { mutableIntStateOf(0) }
    var isCodeMasked by remember(token.id, privacyMaskedDefault) { mutableStateOf(privacyMaskedDefault) }
    var showEditDialog by rememberSaveable(token.id) { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showSingleQrDialog by remember { mutableStateOf(false) }

    val exportUri = remember(token) { onBuildExportUri(token) }

    BackHandler(enabled = !showEditDialog && !showDeleteConfirmDialog && !showSingleQrDialog) {
        onBack()
    }

    LaunchedEffect(copiedFeedback) {
        if (copiedFeedback) {
            delay(Motion.hold)
            copiedFeedback = false
        }
    }

    val copyHintText = when {
        token.secretInvalid -> "密钥损坏，无法复制，请重新导入"
        !copiedFeedback -> "轻触验证码卡片或点击下方按钮复制"
        clipboardClearSeconds <= 0 -> "已复制（自动清除已关闭）"
        else -> "已复制 · $clipboardClearSeconds 秒后自动清除"
    }

    val doCopy = {
        if (!token.secretInvalid) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            val code = computeTokenOtpCode(token, cycleOffset, nowMsProvider())
            SecureClipboard.copy(context, code, clipboardClearSeconds)
            onRecordTokenUsed(token.id)
            copiedFeedback = true
            CopyFeedback.show(snackbarHostState, uiScope, clipboardClearSeconds)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("账户凭据详情", style = Type.TitleSection) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(Touch.MinTarget)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", modifier = Modifier.size(IconSize.L))
                    }
                },
                actions = {
                    if (!token.secretInvalid && exportUri != null) {
                        IconButton(
                            onClick = { showSingleQrDialog = true },
                            modifier = Modifier.size(Touch.MinTarget)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = "导出标准二维码",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(IconSize.L)
                            )
                        }
                    }
                    IconButton(onClick = { showEditDialog = true }, modifier = Modifier.size(Touch.MinTarget)) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑凭据", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(IconSize.L))
                    }
                    IconButton(onClick = { showDeleteConfirmDialog = true }, modifier = Modifier.size(Touch.MinTarget)) {
                        Icon(Icons.Default.Delete, contentDescription = "删除凭据", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(IconSize.L))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .contentMaxWidth()
                .padding(horizontal = Space.XL, vertical = Space.L),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.XL)
        ) {
            ClipboardCountdownBanner(
                remainingSeconds = clipboardRemainingSeconds,
                onClearNow = {
                    ClipboardGuard.clearNow(context)
                    uiScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar("剪贴板已立即清空")
                    }
                }
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = Space.XS)
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(Radius.XL))
                        .background(getBrandGradient(token.issuer))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(Radius.XL)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = issuerAvatarLabel(token.issuer, token.accountName),
                        style = Type.AvatarHero,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(Space.L))

                Text(
                    text = token.issuer,
                    style = Type.TitlePage,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(Space.XS))
                Text(
                    text = token.accountName,
                    style = Type.BodyL,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(Space.S))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.HS),
                    verticalArrangement = Arrangement.spacedBy(Space.XS)
                ) {
                    if (token.tenantEnv.isNotBlank()) {
                        val (envBg, envText) = remember(token.tenantEnv, semantic) {
                            getEnvColors(token.tenantEnv, semantic)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radius.XS))
                                .background(envBg)
                                .padding(horizontal = Space.S, vertical = Space.XXS + 1.dp)
                        ) {
                            Text(token.tenantEnv, style = Type.Label, color = envText)
                        }
                    }
                    allTags.filter { token.tagIds.contains(it.id) }.forEach { tag ->
                        val tagColor = remember(tag.colorHex) { parseHexColor(tag.colorHex) }
                        val tagTextColor = remember(tagColor, semantic.isDark) {
                            accessibleTagTextColor(tagColor, semantic.isDark)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radius.XS))
                                .background(tagColor.copy(alpha = 0.16f))
                                .padding(horizontal = Space.S, vertical = Space.XXS + 1.dp)
                        ) {
                            Text(tag.name, style = Type.Label, color = tagTextColor)
                        }
                    }
                }

                if (token.note.isNotBlank()) {
                    Spacer(modifier = Modifier.height(Space.HS))
                    Text(
                        text = "备忘: ${token.note}",
                        style = Type.BodyS,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, semantic.borderSubtle, RoundedCornerShape(Radius.XL))
                        .clickable { doCopy() },
                    shape = RoundedCornerShape(Radius.XL),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (semantic.isDark) 0.dp else 0.5.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Space.M, bottom = Space.XL, start = Space.XL, end = Space.XL),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = when {
                                    token.type == TokenType.HOTP -> "计数器动态密码 (HOTP)"
                                    cycleOffset == -1 -> "上一周期验证码 (容错回溯)"
                                    cycleOffset == 1 -> "下一周期验证码 (提前备战)"
                                    else -> "当前动态密码"
                                },
                                style = Type.BodyS,
                                fontWeight = FontWeight.SemiBold,
                                color = if (cycleOffset != 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Center)
                            )

                            if (!token.secretInvalid) {
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        isCodeMasked = !isCodeMasked
                                    },
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .size(Touch.MinTarget)
                                ) {
                                    Icon(
                                        imageVector = if (isCodeMasked) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isCodeMasked) "显示验证码" else "防窥隐藏验证码",
                                        tint = if (isCodeMasked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(IconSize.M)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(Space.M))

                        OtpHeroCodeDisplay(
                            token = token,
                            cycleOffset = cycleOffset,
                            isMasked = isCodeMasked,
                            nowMsProvider = nowMsProvider
                        )

                        Spacer(modifier = Modifier.height(Space.XL))

                        if (token.type == TokenType.HOTP) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(Radius.XS))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(horizontal = Space.S, vertical = Space.XS)
                            ) {
                                Text(
                                    text = "计数器凭据（按次递增 · 当前 #${token.counter}）",
                                    style = Type.Caption,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        } else if (cycleOffset == 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.M)
                            ) {
                                OtpLinearProgressBar(
                                    period = token.period,
                                    nowMsProvider = nowMsProvider,
                                    barHeight = 3.5.dp,
                                    modifier = Modifier.weight(1f)
                                )
                                OtpRingTicker(
                                    period = token.period,
                                    nowMsProvider = nowMsProvider,
                                    size = 34.dp
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(Radius.XS))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(horizontal = Space.S, vertical = Space.XS)
                            ) {
                                Text(
                                    text = if (cycleOffset == -1) "容错回溯窗口" else "下一周期预览",
                                    style = Type.Caption,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Space.M))

                        // 安全参数元数据胶囊
                        val metaSummary = buildString {
                            append(token.type.name)
                            append(" · ")
                            append(token.algorithm)
                            append(" · ")
                            append("${token.digits}位")
                            if (token.type != TokenType.HOTP) {
                                append(" · ${token.period}s")
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radius.Full))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                .padding(horizontal = Space.M, vertical = Space.XS)
                        ) {
                            Text(
                                text = metaSummary,
                                style = Type.Caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Space.L))

                if (token.secretInvalid) {
                    OutlinedButton(
                        onClick = onReimportClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Touch.MinTarget),
                        shape = RoundedCornerShape(Radius.M),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = semantic.errorText)
                    ) {
                        Text("重新导入该凭据", style = Type.BodyL, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Button(
                        onClick = { doCopy() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Touch.MinTarget),
                        shape = RoundedCornerShape(Radius.M),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (copiedFeedback) semantic.success else MaterialTheme.colorScheme.primary,
                            contentColor = if (copiedFeedback) semantic.onSuccess else MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        AnimatedContent(
                            targetState = copiedFeedback,
                            transitionSpec = {
                                fadeIn(tween(Motion.fast, easing = Motion.EaseStandard)) togetherWith
                                    fadeOut(tween(Motion.fast, easing = Motion.EaseStandard))
                            },
                            label = "detail_copy_btn"
                        ) { isCopied ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(IconSize.M)
                                )
                                Spacer(modifier = Modifier.width(Space.S))
                                Text(
                                    text = if (isCopied) "已复制" else "复制验证码",
                                    style = Type.BodyL,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Space.M))
                Text(
                    text = copyHintText,
                    style = Type.BodyS,
                    fontWeight = if (copiedFeedback) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (copiedFeedback) semantic.success else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Space.XL),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (token.type == TokenType.HOTP) {
                    Button(
                        onClick = {
                            if (!token.secretInvalid) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onAdvanceHotp(token)
                            }
                        },
                        enabled = !token.secretInvalid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Touch.MinTarget),
                        shape = RoundedCornerShape(Radius.M)
                    ) {
                        Text("使用一次 / 下一个（当前计数 ${token.counter}）", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = cycleOffset == -1,
                            onClick = { cycleOffset = if (cycleOffset == -1) 0 else -1 },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            modifier = Modifier.heightIn(min = Touch.SegmentedHeight)
                        ) { Text("上一周期", style = Type.BodyS) }

                        SegmentedButton(
                            selected = cycleOffset == 0,
                            onClick = { cycleOffset = 0 },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                            modifier = Modifier.heightIn(min = Touch.SegmentedHeight)
                        ) { Text("当前", style = Type.BodyS) }

                        SegmentedButton(
                            selected = cycleOffset == 1,
                            onClick = { cycleOffset = if (cycleOffset == 1) 0 else 1 },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                            modifier = Modifier.heightIn(min = Touch.SegmentedHeight)
                        ) { Text("下一周期", style = Type.BodyS) }
                    }
                }
            }
        }
    }

    if (showSingleQrDialog && exportUri != null) {
        SingleTokenQrExportDialog(
            issuer = token.issuer,
            accountName = token.accountName,
            exportUri = exportUri,
            onDismiss = { showSingleQrDialog = false }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            shape = RoundedCornerShape(Radius.XL),
            title = { Text("确认移入回收站？", style = Type.TitleSection) },
            text = {
                Text(
                    "将把「${token.issuer} · ${token.accountName}」移入回收站，7 天内可在「设置 → 回收站」中恢复，超期后自动永久销毁。",
                    style = Type.BodyM
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteToTrash(token.id)
                    },
                    shape = RoundedCornerShape(Radius.M),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.heightIn(min = Touch.MinTarget)
                ) { Text("确认删除") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false },
                    modifier = Modifier.heightIn(min = Touch.MinTarget)
                ) { Text("取消") }
            }
        )
    }

    if (showEditDialog) {
        EditTokenDialog(
            token = token,
            allTags = allTags,
            allWorkspaces = allWorkspaces,
            onDismiss = { showEditDialog = false },
            onSave = { updated ->
                onUpdateToken(updated)
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun SingleTokenQrExportDialog(
    issuer: String,
    accountName: String,
    exportUri: String,
    onDismiss: () -> Unit
) {
    val semantic = LocalSemanticColors.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    var qrBitmap by remember(exportUri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(exportUri) {
        qrBitmap = withContext(Dispatchers.Default) {
            QrBitmapRenderer.renderQrBitmap(exportUri, 720)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn)
    ) {
        Card(
            shape = RoundedCornerShape(Radius.XL),
            colors = CardDefaults.cardColors(containerColor = semantic.surfaceElevated),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Space.M)
                .border(1.dp, semantic.borderSubtle, RoundedCornerShape(Radius.XL))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = screenHeight * 0.88f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(Space.XL),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "单条标准二维码导出",
                        style = Type.TitleSection,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(Touch.MinTarget)) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(IconSize.M))
                    }
                }
                Spacer(modifier = Modifier.height(Space.XS))
                Text(
                    text = "$issuer · $accountName",
                    style = Type.BodyS,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(Space.L))
                Box(
                    modifier = Modifier
                        .size(236.dp)
                        .clip(RoundedCornerShape(Radius.M))
                        .background(Color.White)
                        .border(1.dp, semantic.borderSubtle, RoundedCornerShape(Radius.M))
                        .padding(Space.L),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = qrBitmap
                    if (bmp == null) {
                        CircularProgressIndicator(modifier = Modifier.size(IconSize.XL))
                    } else {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "otpauth 标准二维码",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Space.M))
                Text(
                    text = "此二维码包含明文 2FA 种子密钥，请勿截屏或展示给无关人员。",
                    style = Type.Caption,
                    color = semantic.errorText,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(Space.L))
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(Radius.M),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Touch.MinTarget)
                ) {
                    Text("完成")
                }
            }
        }
    }
}
