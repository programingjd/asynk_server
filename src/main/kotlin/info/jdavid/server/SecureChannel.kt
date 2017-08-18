package info.jdavid.server

import kotlinx.coroutines.experimental.internal.LockFreeLinkedListHead
import kotlinx.coroutines.experimental.internal.LockFreeLinkedListNode
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult

internal class SecureChannel(private val channel: AsynchronousSocketChannel,
                             private val engine: SSLEngine,
                             private val nodes: LockFreeLinkedListHead,
                             maxRequestSize: Int): Channel() {
  private val node = nodes.removeFirstOrNull() as? Node ?: Node(16384, maxRequestSize,
                                                                Math.max(
                                                                  engine.session.packetBufferSize,
                                                                  engine.session.applicationBufferSize))
  private val segment = node.segment
  private val buffer = node.buffer
  private val buffer1 = node.engine1
  private val buffer2 = node.engine2
  private var exhausted = false

  private val HEX_DIGITS = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                                       'a', 'b', 'c', 'd', 'e', 'f')

  private fun hex(byteBuffer: ByteBuffer, from: Int, to: Int): String {
    val n = to - from
    val result = CharArray(3 * n)
    for (i in 0 until n) {
      result[3*i] = HEX_DIGITS[byteBuffer[from + i].toInt().shr(4).and(0x0f)]
      result[3*i+1] = HEX_DIGITS[byteBuffer[from + i].toInt().and(0x0f)]
      result[3*i+2] = if ((i+1)%40 == 0) '\n' else ' '
    }
    return String(result)
  }

  private suspend fun handshake(channel: AsynchronousSocketChannel,
                                status: SSLEngineResult.Status?,
                                handshakeStatus: SSLEngineResult.HandshakeStatus,
                                readDeadline: Long, writeDeadline: Long) {
    when(status) {
      SSLEngineResult.Status.BUFFER_OVERFLOW -> {
        buffer2.flip()
        val write = channel.aWrite(buffer2, writeDeadline - System.nanoTime(), TimeUnit.NANOSECONDS)
        if (write == -1) throw IOException()
        if (buffer2.position() == 0) buffer2.limit(buffer2.capacity()) else buffer2.compact()
      }
      SSLEngineResult.Status.BUFFER_UNDERFLOW -> {
        if (buffer1.position() == 0) buffer1.limit(buffer1.capacity()) else buffer1.compact()
        val read = channel.aRead(buffer1, readDeadline - System.nanoTime(), TimeUnit.NANOSECONDS)
        if (read == -1) throw IOException()
        buffer1.flip()
      }
      else -> {}
    }
    when(handshakeStatus) {
      SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING -> return
      SSLEngineResult.HandshakeStatus.FINISHED -> return
      SSLEngineResult.HandshakeStatus.NEED_TASK -> {
        engine.delegatedTask?.run()
        handshake(channel, null, engine.handshakeStatus, readDeadline, writeDeadline)
      }
      SSLEngineResult.HandshakeStatus.NEED_WRAP -> {
        segment.flip()
        val result = engine.wrap(segment, buffer2)
        if (segment.position() == 0) segment.limit(segment.capacity()) else segment.compact()
        handshake(channel, SSLEngineResult.Status.BUFFER_OVERFLOW, result.handshakeStatus, readDeadline, writeDeadline)
      }
      SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
        val result = engine.unwrap(buffer1, segment)
        handshake(channel, result.status, result.handshakeStatus, readDeadline, writeDeadline)
      }
      else -> throw IllegalArgumentException()
    }
  }

  suspend override fun start(readDeadline: Long, writeDeadline: Long) {
    engine.beginHandshake()
    handshake(channel, null, engine.handshakeStatus, readDeadline, writeDeadline)
    println("handshake done")
  }

  suspend override fun stop(readDeadline: Long, writeDeadline: Long) {
    engine.closeOutbound()
    segment.rewind().limit(0)
    handshake(channel, null, SSLEngineResult.HandshakeStatus.NEED_WRAP, readDeadline, writeDeadline)
    engine.closeInbound()
    handshake(channel, null, SSLEngineResult.HandshakeStatus.NEED_WRAP, readDeadline, writeDeadline)
  }

  override fun next() {
    buffer.rewind().limit(buffer.capacity())
  }

  override fun recycle() {
    nodes.addLast(node)
  }

  override fun buffer() = buffer

  override fun segment() = segment

  suspend override fun read(deadline: Long) = read(16384, deadline)

  suspend override fun read(bytes: Int, deadline: Long): ByteBuffer {
    TODO()
  }

  suspend override fun write(byteBuffer: ByteBuffer, deadline: Long) {
    TODO()
  }

  private class Node(segmentSize: Int, bufferSize: Int, engineBufferSize: Int): LockFreeLinkedListNode() {
    internal val segment = ByteBuffer.allocateDirect(segmentSize)
    internal val buffer = ByteBuffer.allocateDirect(bufferSize)
    internal val engine1 = ByteBuffer.allocateDirect(engineBufferSize).limit(0)
    internal val engine2 = ByteBuffer.allocateDirect(engineBufferSize)
//    init {
//      println("[${counter.incrementAndGet()}]")
//    }
  }

}
