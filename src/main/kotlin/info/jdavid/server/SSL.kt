package info.jdavid.server

import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLParameters
import javax.net.ssl.TrustManagerFactory

class SSL {

  companion object {

    val http11SslParameters = SSLParameters().apply {
      protocols = Platform.protocols
      cipherSuites = Platform.cipherSuites
      applicationProtocols = arrayOf("http/1.1")
    }
    val http2SslParameters = SSLParameters().apply {
      protocols = Platform.protocols
      cipherSuites = Platform.cipherSuites
      applicationProtocols = arrayOf("h2", "http/1.1")
    }

    fun createSSLContext(certificate: ByteArray?): SSLContext? {
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
        return context
      }
      finally {
        try {
          cert.close()
        }
        catch (ignore: IOException) {}
      }
    }

    fun parameters(): SSLParameters {
      return SSLParameters().apply {
        protocols = Platform.protocols
        cipherSuites = Platform.cipherSuites
      }
    }

    fun createSSLEngine(context: SSLContext, parameters: SSLParameters): SSLEngine {
      val engine = context.createSSLEngine()
      engine.useClientMode = false
      engine.wantClientAuth = false
      engine.enableSessionCreation = true
      engine.sslParameters = parameters
      return engine
    }

  }

}
