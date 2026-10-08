package com.guardian.sentinel

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

data class AppRisk(
    val label: String,
    val packageName: String,
    val isSystem: Boolean,
    val score: Int,
    val flags: List<String>,
    val reasons: List<String>,
    val rxBytes: Long,
    val txBytes: Long
) {
    val level: RiskLevel
        get() = when {
            score >= 70 -> RiskLevel.CRITICAL
            score >= 40 -> RiskLevel.HIGH
            score >= 20 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
}

enum class RiskLevel { CRITICAL, HIGH, MEDIUM, LOW }

object RiskEngine {

    /** Upload di atas angka ini (dalam NetworkUsage.WINDOW_DAYS hari) dianggap tinggi. */
    private const val HIGH_UPLOAD_BYTES = 100L * 1024 * 1024 // 100 MB

    private val WEIGHTS = mapOf(
        "android.permission.RECORD_AUDIO" to 18,
        "android.permission.CAMERA" to 15,
        "android.permission.ACCESS_FINE_LOCATION" to 14,
        "android.permission.ACCESS_BACKGROUND_LOCATION" to 20,
        "android.permission.READ_SMS" to 18,
        "android.permission.RECEIVE_SMS" to 14,
        "android.permission.READ_CALL_LOG" to 16,
        "android.permission.PROCESS_OUTGOING_CALLS" to 14,
        "android.permission.READ_CONTACTS" to 8,
        "android.permission.SYSTEM_ALERT_WINDOW" to 12,
        "android.permission.BIND_ACCESSIBILITY_SERVICE" to 5,
        "android.permission.BIND_DEVICE_ADMIN" to 4,
        "android.permission.PACKAGE_USAGE_STATS" to 10,
        "android.permission.READ_PHONE_STATE" to 6,
        "android.permission.QUERY_ALL_PACKAGES" to 6
    )

    fun scan(context: Context): List<AppRisk> {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)

        val active = ActiveComponents.read(context)
        val traffic = NetworkUsage.readPerUid(context)
        val selfPackage = context.packageName

        return packages.mapNotNull { pkg ->
            if (pkg.packageName == selfPackage) return@mapNotNull null

            val appInfo = pkg.applicationInfo ?: return@mapNotNull null
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val requested = pkg.requestedPermissions?.toSet() ?: emptySet()

            var score = 0
            val reasons = mutableListOf<String>()
            val flags = mutableListOf<String>()

            for ((perm, w) in WEIGHTS) {
                if (requested.contains(perm)) {
                    score += w
                    reasons.add(perm.substringAfterLast('.'))
                }
            }

            val accessibilityActive = active.activeAccessibilityPkgs.contains(pkg.packageName)
            val deviceAdminActive = active.activeDeviceAdminPkgs.contains(pkg.packageName)

            if (accessibilityActive) {
                score += 40
                flags.add("ACCESSIBILITY AKTIF")
                reasons.add("Layanan Accessibility sedang MENYALA")
            }
            if (deviceAdminActive) {
                score += 30
                flags.add("DEVICE ADMIN AKTIF")
                reasons.add("Terdaftar sebagai Device Admin aktif")
            }
            if (accessibilityActive && deviceAdminActive && !isSystem) {
                score += 20
                flags.add("POLA STALKERWARE")
            }

            val canListen = requested.contains("android.permission.RECORD_AUDIO")
            val canSeeLoc = requested.any { it.contains("LOCATION") }
            val canReadMsg = requested.contains("android.permission.READ_SMS")
            if (canListen && canSeeLoc && canReadMsg) {
                score += 25
                flags.add("TRIFECTA MATA-MATA")
            }
            if (requested.contains("android.permission.SYSTEM_ALERT_WINDOW")) {
                flags.add("OVERLAY")
            }
            val hasLauncher = pm.getLaunchIntentForPackage(pkg.packageName) != null
            if (!isSystem && !hasLauncher && score > 0) {
                score += 15
                flags.add("TANPA IKON / TERSEMBUNYI")
            }
            if (isSystem) flags.add("SISTEM")

            val usage = traffic[appInfo.uid]
            val rx = usage?.rx ?: 0L
            val tx = usage?.tx ?: 0L
            if (tx > HIGH_UPLOAD_BYTES && (canListen ||
                        requested.contains("android.permission.CAMERA"))) {
                score += 10
                flags.add("UPLOAD TINGGI")
            }

            if (score == 0) return@mapNotNull null

            AppRisk(
                label = pm.getApplicationLabel(appInfo).toString(),
                packageName = pkg.packageName,
                isSystem = isSystem,
                score = score.coerceAtMost(100),
                flags = flags,
                reasons = reasons.distinct(),
                rxBytes = rx,
                txBytes = tx
            )
        }.sortedByDescending { it.score }
    }
}