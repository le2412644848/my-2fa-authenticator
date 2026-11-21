package com.f2a.authenticator.ui.screens.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.f2a.authenticator.data.model.Tag
import com.f2a.authenticator.data.model.Workspace
import com.f2a.authenticator.security.PinConfigResult
import com.f2a.authenticator.security.SecurityLockManager
import com.f2a.authenticator.ui.components.EmptyStateCompact
import com.f2a.authenticator.ui.state.TrashItemUi
import com.f2a.authenticator.ui.theme.IconSize
import com.f2a.authenticator.ui.theme.LocalSemanticColors
import com.f2a.authenticator.ui.theme.Radius
import com.f2a.authenticator.ui.theme.Space
import com.f2a.authenticator.ui.theme.Touch
import com.f2a.authenticator.ui.theme.Type
import kotlinx.coroutines.delay

@Composable
fun AddWorkspaceDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    val semantic = LocalSemanticColors.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    var newWsName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
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
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(Space.XL)
            ) {
                Text("新增工作区", style = Type.TitleSection)
                Spacer(modifier = Modifier.height(Space.M))
                OutlinedTextField(
                    value = newWsName,
                    onValueChange = { newWsName = it },
                    label = { Text("工作区名称") },
                    placeholder = { Text("如 游戏资产、跨境电商") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.S)
                )
                Spacer(modifier = Modifier.height(Space.L))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Touch.MinTarget)) { Text("取消") }
                    Spacer(modifier = Modifier.width(Space.S))
                    Button(
                        onClick = { if (newWsName.isNotBlank()) onAdd(newWsName.trim()) },
                        enabled = newWsName.isNotBlank(),
                        shape = RoundedCornerShape(Radius.M),
                        modifier = Modifier.heightIn(min = Touch.MinTarget)
                    ) { Text("添加") }
                }
            }
        }
    }
}

@Composable
fun RenameWorkspaceDialog(
    workspace: Workspace,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    val semantic = LocalSemanticColors.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    var name by remember(workspace.id) { mutableStateOf(workspace.name) }

    Dialog(onDismissRequest = onDismiss) {
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
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(Space.XL)
            ) {
                Text("重命名工作区", style = Type.TitleSection)
                Spacer(modifier = Modifier.height(Space.M))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("新名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.S)
                )
                Spacer(modifier = Modifier.height(Space.L))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Touch.MinTarget)) { Text("取消") }
                    Spacer(modifier = Modifier.width(Space.S))
                    Button(
                        onClick = { if (name.isNotBlank()) onRename(name.trim()) },
                        enabled = name.isNotBlank(),
                        shape = RoundedCornerShape(Radius.M),
                        modifier = Modifier.heightIn(min = Touch.MinTarget)
                    ) { Text("保存") }
                }
            }
        }
    }
}

@Composable
fun DeleteWorkspaceConfirmDialog(
    workspace: Workspace,
    affectedTokenCount: Int,
    fallbackWorkspaceName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.XL),
        title = { Text("确认删除工作区？", style = Type.TitleSection) },
        text = {
            Text(
                if (affectedTokenCount > 0) {
                    "将永久删除工作区「${workspace.name}」，其下 $affectedTokenCount 个凭据将自动迁移至「$fallbackWorkspaceName」，凭据不会丢失。"
                } else {
                    "确认删除空工作区「${workspace.name}」吗？"
                },
                style = Type.BodyM
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(Radius.M),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.heightIn(min = Touch.MinTarget)
            ) { Text("确认删除") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Touch.MinTarget)) { Text("取消") }
        }
    )
}

@Composable
fun DeleteTagSettingConfirmDialog(
    tag: Tag,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.XL),
        title = { Text("确认删除标签？", style = Type.TitleSection) },
        text = { Text("确认删除标签「${tag.name}」吗？所有绑定该标签的账号将自动解除绑定，账号本身不会受到影响。", style = Type.BodyM) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(Radius.M),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.heightIn(min = Touch.MinTarget)
            ) { Text("确认删除") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Touch.MinTarget)) { Text("取消") }
        }
    )
}

