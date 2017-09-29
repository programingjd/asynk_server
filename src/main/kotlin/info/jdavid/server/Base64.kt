package info.jdavid.server

import info.jdavid.server.http.Encodings
import java.util.prefs.AbstractPreferences

class Base64: AbstractPreferences(null, "") {

  private var mValue: String? = null

  fun encode(str: String) = encode(str.toByteArray(Encodings.UTF_8))

  fun encode(bytes: ByteArray): String {
    putByteArray(null, bytes)
    val value = mValue ?: throw NullPointerException()
    mValue = null
    return value
  }

  override fun put(key: String?, value: String) {
    mValue = value
  }

  override fun putSpi(key: String?, value: String) {}
  override fun getSpi(key: String?) = null

  override fun removeSpi(key: String?) {}
  override fun removeNodeSpi() {}

  override fun keysSpi(): Array<String?> = arrayOfNulls(0)
  override fun childrenNamesSpi(): Array<String?> = arrayOfNulls(0)
  override fun childSpi(name: String?) = null
  override fun syncSpi() {}
  override fun flushSpi() {}

}
