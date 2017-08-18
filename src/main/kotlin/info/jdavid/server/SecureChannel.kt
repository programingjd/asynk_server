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
  private val inPackets = node.inPackets
  private val outPackets = node.outPackets
  private val application = node.application
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
        outPackets.flip()
        val write = channel.aWrite(outPackets, writeDeadline - System.nanoTime(), TimeUnit.NANOSECONDS)
        println("${write} bytes written to buffer:\n" +
                  hex(outPackets, outPackets.position() - write, outPackets.position()))
        outPackets.compact()
      }
      SSLEngineResult.Status.BUFFER_UNDERFLOW -> {
        if (inPackets.position() == 0) inPackets.limit(inPackets.capacity()) else inPackets.compact()
        val read = channel.aRead(inPackets, readDeadline - System.nanoTime(), TimeUnit.NANOSECONDS)
        println("${read} bytes read from buffer:\n" +
                  hex(inPackets, inPackets.position() - read, inPackets.position()))
        inPackets.flip()
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
        println("Before wrap: ${application.limit() - application.position()}\n" +
                  hex(application, application.position(), application.limit()))
        application.flip()
        val result = engine.wrap(application, outPackets)
        application.compact()
        println("After wrap:\n ${application.limit() - application.position()}" +
                  hex(application, application.position(), application.limit()))
        handshake(channel, SSLEngineResult.Status.BUFFER_OVERFLOW, result.handshakeStatus, readDeadline, writeDeadline)
      }
      SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
        println("Before unwrap: ${inPackets.limit() - inPackets.position()}\n" +
                  hex(inPackets, inPackets.position(), inPackets.limit()))
        val result = engine.unwrap(inPackets, application)
        println("After unwrap: ${inPackets.limit() - inPackets.position()}\n" +
                  hex(inPackets, inPackets.position(), inPackets.limit()))
        handshake(channel, result.status, result.handshakeStatus, readDeadline, writeDeadline)
      }
      else -> throw IllegalArgumentException()
    }
  }

  suspend fun handshake(readDeadline: Long, writeDeadline: Long): SecureChannel {
    engine.beginHandshake()
    handshake(channel, null, engine.handshakeStatus, readDeadline, writeDeadline)
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
    internal val inPackets = ByteBuffer.allocateDirect(packetBufferSize).limit(0)
    internal val outPackets = ByteBuffer.allocateDirect(packetBufferSize)
    internal val application = ByteBuffer.allocateDirect(applicationBufferSize)
//    init {
//      println("[${counter.incrementAndGet()}]")
//    }
  }

}
