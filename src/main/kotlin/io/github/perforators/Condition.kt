package io.github.perforators

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.sync.Mutex

interface Condition {

    /**
     * Causes the current coroutine to suspend until it is signalled or cancelled.
     * It can only be called inside [withScopedLock].
     *
     * **Important:** When returning from this method due to the cancellation of the coroutine,
     * there is a chance that the lock may be in an unlocked state.
     */
    suspend fun LockScope.await()

    /**
     * Wakes up one waiting coroutine.
     *
     * If any coroutines are waiting on this condition then one is selected for waking up.
     * That coroutine must then re-acquire the lock before returning from [await].
     */
    fun signal()

    /**
     * Wakes up all waiting coroutines.
     *
     * If any coroutines are waiting on this condition then they are all woken up.
     * Each coroutine must re-acquire the lock before it can return from await.
     */
    fun signalAll()
}

/**
 * Returns a new [Condition] instance that is bound to this [Mutex] instance.
 *
 * Before waiting on the condition the lock must be held by the current coroutine.
 * A call to [Condition.await] will atomically release the lock before waiting and re-acquire
 * the lock before the wait returns.
 *
 * @return A new [Condition] instance for this [Mutex] instance.
 */
fun Mutex.newCondition(): Condition = ConditionImpl(this)

private class ConditionImpl(
    private val owner: Mutex
) : Condition {

    private val signals = Channel<Unit>(
        capacity = Channel.RENDEZVOUS,
        onBufferOverflow = BufferOverflow.SUSPEND
    )
    private val numberWaits = atomic(0)

    override suspend fun LockScope.await() {
        require(relatedTo(owner)) {
            "await() must be call in the scope of the mutex, that owns the condition!"
        }
        owner.unlock(this@await)
        try {
            numberWaits.incrementAndGet()
            signals.receive()
        } finally {
            numberWaits.decrementAndGet()
        }
        owner.lock(this@await)
    }

    override fun signal() {
        signals.trySend(Unit)
    }

    override fun signalAll() {
        repeat(numberWaits.value) {
            signals.trySend(Unit).onFailure { return }
        }
    }
}
