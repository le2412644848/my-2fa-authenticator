package com.f2a.authenticator.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.f2a.authenticator.data.model.AuthToken
import com.f2a.authenticator.data.model.VaultPreferences
import com.f2a.authenticator.data.model.Workspace
import com.f2a.authenticator.di.AppContainer
import com.f2a.authenticator.security.BiometricHelper
import com.f2a.authenticator.security.BiometricStatus
import com.f2a.authenticator.security.PinConfigResult
import com.f2a.authenticator.security.SecurityLockManager
import com.f2a.authenticator.ui.state.ActiveModal
import com.f2a.authenticator.ui.state.AddTokenDraft
import com.f2a.authenticator.ui.state.AppRoute
import com.f2a.authenticator.ui.state.AppShellUiState
import com.f2a.authenticator.ui.state.VaultAuthState
import com.f2a.authenticator.utils.ClipboardGuard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppViewModel(
    application: Application,
    private val container: AppContainer
) : AndroidViewModel(application) {

    private val tokenRepository = container.tokenRepository
    private val preferencesRepository = container.preferencesRepository
    private val securityManager = container.securityLockManager
    private val backgroundLockMonitor = container.backgroundLockMonitor

    // 【安全红线】isUnlocked 严禁写入 SavedStateHandle / SharedPreferences / 文件，仅驻留内存。
    private val isUnlockedFlow = MutableStateFlow(false)
    private val authMetaFlow = MutableStateFlow(readAuthMeta())
    private val routeFlow = MutableStateFlow<AppRoute>(AppRoute.Home)
    private val activeModalFlow = MutableStateFlow<ActiveModal>(ActiveModal.None)

    // 【安全红线】含明文种子，仅驻留内存；关闭弹窗、锁定、自毁、ViewModel 销毁时清空。
    val addTokenDraft = AddTokenDraft()
    private val activeWorkspaceIdFlow = MutableStateFlow(
        preferencesRepository.preferences.value.lastActiveWorkspaceId.ifBlank { "all" }
    )
    private val flagSecureFlow = MutableStateFlow(securityManager.isFlagSecureEnabled())
    private val snackbarMessageFlow = MutableStateFlow<String?>(null)
    private val missingPinRecoveredNoticeFlow = MutableStateFlow(false)
    private val selfDestructedFlow = MutableStateFlow(false)
    private val _lockSessionCounter = MutableStateFlow(0L)
    val lockSessionCounter: StateFlow<Long> = _lockSessionCounter.asStateFlow()

    init {
        backgroundLockMonitor.setLockHandler(
            timeoutProvider = { preferencesRepository.preferences.value.autoLockTimeout },
            onLock = { lockNow() }
        )
    }

    private val authStateFlow = combine(isUnlockedFlow, authMetaFlow) { unlocked, meta ->
        meta.copy(isUnlocked = unlocked)
    }

    val uiState: StateFlow<AppShellUiState> = combine(
        authStateFlow,
        routeFlow,
        activeModalFlow,
        activeWorkspaceIdFlow,
        preferencesRepository.preferences,
        flagSecureFlow,
        snackbarMessageFlow,
        tokenRepository.workspaces,
        tokenRepository.vaultLoadCorrupted,
        missingPinRecoveredNoticeFlow,
        selfDestructedFlow
    ) { values ->
        val auth = values[0] as VaultAuthState
        val route = values[1] as AppRoute
        val modal = values[2] as ActiveModal
        val activeWs = values[3] as String
        val prefs = values[4] as VaultPreferences
        val flagSecure = values[5] as Boolean
        val snackbar = values[6] as String?
        @Suppress("UNCHECKED_CAST")
        val workspaces = values[7] as List<Workspace>
        val vaultLoadCorrupted = values[8] as Boolean
        val missingPinRecovered = values[9] as Boolean
        val selfDestructed = values[10] as Boolean

        val resolvedActiveWs = if (activeWs != "all" && workspaces.isNotEmpty() && workspaces.none { it.id == activeWs }) {
            "all"
        } else {
            activeWs
        }
        AppShellUiState(
            authState = auth,
            route = route,
            activeModal = modal,
            activeWorkspaceId = resolvedActiveWs,
            themeMode = prefs.themeMode,
            flagSecureEnabled = flagSecure,
            snackbarMessage = snackbar,
            vaultLoadCorrupted = vaultLoadCorrupted,
            missingPinRecoveredNotice = missingPinRecovered,
            selfDestructed = selfDestructed
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = AppShellUiState(
            authState = readAuthMeta().copy(isUnlocked = false),
            themeMode = preferencesRepository.preferences.value.themeMode,
            flagSecureEnabled = flagSecureFlow.value
        )
    )

    private fun readAuthMeta(): VaultAuthState {
        val ctx = getApplication<Application>()
        val isPinConfigured = securityManager.isPinConfigured()
        val bioAvailable = try {
            BiometricHelper.checkAvailability(ctx) == BiometricStatus.AVAILABLE
        } catch (_: Throwable) {
            false
        }
        val missingPinAnomaly = securityManager.isPinMissingAnomaly(tokenRepository.hasStoredEncryptedTokens())
        return VaultAuthState(
            isPinConfigured = isPinConfigured,
            isUnlocked = isUnlockedFlow.value,
            isBiometricEnabled = securityManager.isBiometricEnabled(),
            isBiometricAvailable = bioAvailable,
            remainingAttempts = securityManager.getRemainingAttempts(),
            lockoutSeconds = securityManager.getLockoutRemainingSeconds(),
            missingPinAnomaly = missingPinAnomaly
        )
    }

    fun refreshAuthState() {
        authMetaFlow.value = readAuthMeta()
        flagSecureFlow.value = securityManager.isFlagSecureEnabled()
    }

    fun refreshLockoutSeconds(): Long {
        val sec = securityManager.getLockoutRemainingSeconds()
        authMetaFlow.value = readAuthMeta().copy(lockoutSeconds = sec)
        return sec
    }

    fun dismissMissingPinNotice() {
        missingPinRecoveredNoticeFlow.value = false
    }

    fun setupPinAndUnlock(pin: String, onResult: (PinConfigResult) -> Unit) {
        viewModelScope.launch {
            val wasMissingAnomaly = authMetaFlow.value.missingPinAnomaly
            val result = securityManager.setupPin(pin)
            if (result is PinConfigResult.Ok) {
                tokenRepository.unlockAndLoad()
                backgroundLockMonitor.resetBackgroundTimer()
                isUnlockedFlow.value = true
                if (wasMissingAnomaly) {
                    missingPinRecoveredNoticeFlow.value = true
                }
                refreshAuthState()
            } else {
                refreshAuthState()
            }
            onResult(result)
        }
    }

    fun verifyPinAndUnlock(pin: String, onResult: (SecurityLockManager.VerifyResult) -> Unit) {
        viewModelScope.launch {
            val result = securityManager.verifyPin(pin)
            when (result) {
                is SecurityLockManager.VerifyResult.Success -> {
                    tokenRepository.unlockAndLoad()
                    backgroundLockMonitor.resetBackgroundTimer()
                    isUnlockedFlow.value = true
                }
                is SecurityLockManager.VerifyResult.DuressTriggered -> {
                    // 【安全红线】先同步清空内存密钥、偏好与剪贴板并阻断回写，再解锁空库/诱饵态
                    tokenRepository.wipeInMemoryState()
                    preferencesRepository.clearAllPreferences()
                    ClipboardGuard.clearNow(getApplication())
                    backgroundLockMonitor.resetBackgroundTimer()
                    isUnlockedFlow.value = true
                    _lockSessionCounter.update { it + 1L }
                }
                is SecurityLockManager.VerifyResult.SelfDestructed -> {
                    tokenRepository.wipeInMemoryState()
                    preferencesRepository.clearAllPreferences()
                    ClipboardGuard.clearNow(getApplication())
                    isUnlockedFlow.value = false
                    selfDestructedFlow.value = true
                    _lockSessionCounter.update { it + 1L }
                }
                else -> Unit
            }
            refreshAuthState()
            onResult(result)
        }
    }

    fun authenticateWithBiometric(
        activity: FragmentActivity,
        onError: (String) -> Unit
    ) {
        if (!securityManager.isPinConfigured() || !securityManager.isBiometricEnabled()) return
        val lockout = securityManager.getLockoutRemainingSeconds()
        if (lockout > 0L) {
            refreshAuthState()
            onError("尝试过多已锁定，请 ${lockout} 秒后重试")
            return
        }
        BiometricHelper.authenticate(
            activity = activity,
            onSuccess = {
                viewModelScope.launch {
                    val verified = securityManager.onBiometricVerified()
                    if (!verified) {
                        refreshAuthState()
                        onError("当前处于安全锁定或生物识别未启用，请使用主密码")
                        return@launch
                    }
                    tokenRepository.unlockAndLoad()
                    backgroundLockMonitor.resetBackgroundTimer()
                    isUnlockedFlow.value = true
                    refreshAuthState()
                }
            },
            onError = { _, message -> onError(message) },
            onCancelled = {}
        )
    }

    fun lockNow() {
        isUnlockedFlow.value = false
        routeFlow.value = AppRoute.Home
        activeModalFlow.value = ActiveModal.None
        addTokenDraft.clear()
        tokenRepository.lockInMemory()
        _lockSessionCounter.update { it + 1L }
        refreshAuthState()
    }

    fun onAppResumed() {
        ClipboardGuard.clearIfExpiredOnResume(getApplication())
        if (isUnlockedFlow.value) {
            val timeout = preferencesRepository.preferences.value.autoLockTimeout
            if (backgroundLockMonitor.checkAndConsumeShouldLock(timeout)) {
                lockNow()
                return
            }
        }
        refreshAuthState()
    }

    fun onWindowFocusGained(context: Context) {
        ClipboardGuard.clearIfExpiredOnWindowFocus(context)
    }

    fun suppressNextBackgroundLock() {
        backgroundLockMonitor.suppressNextBackgroundLock()
    }

    fun navigateTo(route: AppRoute) {
        routeFlow.value = route
    }

    fun openModal(modal: ActiveModal) {
        activeModalFlow.value = modal
    }

    fun closeModal() {
        activeModalFlow.value = ActiveModal.None
        addTokenDraft.clear()
    }

    fun setActiveWorkspaceId(workspaceId: String) {
        activeWorkspaceIdFlow.value = workspaceId
        preferencesRepository.setLastActiveWorkspaceId(workspaceId)
    }

    fun setFlagSecureEnabled(enabled: Boolean) {
        flagSecureFlow.value = enabled
    }

    fun consumeSnackbar() {
        snackbarMessageFlow.value = null
    }

    fun showNotice(message: String) {
        snackbarMessageFlow.value = message
    }

    fun addSingleTokenDedup(token: AuthToken) {
        viewModelScope.launch {
            val (added, _) = tokenRepository.addTokensDedup(listOf(token))
            snackbarMessageFlow.value = if (added > 0) {
                "已添加：${token.issuer}"
            } else {
                "凭据已存在，已跳过重复导入"
            }
        }
    }

    fun addBatchTokensDedup(tokens: List<AuthToken>, batchNotice: String? = null) {
        viewModelScope.launch {
            val (added, skipped) = tokenRepository.addTokensDedup(tokens)
            val summary = if (skipped > 0) {
                "成功导入 $added 个凭据，跳过重复 $skipped 个"
            } else {
                "成功导入 $added 个凭据"
            }
            snackbarMessageFlow.value = if (!batchNotice.isNullOrBlank()) {
                "$batchNotice · $summary"
            } else {
                summary
            }
        }
    }

    fun executeSelfDestruct(onComplete: () -> Unit) {
        viewModelScope.launch {
            addTokenDraft.clear()
            tokenRepository.wipeInMemoryState()
            preferencesRepository.clearAllPreferences()
            ClipboardGuard.clearNow(getApplication())
            securityManager.executePermanentSelfDestruct(silent = false)
            isUnlockedFlow.value = false
            selfDestructedFlow.value = true
            _lockSessionCounter.update { it + 1L }
            onComplete()
        }
    }

    override fun onCleared() {
        addTokenDraft.clear()
        super.onCleared()
    }

    class Factory(
        private val application: Application,
        private val container: AppContainer
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AppViewModel(application, container) as T
        }
    }
}
