package com.gemini.bridge.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Small wrapper around SharedPreferences with AES-GCM encryption.
 * Used for the Gemini API key; non-sensitive preferences (model, workspace URI)
 * live in a plain file alongside it.
 */
class SecurePrefs(context: Context) {

    private val master = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encrypted: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "gemini-secrets",
        master,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val plain: SharedPreferences = context.getSharedPreferences("gemini-prefs", Context.MODE_PRIVATE)

    var apiKey: String?
        get() = encrypted.getString(KEY_API, null)
        set(value) = encrypted.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_API) else putString(KEY_API, value)
        }.apply()

    var model: String?
        get() = plain.getString(KEY_MODEL, null)
        set(value) = plain.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_MODEL) else putString(KEY_MODEL, value)
        }.apply()

    var imagenModel: String?
        get() = plain.getString(KEY_IMAGEN_MODEL, null)
        set(value) = plain.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_IMAGEN_MODEL) else putString(KEY_IMAGEN_MODEL, value)
        }.apply()

    var workspaceUri: String?
        get() = plain.getString(KEY_WORKSPACE, null)
        set(value) = plain.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_WORKSPACE) else putString(KEY_WORKSPACE, value)
        }.apply()

    var autoApprove: Boolean
        get() = plain.getBoolean(KEY_AUTO_APPROVE, false)
        set(value) = plain.edit().putBoolean(KEY_AUTO_APPROVE, value).apply()

    var termuxGuideShown: Boolean
        get() = plain.getBoolean(KEY_TERMUX_GUIDE_SHOWN, false)
        set(value) = plain.edit().putBoolean(KEY_TERMUX_GUIDE_SHOWN, value).apply()

    var autoCompressEnabled: Boolean
        get() = plain.getBoolean(KEY_AUTO_COMPRESS, true)
        set(value) = plain.edit().putBoolean(KEY_AUTO_COMPRESS, value).apply()

    var autoSaveEnabled: Boolean
        get() = plain.getBoolean(KEY_AUTO_SAVE, true)
        set(value) = plain.edit().putBoolean(KEY_AUTO_SAVE, value).apply()

    /** Fraction of the model's input-token limit at which to auto-compress (0.5..0.95). */
    var autoCompressThreshold: Float
        get() = plain.getFloat(KEY_AUTO_COMPRESS_THRESHOLD, 0.7f)
        set(value) = plain.edit().putFloat(KEY_AUTO_COMPRESS_THRESHOLD, value).apply()

    fun clearAll() {
        encrypted.edit().clear().apply()
        plain.edit().clear().apply()
    }

    private companion object {
        const val KEY_API = "api_key"
        const val KEY_MODEL = "model"
        const val KEY_IMAGEN_MODEL = "imagen_model"
        const val KEY_WORKSPACE = "workspace_uri"
        const val KEY_AUTO_APPROVE = "auto_approve"
        const val KEY_TERMUX_GUIDE_SHOWN = "termux_guide_shown"
        const val KEY_AUTO_COMPRESS = "auto_compress_enabled"
        const val KEY_AUTO_COMPRESS_THRESHOLD = "auto_compress_threshold"
        const val KEY_AUTO_SAVE = "auto_save_enabled"
    }
}
