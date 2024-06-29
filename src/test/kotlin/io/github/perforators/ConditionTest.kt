package io.github.perforators

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ConditionTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `signalAll() must wake up all suspended coroutines`() = runTest {
        val mutex = Mutex()
        val condition = mutex.newCondition()

        var wakeUpCount = 0
        repeat(10) {
            launch {
                mutex.withScopedLock {
                    with(condition) { await() }
                    wakeUpCount++
                }
            }
        }
        advanceUntilIdle()

        condition.signalAll()

        advanceUntilIdle()

        assertEquals(10, wakeUpCount)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `coroutines must wake up in the order of falling asleep`() = runTest {
        val mutex = Mutex()
        val condition = mutex.newCondition()

        val wakeUpOrder = mutableListOf<Int>()
        repeat(10) {
            launch {
                mutex.withScopedLock {
                    with(condition) { await() }
                    wakeUpOrder.add(it)
                }
            }
        }
        advanceUntilIdle()

        repeat(10) {
            launch {
                condition.signal()
            }
        }
        advanceUntilIdle()

        assertEquals((0 until 10).toList(), wakeUpOrder)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `await() must suspend coroutine until call signal()`() = runTest {
        val mutex = Mutex()
        val condition = mutex.newCondition()

        var conditionAwaitSuccess = false
        launch {
            mutex.withScopedLock {
                with(condition) { await() }
                conditionAwaitSuccess = true
            }
        }
        advanceUntilIdle()

        launch {
            condition.signal()
        }
        advanceUntilIdle()

        assertEquals(true, conditionAwaitSuccess)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `signal() has no effect if there are no suspending coroutines`() = runTest {
        val mutex = Mutex()
        val condition = mutex.newCondition()

        launch {
            condition.signal()
        }
        advanceUntilIdle()

        var conditionAwaitSuccess = false
        val job = launch {
            mutex.withScopedLock {
                with(condition) { await() }
                conditionAwaitSuccess = true
            }
        }
        advanceUntilIdle()

        assertEquals(false, conditionAwaitSuccess)

        job.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `when canceling, await() must not release a mutex held by another coroutine`() = runTest {
        val mutex = Mutex()
        val condition = mutex.newCondition()

        val awaitCondition = launch {
            mutex.withScopedLock {
                with(condition) { await() }
            }
        }
        advanceUntilIdle()

        val infiniteLock = launch {
            mutex.withLock {
                awaitCancellation()
            }
        }
        advanceUntilIdle()

        awaitCondition.cancel()

        assertEquals(true, mutex.isLocked)

        infiniteLock.cancel()
    }
}
