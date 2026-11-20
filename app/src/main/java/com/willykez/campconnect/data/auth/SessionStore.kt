package com.willykez.campconnect.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SavedSession(
  val userId: String,
  val accessToken: String,
  val refreshToken: String,
  val profileJson: String
)

object SessionStore {
  private const val PREFS = "campusconnect_session"
  private const val KEY_ALIAS = "campusconnect_session_key"
  private const val ENTRY = "session"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  private fun key(): SecretKey {
    val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
    val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    gen.init(
      KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build()
    )
    return gen.generateKey()
  }

  fun save(context: Context, s: SavedSession) {
    runCatching {
      val json = JSONObject()
        .put("u", s.userId).put("a", s.accessToken).put("r", s.refreshToken).put("p", s.profileJson)
        .toString().toByteArray(Charsets.UTF_8)
      val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
      val sealed = cipher.doFinal(json)
      val packed = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(sealed, Base64.NO_WRAP)
      prefs(context).edit().putString(ENTRY, packed).apply()
    }
  }

  fun load(context: Context): SavedSession? = runCatching {
    val packed = prefs(context).getString(ENTRY, null) ?: return null
    val (iv, data) = packed.split(":").let { it[0] to it[1] }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
      init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
    }
    val o = JSONObject(String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8))
    SavedSession(o.getString("u"), o.getString("a"), o.getString("r"), o.getString("p"))
  }.getOrElse {
    clear(context)
    null
  }

  fun clear(context: Context) {
    prefs(context).edit().remove(ENTRY).apply()
  }
}
