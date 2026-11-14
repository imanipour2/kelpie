package com.kelpie.browser.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Device-bound secrets are not portable across Android Keystore resets.
 *
 * A restored encrypted XML file may no longer decrypt after reinstall,
 * signing-profile migration or device transfer. Never erase the encrypted file
 * automatically: a later, explicitly approved account recovery flow must own
 * that destructive step. In the meantime, treat the session as signed out.
 */
class SecretStore private constructor(
    private val prefs: SharedPreferences?,
) {
    @Volatile
    private var recoveryRequired = prefs == null

    /** No secrets, keys or exception messages are disclosed by this status. */
    fun needsUserRecovery(): Boolean = recoveryRequired

    fun get(name: String): String? {
        if (recoveryRequired) return null
        return try {
            prefs?.getString(name, null)?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            // Decryption may fail on first read rather than during create().
            // Keep the encrypted file intact for explicit, scoped recovery.
            recoveryRequired = true
            null
        }
    }

    fun set(
        name: String,
        value: String,
    ) {
        try {
            writablePreferences().edit().putString(name, value).apply()
        } catch (_: Exception) {
            recoveryRequired = true
            throw IllegalStateException(RECOVERY_MESSAGE)
        }
    }

    fun remove(name: String) {
        try {
            writablePreferences().edit().remove(name).apply()
        } catch (_: Exception) {
            recoveryRequired = true
            throw IllegalStateException(RECOVERY_MESSAGE)
        }
    }

    /** Action journals must be durably written before remote action dispatch. */
    fun setDurably(
        name: String,
        value: String,
    ) {
        try {
            check(writablePreferences().edit().putString(name, value).commit()) {
                "Could not persist action recovery journal"
            }
        } catch (_: Exception) {
            // A failed durable commit must not be mistaken for successful storage.
            recoveryRequired = true
            throw IllegalStateException(RECOVERY_MESSAGE)
        }
    }

    private fun writablePreferences(): SharedPreferences {
        if (recoveryRequired) throw IllegalStateException(RECOVERY_MESSAGE)
        return prefs ?: throw IllegalStateException(RECOVERY_MESSAGE)
    }

    companion object {
        private const val FILE_NAME = "kelpie_secrets"
        private const val RECOVERY_MESSAGE =
            "Encrypted device secrets are unavailable. Re-authentication and explicit recovery are required."

        @Volatile
        private var instance: SecretStore? = null

        fun get(context: Context): SecretStore {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                val created = SecretStore(openOrUnavailable(context.applicationContext))
                instance = created
                return created
            }
        }

        private fun openOrUnavailable(context: Context): SharedPreferences? =
            try {
                val masterKey =
                    MasterKey
                        .Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                EncryptedSharedPreferences.create(
                    context,
                    FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (_: Exception) {
                // AEADBadTagException and other security exceptions are often
                // wrapped by AndroidX; do not rely on a single exception class.
                // Importantly, do NOT delete the user's encrypted preferences.
                null
            }
    }
}
