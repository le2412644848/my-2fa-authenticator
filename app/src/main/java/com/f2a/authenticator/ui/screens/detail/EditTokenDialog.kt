package com.f2a.authenticator.ui.screens.detail

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.f2a.authenticator.data.model.AuthToken
import com.f2a.authenticator.data.model.Tag
import com.f2a.authenticator.data.model.Workspace
import com.f2a.authenticator.ui.components.parseHexColor
import com.f2a.authenticator.ui.theme.IconSize
import com.f2a.authenticator.ui.theme.LocalSemanticColors
import com.f2a.authenticator.ui.theme.Radius
import com.f2a.authenticator.ui.theme.Space
import com.f2a.authenticator.ui.theme.Touch
import com.f2a.authenticator.ui.theme.Type

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditTokenDialog(
    token: AuthToken,
    allTags: List<Tag>,
    allWorkspaces: List<Workspace>,
    onDismiss: () -> Unit,
    onSave: (AuthToken) -> Unit
) {
    val semantic = LocalSemanticColors.current
    var editIssuer by remember(token.id) { mutableStateOf(token.issuer) }
    var editAccountName by remember(token.id) { mutableStateOf(token.accountName) }
    var editTenantEnv by remember(token.id) { mutableStateOf(token.tenantEnv) }
    var editNote by remember(token.id) { mutableStateOf(token.note) }
    var editWorkspaceId by remember(token.id) { mutableStateOf(token.workspaceId) }
    var editTagIds by remember(token.id) { mutableStateOf(token.tagIds) }
    var editPinned by remember(token.id) { mutableStateOf(token.isPinned) }
    var validationError by remember(token.id) { mutableStateOf("") }

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
                    .heightIn(max = screenHeight * 0.88f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Space.XL, end = Space.S, top = Space.S),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "编辑账户凭据",
                        style = Type.TitleSection,
                        modifier = Modifier.weight(1f)
                    )
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
                    OutlinedTextField(
                        value = editIssuer,
                        onValueChange = { editIssuer = it; validationError = "" },
                        label = { Text("平台/发行方") },
                        supportingText = { Text("修改将同步调整首页分组") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radius.S)
                    )

                    Spacer(modifier = Modifier.height(Space.M))

                    OutlinedTextField(
                        value = editAccountName,
                        onValueChange = { editAccountName = it; validationError = "" },
                        label = { Text("账号/邮箱显示名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radius.S)
                    )

                    Spacer(modifier = Modifier.height(Space.M))

                    OutlinedTextField(
                        value = editTenantEnv,
                        onValueChange = { editTenantEnv = it },
                        label = { Text("环境/租户角标") },
                        placeholder = { Text("如 Prod、Dev、测试号") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radius.S)
                    )

                    Spacer(modifier = Modifier.height(Space.M))

                    Text("所属工作区", style = Type.BodyS, fontWeight = FontWeight.SemiBold)
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Space.XS),
                        horizontalArrangement = Arrangement.spacedBy(Space.S),
                        verticalArrangement = Arrangement.spacedBy(Space.XS)
                    ) {
                        allWorkspaces.forEach { ws ->
                            val wsColor = remember(ws.colorHex) { parseHexColor(ws.colorHex) }
                            FilterChip(
                                selected = editWorkspaceId == ws.id,
                                onClick = { editWorkspaceId = ws.id },
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

                    Text("分配标签", style = Type.BodyS, fontWeight = FontWeight.SemiBold)
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Space.XS),
                        horizontalArrangement = Arrangement.spacedBy(Space.S),
                        verticalArrangement = Arrangement.spacedBy(Space.XS)
                    ) {
                        allTags.forEach { tag ->
                            val isSel = editTagIds.contains(tag.id)
                            val tagColor = remember(tag.colorHex) { parseHexColor(tag.colorHex) }
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    editTagIds = if (isSel) editTagIds - tag.id else editTagIds + tag.id
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
                        value = editNote,
                        onValueChange = { editNote = it },
                        label = { Text("备忘与应急提示") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radius.S)
                    )

                    Spacer(modifier = Modifier.height(Space.S))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = editPinned, onCheckedChange = { editPinned = it })
                        Text("置顶该账号（优先展示在分组顶部）", style = Type.BodyS)
                    }

                    if (validationError.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Space.XS))
                        Text(
                            text = validationError,
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
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = Touch.MinTarget)
                    ) { Text("取消") }
                    Spacer(modifier = Modifier.width(Space.S))
                    Button(
                        onClick = {
                            if (editIssuer.isBlank()) {
                                validationError = "平台/发行方名称不能为空"
                                return@Button
                            }
                            if (editAccountName.isBlank()) {
                                validationError = "账号名称不能为空"
                                return@Button
                            }
                            onSave(
                                token.copy(
                                    issuer = editIssuer.trim(),
                                    accountName = editAccountName.trim(),
                                    tenantEnv = editTenantEnv.trim(),
                                    note = editNote.trim(),
                                    workspaceId = editWorkspaceId,
                                    tagIds = editTagIds,
                                    isPinned = editPinned,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        },
                        shape = RoundedCornerShape(Radius.M),
                        modifier = Modifier.heightIn(min = Touch.MinTarget)
                    ) { Text("保存修改") }
                }
            }
        }
    }
}
