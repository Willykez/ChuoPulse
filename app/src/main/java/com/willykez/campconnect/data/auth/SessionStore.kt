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

/** What is kept so the person stays signed in until they choose to sign out. */
data class SavedSession(
  val userId: String,
  val accessToken: String,
  val refreshToken: String,
  /** The profile row as the server returned it, so the app can start even with no network. */
  val profileJson: String
)

/**
 * Keeps the sign-in on the phone, encrypted with a key that never leaves the Android Keystore.
 * Cleared only by an explicit sign-out (or when the server says the sign-in is no longer valid).
 */
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
    // Unreadable (key lost after a restore, for example): forget it, the person signs in again.
    clear(context)
    null
  }

  fun clear(context: Context) {
    prefs(context).edit().remove(ENTRY).apply()
  }
}
