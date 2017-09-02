package info.jdavid.server.http.http11

import info.jdavid.server.Connection
import kotlinx.coroutines.experimental.internal.LockFreeLinkedListHead

class Http11Connection(bufferPool: LockFreeLinkedListHead,
                       maxRequestSize: Int): Connection(bufferPool, maxRequestSize)
