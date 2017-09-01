package info.jdavid.server

import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class HttpsTests: Http11Tests() {
  init {
    val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
      override fun getAcceptedIssuers() = arrayOf<X509Certificate>()
      override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
      override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
    })
    val sc = SSLContext.getInstance("SSL")
    sc.init(null, trustAllCerts, java.security.SecureRandom())
    HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
    HttpsURLConnection.setDefaultHostnameVerifier(object: HostnameVerifier {
      override fun verify(hostname: String?, session: SSLSession?) = true
    })
  }

  override fun scheme() = "https"

  override fun config(): Config {
    return super.config().certificate(java.io.File("localhost.p12"))
  }

}
