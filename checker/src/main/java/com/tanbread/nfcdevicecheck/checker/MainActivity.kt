package com.tanbread.nfcdevicecheck.checker

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tanbread.nfcdevicecheck.checker.data.CheckLog
import com.tanbread.nfcdevicecheck.checker.data.EnrolledDevice
import com.tanbread.nfcdevicecheck.checker.nfc.TagReader
import com.tanbread.nfcdevicecheck.checker.ui.CheckerViewModel
import com.tanbread.nfcdevicecheck.checker.ui.TapResult
import com.tanbread.nfcdevicecheck.checker.ui.VerifyMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: CheckerViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setContent {
            MaterialTheme {
                val mode by viewModel.mode.collectAsState()
                val enrolled by viewModel.enrolled.collectAsState()
                val logs by viewModel.logs.collectAsState()
                val lastResult by viewModel.lastResult.collectAsState()

                LaunchedEffect(Unit) {
                    viewModel.feedback.collect { playFeedback(it) }
                }

                CheckerScreen(
                    mode = mode,
                    onModeChange = viewModel::setMode,
                    enrolled = enrolled,
                    logs = logs,
                    lastResult = lastResult,
                    onDeleteEnrolled = viewModel::deleteEnrolled,
                    onClearHistory = viewModel::clearHistory,
                    onDismissResult = viewModel::clearResult,
                    nfcEnabled = nfcAdapter?.isEnabled == true,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val reader = TagReader { result ->
            result.fold(
                onSuccess = { bundle -> viewModel.onBundleRead(bundle) },
                onFailure = { e -> viewModel.onReadError(e.message ?: "read failed") },
            )
        }
        nfcAdapter?.enableReaderMode(
            this,
            reader,
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V,
            null,
        )
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    private fun playFeedback(result: TapResult) {
        val accepted = result is TapResult.Accepted || result is TapResult.Enrolled
        val tone = if (accepted) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK
        runCatching {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                .startTone(tone, 250)
        }
        vibrate(if (accepted) 60 else 240)
    }

    private fun vibrate(ms: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        runCatching {
            vibrator.vibrate(
                VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE),
            )
        }
    }
}

@Composable
fun CheckerScreen(
    mode: VerifyMode,
    onModeChange: (VerifyMode) -> Unit,
    enrolled: List<EnrolledDevice>,
    logs: List<CheckLog>,
    lastResult: TapResult?,
    onDeleteEnrolled: (Long) -> Unit,
    onClearHistory: () -> Unit,
    onDismissResult: () -> Unit,
    nfcEnabled: Boolean,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = mode == VerifyMode.VERIFY,
                    onClick = { onModeChange(VerifyMode.VERIFY) },
                    icon = { Text("V") },
                    label = { Text("Verify") },
                )
                NavigationBarItem(
                    selected = mode == VerifyMode.ENROLL,
                    onClick = { onModeChange(VerifyMode.ENROLL) },
                    icon = { Text("E") },
                    label = { Text("Enroll") },
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("NFC Checker", style = MaterialTheme.typography.headlineMedium)

            if (!nfcEnabled) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "NFC is turned off. Enable it in Quick Settings.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (lastResult != null) {
                ResultCard(lastResult, onDismissResult)
            }

            when (mode) {
                VerifyMode.VERIFY -> VerifyTab(enrolled)
                VerifyMode.ENROLL -> EnrollTab(enrolled, onDeleteEnrolled)
            }

            HistoryTab(logs, onClearHistory)
        }
    }
}

@Composable
private fun ResultCard(result: TapResult, onDismiss: () -> Unit) {
    val (title, color, lines) = when (result) {
        is TapResult.Accepted ->
            Triple("ACCEPTED", MaterialTheme.colorScheme.primary, listOf(result.label))

        is TapResult.Denied ->
            Triple(
                "DENIED",
                MaterialTheme.colorScheme.error,
                listOf("Closest: ${result.label}", "Mismatched: ${result.fields.joinToString(", ")}"),
            )

        is TapResult.Enrolled ->
            Triple("ENROLLED", MaterialTheme.colorScheme.primary, listOf(result.label))

        is TapResult.NoEnrolledDevices ->
            Triple(
                "NO DEVICES ENROLLED",
                MaterialTheme.colorScheme.error,
                listOf("Switch to the Enroll tab and tap a device first."),
            )

        is TapResult.Error ->
            Triple("ERROR", MaterialTheme.colorScheme.error, listOf(result.message))
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = color,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Tap to continue")
            }
        }
    }
}

@Composable
private fun VerifyTab(enrolled: List<EnrolledDevice>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Verify mode", style = MaterialTheme.typography.titleMedium)
            Text(
                "Hold the Sender against the back of this phone. " +
                    "The tap only reads hashes — nothing here is deleted.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Enrolled devices: ${enrolled.size}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun EnrollTab(enrolled: List<EnrolledDevice>, onDelete: (Long) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Enroll mode", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tap a Sender to save its identity hashes. Later taps in " +
                    "Verify mode are checked against these.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (enrolled.isEmpty()) {
                Text("No devices enrolled yet.", style = MaterialTheme.typography.bodySmall)
            } else {
                enrolled.forEach { device ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(device.label, style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = { onDelete(device.id) }) {
                            Text("Remove")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryTab(logs: List<CheckLog>, onClear: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("History", style = MaterialTheme.typography.titleMedium)
                if (logs.isNotEmpty()) {
                    OutlinedButton(onClick = onClear) { Text("Clear") }
                }
            }
            if (logs.isEmpty()) {
                Text("No checks yet.", style = MaterialTheme.typography.bodySmall)
            } else {
                logs.take(20).forEach { log -> LogRow(log) }
            }
        }
    }
}

private val dateFormat = SimpleDateFormat("MMM d, HH:mm:ss", Locale.US)

@Composable
private fun LogRow(log: CheckLog) {
    val verdict = if (log.accepted) "✓" else "✗"
    val color = if (log.accepted) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(verdict, color = color, fontFamily = FontFamily.Monospace)
            Text(
                dateFormat.format(Date(log.timestamp)),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                log.deviceLabel ?: "—",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (log.mismatchedFields.isNotBlank()) {
            Text(
                "Mismatched: ${log.mismatchedFields}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CheckerScreenPreview() {
    MaterialTheme {
        CheckerScreen(
            mode = VerifyMode.VERIFY,
            onModeChange = {},
            enrolled = emptyList(),
            logs = emptyList(),
            lastResult = TapResult.Denied("Device 1", listOf("Model")),
            onDeleteEnrolled = {},
            onClearHistory = {},
            onDismissResult = {},
            nfcEnabled = true,
        )
    }
}
