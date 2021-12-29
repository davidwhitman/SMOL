package utilities

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.ktx.Timber
import timber.ktx.d
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

val IOLock = ObservableReentrantReadWriteLock()

object IOLocks {
    val modFolderLock = ReentrantReadWriteLock()
    val stagingFolderLock = ReentrantReadWriteLock()
    val archivesFolderLock = ReentrantReadWriteLock()
    val modFilesLock = modFolderLock + stagingFolderLock + archivesFolderLock
    val configLock = ReentrantReadWriteLock()
    val gameMainFolderLock = ReentrantReadWriteLock()
    val defaultLock = configLock + modFilesLock
    val everythingLock = configLock + modFilesLock + gameMainFolderLock
}

data class LockContext(val locks: List<ReentrantReadWriteLock>)

operator fun ReentrantReadWriteLock.plus(lock: ReentrantReadWriteLock): LockContext =
    LockContext(listOf(this, lock))

operator fun LockContext.plus(lock: ReentrantReadWriteLock): LockContext =
    this.copy(locks = this.locks + lock)

operator fun ReentrantReadWriteLock.plus(lockContext: LockContext): LockContext =
    lockContext.copy(locks = this.asList() + lockContext.locks)

class ObservableReentrantReadWriteLock() {
    val flow = MutableStateFlow(false)
    val tag = "lock"

    val stateFlow: StateFlow<Boolean> = flow.asStateFlow()

    inline fun getCurrentThreadName() = Thread.currentThread().name ?: "unknown thread"

    /**
     * Executes the given [action] under the read lock of this lock.
     * @return the return value of the action.
     */
    inline fun <T> read(lock: ReentrantReadWriteLock, action: () -> T): T =
        read(LockContext(lock.asList()), action)

    /**
     * Executes the given [action] under the read lock of this lock.
     * @return the return value of the action.
     */
    inline fun <T> read(lockContext: LockContext = IOLocks.defaultLock, action: () -> T): T {
        lockContext.locks.forEach { lock ->
            if (lock === lockContext.locks.last()) {
                val result = try {
                    lock.read {
                        Timber.tag(tag)
                            .d { "Read locked from ${timber.Timber.findClassName()} on ${getCurrentThreadName()}." }
                        return@read try {
                            action.invoke()
                        } finally {
                            Timber.tag(tag)
                                .d { "Read unlocked from ${timber.Timber.findClassName()} on ${getCurrentThreadName()}." }
                        }
                    }
                } finally {
                    lockContext.locks.dropLast(1).forEach {
                        Timber.v { "Unlocking $it" }
                        it.readLock().unlock()
                    }
                }
                return result
            } else {
                Timber.v { "Locking $lock" }
                lock.readLock().lock()
            }
        }

        // If no locks, run func and return result.
        return action.invoke()
    }

    inline fun <T> write(lock: ReentrantReadWriteLock, action: () -> T): T = write(
        lockContext = LockContext(lock.asList()),
        action = action
    )

    /**
     * Executes the given [action] under the write lock of this lock.
     *
     * The function does upgrade from read to write lock if needed, but this upgrade is not atomic
     * as such upgrade is not supported by [ReentrantReadWriteLock].
     * In order to do such upgrade this function first releases all read locks held by this thread,
     * then acquires write lock, and after releasing it acquires read locks back again.
     *
     * Therefore if the [action] inside write lock has been initiated by checking some condition,
     * the condition must be rechecked inside the [action] to avoid possible races.
     *
     * @return the return value of the action.
     */
    inline fun <T> write(lockContext: LockContext = IOLocks.defaultLock, action: () -> T): T {
        lockContext.locks.forEach { lock ->
            if (lock === lockContext.locks.last()) {
                val result = try {
                    lock.write {
                        Timber.tag(tag)
                            .d { "Write locked from ${timber.Timber.findClassName()} on ${getCurrentThreadName()}." }
                        flow.tryEmit(true)
                        return@write try {
                            action()
                        } finally {
                            Timber.tag(tag)
                                .d { "Write unlocked from ${timber.Timber.findClassName()} on ${getCurrentThreadName()}." }
                            flow.tryEmit(false)
                        }
                    }
                } finally {
                    lockContext.locks.dropLast(1).forEach { it.writeLock().unlock() }
                }
                return result
            } else {
                lock.writeLock().lock()
            }
        }

        // If no locks, run func and return result.
        return action.invoke()
    }
}

class ObservableLock(private val lock: Lock) : Lock by lock {
    private val flow = MutableStateFlow(false)

    val stateFlow: StateFlow<Boolean> = flow.asStateFlow()

    override fun lock() {
        lock.lock()
        Timber.v { "Locked" }
        flow.tryEmit(true)
    }

    override fun lockInterruptibly() {
        lock.lockInterruptibly()
        Timber.v { "Locked" }
        flow.tryEmit(true)
    }

    override fun tryLock(): Boolean {
        val tryLock = lock.tryLock()

        if (tryLock) {
            Timber.v { "Locked" }
            flow.tryEmit(true)
        }

        return tryLock
    }

    override fun tryLock(time: Long, unit: TimeUnit): Boolean {
        val tryLock = lock.tryLock(time, unit)

        if (tryLock) {
            Timber.v { "Locked" }
            flow.tryEmit(true)
        }

        return tryLock
    }

    override fun unlock() {
        lock.unlock()
        Timber.v { "Unlocked" }
        flow.tryEmit(false)
    }
}