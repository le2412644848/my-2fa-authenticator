package com.f2a.authenticator.security

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.f2a.authenticator.data.model.AutoLockTimeout
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 全部状态仅在主线程读写：生命周期回调由系统在主线程派发，延时锁定也必须在同一线程执行。
 */
class BackgroundLockMonitor(
    private val clock: Clock = SystemClockImpl,
    private val timerDispatcher: CoroutineDispatcher = Dispatchers.Main
) : Application.ActivityLifecycleCallbacks {

    private var startedActivityCount = 0
    private var backgroundedAtElapsedMs: Long? = null
    private var suppressUntilElapsedMs: Long = 0L
    private var suppressedBackgroundDeadlineMs: Long? = null

    private var timeoutProvider: (() -> AutoLockTimeout)? = null
    private var onBackgroundLock: (() -> Unit)? = null
    private var timerScope: CoroutineScope? = null
    private var lockTimerJob: Job? = null

    /**
     * 绑定后台超时自动锁回调（供 AppContainer / AppViewModel 在后台超期时主动清理内存明文）。
     */
    fun setLockHandler(
        timeoutProvider: () -> AutoLockTimeout,
        onLock: () -> Unit
    ) {
        this.timeoutProvider = timeoutProvider
        this.onBackgroundLock = onLock
        if (timerScope == null) {
            timerScope = CoroutineScope(SupervisorJob() + timerDispatcher)
        }
    }

    /**
     * 外部选择器（相册/SAF/BiometricPrompt）跳转前调用，豁免本次后台切出计入自动锁定：
     * - 5 秒跳转时间窗（避免半屏选择器未触发 onStop 污染后续切后台）
     * - 切出后最长豁免 90 秒，超期强制锁定
     */
    fun suppressNextBackgroundLock() {
        suppressUntilElapsedMs = clock.elapsedRealtimeMs() + SUPPRESS_WINDOW_MS
    }

    /**
     * 切回前台时判定是否已超过设定延时；若超时则返回 true 并消费本次标记。
     */
    fun checkAndConsumeShouldLock(timeout: AutoLockTimeout): Boolean {
        cancelTimerLocked()
        val suppressDeadline = suppressedBackgroundDeadlineMs
        suppressedBackgroundDeadlineMs = null
        val leftAt = backgroundedAtElapsedMs
        backgroundedAtElapsedMs = null

        if (timeout == AutoLockTimeout.NEVER) return false

        if (suppressDeadline != null) {
            return clock.elapsedRealtimeMs() >= suppressDeadline
        }

        if (leftAt == null) return false
        val elapsedInBackgroundMs = (clock.elapsedRealtimeMs() - leftAt).coerceAtLeast(0L)
        return elapsedInBackgroundMs >= timeout.seconds * 1000L
    }

    fun resetBackgroundTimer() {
        cancelTimerLocked()
        backgroundedAtElapsedMs = null
        suppressUntilElapsedMs = 0L
        suppressedBackgroundDeadlineMs = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount++
        if (startedActivityCount > 0) {
            cancelTimerLocked()
        }
    }

    override fun onActivityResumed(activity: Activity) {
        // 若外部选择器以半屏/浮层形式运行而未触发 onActivityStopped，恢复前台时立即回收跳转豁免窗口
        suppressUntilElapsedMs = 0L
    }

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
        // 配置变更（旋转/深浅色切换）触发的重建不计入后台切出
        if (startedActivityCount == 0 && !activity.isChangingConfigurations) {
            val nowElapsed = clock.elapsedRealtimeMs()
            val timeout = timeoutProvider?.invoke()
            if (nowElapsed <= suppressUntilElapsedMs) {
                suppressUntilElapsedMs = 0L
                backgroundedAtElapsedMs = null
                suppressedBackgroundDeadlineMs = nowElapsed + MAX_SUPPRESS_DURATION_MS
                if (timeout != null && timeout != AutoLockTimeout.NEVER) {
                    scheduleBackgroundLock(MAX_SUPPRESS_DURATION_MS)
                }
            } else {
                suppressUntilElapsedMs = 0L
                suppressedBackgroundDeadlineMs = null
                backgroundedAtElapsedMs = nowElapsed
                when (timeout) {
                    null, AutoLockTimeout.NEVER -> cancelTimerLocked()
                    AutoLockTimeout.IMMEDIATE -> {
                        cancelTimerLocked()
                        onBackgroundLock?.invoke()
                    }
                    else -> scheduleBackgroundLock(timeout.seconds * 1000L)
                }
            }
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit

    private fun scheduleBackgroundLock(delayMs: Long) {
        val callback = onBackgroundLock ?: return
        val scope = timerScope ?: return
        cancelTimerLocked()
        lockTimerJob = scope.launch {
            delay(delayMs.coerceAtLeast(0L))
            if (startedActivityCount == 0) {
                callback.invoke()
            }
        }
    }

    private fun cancelTimerLocked() {
        lockTimerJob?.cancel()
        lockTimerJob = null
    }

    companion object {
        const val SUPPRESS_WINDOW_MS = 5_000L
        const val MAX_SUPPRESS_DURATION_MS = 90_000L
    }
}
