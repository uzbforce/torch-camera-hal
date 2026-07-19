package com.example

import android.Manifest
import java.util.Locale
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: TorchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.statusBars
                ) { innerPadding ->
                    TorchDashboardScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun TorchDashboardScreen(
    viewModel: TorchViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentMode by viewModel.currentMode.collectAsState()
    val isTorchActive by viewModel.isTorchActive.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val currentError by viewModel.currentError.collectAsState()

    // Setup native permission requester
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onCameraPermissionGranted()
        } else {
            viewModel.onCameraPermissionDenied()
        }
    }

    // Helper to check and request camera permission
    val triggerTorchRequest = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.onCameraPermissionGranted()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Immersive UI: Deep Slate Background
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF111318))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "TorchFix ",
                            color = Color(0xFFD1E4FF),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Light,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "API",
                            color = Color(0xFFFFB74D),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                    }
                    Text(
                        text = "A16 Camera2 Interface Active",
                        color = Color(0xFF919196),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    )
                }

                // Driver status dot
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .background(Color(0xFF1A1C1E), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (isTorchActive) Color(0xFF4ADE80) else Color(0xFF919196),
                                CircleShape
                            )
                    )
                    Text(
                        text = if (isTorchActive) "LINKED" else "READY",
                        color = if (isTorchActive) Color(0xFF4ADE80) else Color(0xFF919196),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Interactive glowing aura & Main Toggle Button with spring animations
            val activeScale by animateFloatAsState(
                targetValue = if (isTorchActive) 1.08f else 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "activeScale"
            )

            val innerColorStart by animateColorAsState(
                targetValue = if (isTorchActive) Color(0xFFFFB74D).copy(alpha = 0.35f) else Color(0xFF25272B),
                animationSpec = tween(400),
                label = "innerColorStart"
            )
            val innerColorEnd by animateColorAsState(
                targetValue = if (isTorchActive) Color(0xFF1E2126) else Color(0xFF1A1C1E),
                animationSpec = tween(400),
                label = "innerColorEnd"
            )

            val iconColor by animateColorAsState(
                targetValue = if (isTorchActive) Color(0xFFFFB74D) else Color(0xFF5A6876),
                animationSpec = tween(300),
                label = "iconColor"
            )

            val buttonBorderColor by animateColorAsState(
                targetValue = if (isTorchActive) Color(0xFFFFB74D).copy(alpha = 0.8f) else Color(0xFF303036),
                animationSpec = tween(450),
                label = "buttonBorderColor"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxWidth()
            ) {
                // Background Ambient Glow Effect
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    if (isTorchActive) Color(0xFFFFB74D).copy(alpha = 0.15f) else Color(0xFFFFB74D).copy(alpha = 0.02f),
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                )

                if (isTorchActive) {
                    val infiniteTransition = rememberInfiniteTransition(label = "immersive_halo")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.25f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "scale"
                    )
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 0.05f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 2000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "alpha"
                    )

                    // Outer pulsing rings
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .scale(pulseScale)
                            .border(1.dp, Color(0xFFFFB74D).copy(alpha = pulseAlpha), CircleShape)
                    )
                }

                // Interactive outer frame with animated scale and border transitions
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .scale(activeScale)
                        .clip(CircleShape)
                        .background(Color(0xFF1A1C1E))
                        .border(4.dp, buttonBorderColor, CircleShape)
                        .clickable {
                            viewModel.toggleTorch { triggerTorchRequest() }
                        }
                        .testTag("power_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(176.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(innerColorStart, innerColorEnd)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PowerSettingsNew,
                                contentDescription = "Toggle Torch Status",
                                tint = iconColor,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isTorchActive) "ON" else "OFF",
                                color = iconColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                        }
                    }
                }
            }

            // Driver detail banner
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFF4ADE80), CircleShape)
                    )
                    Text(
                        text = "DEVICE DRIVER LINKED",
                        color = Color(0xFF919196),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "Bypassing system intent filters via hardware camera context.",
                    color = Color(0xFFE2E2E6).copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            // Warning Banner for Errors
            AnimatedVisibility(
                visible = currentError != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1E22)),
                    border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Alert Error",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = currentError ?: "",
                            color = Color(0xFFFFCDD2),
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Hardware Luminance & Overdrive Control Panel
            val torchStrength by viewModel.torchStrength.collectAsState()
            val maxStrength = viewModel.getMaxTorchStrengthLevel()

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1E)),
                border = BorderStroke(1.dp, Color(0xFF303036)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFF2D2F36), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Bolt,
                                    contentDescription = "Brightness level",
                                    tint = Color(0xFFFFB74D),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Hardware Luminance",
                                    color = Color(0xFFE2E2E6),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (currentMode == TorchMode.STANDARD) "System driver strength controller" else "Camera HAL bypass active",
                                    color = Color(0xFF919196),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        if (currentMode == TorchMode.STANDARD && maxStrength > 1) {
                            Text(
                                text = "Lvl $torchStrength/$maxStrength",
                                color = Color(0xFFFFB74D),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .background(Color(0xFF2D2F36), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (currentMode == TorchMode.STANDARD) {
                        if (maxStrength > 1) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Slider(
                                    value = torchStrength.toFloat(),
                                    onValueChange = { viewModel.setTorchStrengthLevel(it.toInt()) },
                                    valueRange = 1f..maxStrength.toFloat(),
                                    steps = maxStrength - 2,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFFFFB74D),
                                        activeTrackColor = Color(0xFFFFB74D),
                                        inactiveTrackColor = Color(0xFF111318)
                                    ),
                                    modifier = Modifier.height(24.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Min Strength", color = Color(0xFF919196), fontSize = 10.sp)
                                    Text("Max Overdrive", color = Color(0xFFFFB74D), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Text(
                                text = "Hardware brightness control requires Android 13+ (API 33) and vendor device HAL support. Your hardware uses 1-step logic.",
                                color = Color(0xFF919196),
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    } else {
                        // CAMERA_API Mode
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF2D201A), RoundedCornerShape(12.dp))
                                .border(BorderStroke(1.dp, Color(0xFFFFB74D).copy(alpha = 0.2f)), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFFFFB74D), CircleShape)
                                )
                                Text(
                                    text = "100% MAXIMUM OVERDRIVE ACTIVE",
                                    color = Color(0xFFFFB74D),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Camera HAL direct session overrides system software limits, forcing the LED hardware flash to full hardware capability.",
                                color = Color(0xFFFFE0B2),
                                fontSize = 10.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }

            // Mode Selection cards styled like custom sliders
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "HARDWARE TUNNEL SELECTION",
                    color = Color(0xFF919196),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    fontFamily = FontFamily.Monospace
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ModeCard(
                        title = "Camera API",
                        subtitle = "ROM WORKAROUND",
                        description = "Directly triggers the hardware camera HAL.",
                        isSelected = currentMode == TorchMode.CAMERA_API,
                        icon = Icons.Filled.CameraAlt,
                        onClick = { viewModel.setMode(TorchMode.CAMERA_API) },
                        modifier = Modifier.weight(1f)
                    )
                    ModeCard(
                        title = "System API",
                        subtitle = "STANDARD TUNNEL",
                        description = "Lightweight, but often disabled on custom ROMs.",
                        isSelected = currentMode == TorchMode.STANDARD,
                        icon = Icons.Filled.FlashlightOn,
                        onClick = { viewModel.setMode(TorchMode.STANDARD) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Diagnostics Terminal Window (Material 3 rounded-[28px])
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1E)),
                border = BorderStroke(1.dp, Color(0xFF303036)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.9f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFF4ADE80), CircleShape)
                            )
                            Text(
                                text = "DIAGNOSTICS KERNEL LOG",
                                color = Color(0xFF4ADE80),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        IconButton(
                            onClick = { viewModel.clearLogs() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = "Clear logs panel",
                                tint = Color(0xFF919196),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val listState = rememberLazyListState()
                    LaunchedEffect(logs.size) {
                        if (logs.isNotEmpty()) {
                            listState.animateScrollToItem(logs.size - 1)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logs, key = { it.id }) { log ->
                            val levelColor = when (log.level) {
                                LogLevel.INFO -> Color(0xFFD1E4FF)
                                LogLevel.SUCCESS -> Color(0xFF4ADE80)
                                LogLevel.WARNING -> Color(0xFFFFB74D)
                                LogLevel.ERROR -> Color(0xFFFF5252)
                            }
                            val levelTag = when (log.level) {
                                LogLevel.INFO -> "INFO"
                                LogLevel.SUCCESS -> " OK "
                                LogLevel.WARNING -> "WARN"
                                LogLevel.ERROR -> "FAIL"
                            }
                            Text(
                                text = "[${log.timestamp}] [$levelTag] ${log.text}",
                                color = levelColor,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }

            // Beautiful Navigation Bar Placeholder (Matches "Immersive UI" template)
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(5.dp)
                    .background(Color(0xFFE2E2E6).copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 2.dp)
            )
        }
    }
}

@Composable
fun ModeCard(
    title: String,
    subtitle: String,
    description: String,
    isSelected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF2D2F36) else Color(0xFF1A1C1E)
        ),
        border = BorderStroke(
            1.5.dp,
            if (isSelected) Color(0xFFFFB74D) else Color(0xFF303036)
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) Color(0xFFFFB74D) else Color(0xFF919196),
                    modifier = Modifier.size(16.dp)
                )
                Column {
                    Text(
                        text = title,
                        color = if (isSelected) Color.White else Color(0xFFE2E2E6),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        color = if (isSelected) Color(0xFFFFB74D) else Color(0xFF919196),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
            Text(
                text = description,
                color = Color(0xFF919196),
                fontSize = 10.sp,
                lineHeight = 13.sp
            )
        }
    }
}
