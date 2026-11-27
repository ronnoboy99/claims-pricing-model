package com.flick.app.util

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun Context.findActivity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

fun relativeDate(ms: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val days = java.time.temporal.ChronoUnit.DAYS.between(date, today)
    return when {
        days <= 0 -> "Today"
        days == 1L -> "Yesterday"
        days < 7 -> date.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
        date.year == today.year -> date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
        else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
    }
}

fun shareText(context: Context, subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

object BiometricGate {
    private const val AUTH =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(activity: FragmentActivity, title: String, onSuccess: () -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(AUTH)
            .build()
        prompt.authenticate(info)
    }
}
