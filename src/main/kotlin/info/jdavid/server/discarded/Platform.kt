package info.jdavid.server.discarded

import javax.net.ssl.SSLEngine

internal class Platform {

  companion object {
    val protocols = arrayOf("TLSv1.2")
    val cipherSuites = arrayOf(
      "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
      "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
      "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256",
      "TLS_RSA_WITH_AES_128_GCM_SHA256",
      "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
      "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
      "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256",
      "TLS_RSA_WITH_AES_128_CBC_SHA256"
    )
    fun isHttp2(engine: SSLEngine): Boolean {
      return false
      //return engine.applicationProtocol == "h2"
    }
  }


}
