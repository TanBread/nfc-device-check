package com.tanbread.nfcdevicecheck.sender.identity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.telephony.euicc.EuiccManager
import androidx.core.content.ContextCompat

/**
 * Collects the raw identity fields on Phone B. Raw values stay on-device;
 * only their hashes ever leave this phone (see HashBundle.fromRaw).
 */
object IdentityCollector {

    fun deviceName(context: Context): String? =
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)

    fun androidVersion(): String = Build.VERSION.RELEASE

    fun buildNumber(): String = Build.DISPLAY

    fun imei1(context: Context): String? = imeiAt(context, slotIndex = 0)

    fun imei2(context: Context): String? = imeiAt(context, slotIndex = 1)

    private fun imeiAt(context: Context, slotIndex: Int): String? {
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return null
        if (!hasPhoneState(context)) return null
        return try {
            val slotCount = tm.activeModemCount.coerceAtLeast(1)
            if (slotIndex >= slotCount) return null
            tm.getImei(slotIndex)?.takeIf { it.isNotBlank() }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * EID needs carrier privileges or READ_PRIVILEGED_PHONE_STATE — neither is
     * available to a device-owner app — so programmatic access usually throws.
     * Returns null and the caller falls back to manual entry.
     */
    fun eidFromApi(context: Context): String? {
        if (!hasPhoneState(context)) return null
        val euicc = context.getSystemService(EuiccManager::class.java) ?: return null
        return try {
            if (!euicc.isEnabled) null
            else euicc.eid?.takeIf { it.isNotBlank() }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    /** Manual EID (32 hex chars) stored locally; used when API access fails. */
    fun storedManualEid(context: Context): String? =
        prefs(context).getString(KEY_MANUAL_EID, null)?.takeIf { it.isNotBlank() }

    fun storeManualEid(context: Context, eid: String) {
        prefs(context).edit().putString(KEY_MANUAL_EID, eid).apply()
    }

    fun hasDeviceOwner(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DEVICE_OWNER, false)

    fun markDeviceOwner(context: Context, granted: Boolean) {
        prefs(context).edit().putBoolean(KEY_DEVICE_OWNER, granted).apply()
    }

    fun hasPhoneState(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    private fun prefs(context: Context) =
        context.getSharedPreferences("sender_identity", Context.MODE_PRIVATE)

    private const val KEY_MANUAL_EID = "manual_eid"
    private const val KEY_DEVICE_OWNER = "device_owner"
}
