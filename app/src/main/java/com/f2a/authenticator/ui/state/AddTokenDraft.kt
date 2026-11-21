package com.f2a.authenticator.ui.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.f2a.authenticator.data.model.TokenType

/**
 * 添加凭据弹窗的输入草稿。
 *
 * 【安全红线】只驻留在 ViewModel 内存中，使旋转屏幕等配置变更不丢输入；
 * 严禁写入 SavedStateHandle / Bundle / 磁盘。弹窗关闭、锁定、自毁时必须 [clear]。
 */
@Stable
class AddTokenDraft {
    var rawUriOrKey by mutableStateOf("")
    var issuer by mutableStateOf("")
    var accountName by mutableStateOf("")
    var tenantEnv by mutableStateOf("")
    var secret by mutableStateOf("")
    var note by mutableStateOf("")

    /** null 表示用户尚未手动选择，沿用弹窗的默认空间。 */
    var selectedWorkspaceId by mutableStateOf<String?>(null)
    var selectedTagIds by mutableStateOf(listOf<String>())
    var isPinned by mutableStateOf(false)

    var selectedAlgorithm by mutableStateOf(DEFAULT_ALGORITHM)
    var selectedDigits by mutableIntStateOf(DEFAULT_DIGITS)
    var selectedPeriod by mutableIntStateOf(DEFAULT_PERIOD)
    var selectedCounter by mutableLongStateOf(0L)
    var selectedType by mutableStateOf(TokenType.TOTP)

    var step by mutableIntStateOf(1)
    var showAdvanced by mutableStateOf(false)

    fun clear() {
        rawUriOrKey = ""
        issuer = ""
        accountName = ""
        tenantEnv = ""
        secret = ""
        note = ""
        selectedWorkspaceId = null
        selectedTagIds = emptyList()
        isPinned = false
        selectedAlgorithm = DEFAULT_ALGORITHM
        selectedDigits = DEFAULT_DIGITS
        selectedPeriod = DEFAULT_PERIOD
        selectedCounter = 0L
        selectedType = TokenType.TOTP
        step = 1
        showAdvanced = false
    }

    private companion object {
        const val DEFAULT_ALGORITHM = "SHA1"
        const val DEFAULT_DIGITS = 6
        const val DEFAULT_PERIOD = 30
    }
}
