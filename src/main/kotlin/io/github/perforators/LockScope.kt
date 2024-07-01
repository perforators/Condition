package io.github.perforators

import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentLinkedQueue

sealed interface LockScope {
    fun relatedTo(mutex: Mutex): Boolean
}

@InternalConditionApi
class ScopePool(
    private val capacity: Int = DEFAULT_POOL_CAPACITY
) {

    private val reusableScopes = ConcurrentLinkedQueue<ReusableScope>()

    fun poll(owner: Mutex): LockScope {
        return (reusableScopes.poll() ?: ReusableScope()).also { it.owner = owner }
    }

    fun offer(scope: LockScope) {
        if (scope !is ReusableScope || reusableScopes.size >= capacity) {
            return
        }
        scope.owner = null
        reusableScopes.offer(scope)
    }

    private class ReusableScope : LockScope {
        var owner: Mutex? = null
        override fun relatedTo(mutex: Mutex) = owner === mutex
    }

    companion object {
        private const val DEFAULT_POOL_CAPACITY = 128
    }
}

@InternalConditionApi
val globalScopePool = ScopePool()
