package com.google.firebase.analytics

import com.google.firebase.Firebase

/** no-op */
class FirebaseAnalytics {
    fun logEvent(name: String, params: Any? = null) = Unit

    fun setUserId(id: String?) = Unit

    fun setUserProperty(name: String, value: String?) = Unit
}

private val sharedAnalytics = FirebaseAnalytics()

val Firebase.analytics: FirebaseAnalytics get() = sharedAnalytics
