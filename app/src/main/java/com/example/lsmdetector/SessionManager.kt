package com.example.lsmdetector

import android.content.Context

class SessionManager(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun saveSession(email: String, name: String) {
        preferences.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_NAME, name)
            .apply()
    }

    fun getSession(): UserSession? {
        val email = preferences.getString(KEY_EMAIL, null) ?: return null
        val name = preferences.getString(KEY_NAME, null) ?: return null
        return UserSession(email, name)
    }

    fun clearSession() {
        preferences.edit().clear().apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "lsm_session"
        private const val KEY_EMAIL = "email"
        private const val KEY_NAME = "name"
    }
}

data class UserSession(
    val email: String,
    val name: String
)
