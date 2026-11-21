package com.f2a.authenticator.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.f2a.authenticator.data.model.AuthToken
import com.f2a.authenticator.data.model.SortMode
import com.f2a.authenticator.data.model.Tag
import com.f2a.authenticator.data.model.Workspace
import com.f2a.authenticator.ui.components.ClipboardCountdownBanner
import com.f2a.authenticator.ui.components.CollapsingHeader
import com.f2a.authenticator.ui.components.CopyFeedback
import com.f2a.authenticator.ui.components.EmptyState
import com.f2a.authenticator.ui.components.IssuerAccordionCard
import com.f2a.authenticator.ui.components.TagFilterBar
import com.f2a.authenticator.ui.components.WorkspaceTabBar
import com.f2a.authenticator.ui.components.collapsingHeaderConnection
import com.f2a.authenticator.ui.components.rememberCollapsingHeaderState
import com.f2a.authenticator.ui.components.rememberForwardingScrollState
import com.f2a.authenticator.ui.state.HomeUiState
import com.f2a.authenticator.ui.theme.IconSize
import com.f2a.authenticator.ui.theme.LocalSemanticColors
import com.f2a.authenticator.ui.theme.Motion
import com.f2a.authenticator.ui.theme.Radius
import com.f2a.authenticator.ui.theme.Space
import com.f2a.authenticator.ui.theme.Touch
import com.f2a.authenticator.ui.theme.Type
import com.f2a.authenticator.ui.theme.contentMaxWidth
import com.f2a.authenticator.utils.ClipboardGuard
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainHomeScreen(
    uiState: HomeUiState,
    nowMsProvider: () -> Long,
    missingPinRecoveredNotice: Boolean = false,
    onDismissMissingPinNotice: () -> Unit = {},
    onSelectWorkspace: (String) -> Unit,
    onSetSearchExpanded: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit = { onSearchQueryChange("") },
    onTogglePrivacyMask: () -> Unit = {},
    onSelectTagFilter: (String?) -> Unit,
    onClearFilters: () -> Unit,
    onToggleExpandGroup: (String) -> Unit,
    onToggleExpandAll: () -> Unit,
    onSelectSortMode: (SortMode) -> Unit,
    onJumpToIssuer: (String) -> Int,
    onTogglePin: (String) -> Unit,
    onSoftDeleteToken: (String) -> Unit,
    onAdvanceHotp: (AuthToken) -> Unit,
    onRecordTokenUsed: (String) -> Unit,
    onAddTag: (name: String, colorHex: String) -> Unit,
    onDeleteTag: (String) -> Unit,
    onOpenAddDialog: () -> Unit,
    onOpenScanScreen: () -> Unit,
    onOpenBackupDialog: () -> Unit = {},
    onOpenSettingsScreen: () -> Unit,
    onLockNow: () -> Unit,
    onTokenClick: (AuthToken) -> Unit,
    importNotice: String?,
    onImportNoticeShown: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val semantic = LocalSemanticColors.current
    val snackbarHostState = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()
    val searchFocusRequester = remember { FocusRequester() }
    val clipboardRemainingSeconds by ClipboardGuard.remainingSeconds.collectAsState()

    var showCreateTagDialog by remember { mutableStateOf(false) }
    var tagToDelete by remember { mutableStateOf<Tag?>(null) }
    var tokenToDelete by remember { mutableStateOf<AuthToken?>(null) }
    var backupBannerDismissed by remember { mutableStateOf(false) }

    BackHandler(enabled = uiState.isSearchExpanded) {
        onSetSearchExpanded(false)
    }

    LaunchedEffect(uiState.isSearchExpanded) {
        if (uiState.isSearchExpanded) {
            delay(60L)
            searchFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(importNotice) {
        if (!importNotice.isNullOrBlank()) {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(importNotice)
            onImportNoticeShown()
        }
    }

    val workspaceList = remember(uiState.workspaces) {
        listOf(Workspace(id = "all", name = "全部", colorHex = "#1D4ED8")) + uiState.workspaces
    }
    val initialPage = remember(workspaceList.size, uiState.activeWorkspaceId) {
        workspaceList.indexOfFirst { it.id == uiState.activeWorkspaceId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { workspaceList.size })
    val listStates = remember { mutableMapOf<String, LazyListState>() }
    var pendingScrollTarget by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(uiState.activeWorkspaceId, workspaceList, pagerState.settledPage) {
        if (uiState.workspaces.isEmpty()) return@LaunchedEffect
        val targetIndex = workspaceList.indexOfFirst { it.id == uiState.activeWorkspaceId }
        if (pendingScrollTarget == pagerState.settledPage) {
            pendingScrollTarget = null
        }
        if (targetIndex >= 0 &&
            pendingScrollTarget == null &&
            targetIndex != pagerState.currentPage &&
            !pagerState.isScrollInProgress
        ) {
            pagerState.scrollToPage(targetIndex)
        }
    }

    LaunchedEffect(pagerState, workspaceList, uiState.workspaces.isEmpty()) {
        if (uiState.workspaces.isEmpty()) return@LaunchedEffect
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                workspaceList.getOrNull(page)?.id?.let(onSelectWorkspace)
            }
    }

    val headerState = rememberCollapsingHeaderState()
    val activeWorkspaceId by rememberUpdatedState(uiState.activeWorkspaceId)
    val outsideListScroll = rememberForwardingScrollState { listStates[activeWorkspaceId] }
    val headerConnection = remember(headerState, listStates) {
        collapsingHeaderConnection(headerState) {
            listStates[activeWorkspaceId]?.canScrollForward == true
        }
    }
    // 切换工作区或筛选条件后列表可能不足一屏，头部必须可见
    LaunchedEffect(
        pagerState.settledPage,
        uiState.isSearchExpanded,
        uiState.searchQuery,
        uiState.selectedTagFilterId
    ) {
        headerState.expand()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(Radius.S))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                    RoundedCornerShape(Radius.S)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(IconSize.S)
                            )
                        }
                        Spacer(modifier = Modifier.width(Space.S))
                        Text(text = "极盾 2FA", style = Type.TitlePage)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTogglePrivacyMask()
                        },
                        modifier = Modifier.size(width = 42.dp, height = Touch.MinTarget)
                    ) {
                        Icon(
                            imageVector = if (uiState.privacyMaskEnabled) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (uiState.privacyMaskEnabled) "关闭防窥遮罩" else "开启防窥遮罩",
                            tint = if (uiState.privacyMaskEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(IconSize.M)
                        )
                    }
                    IconButton(
                        onClick = { onSetSearchExpanded(!uiState.isSearchExpanded) },
                        modifier = Modifier.size(width = 42.dp, height = Touch.MinTarget)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索",
                            tint = if (uiState.isSearchExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(IconSize.L)
                        )
                    }
                    IconButton(
                        onClick = onOpenAddDialog,
                        modifier = Modifier.size(width = 42.dp, height = Touch.MinTarget)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "添加凭据",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(IconSize.L)
                        )
                    }
                    IconButton(
                        onClick = onLockNow,
                        modifier = Modifier.size(width = 42.dp, height = Touch.MinTarget)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "立即锁定",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(IconSize.M)
                        )
                    }
                    IconButton(
                        onClick = onOpenSettingsScreen,
                        modifier = Modifier.size(width = 42.dp, height = Touch.MinTarget)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(IconSize.L)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenScanScreen()
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "扫码添加", modifier = Modifier.size(IconSize.XL))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(headerConnection)
                .scrollable(outsideListScroll, Orientation.Vertical)
                .contentMaxWidth()
        ) {
            // 独立搜索胶囊栏（彻底解决 TopAppBar 内嵌 TextField 裁切问题）
            AnimatedVisibility(
                visible = uiState.isSearchExpanded,
                enter = expandVertically(tween(Motion.base, easing = Motion.EaseStandard)) +
                    fadeIn(tween(Motion.base)),
                exit = shrinkVertically(tween(Motion.fast, easing = Motion.EaseStandard)) +
                    fadeOut(tween(Motion.fast))
            ) {
                SearchCapsuleBar(
                    query = uiState.searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onClearQuery = onClearSearch,
                    onClose = { onSetSearchExpanded(false) },
                    focusRequester = searchFocusRequester
                )
            }

            // 数据损坏只读保护告警条
            AnimatedVisibility(visible = uiState.vaultLoadCorrupted) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.L, vertical = Space.XS)
                        .clip(RoundedCornerShape(Radius.M))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                            RoundedCornerShape(Radius.M)
                        )
                        .padding(horizontal = Space.M, vertical = Space.S),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(IconSize.S)
                    )
                    Spacer(modifier = Modifier.width(Space.S))
                    Text(
                        text = "保险库主文件读取异常，已进入只读保护模式防止覆写，请通过加密备份恢复。",
                        style = Type.BodyS,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 剪贴板敏感凭据自动清除倒计时浮条
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

            CollapsingHeader(state = headerState) {
                Column {
                    // 加密灾备提醒条
                    AnimatedVisibility(
                        visible = uiState.backupOverdueBanner && !backupBannerDismissed && !uiState.vaultLoadCorrupted
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Space.L, vertical = Space.XS)
                                .clip(RoundedCornerShape(Radius.M))
                                .background(semantic.warningBg)
                                .border(
                                    1.dp,
                                    semantic.timerWarning.copy(alpha = 0.35f),
                                    RoundedCornerShape(Radius.M)
                                )
                                .padding(start = Space.M, end = Space.XS, top = Space.XS, bottom = Space.XS),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Backup,
                                contentDescription = null,
                                tint = semantic.warningText,
                                modifier = Modifier.size(IconSize.S)
                            )
                            Spacer(modifier = Modifier.width(Space.S))
                            Text(
                                text = if (uiState.daysSinceLastBackup == null) {
                                    "尚未创建过 .jdf2 加密灾备"
                                } else {
                                    "距上次安全备份已 ${uiState.daysSinceLastBackup} 天"
                                },
                                style = Type.BodyS,
                                fontWeight = FontWeight.Medium,
                                color = semantic.warningText,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onOpenBackupDialog) {
                                Text("去备份", style = Type.Label, color = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { backupBannerDismissed = true }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "暂不提醒",
                                    tint = semantic.warningText,
                                    modifier = Modifier.size(IconSize.XS)
                                )
                            }
                        }
                    }

                    WorkspaceTabBar(
                        workspaces = workspaceList,
                        selectedIndex = pagerState.currentPage,
                        onSelectWorkspace = { index, ws ->
                            pendingScrollTarget = index
                            onSelectWorkspace(ws.id)
                            uiScope.launch {
                                try {
                                    pagerState.animateScrollToPage(index)
                                } finally {
                                    if (pendingScrollTarget == index) {
                                        pendingScrollTarget = null
                                    }
                                }
                            }
                        }
                    )

                    TagFilterBar(
                        tags = uiState.tags,
                        selectedTagFilterId = uiState.selectedTagFilterId,
                        issuerGroups = uiState.issuerGroups,
                        allExpanded = uiState.allExpanded,
                        isFiltering = uiState.isFiltering,
                        sortMode = uiState.sortMode,
                        onSelectTag = onSelectTagFilter,
                        onLongClickTag = { tag -> tagToDelete = tag },
                        onAddTagClick = { showCreateTagDialog = true },
                        onToggleExpandAll = onToggleExpandAll,
                        onSelectSortMode = onSelectSortMode,
                        onJumpToIssuer = { key ->
                            val idx = onJumpToIssuer(key)
                            if (idx >= 0) {
                                val activeListState = listStates[uiState.activeWorkspaceId]
                                if (activeListState != null) {
                                    uiScope.launch { activeListState.animateScrollToItem(idx) }
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(Space.XXS))
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val pageWorkspace = workspaceList.getOrNull(page) ?: return@HorizontalPager
                val pageListState = remember(pageWorkspace.id) {
                    listStates.getOrPut(pageWorkspace.id) { LazyListState() }
                }
                val pageGroups = uiState.groupsByWorkspace[pageWorkspace.id] ?: emptyList()

                if (!uiState.isLoaded) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.5.dp)
                    }
                } else if (pageGroups.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = if (uiState.isFiltering) Icons.Default.SearchOff else Icons.Default.Shield,
                            title = if (uiState.isFiltering) "未找到匹配凭据" else "当前工作区暂无凭据",
                            description = if (uiState.isFiltering) {
                                "换个关键词，或清除当前筛选条件"
                            } else {
                                "点击右下角扫码按钮，添加您的第一个 2FA 凭据"
                            },
                            primaryActionText = if (uiState.isFiltering) null else "扫码添加",
                            onPrimaryAction = if (uiState.isFiltering) null else onOpenScanScreen,
                            secondaryActionText = if (uiState.isFiltering) "清除筛选" else null,
                            onSecondaryAction = if (uiState.isFiltering) onClearFilters else null
                        )
                    }
                } else {
                    LazyColumn(
                        state = pageListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = Space.XS, bottom = Space.BottomListPad)
                    ) {
                        items(
                            items = pageGroups,
                            key = { "${pageWorkspace.id}_${it.key}" },
                            contentType = { "issuer_group" }
                        ) { group ->
                            IssuerAccordionCard(
                                group = group,
                                allTags = uiState.tags,
                                clipboardClearSeconds = uiState.clipboardClearSeconds,
                                privacyMasked = uiState.privacyMaskEnabled,
                                nowMsProvider = nowMsProvider,
                                onToggleExpand = { onToggleExpandGroup(group.key) },
                                onAccountClick = onTokenClick,
                                onTogglePin = onTogglePin,
                                onRequestDelete = { token -> tokenToDelete = token },
                                onAdvanceHotp = onAdvanceHotp,
                                onCopied = { tokenId ->
                                    onRecordTokenUsed(tokenId)
                                    CopyFeedback.show(snackbarHostState, uiScope, uiState.clipboardClearSeconds)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (missingPinRecoveredNotice) {
        AlertDialog(
            onDismissRequest = onDismissMissingPinNotice,
            shape = RoundedCornerShape(Radius.XL),
            title = { Text("主密码配置已重建", style = Type.TitleSection) },
            text = {
                Text(
                    "检测到本地主密码配置曾丢失，您已成功重设新主密码并恢复保险库访问。建议立即检查设备安全性并完成一次 .jdf2 加密灾备。",
                    style = Type.BodyM
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDismissMissingPinNotice()
                        onOpenBackupDialog()
                    },
                    shape = RoundedCornerShape(Radius.M),
                    modifier = Modifier.heightIn(min = Touch.MinTarget)
                ) { Text("立即备份") }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissMissingPinNotice,
                    modifier = Modifier.heightIn(min = Touch.MinTarget)
                ) { Text("我知道了") }
            }
        )
    }

    tagToDelete?.let { target ->
        DeleteTagConfirmDialog(
            tag = target,
            onDismiss = { tagToDelete = null },
            onConfirm = {
                onDeleteTag(target.id)
                tagToDelete = null
                uiScope.launch { snackbarHostState.showSnackbar("标签已删除") }
            }
        )
    }

    if (showCreateTagDialog) {
        CreateTagDialog(
            onDismiss = { showCreateTagDialog = false },
            onCreate = { name, hex ->
                onAddTag(name, hex)
                showCreateTagDialog = false
                uiScope.launch { snackbarHostState.showSnackbar("标签创建成功") }
            }
        )
    }

    tokenToDelete?.let { target ->
        SoftDeleteTokenConfirmDialog(
            token = target,
            onDismiss = { tokenToDelete = null },
            onConfirm = {
                onSoftDeleteToken(target.id)
                tokenToDelete = null
                uiScope.launch { snackbarHostState.showSnackbar("已移入回收站（7 天内可恢复）") }
            }
        )
    }
}

@Composable
private fun SearchCapsuleBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onClose: () -> Unit,
    focusRequester: FocusRequester
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.L, vertical = Space.XS),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(Radius.Full))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(Radius.Full)
                )
                .padding(horizontal = Space.M),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconSize.S)
            )
            Spacer(modifier = Modifier.width(Space.S))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "搜索平台、账号、环境或标签…",
                        style = Type.BodyM,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = Type.BodyM.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearQuery) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "清空搜索词",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IconSize.XS)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(Space.XS))
        TextButton(
            onClick = onClose,
            modifier = Modifier.heightIn(min = Touch.MinTarget)
        ) {
            Text("取消", style = Type.BodyM)
        }
    }
}
