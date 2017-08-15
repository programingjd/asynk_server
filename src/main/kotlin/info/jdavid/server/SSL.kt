package info.jdavid.server

import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.*

class SSL {

  companion object {

    fun createSSLEngine(certificate: ByteArray?): SSLEngine? {
      if (certificate == null) return null
      val cert = ByteArrayInputStream(certificate)
      try {
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(cert, CharArray(0))
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, CharArray(0))
        val trustStore = KeyStore.getInstance("JKS")
        trustStore.load(null, null)
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(trustStore)
        val context = SSLContext.getInstance("TLS")
        context.init(kmf.keyManagers, tmf.trustManagers, SecureRandom())
        return context.createSSLEngine()
      }
      finally {
        try {
          cert.close()
        }
        catch (ignore: IOException) {
        }
      }
    }

    suspend fun r(channel: AsynchronousSocketChannel, engine: SSLEngine) {
      engine.useClientMode = false
      engine.wantClientAuth = false
      engine.enableSessionCreation = true
      engine.sslParameters = SSLParameters().apply {
        protocols = Platform.protocols
        cipherSuites = Platform.cipherSuites
      }
      engine.beginHandshake()
      val net = ByteBuffer.wrap(ByteArray(engine.session.packetBufferSize))
      val app = ByteBuffer.wrap(ByteArray(engine.session.applicationBufferSize))
    }

  }

}
