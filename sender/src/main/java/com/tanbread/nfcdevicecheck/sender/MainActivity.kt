package com.tanbread.nfcdevicecheck.sender

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.tanbread.nfcdevicecheck.sender.identity.SystemInfoCollector
import com.tanbread.nfcdevicecheck.sender.nfc.PayloadProvider
import com.tanbread.nfcdevicecheck.shared.hashing.HashBundle

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                SenderScreen(
                    initialState = { currentBundle() },
                    onRefresh = { currentBundle() },
                )
            }
        }
    }

    private fun currentBundle(): HashBundle = SystemInfoCollector.hashBundle(this)
        .also { PayloadProvider.update(it) }
}

@Composable
fun SenderScreen(
    initialState: () -> HashBundle,
    onRefresh: () -> HashBundle,
) {
    var bundle by mutableStateOf(initialState())

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

            Button(
                onClick = { bundle = onRefresh() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Re-collect system info")
            }

            HashPreviewCard(bundle)

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
private fun HashPreviewCard(bundle: HashBundle) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "System info hashed (${bundle.fields.size} fields)",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Only these hashes are sent over NFC — raw values stay on this phone.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            bundle.fields.forEach { (label, hash) -> HashLine(label, hash) }
        }
    }
}

@Composable
private fun HashLine(label: String, hash: String) {
    Text(
        text = "$label: ${hash.take(16)}…",
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

@Preview(showBackground = true)
@Composable
fun SenderScreenPreview() {
    MaterialTheme {
        SenderScreen(
            initialState = { previewBundle() },
            onRefresh = { previewBundle() },
        )
    }
}

private fun previewBundle(): HashBundle = HashBundle.fromRaw(
    mapOf(
        "Manufacturer" to "Google",
        "Model" to "Pixel 8",
        "Android version" to "17",
        "Build number" to "BP2A.250605.031",
        "GPU renderer" to "Adreno (TM) 740",
    ),
)
