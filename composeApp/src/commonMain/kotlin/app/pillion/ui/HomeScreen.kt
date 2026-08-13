package app.pillion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pillion.core.MirrorState
import app.pillion.core.UpdateInfo

@Composable
internal fun HomeScreen(
    state: MirrorState,
    update: UpdateInfo?,
    onOpenSettings: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 28.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (update != null) {
            UpdateReminder(update.version, onClick = onOpenSettings)
            Spacer(Modifier.height(12.dp))
        }
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.align(Alignment.TopCenter)) { Wordmark() }
            IconButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        // Always visible — this is the diagnostics panel (not a system status bar). Shown on the
        // home screen even before Start mirroring so a missing CCU is obvious without Console.
        ConnectionPanel(state)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = if (state is MirrorState.Idle) Arrangement.Top else Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state is MirrorState.Idle) {
                    ConnectGuide()
                } else {
                    StatusDisplay(state)
                }
            }
        }
        PrimaryButton(state, onStart, onStop)
    }
}

@Composable
private fun ConnectionPanel(state: MirrorState) {
    val (title, body, warning) = when (state) {
        is MirrorState.Idle -> Triple(
            "Connection status",
            state.hint ?: "Scanning for the bike’s CCU…",
            state.hint?.contains("NOT found", ignoreCase = true) == true ||
                state.hint?.contains("none", ignoreCase = true) == true,
        )
        MirrorState.Connecting -> Triple("Connection status", "Connecting to dash…", false)
        is MirrorState.Streaming -> Triple(
            "Streaming",
            "${formatFps(state.fps)} fps • ${state.kbPerFrame} KB/frame",
            false,
        )
        is MirrorState.Broadcasting -> Triple(
            state.headline,
            state.detail ?: "Broadcast active — open this screen (not Maps) to read live diagnostics.",
            state.headline.contains("failed", ignoreCase = true) ||
                state.detail?.contains("NOT found") == true ||
                state.detail?.contains("emulator") == true,
        )
        is MirrorState.Error -> Triple("Disconnected", state.message, true)
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (warning) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(
                    if (warning) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (warning) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun UpdateReminder(version: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Update available — $version",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun Wordmark() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Pillion",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
        Text(
            "your screen, on the bike dash",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusDisplay(state: MirrorState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (state) {
            is MirrorState.Idle -> Unit
            MirrorState.Connecting -> {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text("Connecting to dash…", style = MaterialTheme.typography.titleMedium)
            }
            is MirrorState.Streaming -> {
                Text(
                    formatFps(state.fps),
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "fps",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is MirrorState.Broadcasting -> {
                if (state.fps != null) {
                    Text(
                        formatFps(state.fps),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "fps on dash link",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    "Keep Pillion open to watch this panel — Maps/Waze hides it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            is MirrorState.Error -> Unit
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(Modifier.size(12.dp).clip(CircleShape).background(color))
}

@Composable
private fun ConnectGuide() {
    val steps = listOf(
        "Pair your phone with the bike in Bluetooth settings (one time). StreetCross must be closed — only one app can hold the NaviLite link.",
        "Mount the phone in landscape and turn on auto-rotate, so the map fills the dash.",
        "On the bike, switch the dash to Navigation mode (same as for StreetCross).",
        "Check Connection status above — it must say CCU found before you start.",
        "Tap Start mirroring, then choose \"Pillion Mirror\" / Entire screen and allow capture.",
        "Stay on Pillion a few seconds to confirm Transport: bike, then open Maps/Waze.",
    )
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Before you ride",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        steps.forEachIndexed { index, step -> StepRow(index + 1, step) }
    }
}

@Composable
private fun StepRow(number: Int, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$number",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Spacer(Modifier.size(14.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PrimaryButton(state: MirrorState, onStart: () -> Unit, onStop: () -> Unit) {
    val active = state is MirrorState.Streaming || state is MirrorState.Connecting ||
        state is MirrorState.Broadcasting
    Button(
        onClick = if (active) onStop else onStart,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(
            if (active) "Stop mirroring" else "Start mirroring",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun formatFps(fps: Double): String {
    val rounded = (fps * 10).toInt()
    return "${rounded / 10}.${rounded % 10}"
}
