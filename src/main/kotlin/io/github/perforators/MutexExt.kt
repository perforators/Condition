package io.github.perforators

import kotlinx.coroutines.sync.Mutex
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Executes the given [action] under this mutex's lock.
 * Allows you to call [Condition.await].
 *
 * @return the return value of the action.
 */
@OptIn(ExperimentalContracts::class)
suspend fun <T> Mutex.withScopedLock(action: suspend LockScope.() -> T): T {
    contract {
        callsInPlace(action, InvocationKind.EXACTLY_ONCE)
    }
    val scope = scopePool.poll(this)
    lock(scope)
    return try {
        scope.action()
    } finally {
        if (holdsLock(scope)) {
            unlock(scope)
        }
        scopePool.offer(scope)
    }
}
