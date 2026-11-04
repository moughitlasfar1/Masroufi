package com.example.data

import android.util.Log
import com.example.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

/**
 * SEC-09: Structured Firestore error handler that never logs personal email addresses
 * or provider PII to logcat, and only logs diagnostic metadata in debug builds.
 */
fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String?): String {
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    val authInfoJson = JSONObject().apply {
        put("authenticated", currentUser != null)
        if (BuildConfig.DEBUG) {
            put("userId", currentUser?.uid)
        }
    }

    val errorInfoJson = JSONObject().apply {
        put("error", exception.message ?: exception.javaClass.simpleName)
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    if (BuildConfig.DEBUG) {
        Log.e("FirestoreError", "Firestore Error: $jsonString")
    }
    return jsonString
}
