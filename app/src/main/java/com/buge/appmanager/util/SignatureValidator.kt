// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.security.MessageDigest

object SignatureValidator {

    private const val TAG = "SignatureValidator"

    // Expected SHA-256 fingerprint of the official release signing certificate
    // Generated from keystore.jks alias BugeStudioTeam
    // Fuck: Make this public so other classes can access it
    const val EXPECTED_SHA256 = "4B0E4446330530801756B5A2680EE5526B64BA982C327F56758E05497377885B"

    private var cachedValid: Boolean? = null
    private var cachedFingerprint: String? = null

    /**
     * Check if the current APK is signed with the official certificate
     * @return true if signature matches, false otherwise
     */
    fun isSignatureValid(context: Context): Boolean {
        cachedValid?.let { return it }

        val fingerprint = getSignatureFingerprint(context)
        cachedFingerprint = fingerprint

        val isValid = fingerprint != null && fingerprint.equals(EXPECTED_SHA256, ignoreCase = true)
        cachedValid = isValid

        if (!isValid) {
            LogManager.warning(
                context,
                "Signature validation failed!",
                "Expected: $EXPECTED_SHA256, Got: $fingerprint"
            )
        }

        return isValid
    }

    /**
     * Get the SHA-256 fingerprint of the signing certificate
     * @return fingerprint as hex string, or null if unable to retrieve
     */
    fun getSignatureFingerprint(context: Context): String? {
        cachedFingerprint?.let { return it }

        return try {
            val packageManager = context.packageManager
            val packageName = context.packageName

            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNATURES
                )
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            if (signatures.isNullOrEmpty()) {
                Log.w(TAG, "No signatures found for package: $packageName")
                return null
            }

            val signature = signatures[0]
            val signatureBytes = signature.toByteArray()
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(signatureBytes)
            val fingerprint = bytesToHex(hashBytes)

            fingerprint
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get signature fingerprint", e)
            null
        }
    }

    /**
     * Get the formatted SHA-256 fingerprint with colons (for display)
     */
    fun getFormattedFingerprint(context: Context): String? {
        val raw = getSignatureFingerprint(context) ?: return null
        return raw.chunked(2).joinToString(":")
    }

    /**
     * Convert byte array to hex string
     */
    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = "0123456789ABCDEF"[v ushr 4]
            hexChars[i * 2 + 1] = "0123456789ABCDEF"[v and 0x0F]
        }
        return String(hexChars)
    }

    /**
     * Check if running on a debug build (debuggable)
     */
    fun isDebugBuild(context: Context): Boolean {
        return (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    /**
     * Get validation status description for display
     */
    fun getValidationStatus(context: Context): ValidationStatus {
        return if (isSignatureValid(context)) {
            ValidationStatus.VALID
        } else {
            ValidationStatus.INVALID
        }
    }

    enum class ValidationStatus {
        VALID,
        INVALID
    }
}