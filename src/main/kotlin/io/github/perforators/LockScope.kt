package io.github.perforators

import kotlinx.coroutines.sync.Mutex

sealed interface LockScope {
    fun relatedTo(mutex: Mutex): Boolean
}
