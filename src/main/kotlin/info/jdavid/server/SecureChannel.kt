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
  private val segmentR = node.segmentR
  private val segmentW = node.segmentW
  private val segment = node.segment
  private val buffer = node.buffer
  private val wireIn = node.wireIn
  private val wireOut = node.wireOut
  private val appIn = node.appIn
  private val appOut = node.appOut

  fun applicationProtocol() = engine.applicationProtocol

  suspend private fun handshake(channel: AsynchronousSocketChannel,
                                status: SSLEngineResult.Status?,
                                handshakeStatus: SSLEngineResult.HandshakeStatus,
                                readDeadline: Long, writeDeadline: Long) {
    when(status) {
      SSLEngineResult.Status.BUFFER_OVERFLOW -> {
        wireOut.flip()
        val write = channel.aWrite(wireOut, writeDeadline - System.nanoTime(), TimeUnit.NANOSECONDS)
        if (write == -1) throw IOException()
        if (wireOut.position() == 0) wireOut.limit(wireOut.capacity()) else wireOut.compact()
      }
      SSLEngineResult.Status.BUFFER_UNDERFLOW -> {
        if (wireIn.position() == 0) wireIn.limit(wireIn.capacity()) else wireIn.compact()
        val read = channel.aRead(wireIn, readDeadline - System.nanoTime(), TimeUnit.NANOSECONDS)
        if (read == -1) throw IOException()
        wireIn.flip()
      }
      else -> {}
    }
    when(handshakeStatus) {
      SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING -> {
        if (wireIn.remaining() > 0) {
          val result = engine.unwrap(wireIn, appIn)
          handshake(channel, result.status, result.handshakeStatus, readDeadline, writeDeadline)
        }
        if (appOut.position() > 0) {
          appOut.flip()
          val result = engine.wrap(appOut, wireOut)
          if (appOut.position() == 0) appOut.limit(appOut.capacity()) else appOut.compact()
          handshake(channel, SSLEngineResult.Status.BUFFER_OVERFLOW, result.handshakeStatus,
                    readDeadline, writeDeadline)
        }
      }
      SSLEngineResult.HandshakeStatus.FINISHED -> return
      SSLEngineResult.HandshakeStatus.NEED_TASK -> {
        engine.delegatedTask?.run()
        handshake(channel, null, engine.handshakeStatus, readDeadline, writeDeadline)
      }
      SSLEngineResult.HandshakeStatus.NEED_WRAP -> {
        appOut.flip()
        val result = engine.wrap(appOut, wireOut)
        if (appOut.position() == 0) appOut.limit(appOut.capacity()) else appOut.compact()
        handshake(channel, SSLEngineResult.Status.BUFFER_OVERFLOW, result.handshakeStatus,
                  readDeadline, writeDeadline)
      }
      SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
        val result = engine.unwrap(wireIn, appIn)
        handshake(channel, result.status, result.handshakeStatus, readDeadline, writeDeadline)
      }
      else -> throw IllegalArgumentException()
    }
  }

  suspend override fun start(readDeadline: Long, writeDeadline: Long) {
    engine.beginHandshake()
    handshake(channel, null, engine.handshakeStatus, readDeadline, writeDeadline)
  }

  suspend override fun stop(readDeadline: Long, writeDeadline: Long) {
    engine.closeOutbound()
    appIn.rewind().limit(0)
    handshake(channel, null, SSLEngineResult.HandshakeStatus.NEED_WRAP, readDeadline, writeDeadline)
    engine.closeInbound()
    handshake(channel, null, SSLEngineResult.HandshakeStatus.NEED_WRAP, readDeadline, writeDeadline)
  }

  override fun next() {
    buffer.rewind().limit(buffer.capacity())
  }

  override fun recycle() {
    wireIn.rewind().limit(0)
    wireOut.rewind().limit(wireOut.capacity())
    appIn.rewind().limit(appIn.capacity())
    appOut.rewind().limit(appOut.capacity())
    nodes.addLast(node)
  }

  override fun buffer() = buffer

  override fun segment() = segment

  override fun segmentR() = segmentR

  override fun segmentW() = segmentW

  suspend override fun read(deadline: Long) = read(deadline,16384)

  suspend override fun read(deadline: Long, bytes: Int): ByteBuffer {
    segmentR.rewind().limit(bytes)
    if (bytes == 0) return segmentR.flip() as ByteBuffer
    val p = appIn.position()
    if (p > bytes) {
      appIn.rewind().limit(bytes)
      segmentR.put(appIn)
      appIn.limit(p)
      if (appIn.position() == 0) appIn.limit(appIn.capacity()) else appIn.compact()
    }
    else if (p > 0) {
      appIn.flip()
      segmentR.put(appIn)
      if (appIn.position() == 0) appIn.limit(appIn.capacity()) else appIn.compact()
    }
    else {
      handshake(channel, null, SSLEngineResult.HandshakeStatus.NEED_UNWRAP, deadline, deadline)
      val p2 = appIn.position()
      if (p2 > bytes) {
        appIn.rewind().limit(bytes)
        segmentR.put(appIn)
        appIn.limit(p2)
        if (appIn.position() == 0) appIn.limit(appIn.capacity()) else appIn.compact()
      }
      else if (p2 > 0) {
        appIn.flip()
        segmentR.put(appIn)
        if (appIn.position() == 0) appIn.limit(appIn.capacity()) else appIn.compact()
      }
    }
    return segmentR.flip() as ByteBuffer
  }

  suspend override fun write(deadline: Long, byteBuffer: ByteBuffer) {
    byteBuffer.flip()
    val bytes = byteBuffer.remaining()
    if (bytes == 0) return
    val r = appOut.remaining()
    if (r >= bytes) {
      appOut.put(byteBuffer)
      handshake(channel, null, SSLEngineResult.HandshakeStatus.NEED_WRAP, deadline, deadline)
    }
    else {
      byteBuffer.limit(r)
      appOut.put(byteBuffer)
      byteBuffer.limit(bytes)
      handshake(channel, null, SSLEngineResult.HandshakeStatus.NEED_WRAP, deadline, deadline)
      appOut.put(byteBuffer)
      handshake(channel, null, SSLEngineResult.HandshakeStatus.NEED_WRAP, deadline, deadline)
    }
    byteBuffer.rewind().limit(byteBuffer.capacity())
  }

  private class Node(segmentSize: Int, bufferSize: Int, engineBufferSize: Int): LockFreeLinkedListNode() {
    internal val segmentR = ByteBuffer.allocateDirect(segmentSize)
    internal val segmentW = ByteBuffer.allocateDirect(segmentSize)
    internal val segment = ByteBuffer.allocateDirect(segmentSize)
    internal val buffer = ByteBuffer.allocateDirect(bufferSize)
    internal val wireIn = ByteBuffer.allocateDirect(engineBufferSize).limit(0)
    internal val wireOut = ByteBuffer.allocateDirect(engineBufferSize)
    internal val appIn = ByteBuffer.allocateDirect(engineBufferSize)
    internal val appOut = ByteBuffer.allocateDirect(engineBufferSize)
//    init {
//      println("[${counter.incrementAndGet()}]")
//    }
  }

}
