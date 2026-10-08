package com.guardian.sentinel

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils

/** Hasil pembacaan komponen yang BENAR-BENAR aktif di sistem, bukan sekadar diminta. */
data class ActiveState(
    val activeAccessibilityPkgs: Set<String>,
    val activeDeviceAdminPkgs: Set<String>
)

object ActiveComponents {

    fun read(context: Context): ActiveState =
        ActiveState(readAccessibility(context), readDeviceAdmins(context))

    private fun readAccessibility(context: Context): Set<String> {
        val raw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return emptySet()
        if (raw.isEmpty()) return emptySet()

        val result = mutableSetOf<String>()
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(raw)
        for (entry in splitter) {
            ComponentName.unflattenFromString(entry)?.let {
                result.add(it.packageName)
            }
        }
        return result
    }

    private fun readDeviceAdmins(context: Context): Set<String> {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                as? DevicePolicyManager ?: return emptySet()
        return dpm.activeAdmins?.map { it.packageName }?.toSet() ?: emptySet()
    }
}