@Composable
fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { next ->
            if (next.length <= 6 && next.all { it.isDigit() }) onValueChange(next)
        },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }, modifier = Modifier.size(Touch.MinTarget)) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "隐藏密码" else "显示密码",
                    modifier = Modifier.size(IconSize.M)
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.M)
    )
}

fun formatPinConfigError(res: PinConfigResult): String = when (res) {
    PinConfigResult.Ok -> ""
    PinConfigResult.InvalidFormat -> "请输入 6 位纯数字密码"
    PinConfigResult.WeakPin -> "密码过于简单，请勿使用全同、连续或重复模式数字"
    PinConfigResult.SameAsCounterpart -> "胁迫密码不能与主密码相同"
    is PinConfigResult.AuthFailed -> formatVerifyError(res.verifyResult)
}

fun formatVerifyError(res: SecurityLockManager.VerifyResult): String = when (res) {
    SecurityLockManager.VerifyResult.Success -> ""
    SecurityLockManager.VerifyResult.NotConfigured -> "尚未配置主密码"
    SecurityLockManager.VerifyResult.DuressTriggered -> "密码验证异常"
    SecurityLockManager.VerifyResult.SelfDestructed -> "已触发安全自毁"
    is SecurityLockManager.VerifyResult.WrongPin -> "密码错误，还可尝试 ${res.remaining} 次"
    is SecurityLockManager.VerifyResult.LockedOut -> "尝试过多已锁定，请 ${res.waitSeconds} 秒后重试"
}

@Composable
fun ChangeMasterPinDialog(
    onDismiss: () -> Unit,
    onDuressTriggered: () -> Unit = {},
    onSelfDestructed: () -> Unit = {},
    onChangePin: (current: String, next: String, onResult: (PinConfigResult) -> Unit) -> Unit
) {
    val semantic = LocalSemanticColors.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    // 【安全红线】敏感 PIN 严禁使用 rememberSaveable
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
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
                    Text("修改保险库主密码", style = Type.TitleSection, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.size(Touch.MinTarget)) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(IconSize.M))
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(horizontal = Space.XL),
                    verticalArrangement = Arrangement.spacedBy(Space.S)
                ) {
                    PinField(value = currentPin, onValueChange = { currentPin = it; errorMsg = "" }, label = "当前 6 位主密码")
                    PinField(value = newPin, onValueChange = { newPin = it; errorMsg = "" }, label = "新 6 位主密码")
                    PinField(value = confirmPin, onValueChange = { confirmPin = it; errorMsg = "" }, label = "再次确认新密码")
                    if (errorMsg.isNotEmpty()) {
                        Text(text = errorMsg, color = semantic.errorText, style = Type.BodyS, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(Space.XS))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = Space.XL, vertical = Space.M),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        enabled = !busy,
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = Touch.MinTarget)
                    ) { Text("取消") }
                    Spacer(modifier = Modifier.width(Space.S))
                    Button(
                        enabled = !busy && currentPin.length == 6 && newPin.length == 6 && confirmPin.length == 6,
                        onClick = {
                            if (newPin != confirmPin) {
                                errorMsg = "两次输入的新密码不一致"
                                return@Button
                            }
                            busy = true
                            onChangePin(currentPin, newPin) { res ->
                                busy = false
                                when {
                                    res is PinConfigResult.Ok -> {
                                        currentPin = ""; newPin = ""; confirmPin = ""
                                        onDismiss()
                                    }
                                    res is PinConfigResult.AuthFailed &&
                                        res.verifyResult is SecurityLockManager.VerifyResult.DuressTriggered -> {
                                        currentPin = ""; newPin = ""; confirmPin = ""
                                        onDismiss()
                                        onDuressTriggered()
                                    }
                                    res is PinConfigResult.AuthFailed &&
                                        res.verifyResult is SecurityLockManager.VerifyResult.SelfDestructed -> {
                                        currentPin = ""; newPin = ""; confirmPin = ""
                                        onDismiss()
                                        onSelfDestructed()
                                    }
                                    else -> errorMsg = formatPinConfigError(res)
                                }
                            }
                        },
                        shape = RoundedCornerShape(Radius.M),
                        modifier = Modifier.heightIn(min = Touch.MinTarget)
                    ) { Text("确认修改") }
                }
            }
        }
    }
}

