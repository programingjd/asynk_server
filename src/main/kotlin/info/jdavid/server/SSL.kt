package info.jdavid.server

import java.io.ByteArrayInputStream
import java.io.IOException
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
        val engine = context.createSSLEngine()
        engine.useClientMode = false
        engine.wantClientAuth = false
        engine.enableSessionCreation = true
        engine.sslParameters = SSLParameters().apply {
          protocols = Platform.protocols
          cipherSuites = Platform.cipherSuites
        }
        return engine
      }
      finally {
        try {
          cert.close()
        }
        catch (ignore: IOException) {}
      }
    }

  }

}
