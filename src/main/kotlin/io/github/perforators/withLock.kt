package io.github.perforators

import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

private class LockScopeImpl : LockScope {
    @Volatile var owner: Mutex? = null
    override fun relatedTo(mutex: Mutex) = owner == mutex
}

private val reusableScopes = ConcurrentLinkedQueue<LockScopeImpl>()
private const val MAX_POOL_SIZE = 128

/**
 * Executes the given [action] under this mutex's lock.
 * Allows you to call [Condition.await].
 *
 * @return the return value of the action.
 */
@OptIn(ExperimentalContracts::class)
suspend fun Mutex.withScopedLock(action: suspend LockScope.() -> Unit) {
    contract {
        callsInPlace(action, InvocationKind.EXACTLY_ONCE)
    }
    val scope = reusableScopes.poll() ?: LockScopeImpl()
    scope.owner = this
    lock(scope)
    return try {
        scope.action()
    } finally {
        if (holdsLock(scope)) {
            unlock(scope)
        }
        if (reusableScopes.size < MAX_POOL_SIZE) {
            scope.owner = null
            reusableScopes.offer(scope)
        }
    }
}
