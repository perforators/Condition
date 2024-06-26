package io.github.perforators

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex

interface Condition {

    /**
     * Causes the current coroutine to suspend until it is signalled or cancelled.
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
}

fun Mutex.newCondition(): Condition = ConditionImpl(this)

private class ConditionImpl(
    private val owner: Mutex
) : Condition {

    private val signals = Channel<Unit>()

    override suspend fun LockScope.await() {
        require(relatedTo(owner)) {
            "await() must be call in the scope of the mutex, that owns the condition."
        }
        owner.unlock(this@await)
        signals.receive()
        owner.lock(this@await)
    }

    override fun signal() {
        signals.trySend(Unit)
    }
}
