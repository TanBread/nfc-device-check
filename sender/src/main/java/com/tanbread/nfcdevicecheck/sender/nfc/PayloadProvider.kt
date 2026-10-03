package com.tanbread.nfcdevicecheck.sender.nfc

import com.tanbread.nfcdevicecheck.shared.hashing.HashBundle

/**
 * Bridge between the UI (which builds the current HashBundle) and the HCE
 * service (which serves it). Volatile so reads/writes are visible across
 * threads — processCommandApdu runs on a binder thread.
 */
object PayloadProvider {
    @Volatile
    var payload: ByteArray? = null

    fun update(bundle: HashBundle) {
        payload = bundle.toJson().toByteArray(Charsets.UTF_8)
    }

    fun clear() {
        payload = null
    }
}
