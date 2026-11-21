package com.f2a.authenticator.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import com.f2a.authenticator.crypto.BackupExportResult
import com.f2a.authenticator.crypto.BackupManager
import com.f2a.authenticator.crypto.SecretCipher
import com.f2a.authenticator.data.local.PreferencesStorage
import com.f2a.authenticator.data.local.TokenRepository
import com.f2a.authenticator.data.local.TokenVaultRepository
import com.f2a.authenticator.data.local.VaultDocumentStorage
import com.f2a.authenticator.data.local.VaultPreferencesRepository
import com.f2a.authenticator.data.local.VaultPreferencesStore
import com.f2a.authenticator.data.model.AuthToken
import com.f2a.authenticator.data.model.BackupPayload
import com.f2a.authenticator.data.model.SortMode
import com.f2a.authenticator.data.model.TokenType
import com.f2a.authenticator.data.model.Workspace
import com.f2a.authenticator.di.AppContainer
import com.f2a.authenticator.security.BackgroundLockMonitor
import com.f2a.authenticator.security.Clock
import com.f2a.authenticator.security.PinConfigResult
import com.f2a.authenticator.security.SecurityLockManager
import com.f2a.authenticator.ui.state.ActiveModal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelsTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setupMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    private class MutableClock(initialMs: Long = 1_700_000_000_000L) : Clock {
        val nowMs = AtomicLong(initialMs)
        val elapsedMs = AtomicLong(100_000L)
        override fun elapsedRealtimeMs(): Long = elapsedMs.get()
        override fun currentTimeMillis(): Long = nowMs.get()
    }

    private class InMemorySharedPrefs : SharedPreferences {
        private val data = LinkedHashMap<String, Any?>()
        override fun getAll(): MutableMap<String, *> = LinkedHashMap(data)
        override fun getString(key: String?, defValue: String?) = (data[key] as? String) ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?) =
            (data[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int) = (data[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long) = (data[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float) = (data[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean) = (data[key] as? Boolean) ?: defValue
        override fun contains(key: String?) = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = LinkedHashMap<String, Any?>()
            private var clearAll = false
            override fun putString(key: String?, value: String?) = apply { if (key != null) pending[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply {
                if (key != null) pending[key] = values?.toSet()
            }
            override fun putInt(key: String?, value: Int) = apply { if (key != null) pending[key] = value }
            override fun putLong(key: String?, value: Long) = apply { if (key != null) pending[key] = value }
            override fun putFloat(key: String?, value: Float) = apply { if (key != null) pending[key] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) pending[key] = value }
            override fun remove(key: String?) = apply { if (key != null) pending[key] = REMOVED }
            override fun clear() = apply { clearAll = true; pending.clear() }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearAll) data.clear()
                for ((k, v) in pending) {
                    if (v === REMOVED) data.remove(k) else data[k] = v
                }
            }
        }
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        companion object { val REMOVED = Any() }
    }

    private class FakeApplication(private val dir: File) : Application() {
        val prefsMap = LinkedHashMap<String, InMemorySharedPrefs>()
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
            prefsMap.getOrPut(name ?: "default") { InMemorySharedPrefs() }
        override fun deleteSharedPreferences(name: String?): Boolean {
            prefsMap.remove(name ?: "default")?.edit()?.clear()?.commit()
            return true
        }
        override fun getApplicationInfo(): ApplicationInfo = ApplicationInfo().apply {
            dataDir = dir.absolutePath
        }
        override fun getFilesDir(): File = File(dir, "files").apply { mkdirs() }
        override fun getSystemService(name: String): Any? = null
    }

    private class InMemoryDocStorage : VaultDocumentStorage {
        var json: String? = null
        override fun readDocumentJson(): String? = json
        override fun writeDocumentJson(json: String) { this.json = json }
        override fun hasLegacyTokens(): Boolean = false
        override fun readLegacySections() = null
        override fun clearLegacy() = Unit
    }

    private class InMemoryPrefStorage : PreferencesStorage {
        var json: String? = null
        override fun readJson(): String? = json
        override fun writeJson(json: String) { this.json = json }
        override fun clear() { this.json = null }
    }

    private class FakeCipher : SecretCipher {
        override fun encrypt(plainText: String) = SecretCipher.EncryptResult.Ok("ENC:$plainText")
        override fun decrypt(cipherTextBase64: String) = SecretCipher.DecryptResult.Ok(cipherTextBase64.removePrefix("ENC:"))
        override fun destroyMasterKey() = Unit
    }

    private fun createEnv(): Triple<FakeApplication, AppContainer, MutableClock> {
        val tempDir = Files.createTempDirectory("f2a-vm-test").toFile()
        tempDir.deleteOnExit()
        val app = FakeApplication(tempDir)
        val clock = MutableClock()
        val cipher = FakeCipher()
        val tokenRepo = TokenRepository(InMemoryDocStorage(), cipher, clock, testDispatcher, isDebugBuild = false)
        val prefRepo = VaultPreferencesStore(InMemoryPrefStorage())
        val secManager = SecurityLockManager(
            context = app,
            clock = clock,
            cipher = cipher,
            cryptoDispatcher = testDispatcher,
            ioDispatcher = testDispatcher
        ).apply {
            setOnBeforeDestructListener {
                tokenRepo.wipeInMemoryImmediate()
                prefRepo.clearAllPreferences()
            }
        }
        val monitor = BackgroundLockMonitor(clock)
        val container = object : AppContainer {
            override val clock: Clock = clock
            override val secretCipher: SecretCipher = cipher
            override val tokenRepository: TokenVaultRepository = tokenRepo
            override val preferencesRepository: VaultPreferencesRepository = prefRepo
            override val securityLockManager: SecurityLockManager = secManager
            override val backgroundLockMonitor: BackgroundLockMonitor = monitor
        }
        return Triple(app, container, clock)
    }

    @Test
    fun appViewModel_pinSetupVerifyDuressAndLockNow_fullLifecycle() = runTest(testDispatcher) {
        val (app, container, _) = createEnv()
        val vm = AppViewModel(app, container)
        val collectJob = launch { vm.uiState.collect {} }

        var setupResult: PinConfigResult? = null
        vm.setupPinAndUnlock("258013") { setupResult = it }
        advanceUntilIdle()
        assertEquals(PinConfigResult.Ok, setupResult)
        assertTrue(vm.uiState.value.authState.isUnlocked)
        assertTrue(container.tokenRepository.isLoaded.value)

        // 配置胁迫码并测试锁屏 -> 胁迫码进入清空态
        assertEquals(PinConfigResult.Ok, container.securityLockManager.setDuressPin("258013", "741852"))
        container.tokenRepository.addToken(
            AuthToken(id = "t1", issuer = "AWS", accountName = "root", secret = "JBSWY3DPEHPK3PXP")
        )
        container.preferencesRepository.setPrivacyMaskEnabled(true)

        vm.lockNow()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.authState.isUnlocked)
        assertEquals(1L, vm.lockSessionCounter.value)

        var verifyRes: SecurityLockManager.VerifyResult? = null
        vm.verifyPinAndUnlock("741852") { verifyRes = it }
        advanceUntilIdle()
        assertEquals(SecurityLockManager.VerifyResult.DuressTriggered, verifyRes)
        assertTrue(container.tokenRepository.tokens.value.isEmpty())
        assertTrue(container.tokenRepository.isWiped.value)
        assertFalse(container.tokenRepository.isLoaded.value)
        assertFalse(container.preferencesRepository.preferences.value.privacyMaskEnabled)

        // 伪装空库下 HomeViewModel.uiState.isLoaded 必须为 true，避免界面永久转圈
        val homeVm = HomeViewModel(
            tokenRepository = container.tokenRepository,
            preferencesRepository = container.preferencesRepository,
            securityManager = container.securityLockManager,
            computeDispatcher = testDispatcher
        )
        val homeJob = launch { homeVm.uiState.collect {} }
        val homeState = homeVm.uiState.first { it.isLoaded }
        assertTrue(homeState.isLoaded)
        assertTrue(homeState.issuerGroups.isEmpty())
        homeJob.cancel()

        collectJob.cancel()
        container.tokenRepository.close()
    }

    @Test
    fun homeViewModel_searchSortPrivacyMaskAndBackupOverdue_workCorrectly() = runTest(testDispatcher) {
        val (_, container, clock) = createEnv()
        container.tokenRepository.unlockAndLoad()
        container.tokenRepository.addTokens(
            listOf(
                AuthToken(
                    id = "t1",
                    issuer = "GitHub",
                    accountName = "alice",
                    secret = "JBSWY3DPEHPK3PXP",
                    lastUsedAt = 100L
                ),
                AuthToken(
                    id = "t2",
                    issuer = "AWS",
                    accountName = "root",
                    secret = "JBSWY3DPEHPK3PXP",
                    lastUsedAt = 500L
                )
            )
        )

        val vm = HomeViewModel(
            tokenRepository = container.tokenRepository,
            preferencesRepository = container.preferencesRepository,
            securityManager = container.securityLockManager,
            clock = clock,
            computeDispatcher = testDispatcher
        )
        val collectJob = launch { vm.uiState.collect {} }

        // 从未备份且有凭据 -> 提醒横幅开启
        val loadedState = vm.uiState.first { it.isLoaded && it.issuerGroups.size == 2 }
        assertTrue(loadedState.backupOverdueBanner)

        // 刚完成备份 -> 提醒横幅关闭
        container.preferencesRepository.recordBackupCompleted(clock.currentTimeMillis(), 2)
        val backedUpState = vm.uiState.first { !it.backupOverdueBanner }
        assertFalse(backedUpState.backupOverdueBanner)
        assertEquals(0, backedUpState.daysSinceLastBackup)

        // 隐私防窥切换
        vm.togglePrivacyMask()
        assertTrue(vm.uiState.first { it.privacyMaskEnabled }.privacyMaskEnabled)

        // RECENTLY_USED 按 lastUsedAt 排序（AWS 500 > GitHub 100）
        vm.setSortMode(SortMode.RECENTLY_USED)
        val sortedState = vm.uiState.first { it.sortMode == SortMode.RECENTLY_USED }
        assertEquals("AWS", sortedState.issuerGroups.first().issuer)

        // 搜索过滤 & clearSearch
        vm.setSearchQuery("github")
        val filteredState = vm.uiState.first { it.issuerGroups.size == 1 }
        assertEquals("GitHub", filteredState.issuerGroups.single().issuer)
        vm.clearSearch()
        assertEquals(2, vm.uiState.first { it.issuerGroups.size == 2 }.issuerGroups.size)

        collectJob.cancel()
        container.tokenRepository.close()
    }

    @Test
    fun backupViewModel_passphraseStrengthExternalImportAndSelfDestruct_workCorrectly() = runTest(testDispatcher) {
        val (app, container, _) = createEnv()
        container.tokenRepository.unlockAndLoad()
        container.securityLockManager.setMaxAttempts(3)
        container.securityLockManager.setupPin("258013")

        val vm = BackupViewModel(
            tokenRepository = container.tokenRepository,
            securityManager = container.securityLockManager,
            preferencesRepository = container.preferencesRepository,
            defaultDispatcher = testDispatcher,
            ioDispatcher = testDispatcher
        )

        assertEquals(0, vm.evaluatePassphraseStrength("1234567"))
        assertEquals(1, vm.evaluatePassphraseStrength("12345678"))
        assertEquals(2, vm.evaluatePassphraseStrength("Abcdef1234"))
        assertEquals(3, vm.evaluatePassphraseStrength("CorrectHorse#2026"))

        // 外部多行导入
        vm.importExternalText(
            "otpauth://totp/Cloudflare:ops@corp.io?secret=JBSWY3DPEHPK3PXP&issuer=Cloudflare",
            Workspace.DEFAULT_DEVOPS.id
        )
        advanceUntilIdle()
        assertEquals(1, container.tokenRepository.tokens.value.size)

        // 加密备份包导出与导入还原
        val payload = BackupPayload(
            workspaces = container.tokenRepository.workspaces.value,
            tags = container.tokenRepository.tags.value,
            tokens = container.tokenRepository.tokens.value
        )
        val exported = BackupManager.exportEncryptedBackup(payload, "CorrectHorse#2026")
        assertTrue(exported is BackupExportResult.Ok)
        container.tokenRepository.clearAllTokens()
        assertTrue(container.tokenRepository.tokens.value.isEmpty())

        vm.importFromString((exported as BackupExportResult.Ok).encodedBase64, "CorrectHorse#2026") {}
        advanceUntilIdle()
        assertEquals(1, container.tokenRepository.tokens.value.size)

        // 连续输错 3 次触发自毁事件
        vm.verifyPinForBatchExport("999001", app) {}
        vm.verifyPinForBatchExport("999002", app) {}
        var lastRes: SecurityLockManager.VerifyResult? = null
        vm.verifyPinForBatchExport("999003", app) { lastRes = it }
        advanceUntilIdle()
        assertEquals(SecurityLockManager.VerifyResult.SelfDestructed, lastRes)
        assertTrue(vm.selfDestructedEvent.value)
        assertTrue(container.tokenRepository.tokens.value.isEmpty())

        container.tokenRepository.close()
    }

    @Test
    fun settingsViewModel_generatesHealthReportAndWipesOnClearDuress() = runTest(testDispatcher) {
        val (app, container, _) = createEnv()
        container.tokenRepository.unlockAndLoad()
        container.securityLockManager.setupPin("258013", "741852")
        container.tokenRepository.addToken(
            AuthToken(id = "t1", issuer = "Google", accountName = "u@g.com", secret = "JBSWY3DPEHPK3PXP")
        )

        val vm = SettingsViewModel(app, container)
        val collectJob = launch { vm.uiState.collect {} }

        val state = vm.uiState.first { it.healthReport != null }
        assertNotNull(state.healthReport)
        assertTrue(state.hasDuressPin)

        // 在清空入口输入胁迫密码 -> 必须触发静默清空与偏好重置
        var clearRes: SecurityLockManager.VerifyResult? = null
        vm.verifyAndClearAllTokens("741852") { clearRes = it }
        advanceUntilIdle()
        assertEquals(SecurityLockManager.VerifyResult.DuressTriggered, clearRes)
        assertTrue(container.tokenRepository.tokens.value.isEmpty())
        assertFalse(vm.uiState.first { !it.hasDuressPin }.hasDuressPin)

        collectJob.cancel()
        container.tokenRepository.close()
    }

    @Test
    fun appViewModel_addTokenDraft_survivesInMemoryAndIsClearedOnCloseAndLock() = runTest(testDispatcher) {
        val (app, container, _) = createEnv()
        val vm = AppViewModel(app, container)
        val collectJob = launch { vm.uiState.collect {} }
        vm.setupPinAndUnlock("258013") {}
        advanceUntilIdle()

        fun fillDraft() {
            vm.openModal(ActiveModal.AddToken)
            vm.addTokenDraft.apply {
                rawUriOrKey = "JBSWY3DPEHPK3PXP"
                secret = "JBSWY3DPEHPK3PXP"
                issuer = "AWS"
                selectedWorkspaceId = "ws-1"
                selectedTagIds = listOf("tag-1")
                selectedType = TokenType.HOTP
                selectedCounter = 7L
                step = 2
            }
        }

        fun assertDraftEmpty() {
            vm.addTokenDraft.apply {
                assertEquals("", rawUriOrKey)
                assertEquals("", secret)
                assertEquals("", issuer)
                assertEquals(null, selectedWorkspaceId)
                assertTrue(selectedTagIds.isEmpty())
                assertEquals(TokenType.TOTP, selectedType)
                assertEquals(0L, selectedCounter)
                assertEquals(1, step)
            }
        }

        // 草稿由 ViewModel 持有：界面重建（旋转）后读到的仍是同一份输入
        fillDraft()
        assertEquals("JBSWY3DPEHPK3PXP", vm.addTokenDraft.rawUriOrKey)
        assertEquals(2, vm.addTokenDraft.step)

        vm.closeModal()
        assertDraftEmpty()

        fillDraft()
        vm.lockNow()
        advanceUntilIdle()
        assertEquals(ActiveModal.None, vm.uiState.value.activeModal)
        assertDraftEmpty()

        collectJob.cancel()
        container.tokenRepository.close()
    }
}
