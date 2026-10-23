package com.bro.assistant.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecretBox {
    private const val PREF_FILE = "bro_encrypted_vault"

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREF_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveKey(context: Context, provider: String, rawKey: String): Result<Unit> {
        val cleaned = rawKey.trim().replace("\\s+".toRegex(), "")
        if (provider.equals("CLAUDE", ignoreCase = true) && cleaned.isNotEmpty() && !cleaned.startsWith("sk-ant-")) {
            return Result.failure(IllegalArgumentException("Claude API keys must start with 'sk-ant-'"))
        }
        prefs(context).edit().putString("key_${provider.uppercase()}", cleaned).apply()
        return Result.success(Unit)
    }

    fun getKey(context: Context, provider: String): String {
        return prefs(context).getString("key_${provider.uppercase()}", "")?.trim() ?: ""
    }

    fun saveSetting(context: Context, key: String, value: String) {
        prefs(context).edit().putString("cfg_$key", value.trim()).apply()
    }

    fun getSetting(context: Context, key: String, default: String = ""): String {
        return prefs(context).getString("cfg_$key", default) ?: default
    }
}
