package com.guardian.sentinel

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings

data class UidTraffic(val rx: Long, val tx: Long)

object NetworkUsage {

    /** Rentang waktu statistik yang dibaca (hari ke belakang dari sekarang). */
    const val WINDOW_DAYS = 7
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Cek apakah izin Akses Penggunaan sudah diberikan pengguna. */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // For Android 16 (API 36) and higher
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            // For Android 10 (API 29) through Android 15 (API 35)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
        }
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT ->
                context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                        PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }

    /** Buka layar Pengaturan Akses Penggunaan (langsung ke halaman app ini bila didukung). */
    fun openUsageAccessSettings(context: Context) {
        val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(direct)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /**
     * Total rx/tx per-UID (Wi-Fi + seluler) selama WINDOW_DAYS terakhir.
     * Mengembalikan map kosong bila izin belum diberikan.
     */
    @Suppress("DEPRECATION")
    fun readPerUid(context: Context): Map<Int, UidTraffic> {
        if (!hasUsageAccess(context)) return emptyMap()

        val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val end = System.currentTimeMillis()
        val start = end - WINDOW_DAYS * DAY_MS

        val rx = HashMap<Int, Long>()
        val tx = HashMap<Int, Long>()

        for (type in intArrayOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
            val stats: NetworkStats = try {
                // subscriberId = null -> semua jaringan seluler (Android 10+)
                nsm.querySummary(type, null, start, end)
            } catch (e: Exception) {
                continue
            }
            try {
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    val uid = bucket.uid
                    rx[uid] = (rx[uid] ?: 0L) + bucket.rxBytes
                    tx[uid] = (tx[uid] ?: 0L) + bucket.txBytes
                }
            } finally {
                stats.close()
            }
        }

        return (rx.keys + tx.keys).associateWith { uid ->
            UidTraffic(rx = rx[uid] ?: 0L, tx = tx[uid] ?: 0L)
        }
    }
}