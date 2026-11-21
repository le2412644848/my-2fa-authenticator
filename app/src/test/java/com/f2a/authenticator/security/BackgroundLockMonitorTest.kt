package com.f2a.authenticator.security

import android.app.Activity
import com.f2a.authenticator.data.model.AutoLockTimeout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundLockMonitorTest {

    private class MutableClock(var elapsed: Long = 10_000L) : Clock {
        override fun elapsedRealtimeMs(): Long = elapsed
        override fun currentTimeMillis(): Long = 1_700_000_000_000L + elapsed
    }

    @Test
    fun suppressNextBackgroundLock_clearedOnResumeAndExpiresAfterWindow() {
        val clock = MutableClock(10_000L)
        val monitor = BackgroundLockMonitor(clock)
        val activity = Activity()

        monitor.onActivityStarted(activity)

        // 1. 若外部选择器以半屏/浮层展示（未触发 onStop）并在回到前台时触发 onResume，豁免标记必须清除
        monitor.suppressNextBackgroundLock()
        monitor.onActivityResumed(activity)
        clock.elapsed += 1_000L
        monitor.onActivityStopped(activity)
        clock.elapsed += 35_000L
        assertTrue(monitor.checkAndConsumeShouldLock(AutoLockTimeout.SECONDS_30))

        // 2. 若超过 5s 豁免窗口仍未发生后台切出，之后真实切出仍应正常触发超时锁
        monitor.onActivityStarted(activity)
        monitor.suppressNextBackgroundLock()
        clock.elapsed += 6_000L
        monitor.onActivityStopped(activity)
        clock.elapsed += 35_000L
        assertTrue(monitor.checkAndConsumeShouldLock(AutoLockTimeout.SECONDS_30))

        // 3. 5s 窗口内正常跳转到系统相册/SAF（触发 onStop），60s 内返回不应锁屏
        monitor.onActivityStarted(activity)
        monitor.suppressNextBackgroundLock()
        clock.elapsed += 200L
        monitor.onActivityStopped(activity)
        clock.elapsed += 60_000L
        assertFalse(monitor.checkAndConsumeShouldLock(AutoLockTimeout.IMMEDIATE))
    }

    @Test
    fun suppressNextBackgroundLock_enforces90sMaxExemptionCap() {
        val clock = MutableClock(10_000L)
        val monitor = BackgroundLockMonitor(clock)
        val activity = Activity()

        monitor.onActivityStarted(activity)
        monitor.suppressNextBackgroundLock()
        clock.elapsed += 200L
        monitor.onActivityStopped(activity)

        // 超过 90 秒豁免上限后强制锁定
        clock.elapsed += BackgroundLockMonitor.MAX_SUPPRESS_DURATION_MS
        assertTrue(monitor.checkAndConsumeShouldLock(AutoLockTimeout.IMMEDIATE))
    }

    @Test
    fun immediateTimeout_invokesLockHandlerImmediatelyOnStop() {
        val clock = MutableClock(10_000L)
        val monitor = BackgroundLockMonitor(clock)
        val activity = Activity()
        var lockCount = 0

        monitor.setLockHandler(
            timeoutProvider = { AutoLockTimeout.IMMEDIATE },
            onLock = { lockCount++ }
        )

        monitor.onActivityStarted(activity)
        monitor.onActivityStopped(activity)
        assertEquals(1, lockCount)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun timedTimeout_locksOnTimerDispatcherOnlyWhileStillInBackground() {
        val scheduler = TestCoroutineScheduler()
        val clock = MutableClock(10_000L)
        val monitor = BackgroundLockMonitor(clock, StandardTestDispatcher(scheduler))
        val activity = Activity()
        val timeout = AutoLockTimeout.entries.first {
            it != AutoLockTimeout.NEVER && it != AutoLockTimeout.IMMEDIATE
        }
        val timeoutMs = timeout.seconds * 1000L
        var lockCount = 0

        monitor.setLockHandler(
            timeoutProvider = { timeout },
            onLock = { lockCount++ }
        )

        monitor.onActivityStarted(activity)
        monitor.onActivityStopped(activity)
        scheduler.advanceTimeBy(timeoutMs - 1)
        scheduler.runCurrent()
        assertEquals(0, lockCount)
        scheduler.advanceTimeBy(1)
        scheduler.runCurrent()
        assertEquals(1, lockCount)

        monitor.onActivityStarted(activity)
        monitor.onActivityStopped(activity)
        scheduler.advanceTimeBy(timeoutMs - 1)
        monitor.onActivityStarted(activity)
        scheduler.advanceUntilIdle()
        assertEquals(1, lockCount)
    }
}
