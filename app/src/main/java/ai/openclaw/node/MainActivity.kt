package ai.openclaw.node

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.openclaw.node.service.NodeService

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Permissions granted/denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request permissions
        val perms = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
        )
        if (Build.VERSION.SDK_INT >= 33) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())

        setContent {
            OpenClawTheme {
                OpenClawApp(
                    onConnect = { url, token, name -> startNodeService(url, token, name) },
                    onDisconnect = { stopNodeService() }
                )
            }
        }
    }

    private fun startNodeService(url: String, token: String, name: String) {
        val intent = Intent(this, NodeService::class.java).apply {
            putExtra(NodeService.EXTRA_GATEWAY_URL, url)
            putExtra(NodeService.EXTRA_AUTH_TOKEN, token)
            putExtra(NodeService.EXTRA_NODE_NAME, name)
        }
        startForegroundService(intent)
    }

    private fun stopNodeService() {
        stopService(Intent(this, NodeService::class.java))
    }
}

// ── Theme ──

private val DarkBg = Color(0xFF0F1117)
private val Surface = Color(0xFF1A1D27)
private val Surface2 = Color(0xFF232734)
private val Accent = Color(0xFF6C63FF)
private val Accent2 = Color(0xFF00D4AA)
private val TextPrimary = Color(0xFFE4E6EF)
private val TextDim = Color(0xFF8B8FA3)

@Composable
fun OpenClawTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            secondary = Accent2,
            background = DarkBg,
            surface = Surface,
            onPrimary = Color.White,
            onBackground = TextPrimary,
            onSurface = TextPrimary,
        ),
        content = content
    )
}

// ── App ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenClawApp(
    onConnect: (String, String, String) -> Unit,
    onDisconnect: () -> Unit
) {
    var gatewayUrl by remember { mutableStateOf("ws://10.100.0.1:18789") }
    var authToken by remember { mutableStateOf("") }
    var nodeName by remember { mutableStateOf("dj-phone") }
    var connected by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🐾", fontSize = 24.sp)
                        Column {
                            Text("OpenClaw", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text("Agent Node", color = TextDim, fontSize = 12.sp)
                        }
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (connected) Accent2 else Color(0xFFFF4757))
                        )
                        Text(
                            if (connected) "Connected" else "Disconnected",
                            color = TextDim,
                            fontSize = 13.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            // Setup card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Connection", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                        OutlinedTextField(
                            value = gatewayUrl,
                            onValueChange = { gatewayUrl = it },
                            label = { Text("Gateway URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = authToken,
                            onValueChange = { authToken = it },
                            label = { Text("Auth Token") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = nodeName,
                            onValueChange = { nodeName = it },
                            label = { Text("Node Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                if (connected) {
                                    onDisconnect()
                                    connected = false
                                } else {
                                    onConnect(gatewayUrl, authToken, nodeName)
                                    connected = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (connected) Color(0xFFFF4757) else Accent
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                if (connected) Icons.Default.LinkOff else Icons.Default.Link,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (connected) "Disconnect" else "Connect", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Capabilities card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Capabilities", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                        val caps = listOf(
                            "📸" to "Camera (front/back)",
                            "📍" to "Location (GPS)",
                            "🔔" to "Notifications",
                            "📱" to "Screen Control",
                            "📁" to "File Access",
                            "🔋" to "Sensors & Battery",
                            "🔦" to "Flashlight",
                            "📳" to "Vibration",
                            "🗣️" to "Text-to-Speech",
                            "💬" to "SMS",
                            "📞" to "Phone Calls",
                            "👥" to "Contacts",
                            "📅" to "Calendar",
                            "♿" to "Screen Automation (A11y)",
                        )

                        caps.forEach { (emoji, label) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(emoji, fontSize = 18.sp)
                                Spacer(Modifier.width(12.dp))
                                Text(label, color = TextPrimary, fontSize = 14.sp)
                                Spacer(Modifier.weight(1f))
                                Text("✓", color = Accent2, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}
