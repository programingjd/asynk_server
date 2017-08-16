package info.jdavid.server

import kotlinx.coroutines.experimental.internal.LockFreeLinkedListHead
import kotlinx.coroutines.experimental.internal.LockFreeLinkedListNode
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLHandshakeException

internal class SecureChannel(private val channel: AsynchronousSocketChannel,
                             private val engine: SSLEngine,
                             private val nodes: LockFreeLinkedListHead,
                             maxRequestSize: Int): Channel() {
  private val node = nodes.removeFirstOrNull() as? Node ?: Node(16384,
                                                                engine.session.packetBufferSize,
                                                                engine.session.applicationBufferSize,
                                                                maxRequestSize)
  private val segment = node.segment
  private val buffer = node.buffer
  private val packet = node.packet
  private val application = node.application
  private var exhausted = false

  private suspend fun handshake(channel: AsynchronousSocketChannel,
                                status: SSLEngineResult.HandshakeStatus,
                                readDeadline: Long, writeDeadline: Long) {
    when(status) {
      SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING -> false
      SSLEngineResult.HandshakeStatus.FINISHED -> false
      SSLEngineResult.HandshakeStatus.NEED_TASK -> {
        engine.delegatedTask?.run()
        handshake(channel, engine.handshakeStatus, readDeadline, writeDeadline)
      }
      SSLEngineResult.HandshakeStatus.NEED_WRAP -> {
        val s = engine.wrap(application, packet).handshakeStatus
        packet.flip()
        channel.aWrite(packet, writeDeadline - System.nanoTime(), TimeUnit.NANOSECONDS)
//        channel.write(packet).get()
        handshake(channel, s, readDeadline, writeDeadline)
      }
      SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
        channel.aRead(packet, readDeadline - System.nanoTime(), TimeUnit.NANOSECONDS)
//        channel.read(packet).get()
        packet.flip()
        val s = engine.unwrap(packet, application).handshakeStatus
        packet.compact()
        handshake(channel, s, readDeadline, writeDeadline)
      }
      else -> throw IllegalArgumentException()
    }
  }

  suspend fun handshake(readDeadline: Long, writeDeadline: Long): SecureChannel {
    engine.beginHandshake()
    handshake(channel, engine.handshakeStatus, readDeadline, writeDeadline)
    println("handshake done")
    return this
  }

  override fun next() {
    buffer.rewind().limit(buffer.capacity())
  }

  override fun done() {
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

  private class Node(segmentSize: Int,
                     packetBufferSize: Int, applicationBufferSize: Int,
                     bufferSize: Int): LockFreeLinkedListNode() {
    internal val segment = ByteBuffer.allocateDirect(segmentSize)
    internal val buffer = ByteBuffer.allocateDirect(bufferSize)
    internal val packet = ByteBuffer.allocateDirect(packetBufferSize)
    internal val application = ByteBuffer.allocateDirect(applicationBufferSize)
//    init {
//      println("[${counter.incrementAndGet()}]")
//    }
  }

}
