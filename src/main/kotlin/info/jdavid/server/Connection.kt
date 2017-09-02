package info.jdavid.server

import kotlinx.coroutines.experimental.internal.LockFreeLinkedListHead

abstract class Connection(protected val buffers: LockFreeLinkedListHead) {

  suspend abstract fun close()

  abstract internal fun next()

  abstract internal fun recycle()

}
