package com.tanbread.nfcdevicecheck.sender.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.tanbread.nfcdevicecheck.sender.identity.IdentityCollector

/**
 * Marks this app as provisioned device owner (set via
 * `adb shell dpm set-device-owner`). Device owner + READ_PHONE_STATE is what
 * grants IMEI access on Android 10+.
 */
class SenderDeviceAdmin : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        IdentityCollector.markDeviceOwner(context, true)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        IdentityCollector.markDeviceOwner(context, false)
    }
}
