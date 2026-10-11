package com.google.firebase.crashlytics

import com.google.firebase.Firebase

/** Crashlytics no-op */
class FirebaseCrashlytics {
    fun log(msg: String) = Unit

    fun recordException(throwable: Throwable) = Unit

    fun setUserId(id: String?) = Unit

    fun setCustomKey(key: String, value: Any?) = Unit
}

private val sharedCrashlytics = FirebaseCrashlytics()

val Firebase.crashlytics: FirebaseCrashlytics get() = sharedCrashlytics
