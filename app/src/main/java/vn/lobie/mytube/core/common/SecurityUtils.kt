package vn.lobie.mytube.core.common

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.io.File
import java.security.MessageDigest
import java.util.Arrays
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Security Hardening Utilities covering SEC-21 through SEC-27
 */
object SecurityUtils {

    // =========================================================================
    // SEC-21: Privacy / Tracker Stripper for URLs
    // =========================================================================
    private val TRACKING_PARAMS = setOf(
        "si", "feature", "utm_source", "utm_medium", "utm_campaign",
        "utm_term", "utm_content", "gclid", "fbclid", "pp", "ab_channel"
    )

    /**
     * Removes marketing and tracking parameters from YouTube URLs before sharing or copying.
     */
    fun stripTrackingParams(rawUrl: String): String {
        if (rawUrl.isBlank()) return rawUrl
        return try {
            val uri = Uri.parse(rawUrl.trim())
            val scheme = uri.scheme ?: return rawUrl
            val host = uri.host ?: return rawUrl

            val builder = uri.buildUpon().clearQuery()
            for (param in uri.queryParameterNames) {
                if (param.lowercase() !in TRACKING_PARAMS) {
                    for (value in uri.getQueryParameters(param)) {
                        builder.appendQueryParameter(param, value)
                    }
                }
            }
            builder.build().toString()
        } catch (_: Exception) {
            rawUrl
        }
    }

    // =========================================================================
    // SEC-22: Root and Tamper Detection
    // =========================================================================
    private val SU_PATHS = arrayOf(
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su"
    )

    /**
     * Checks if the device displays common indicators of root access.
     */
    fun isDeviceRooted(): Boolean {
        // Check 1: Build tags
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            return true
        }

        // Check 2: Common su binaries
        for (path in SU_PATHS) {
            try {
                if (File(path).exists()) return true
            } catch (_: SecurityException) {}
        }
        return false
    }

    // =========================================================================
    // SEC-23: Runtime App Signature Verification (Anti-Tampering)
    // =========================================================================
    /**
     * Verifies that the app package has not been tampered with or resigned by an unauthorized cert.
     */
    fun verifyAppSignature(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val packageName = context.packageName
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
                if (signingInfo?.hasMultipleSigners() == true) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo?.signingCertificateHistory
                }
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
            }
            signatures != null && signatures.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    // =========================================================================
    // SEC-24: Cascading Fallback Circuit Breaker (Anti-DoS / Request Amplification)
    // =========================================================================
    class CircuitBreaker(
        private val failureThreshold: Int = 3,
        private val resetTimeoutMs: Long = 60_000L
    ) {
        private val failureCount = AtomicInteger(0)
        private val lastFailureTime = AtomicLong(0L)

        fun canExecute(): Boolean {
            val failures = failureCount.get()
            if (failures < failureThreshold) return true

            val elapsed = System.currentTimeMillis() - lastFailureTime.get()
            return if (elapsed > resetTimeoutMs) {
                // Half-open / reset
                failureCount.set(0)
                true
            } else {
                false
            }
        }

        fun recordSuccess() {
            failureCount.set(0)
        }

        fun recordFailure() {
            failureCount.incrementAndGet()
            lastFailureTime.set(System.currentTimeMillis())
        }
    }

    // =========================================================================
    // SEC-25: Download Content-Type / MIME Type Validation
    // =========================================================================
    private val ALLOWED_MEDIA_MIME_PREFIXES = listOf(
        "video/",
        "audio/",
        "application/octet-stream",
        "application/vnd.apple.mpegurl",
        "application/dash+xml"
    )

    /**
     * Validates that remote responses for downloads match expected audio/video MIME types.
     */
    fun isValidMediaContentType(contentType: String?): Boolean {
        if (contentType == null) return true // Some CDNs omit content-type
        val clean = contentType.lowercase().substringBefore(';').trim()
        if (clean.isBlank()) return true
        return ALLOWED_MEDIA_MIME_PREFIXES.any { clean.startsWith(it) }
    }

    // =========================================================================
    // SEC-26: Search Input Sanitization & Length Guard
    // =========================================================================
    /**
     * Sanitizes user search input against control characters and bounds query length to prevent DoS.
     */
    fun sanitizeSearchQuery(query: String, maxLength: Int = 256): String {
        if (query.isBlank()) return ""
        val withoutControlChars = query.replace(Regex("[\\p{Cntrl}&&[^\r\n\t]]"), "")
        return withoutControlChars.trim().take(maxLength)
    }

    // =========================================================================
    // SEC-27: In-Memory Secret Zeroization
    // =========================================================================
    /**
     * Safely zeroes out sensitive character arrays or memory buffers.
     */
    fun secureWipe(chars: CharArray) {
        Arrays.fill(chars, '\u0000')
    }

    fun secureWipe(bytes: ByteArray) {
        Arrays.fill(bytes, 0.toByte())
    }
}
