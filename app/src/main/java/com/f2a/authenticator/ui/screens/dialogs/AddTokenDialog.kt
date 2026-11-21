package com.f2a.authenticator.ui.screens.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.f2a.authenticator.data.model.AuthToken
import com.f2a.authenticator.data.model.Tag
import com.f2a.authenticator.data.model.TokenType
import com.f2a.authenticator.data.model.Workspace
import com.f2a.authenticator.totp.ExternalImportParser
import com.f2a.authenticator.totp.ExternalImportResult
import com.f2a.authenticator.totp.OtpUriParser
import com.f2a.authenticator.totp.WorkspaceInferenceEngine
import com.f2a.authenticator.ui.components.parseHexColor
import com.f2a.authenticator.ui.state.AddTokenDraft
import com.f2a.authenticator.ui.theme.IconSize
import com.f2a.authenticator.ui.theme.LocalSemanticColors
import com.f2a.authenticator.ui.theme.Radius
import com.f2a.authenticator.ui.theme.Space
import com.f2a.authenticator.ui.theme.Touch
import com.f2a.authenticator.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddTokenDialog(
    workspaces: List<Workspace>,
    tags: List<Tag>,
    activeWorkspaceId: String,
    draft: AddTokenDraft,
    onDismiss: () -> Unit,
    onConfirm: (AuthToken) -> Unit,
    onBatchConfirm: (List<AuthToken>) -> Unit,
    onOpenScan: () -> Unit
) {
    val semantic = LocalSemanticColors.current
    val scope = rememberCoroutineScope()
    val initialWsId = remember(workspaces, activeWorkspaceId) {
        if (activeWorkspaceId != "all" && workspaces.any { it.id == activeWorkspaceId }) {
            activeWorkspaceId
        } else {
            workspaces.firstOrNull()?.id ?: Workspace.DEFAULT_PERSONAL.id
        }
    }

    // 【安全红线】输入草稿只存 ViewModel 内存（旋转不丢），禁止 rememberSaveable，防止明文种子落入 Bundle
    var rawUriOrKey by draft::rawUriOrKey
    var issuer by draft::issuer
    var accountName by draft::accountName
    var tenantEnv by draft::tenantEnv
    var secret by draft::secret
    var note by draft::note
    val selectedWorkspaceId = draft.selectedWorkspaceId ?: initialWsId
    var selectedTagIds by draft::selectedTagIds
    var isPinned by draft::isPinned

    var selectedAlgorithm by draft::selectedAlgorithm
    var selectedDigits by draft::selectedDigits
    var selectedPeriod by draft::selectedPeriod
    var selectedCounter by draft::selectedCounter
    var selectedType by draft::selectedType

    var errorMessage by remember { mutableStateOf("") }
    var batchPreview by remember { mutableStateOf<ExternalImportResult?>(null) }
    var step by draft::step
    var showAdvanced by draft::showAdvanced

    // 异步识别多行 otpauth://、Google 迁移链接与 Aegis / 2FAS / Bitwarden JSON，避免阻塞主线程
    val batchDefaultWorkspaceId = if (activeWorkspaceId == "all") "all" else selectedWorkspaceId
    LaunchedEffect(rawUriOrKey, batchDefaultWorkspaceId, workspaces) {
        val clean = rawUriOrKey.trim()
        if (clean.isEmpty()) {
            batchPreview = null
            return@LaunchedEffect
        }
        val isMultiOrExternal = clean.startsWith("otpauth-migration://", ignoreCase = true) ||
            clean.startsWith("{") ||
            clean.startsWith("[") ||
            clean.contains('\n') ||
            clean.contains('\r')
        if (!isMultiOrExternal) {
            batchPreview = null
            return@LaunchedEffect
        }
        val parsed = withContext(Dispatchers.Default) {
            ExternalImportParser.parse(
                rawInput = clean,
                defaultWorkspaceId = batchDefaultWorkspaceId,
                existingWorkspaces = workspaces
            )
        }
        if (parsed.tokens.isNotEmpty()) {
            batchPreview = parsed
            errorMessage = ""
        } else {
            batchPreview = null
            errorMessage = parsed.warnings.firstOrNull() ?: "无法识别批量迁移内容"
        }
    }

    val doParse: () -> Unit = {
        val clean = rawUriOrKey.trim()
        val isMultiOrExternal = clean.startsWith("otpauth-migration://", ignoreCase = true) ||
            clean.startsWith("{") ||
            clean.startsWith("[") ||
            clean.contains('\n') ||
            clean.contains('\r')

        if (isMultiOrExternal) {
            scope.launch {
                val parsed = withContext(Dispatchers.Default) {
                    ExternalImportParser.parse(
                        rawInput = clean,
                        defaultWorkspaceId = batchDefaultWorkspaceId,
                        existingWorkspaces = workspaces
                    )
                }
                if (parsed.tokens.isNotEmpty()) {
                    batchPreview = parsed
                    errorMessage = ""
                } else {
                    batchPreview = null
                    errorMessage = parsed.warnings.firstOrNull() ?: "无法解析批量内容"
                }
            }
        } else {
            when (val parsed = OtpUriParser.parseDetailed(clean)) {
                is OtpUriParser.OtpParseResult.Ok -> {
                    val token = parsed.token
                    issuer = token.issuer
                    accountName = token.accountName
                    secret = token.secret
                    selectedAlgorithm = token.algorithm
                    selectedDigits = token.digits
                    selectedPeriod = token.period
                    selectedCounter = token.counter
                    selectedType = token.type
                    if (token.tenantEnv.isNotBlank()) tenantEnv = token.tenantEnv
                    val suggestion = WorkspaceInferenceEngine.infer(
                        issuer = token.issuer,
                        accountName = token.accountName,
                        tenantEnv = token.tenantEnv,
                        currentActiveWorkspaceId = activeWorkspaceId,
                        allWorkspaces = workspaces
                    )
                    draft.selectedWorkspaceId = suggestion.workspaceId
                    val validTagIds = tags.map { it.id }.toHashSet()
                    val filteredSuggestedTags = suggestion.suggestedTagIds.filter {
                        validTagIds.isEmpty() || it in validTagIds
                    }
                    if (filteredSuggestedTags.isNotEmpty()) {
                        selectedTagIds = (selectedTagIds + filteredSuggestedTags).distinct()
                    }
                    batchPreview = null
                    errorMessage = ""
                    step = 2
                }
                is OtpUriParser.OtpParseResult.Error -> {
                    errorMessage = parsed.reason
                }
            }
        }
    }

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

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
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Space.XL, end = Space.S, top = Space.S),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "添加 2FA 凭据", style = Type.TitleSection)
                        Text(
                            text = if (step == 1) "步骤 1 / 2 · 导入或批量识别" else "步骤 2 / 2 · 确认信息",
                            style = Type.BodyS,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(Touch.MinTarget)) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(IconSize.M))
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(horizontal = Space.XL)
                ) {
                    if (step == 1) {
                        Spacer(modifier = Modifier.height(Space.M))
                        OutlinedTextField(
                            value = rawUriOrKey,
                            onValueChange = { rawUriOrKey = it; errorMessage = "" },
                            label = { Text("粘贴链接、密钥或批量导出文本") },
                            placeholder = { Text("支持单条/多行 otpauth://、Google 迁移码、Aegis / 2FAS / Bitwarden JSON") },
                            trailingIcon = { Icon(Icons.Default.QrCode, contentDescription = "解析内容") },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Radius.M)
                        )
                        Spacer(modifier = Modifier.height(Space.M))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Space.M)
                        ) {
                            OutlinedButton(
                                onClick = onOpenScan,
                                modifier = Modifier.weight(1f).heightIn(min = Touch.MinTarget),
                                shape = RoundedCornerShape(Radius.M)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(IconSize.S))
                                Spacer(modifier = Modifier.width(Space.XS))
                                Text("扫码录入", style = Type.BodyM)
                            }
                            Button(
                                onClick = { doParse() },
                                modifier = Modifier.weight(1f).heightIn(min = Touch.MinTarget),
                                shape = RoundedCornerShape(Radius.M)
                            ) {
                                Text("粘贴解析", style = Type.BodyM, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        val preview = batchPreview
                        if (preview != null && preview.tokens.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(Space.M))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                shape = RoundedCornerShape(Radius.M),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                        RoundedCornerShape(Radius.M)
                                    )
                            ) {
                                Column(modifier = Modifier.padding(Space.ML)) {
                                    Text(
                                        text = "已识别「${preview.formatName}」· ${preview.tokens.size} 个凭据",
                                        style = Type.BodyM,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    if (preview.skippedCount > 0) {
                                        Spacer(modifier = Modifier.height(Space.XS))
                                        Text(
                                            text = "已跳过 ${preview.skippedCount} 条不支持或无效条目",
                                            style = Type.Caption,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(Space.S))
                                    Button(
                                        onClick = { onBatchConfirm(preview.tokens) },
                                        shape = RoundedCornerShape(Radius.S),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = Touch.MinTarget)
                                    ) {
                                        Text("一键导入全部 (${preview.tokens.size})")
                                    }
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(Space.M))
                        OutlinedTextField(
                            value = issuer,
                            onValueChange = { issuer = it; errorMessage = "" },
                            label = { Text("平台/服务商") },
                            placeholder = { Text("如 AWS、Google、Steam") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Radius.M)
                        )
                        Spacer(modifier = Modifier.height(Space.S))
                        OutlinedTextField(
                            value = accountName,
                            onValueChange = { accountName = it },
                            label = { Text("账号/邮箱") },
                            placeholder = { Text("如 user@company.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Radius.M)
                        )
                        Spacer(modifier = Modifier.height(Space.S))
                        OutlinedTextField(
                            value = secret,
                            onValueChange = { secret = it; errorMessage = "" },
                            label = { Text("Secret 密钥") },
                            placeholder = { Text("Base32，至少 8 位，如 JBSWY3DPEHPK3PXP") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Radius.M)
                        )
                        Spacer(modifier = Modifier.height(Space.S))
                        OutlinedTextField(
                            value = tenantEnv,
                            onValueChange = { tenantEnv = it },
                            label = { Text("环境标注 (选填)") },
                            placeholder = { Text("如 Prod、Dev") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Radius.M)
                        )
                        Spacer(modifier = Modifier.height(Space.M))

                        Text("归属工作区", style = Type.BodyS, fontWeight = FontWeight.SemiBold)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(vertical = Space.XS),
                            horizontalArrangement = Arrangement.spacedBy(Space.S),
                            verticalArrangement = Arrangement.spacedBy(Space.XS)
                        ) {
                            workspaces.forEach { ws ->
                                val wsColor = remember(ws.colorHex) { parseHexColor(ws.colorHex) }
                                FilterChip(
                                    selected = selectedWorkspaceId == ws.id,
                                    onClick = { draft.selectedWorkspaceId = ws.id },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(wsColor)
                                        )
                                    },
                                    label = { Text(ws.name) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Space.S))
                        Text("绑定标签 (可多选)", style = Type.BodyS, fontWeight = FontWeight.SemiBold)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(vertical = Space.XS),
                            horizontalArrangement = Arrangement.spacedBy(Space.S),
                            verticalArrangement = Arrangement.spacedBy(Space.XS)
                        ) {
                            tags.forEach { tag ->
                                val isSelected = selectedTagIds.contains(tag.id)
                                val tagColor = remember(tag.colorHex) { parseHexColor(tag.colorHex) }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedTagIds = if (isSelected) selectedTagIds - tag.id else selectedTagIds + tag.id
                                    },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(tagColor)
                                        )
                                    },
                                    label = { Text(tag.name) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Space.S))
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("备忘与应急提示 (选填)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Radius.M)
                        )
                        Spacer(modifier = Modifier.height(Space.XS))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isPinned, onCheckedChange = { isPinned = it })
                            Text("置顶到当前工作区顶部", style = Type.BodyS)
                        }

                        AddTokenAdvancedSection(
                            expanded = showAdvanced,
                            onToggle = { showAdvanced = !showAdvanced },
                            selectedType = selectedType,
                            onTypeChange = { t ->
                                selectedType = t
                                if (t == TokenType.STEAM) {
                                    selectedDigits = 5
                                    selectedPeriod = 30
                                } else if (selectedDigits == 5) {
                                    selectedDigits = 6
                                }
                            },
                            selectedAlgorithm = selectedAlgorithm,
                            onAlgorithmChange = { selectedAlgorithm = it },
                            selectedDigits = selectedDigits,
                            onDigitsChange = { selectedDigits = it },
                            selectedPeriod = selectedPeriod,
                            onPeriodChange = { selectedPeriod = it },
                            selectedCounter = selectedCounter,
                            onCounterChange = { selectedCounter = it }
                        )
                    }

                    if (errorMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Space.S))
                        Text(
                            text = errorMessage,
                            color = semantic.errorText,
                            style = Type.BodyS,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(Space.S))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = Space.XL, vertical = Space.M),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Touch.MinTarget)) { Text("取消") }
                    Spacer(modifier = Modifier.width(Space.S))
                    if (step == 1) {
                        val preview = batchPreview
                        Button(
                            onClick = {
                                if (preview != null && preview.tokens.isNotEmpty()) {
                                    onBatchConfirm(preview.tokens)
                                } else if (rawUriOrKey.isNotBlank()) {
                                    doParse()
                                } else {
                                    step = 2
                                }
                            },
                            shape = RoundedCornerShape(Radius.M),
                            modifier = Modifier.heightIn(min = Touch.MinTarget)
                        ) {
                            Text(if (preview != null && preview.tokens.isNotEmpty()) "导入全部" else "下一步")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { step = 1; errorMessage = "" },
                            shape = RoundedCornerShape(Radius.M),
                            modifier = Modifier.heightIn(min = Touch.MinTarget)
                        ) { Text("上一步") }
                        Spacer(modifier = Modifier.width(Space.S))
                        Button(
                            onClick = {
                                if (issuer.isBlank()) {
                                    errorMessage = "请输入平台/服务商名称"
                                    return@Button
                                }
                                if (!OtpUriParser.isStrictBase32Secret(secret)) {
                                    errorMessage = "请输入合法的 Base32 密钥（至少 8 位，仅含 A-Z 与 2-7）"
                                    return@Button
                                }
                                onConfirm(
                                    AuthToken(
                                        workspaceId = selectedWorkspaceId,
                                        issuer = issuer.trim(),
                                        accountName = accountName.trim().ifBlank { "默认账号" },
                                        tenantEnv = tenantEnv.trim(),
                                        secret = OtpUriParser.normalizeBase32(secret),
                                        tagIds = selectedTagIds,
                                        algorithm = selectedAlgorithm,
                                        digits = if (selectedType == TokenType.STEAM) 5 else selectedDigits,
                                        period = if (selectedType == TokenType.STEAM) 30 else selectedPeriod,
                                        counter = selectedCounter,
                                        type = selectedType,
                                        isPinned = isPinned,
                                        note = note.trim()
                                    )
                                )
                            },
                            shape = RoundedCornerShape(Radius.M),
                            modifier = Modifier.heightIn(min = Touch.MinTarget)
                        ) { Text("保存") }
                    }
                }
            }
        }
    }
}