@Composable
fun ConfigureDuressPinDialog(
    hasDuressPin: Boolean,
    onDismiss: () -> Unit,
    onDuressTriggered: () -> Unit = {},
    onSelfDestructed: () -> Unit = {},
    onSetDuressPin: (masterPin: String, duressPin: String, onResult: (PinConfigResult) -> Unit) -> Unit,
    onClearDuressPin: (masterPin: String, onResult: (PinConfigResult) -> Unit) -> Unit
) {
    val semantic = LocalSemanticColors.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    var masterPin by remember { mutableStateOf("") }
    var duressPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
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
                    Text(
                        text = if (hasDuressPin) "管理胁迫销毁密码" else "设置胁迫销毁密码",
                        style = Type.TitleSection,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.size(Touch.MinTarget)) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(IconSize.M))
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(horizontal = Space.XL),
                    verticalArrangement = Arrangement.spacedBy(Space.S)
                ) {
                    Text(
                        text = "在锁屏输入胁迫密码将静默抹除本地密钥并进入空库伪装态（不可与主密码相同）。",
                        style = Type.BodyS,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PinField(value = masterPin, onValueChange = { masterPin = it; errorMsg = "" }, label = "当前 6 位主密码")
                    PinField(value = duressPin, onValueChange = { duressPin = it; errorMsg = "" }, label = "新 6 位胁迫密码（设置/更新时填写）")
                    if (errorMsg.isNotEmpty()) {
                        Text(text = errorMsg, color = semantic.errorText, style = Type.BodyS, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(Space.XS))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = Space.XL, vertical = Space.M),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasDuressPin) {
                        TextButton(
                            enabled = !busy && masterPin.length == 6,
                            onClick = {
                                busy = true
                                onClearDuressPin(masterPin) { res ->
                                    busy = false
                                    when {
                                        res is PinConfigResult.Ok -> {
                                            masterPin = ""; duressPin = ""
                                            onDismiss()
                                        }
                                        res is PinConfigResult.AuthFailed &&
                                            res.verifyResult is SecurityLockManager.VerifyResult.DuressTriggered -> {
                                            masterPin = ""; duressPin = ""
                                            onDismiss()
                                            onDuressTriggered()
                                        }
                                        res is PinConfigResult.AuthFailed &&
                                            res.verifyResult is SecurityLockManager.VerifyResult.SelfDestructed -> {
                                            masterPin = ""; duressPin = ""
                                            onDismiss()
                                            onSelfDestructed()
                                        }
                                        else -> errorMsg = formatPinConfigError(res)
                                    }
                                }
                            },
                            modifier = Modifier.heightIn(min = Touch.MinTarget)
                        ) { Text("关闭胁迫码", color = semantic.errorText) }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    TextButton(
                        enabled = !busy,
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = Touch.MinTarget)
                    ) { Text("取消") }
                    Spacer(modifier = Modifier.width(Space.S))
                    Button(
                        enabled = !busy && masterPin.length == 6 && duressPin.length == 6,
                        onClick = {
                            busy = true
                            onSetDuressPin(masterPin, duressPin) { res ->
                                busy = false
                                when {
                                    res is PinConfigResult.Ok -> {
                                        masterPin = ""; duressPin = ""
                                        onDismiss()
                                    }
                                    res is PinConfigResult.AuthFailed &&
                                        res.verifyResult is SecurityLockManager.VerifyResult.DuressTriggered -> {
                                        masterPin = ""; duressPin = ""
                                        onDismiss()
                                        onDuressTriggered()
                                    }
                                    res is PinConfigResult.AuthFailed &&
                                        res.verifyResult is SecurityLockManager.VerifyResult.SelfDestructed -> {
                                        masterPin = ""; duressPin = ""
                                        onDismiss()
                                        onSelfDestructed()
                                    }
                                    else -> errorMsg = formatPinConfigError(res)
                                }
                            }
                        },
                        shape = RoundedCornerShape(Radius.M),
                        modifier = Modifier.heightIn(min = Touch.MinTarget)
                    ) { Text("保存胁迫码") }
                }
            }
        }
    }
}

