package com.example.data

/**
 * FOSS compatibility shim exposing [LOCAL_DEVICE_USER_ID] without any Firestore dependency.
 */
object MasroufiRepository {
    const val LOCAL_DEVICE_USER_ID: String = com.example.data.LOCAL_DEVICE_USER_ID
}
