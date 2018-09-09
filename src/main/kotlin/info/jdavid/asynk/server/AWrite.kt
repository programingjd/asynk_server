package info.jdavid.asynk.server

import kotlinx.coroutines.experimental.nio.aWrite
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.TimeUnit

internal object AWrite {

  suspend inline fun all(channel: AsynchronousSocketChannel, buffer: ByteBuffer,
                         timeout: Long = 0L, timeUnit: TimeUnit = TimeUnit.MILLISECONDS) {
    while (buffer.remaining() > 0) channel.aWrite(buffer, timeout, timeUnit)
  }

}