@Composable
fun TrashManagerDialog(
    items: List<TrashItemUi>,
    onDismiss: () -> Unit,
    onRestore: (String) -> Unit,
    onPermanentDelete: (String) -> Unit,
    onEmptyTrash: () -> Unit
) {
    val semantic = LocalSemanticColors.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    Dialog(onDismissRequest = onDismiss) {
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
                    .heightIn(max = screenHeight * 0.85f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Space.XL, end = Space.S, top = Space.S),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("回收站（7 天保留）", style = Type.TitleSection, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(Touch.MinTarget)) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(IconSize.M))
                    }
                }

                if (items.isEmpty()) {
                    EmptyStateCompact(
                        icon = Icons.Default.DeleteSweep,
                        title = "回收站为空",
                        description = "删除的凭据会在此保留 7 天，随时可恢复",
                        modifier = Modifier.padding(horizontal = Space.XL)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(horizontal = Space.L)
                    ) {
                        items(
                            items = items,
                            key = { it.token.id },
                            contentType = { "trash_item" }
                        ) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Space.S),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${item.token.issuer} · ${item.token.accountName}",
                                        style = Type.BodyM,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "剩余 ${item.remainingDays} 天后自动永久销毁",
                                        style = Type.Caption,
                                        color = semantic.errorText
                                    )
                                }
                                IconButton(
                                    onClick = { onRestore(item.token.id) },
                                    modifier = Modifier.size(Touch.MinTarget)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RestoreFromTrash,
                                        contentDescription = "恢复凭据",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = { onPermanentDelete(item.token.id) },
                                    modifier = Modifier.size(Touch.MinTarget)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteForever,
                                        contentDescription = "彻底删除",
                                        tint = semantic.errorText
                                    )
                                }
                            }
                            HorizontalDivider(color = semantic.borderSubtle)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = Space.XL, vertical = Space.M),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (items.isNotEmpty()) {
                        TextButton(
                            onClick = onEmptyTrash,
                            modifier = Modifier.heightIn(min = Touch.MinTarget)
                        ) {
                            Text("清空回收站", color = semantic.errorText)
                        }
                        Spacer(modifier = Modifier.width(Space.S))
                    }
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(Radius.M),
                        modifier = Modifier.heightIn(min = Touch.MinTarget)
                    ) {
                        Text("关闭")
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmClearTokensDialog(
    onDismiss: () -> Unit,
    onDuressTriggered: () -> Unit = {},
    onSelfDestructed: () -> Unit = {},
    onVerifyAndClear: (pin: String, onResult: (SecurityLockManager.VerifyResult) -> Unit) -> Unit
) {
    val semantic = LocalSemanticColors.current
    // 【安全红线】掩码输入且使用 remember（非 rememberSaveable）
    var clearPasswordConfirm by remember { mutableStateOf("") }
    var clearError by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        shape = RoundedCornerShape(Radius.XL),
        icon = { Icon(Icons.Default.Warning, contentDescription = "警告", tint = MaterialTheme.colorScheme.error) },
        title = { Text("确认清空全部 2FA 凭据？", style = Type.TitleSection) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("此操作将永久清空当前保险库内所有动态验证码，请确认已提前完成灾备导出！", style = Type.BodyM)
                Spacer(modifier = Modifier.height(Space.MS))
                PinField(
                    value = clearPasswordConfirm,
                    onValueChange = { clearPasswordConfirm = it; clearError = "" },
                    label = "输入 6 位主密码以确认"
                )
                if (clearError.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(Space.XS))
                    Text(clearError, color = semantic.errorText, style = Type.BodyS, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && clearPasswordConfirm.length == 6,
                onClick = {
                    busy = true
                    onVerifyAndClear(clearPasswordConfirm) { verify ->
                        busy = false
                        when (verify) {
                            is SecurityLockManager.VerifyResult.Success -> {
                                clearPasswordConfirm = ""
                                onDismiss()
                            }
                            is SecurityLockManager.VerifyResult.DuressTriggered -> {
                                clearPasswordConfirm = ""
                                onDismiss()
                                onDuressTriggered()
                            }
                            is SecurityLockManager.VerifyResult.SelfDestructed -> {
                                clearPasswordConfirm = ""
                                onDismiss()
                                onSelfDestructed()
                            }
                            else -> clearError = formatVerifyError(verify)
                        }
                    }
                },
                shape = RoundedCornerShape(Radius.M),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.heightIn(min = Touch.MinTarget)
            ) { Text("确认清空") }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = Touch.MinTarget)
            ) { Text("取消") }
        }
    )
}

