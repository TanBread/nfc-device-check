package com.tanbread.nfcdevicecheck.sender

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.tanbread.nfcdevicecheck.sender.identity.IdentityCollector
import com.tanbread.nfcdevicecheck.sender.nfc.PayloadProvider
import com.tanbread.nfcdevicecheck.shared.hashing.HashBundle

class MainActivity : ComponentActivity() {

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermission.launch(Manifest.permission.READ_PHONE_STATE)
        }

        setContent {
            MaterialTheme {
                SenderScreen(
                    onRefresh = { refreshState() },
                    onManualEid = { eid ->
                        IdentityCollector.storeManualEid(this, eid)
                        PayloadProvider.update(currentIdentity())
                    },
                    initialState = { refreshState() },
                )
            }
        }
    }

    private fun refreshState(): SenderUiState {
        val eidApi = IdentityCollector.eidFromApi(this) != null
        return SenderUiState(
            deviceOwner = IdentityCollector.hasDeviceOwner(this),
            phoneState = IdentityCollector.hasPhoneState(this),
            eidFromApi = eidApi,
            manualEid = IdentityCollector.storedManualEid(this) ?: "",
            bundle = currentIdentity(),
        )
    }

    private fun currentIdentity(): HashBundle {
        val eid = IdentityCollector.eidFromApi(this)
            ?: IdentityCollector.storedManualEid(this)
        return HashBundle.fromRaw(
            eid = eid,
            imei1 = IdentityCollector.imei1(this),
            imei2 = IdentityCollector.imei2(this),
            androidVersion = IdentityCollector.androidVersion(),
            buildNumber = IdentityCollector.buildNumber(),
        ).also { PayloadProvider.update(it) }
    }
}

data class SenderUiState(
    val deviceOwner: Boolean = false,
    val phoneState: Boolean = false,
    val eidFromApi: Boolean = false,
    val manualEid: String = "",
    val bundle: HashBundle? = null,
)

@Composable
fun SenderScreen(
    onRefresh: () -> SenderUiState,
    onManualEid: (String) -> Unit,
    initialState: () -> SenderUiState,
) {
    var state by mutableStateOf(initialState())
    var eidInput by mutableStateOf("")

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "NFC Sender", style = MaterialTheme.typography.headlineMedium)

            StatusCard(state)

            Button(
                onClick = { state = onRefresh() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Refresh identity hashes")
            }

            if (state.bundle != null) {
                HashPreviewCard(state.bundle!!)
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("EID fallback (32 hex chars)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "If the EID shows unavailable, copy it from " +
                            "Settings > About phone > SIM status (or dial *#06#) and save it.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = eidInput,
                        onValueChange = { eidInput = it.trim().lowercase() },
                        label = { Text("Manual EID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            if (eidInput.length == 32 && eidInput.all { it in "0123456789abcdef" }) {
                                onManualEid(eidInput)
                                eidInput = ""
                            }
                        },
                        enabled = eidInput.length == 32 &&
                            eidInput.all { it in "0123456789abcdef" },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Save EID")
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Ready to tap", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Keep this screen open and hold the back of this phone " +
                            "against the Checker.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(state: SenderUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Setup status", style = MaterialTheme.typography.titleMedium)
            StatusLine("Device owner", state.deviceOwner)
            StatusLine("READ_PHONE_STATE", state.phoneState)
            StatusLine("EID readable via API", state.eidFromApi)
        }
    }
}

@Composable
private fun StatusLine(label: String, ok: Boolean) {
    Text(
        text = "${if (ok) "✓" else "✗"}  $label",
        style = MaterialTheme.typography.bodyMedium,
        color = if (ok) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun HashPreviewCard(bundle: HashBundle) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Hash preview (what gets sent)", style = MaterialTheme.typography.titleMedium)
            HashLine("EID", bundle.eid)
            HashLine("IMEI 1", bundle.imei1)
            HashLine("IMEI 2", bundle.imei2)
            HashLine("Android", bundle.androidVersion)
            HashLine("Build", bundle.buildNumber)
        }
    }
}

@Composable
private fun HashLine(label: String, value: String?) {
    Text(
        text = "$label: ${value?.take(16) ?: "unavailable"}…",
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
    )
}

@Preview(showBackground = true)
@Composable
fun SenderScreenPreview() {
    MaterialTheme {
        SenderScreen(
            onRefresh = {
                SenderUiState(bundle = HashBundle.fromRaw(null, "1", null, "17", "B"))
            },
            onManualEid = {},
            initialState = {
                SenderUiState(bundle = HashBundle.fromRaw(null, "1", null, "17", "B"))
            },
        )
    }
}
