package com.tanbread.nfcdevicecheck.checker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tanbread.nfcdevicecheck.checker.data.CheckerDatabase
import com.tanbread.nfcdevicecheck.checker.data.CheckLog
import com.tanbread.nfcdevicecheck.checker.data.EnrolledDevice
import com.tanbread.nfcdevicecheck.checker.verify.Verifier
import com.tanbread.nfcdevicecheck.checker.verify.VerifyOutcome
import com.tanbread.nfcdevicecheck.shared.hashing.HashBundle
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the user picked on the Verify tab. */
enum class VerifyMode { ENROLL, VERIFY }

sealed interface TapResult {
    data class Enrolled(val label: String) : TapResult
    data class Accepted(val label: String) : TapResult
    data class Denied(val label: String, val fields: List<String>) : TapResult
    data object NoEnrolledDevices : TapResult
    data class Error(val message: String) : TapResult
}

class CheckerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = CheckerDatabase.get(application)

    private val _mode = MutableStateFlow(VerifyMode.VERIFY)
    val mode: StateFlow<VerifyMode> = _mode.asStateFlow()

    private val _enrolled = MutableStateFlow<List<EnrolledDevice>>(emptyList())
    val enrolled: StateFlow<List<EnrolledDevice>> = _enrolled.asStateFlow()

    private val _logs = MutableStateFlow<List<CheckLog>>(emptyList())
    val logs: StateFlow<List<CheckLog>> = _logs.asStateFlow()

    private val _lastResult = MutableStateFlow<TapResult?>(null)
    val lastResult: StateFlow<TapResult?> = _lastResult.asStateFlow()

    /** Fires when a tap produced a decision — drives sound/haptic feedback. */
    private val _feedback = MutableSharedFlow<TapResult>(extraBufferCapacity = 1)
    val feedback: SharedFlow<TapResult> = _feedback.asSharedFlow()

    init {
        viewModelScope.launch {
            db.enrolledDeviceDao().observeAll().collect { _enrolled.value = it }
        }
        viewModelScope.launch {
            db.checkLogDao().observeRecent().collect { _logs.value = it }
        }
    }

    fun setMode(mode: VerifyMode) {
        _mode.value = mode
        _lastResult.value = null
    }

    /**
     * Entry point for every successful NFC read. Runs in ENROLL mode or
     * VERIFY mode depending on the selected tab.
     */
    fun onBundleRead(bundle: HashBundle) {
        viewModelScope.launch {
            val result = when (_mode.value) {
                VerifyMode.ENROLL -> enroll(bundle)
                VerifyMode.VERIFY -> verify(bundle)
            }
            _lastResult.value = result
            _feedback.tryEmit(result)
        }
    }

    fun onReadError(message: String) {
        val result = TapResult.Error(message)
        _lastResult.value = result
        _feedback.tryEmit(result)
    }

    fun clearResult() {
        _lastResult.value = null
    }

    private suspend fun enroll(bundle: HashBundle): TapResult {
        val existing = _enrolled.value
        val label = "Device ${existing.size + 1}"
        // Skip duplicate enrollment of the same identity.
        if (existing.any { it.toBundle().compare(bundle).allMatch }) {
            return TapResult.Error("This device is already enrolled")
        }
        val device = EnrolledDevice.fromBundle(label, bundle)
        db.enrolledDeviceDao().insert(device)
        return TapResult.Enrolled(label)
    }

    private suspend fun verify(bundle: HashBundle): TapResult {
        val outcome = Verifier.verify(bundle, _enrolled.value)
        db.checkLogDao().insert(Verifier.toLog(outcome, bundle))
        return when (outcome) {
            is VerifyOutcome.Accept -> TapResult.Accepted(outcome.device.label)
            is VerifyOutcome.Deny ->
                TapResult.Denied(
                    label = outcome.closest.label,
                    fields = outcome.comparison.mismatchedFields(),
                )

            VerifyOutcome.NoEnrolledDevices -> TapResult.NoEnrolledDevices
        }
    }

    fun deleteEnrolled(id: Long) {
        viewModelScope.launch { db.enrolledDeviceDao().delete(id) }
    }

    fun clearHistory() {
        viewModelScope.launch { db.checkLogDao().clear() }
    }
}