@Composable
fun SelfDestructInputDialog(
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit
) {
    val confirmWord = "确认自毁"
    var inputText by remember { mutableStateOf("") }
    val canProceed = inputText.trim() == confirmWord

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        shape = RoundedCornerShape(Radius.XL),
        icon = { Icon(Icons.Default.Warning, contentDescription = "自毁警告", tint = MaterialTheme.colorScheme.error) },
        title = { Text("触发自毁程序（第 2/3 步）", style = Type.TitleSection, color = MaterialTheme.colorScheme.error) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("此操作将注销并永久抹除硬件主密钥使本地密文永久不可解，并删除本地数据文件。此操作不可恢复！", style = Type.BodyM)
                Spacer(modifier = Modifier.height(Space.MS))
                Text("请输入「$confirmWord」四个字以继续：", style = Type.BodyS, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(Space.S))
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("输入 $confirmWord") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.S)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmed,
                enabled = canProceed,
                shape = RoundedCornerShape(Radius.M),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.heightIn(min = Touch.MinTarget)
            ) { Text("下一步") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Touch.MinTarget)) { Text("放弃") }
        }
    )
}

@Composable
fun SelfDestructCountdownDialog(
    onDismiss: () -> Unit,
    onExecuteDestruct: () -> Unit
) {
    var countdown by remember { mutableIntStateOf(5) }
    LaunchedEffect(Unit) {
        countdown = 5
        while (countdown > 0) {
            delay(1000L)
            countdown -= 1
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        shape = RoundedCornerShape(Radius.XL),
        icon = { Icon(Icons.Default.Warning, contentDescription = "自毁倒计时", tint = MaterialTheme.colorScheme.error) },
        title = { Text("最终确认（第 3/3 步）", style = Type.TitleSection, color = MaterialTheme.colorScheme.error) },
        text = {
            Text(
                if (countdown > 0) "倒计时结束后方可引爆：$countdown 秒…\n此操作不可恢复，请确认已做好全部灾备。"
                else "倒计时已结束，可立即引爆。此操作不可恢复！",
                style = Type.BodyM
            )
        },
        confirmButton = {
            Button(
                onClick = onExecuteDestruct,
                enabled = countdown <= 0,
                shape = RoundedCornerShape(Radius.M),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.heightIn(min = Touch.MinTarget)
            ) {
                Text(if (countdown > 0) "请等待 $countdown 秒" else "立即引爆自毁")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Touch.MinTarget)) { Text("取消") }
        }
    )
}
