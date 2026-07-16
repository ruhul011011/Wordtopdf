package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.HistoryEntity
import com.example.ui.theme.*
import com.example.viewmodel.AppViewModel
import com.example.viewmodel.ProcessingState
import com.example.viewmodel.Screen
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: AppViewModel = viewModel()
                val currentScreen = viewModel.currentScreen

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BackgroundColor)
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            },
                            label = "screen_transitions"
                        ) { screen ->
                            when (screen) {
                                is Screen.Dashboard -> DashboardScreen(viewModel)
                                is Screen.Convert -> ConvertScreen(viewModel)
                                is Screen.Editor -> EditorScreen(viewModel)
                                is Screen.Merge -> MergeScreen(viewModel)
                                is Screen.Optimize -> OptimizeScreen(viewModel)
                                is Screen.AiSummary -> AiSummaryScreen(viewModel)
                                is Screen.Security -> SecurityScreen(viewModel)
                            }
                        }

                        // Persistent Privacy HUD (Floating indicator in bottom right)
                        PrivacyHud(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        )

                        // Global Processing State Dialog
                        ProcessingOverlay(viewModel)
                    }
                }
            }
        }
    }
}

// --- Glassmorphism UI Modifiers & Helpers ---

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
fun PulsingDot(
    modifier: Modifier = Modifier,
    color: Color = OptimizeGreen
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .size(10.dp)
            .alpha(alpha)
            .background(color, CircleShape)
            .border(2.dp, color.copy(alpha = scale / 2), CircleShape)
    )
}

@Composable
fun PrivacyHud(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(50.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PulsingDot(color = OptimizeGreen)
            Text(
                text = "LOCAL PROCESSING ACTIVE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// --- Dashboard Screen ---

@Composable
fun DashboardScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val history by viewModel.historyList.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    var showClearHistoryDialog by remember { mutableStateOf(false) }

    // File pickers to support interactive "Dropzone" imports
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.currentScreen = Screen.Convert
            Toast.makeText(context, "Image imported successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.currentScreen = Screen.AiSummary
            Toast.makeText(context, "Document imported! Opening AI summary...", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "WordToPDFHub",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = PrimaryColor,
                modifier = Modifier.clickable { viewModel.currentScreen = Screen.Dashboard }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { Toast.makeText(context, "Local Processing Secured (AES-256)", Toast.LENGTH_SHORT).show() }) {
                    Icon(Icons.Default.Shield, contentDescription = "Security Status", tint = OptimizeGreen)
                }
            }
        }

        // Active Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50.dp))
                .background(Color(0x1FADC6FF))
                .border(1.dp, Color(0x33ADC6FF), RoundedCornerShape(50.dp))
                .padding(vertical = 8.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PulsingDot(color = PrimaryColor)
                Text(
                    text = "NEW: BATCH PROCESSING IS LIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Hero Headers
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = buildAnnotatedString {
                    append("PDF tools that feel\n")
                    withStyle(style = SpanStyle(color = PrimaryColor, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Black)) {
                        append("effortless")
                    }
                },
                style = MaterialTheme.typography.displayLarge,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.ExtraBold,
                color = OnSurface
            )

            Text(
                text = "Merge, split, compress, convert, and sign — right on your device. Free, private, and exceptionally fast.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = OnSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Command Launcher Input
        GlassCard(
            borderColor = Color(0x4DADC6FF),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = viewModel.commandInput,
                onValueChange = { viewModel.commandInput = it },
                placeholder = { Text("What do you want to do? (e.g., 'Encrypt')", color = OnSurfaceVariant.copy(alpha = 0.5f)) },
                leadingIcon = { Icon(Icons.Default.Terminal, contentDescription = "Terminal Prompt", tint = PrimaryColor) },
                trailingIcon = {
                    if (viewModel.commandInput.isNotEmpty()) {
                        IconButton(onClick = { viewModel.commandInput = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear input", tint = OnSurfaceVariant)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("command_prompt_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryColor,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color(0x11FFFFFF),
                    unfocusedContainerColor = Color(0x08FFFFFF),
                    focusedTextColor = OnSurface,
                    unfocusedTextColor = OnSurface
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Live filter quick triggers based on terminal prompt
            val query = viewModel.commandInput.lowercase(Locale.ROOT)
            if (query.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Matching Tools:", style = MaterialTheme.typography.labelSmall, color = PrimaryColor)
                Spacer(modifier = Modifier.height(6.dp))

                val matchedScreens = mutableListOf<Pair<String, Screen>>()
                if ("conv" in query || "pdf" in query || "image" in query || "txt" in query) matchedScreens.add("Convert Images/Text to PDF" to Screen.Convert)
                if ("edit" in query || "draw" in query || "sketch" in query || "annot" in query) matchedScreens.add("PDF Editor & Annotation Canvas" to Screen.Editor)
                if ("merg" in query || "comb" in query || "join" in query) matchedScreens.add("Merge Documents" to Screen.Merge)
                if ("opt" in query || "comp" in query || "shrink" in query || "size" in query) matchedScreens.add("Optimize / Compress" to Screen.Optimize)
                if ("ai" in query || "sum" in query || "gem" in query) matchedScreens.add("AI Document Summary" to Screen.AiSummary)
                if ("sec" in query || "enc" in query || "pass" in query || "lock" in query || "dec" in query) matchedScreens.add("Security Portal (AES Encrypt)" to Screen.Security)

                if (matchedScreens.isEmpty()) {
                    Text("No matching tools found. Try typing 'convert', 'ai', or 'encrypt'.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        matchedScreens.forEach { (label, targetScreen) ->
                            Button(
                                onClick = {
                                    viewModel.commandInput = ""
                                    viewModel.currentScreen = targetScreen
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0x33ADC6FF)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(label, style = MaterialTheme.typography.bodySmall, color = PrimaryColor)
                            }
                        }
                    }
                }
            }
        }

        // Dropzone Primary Portal Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0x0AADC6FF))
                .border(2.dp, Brush.sweepGradient(listOf(PrimaryColor, SecondaryColor, TertiaryColor, PrimaryColor)), RoundedCornerShape(24.dp))
                .clickable { imagePickerLauncher.launch("image/*") }
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color(0x1FADC6FF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.UploadFile,
                        contentDescription = "Upload Icon",
                        tint = PrimaryColor,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = "Import a photo or files to get started",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    color = OnSurface,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Tap to pick from device — processing is kept 100% offline",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = OnSurfaceVariant
                )

                Row(
                    modifier = Modifier.padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "LOCAL CRYPTO",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "MAX 100MB",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // Telemetry Row (4 glass cards)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("PERFORMANCE TELEMETRY", style = MaterialTheme.typography.labelMedium, color = PrimaryColor)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassCard(modifier = Modifier.weight(1f)) {
                    Text("ENCRYPTION", style = MaterialTheme.typography.labelSmall, color = PrimaryColor)
                    Text("AES-256", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Files never leave your phone", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, modifier = Modifier.alpha(0.6f))
                }
                GlassCard(modifier = Modifier.weight(1f)) {
                    Text("WASM-SPEED", style = MaterialTheme.typography.labelSmall, color = OptimizeGreen)
                    Text("FAST", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = OptimizeGreen)
                    Text("Ready in <800ms locally", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, modifier = Modifier.alpha(0.6f))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassCard(modifier = Modifier.weight(1f)) {
                    Text("COVERAGE", style = MaterialTheme.typography.labelSmall, color = AiCyan)
                    Text("190 countries", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Global trust network", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, modifier = Modifier.alpha(0.6f))
                }
                GlassCard(modifier = Modifier.weight(1f)) {
                    Text("RATING", style = MaterialTheme.typography.labelSmall, color = SecurityAmber)
                    Text("4.9/5", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = SecurityAmber)
                    Text("Based on peer reviews", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, modifier = Modifier.alpha(0.6f))
                }
            }
        }

        // Bento Grid: Popular Tools
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("POPULAR TOOLS", style = MaterialTheme.typography.labelMedium, color = PrimaryColor)
                    Text("Digital Precision Workbench", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
            }

            // Bento Grid Items:
            // 1. Convert Card (Spans full width for prominence)
            BentoItemCard(
                title = "Convert Document Suite",
                desc = "Convert any file format. Local image-to-PDF, and dynamic high-fidelity text conversions.",
                accentColor = ConvertBlue,
                icon = Icons.Default.Sync,
                badge = "HI-FI CONVERSION",
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bento_convert_card"),
                pills = listOf("IMAGE TO PDF", "TEXT TO PDF"),
                onClick = { viewModel.currentScreen = Screen.Convert }
            )

            // Row containing PDF Editor & Merge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BentoItemCard(
                    title = "PDF Editor",
                    desc = "Draw freehand annotations or sketch technical drafts.",
                    accentColor = EditPurple,
                    icon = Icons.Default.EditNote,
                    badge = "EDITABLE",
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.currentScreen = Screen.Editor }
                )

                BentoItemCard(
                    title = "Merge Documents",
                    desc = "Combine text documents into a unified output PDF.",
                    accentColor = ErrorColor,
                    icon = Icons.Default.CallMerge,
                    badge = "OFFLINE",
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.currentScreen = Screen.Merge }
                )
            }

            // Row containing Optimize & AI summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BentoItemCard(
                    title = "Optimize Size",
                    desc = "Compress image files with granular control over quality metrics.",
                    accentColor = OptimizeGreen,
                    icon = Icons.Default.Compress,
                    badge = "COMPRESSION",
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.currentScreen = Screen.Optimize }
                )

                BentoItemCard(
                    title = "AI Document Summary",
                    desc = "Instant takeaways and summaries powered by Gemini 3.5.",
                    accentColor = AiCyan,
                    icon = Icons.Default.Bolt,
                    badge = "GEMINI-POWERED",
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.currentScreen = Screen.AiSummary }
                )
            }

            // Security Portal
            BentoItemCard(
                title = "Security Portal Locker",
                desc = "Password protect, encrypt, or decrypt files securely using robust local AES-256 encryption.",
                accentColor = SecurityAmber,
                icon = Icons.Default.Lock,
                badge = "ZERO TRUST LOCKER",
                modifier = Modifier.fillMaxWidth(),
                pills = listOf("PASSWORD LOCK", "AES-256 DECRYPT"),
                onClick = { viewModel.currentScreen = Screen.Security }
            )
        }

        // Room Database Persistence: Processing History Log
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("LOCAL PERSISTENT HISTORY", style = MaterialTheme.typography.labelMedium, color = PrimaryColor)
                    Text("Your Document Vault", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }

                if (history.isNotEmpty()) {
                    TextButton(onClick = { showClearHistoryDialog = true }) {
                        Text("Clear Vault", color = ErrorColor)
                    }
                }
            }

            if (history.isEmpty()) {
                GlassCard(
                    borderColor = Color(0x1FADC6FF),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.History, contentDescription = "History Empty", tint = OnSurfaceVariant, modifier = Modifier.size(48.dp))
                        Text("Vault is empty", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Documents processed in this session will show up securely here for immediate access and sharing.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = OnSurfaceVariant)
                    }
                }
            } else {
                history.forEach { item ->
                    HistoryLogItem(item = item, onDelete = { viewModel.deleteHistoryItem(item.id) }, context = context)
                }
            }
        }

        // Workflow Steps (How It Works)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("WORKFLOW LOGIC", style = MaterialTheme.typography.labelMedium, color = PrimaryColor)
            Text("Three steps. That's it.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

            WorkflowStepRow(num = "01", title = "Drop your file", desc = "Pick a photo, file, or paste text into any tool. Multi-mode clipboard works completely offline.")
            WorkflowStepRow(num = "02", title = "Tweak the details", desc = "Specify names, passwords, annotations, or sliders. No hidden servers or usage limits.")
            WorkflowStepRow(num = "03", title = "Download in seconds", desc = "Your document is saved directly to your Downloads folder — clean, free, and watermark-free.")
        }

        // Testimonials (Loved by document nerds)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("COMMUNITY TRUST", style = MaterialTheme.typography.labelMedium, color = PrimaryColor)
            Text("Loved by document enthusiasts", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

            TestimonialCard(
                quote = "\"It's the first document tool that doesn't feel like it was built in 2008. The offline rendering speeds are mind-blowing.\"",
                author = "Maya Ortiz",
                role = "Design lead, Northwind"
            )

            TestimonialCard(
                quote = "\"Encrypted 200 invoices locally while my coffee was still hot. Total security control is an absolute game-changer.\"",
                author = "Ben Alsaid",
                role = "Ops manager, Fabrikam"
            )
        }

        // FAQs (Accordion style)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("KNOWLEDGE BASE", style = MaterialTheme.typography.labelMedium, color = PrimaryColor)
            Text("Frequently Asked Questions", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

            FaqAccordion(
                question = "Do my files ever leave my device?",
                answer = "Never. Every conversion, drawing rendering, size optimization, and AES cryptographic locking takes place strictly within this sandbox app. No data is ever sent to a remote server."
            )

            FaqAccordion(
                question = "What is the difference between Live Mode and Simulation Mode?",
                answer = "In simulation mode, local utilities run with 100% full features, but AI operations use local mock responses. Configuring your GEMINI_API_KEY in the Secrets panel activates the live AI model."
            )

            FaqAccordion(
                question = "How do I access saved documents?",
                answer = "All files successfully converted, compressed, or encrypted are immediately saved to your device's primary Downloads folder inside the 'WordToPDFHub' directory. You can also share them directly from the local vault below."
            )
        }

        // Footer block
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "© 2026 WordToPDFHub. Secure client-side processing active.",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }

    // Clear Vault Confirmation Dialog
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear Vault") },
            text = { Text("Are you sure you want to permanently clear your persistent local history? Saved files in your Downloads folder will not be affected.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    showClearHistoryDialog = false
                    Toast.makeText(context, "Vault successfully cleared!", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Clear All", color = ErrorColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// --- Composable Sub-Components ---

@Composable
fun BentoItemCard(
    title: String,
    desc: String,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: String,
    modifier: Modifier = Modifier,
    pills: List<String> = emptyList(),
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = title, tint = accentColor, modifier = Modifier.size(24.dp))
                }

                Box(
                    modifier = Modifier
                        .background(accentColor.copy(alpha = 0.1f), RoundedCornerShape(50.dp))
                        .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(50.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = badge, style = MaterialTheme.typography.labelSmall, color = accentColor, fontWeight = FontWeight.Bold)
                }
            }

            Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = OnSurface)
            Text(text = desc, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, minLines = 2, maxLines = 3, overflow = TextOverflow.Ellipsis)

            if (pills.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    pills.forEach { pillText ->
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(50.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(pillText, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = OnSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryLogItem(
    item: HistoryEntity,
    onDelete: () -> Unit,
    context: Context
) {
    val dateString = remember(item.timestamp) {
        val sdf = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.ROOT)
        sdf.format(Date(item.timestamp))
    }

    GlassCard(
        borderColor = Color(0x0AADC6FF),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0x0FFFFFFF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (item.toolName) {
                            "Image to PDF", "Text to PDF" -> Icons.Default.PictureAsPdf
                            "PDF Annotator" -> Icons.Default.Gesture
                            "AI Summary" -> Icons.Default.AutoAwesome
                            "Security (Encrypt)" -> Icons.Default.Lock
                            else -> Icons.Default.InsertDriveFile
                        },
                        contentDescription = "Tool Icon",
                        tint = PrimaryColor
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.filename,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.toolName, style = MaterialTheme.typography.labelSmall, color = PrimaryColor)
                        Text("•", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text(item.fileSize, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text("•", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        Text(dateString, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    }
                }
            }

            Row {
                if (item.fileUriString != null) {
                    IconButton(onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/*"
                                putExtra(Intent.EXTRA_STREAM, Uri.parse(item.fileUriString))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Document"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error sharing file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = OnSurfaceVariant)
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete from history", tint = ErrorColor)
                }
            }
        }
    }
}

@Composable
fun WorkflowStepRow(num: String, title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color(0x1FADC6FF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(num, style = MaterialTheme.typography.labelMedium, color = PrimaryColor, fontWeight = FontWeight.Bold)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = OnSurface)
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
        }
    }
}

@Composable
fun TestimonialCard(quote: String, author: String, role: String) {
    GlassCard(borderColor = Color(0x0FFFFFFF)) {
        Text(quote, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = OnSurface)
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(32.dp).background(PrimaryColor.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                Text(author.take(1), style = MaterialTheme.typography.labelMedium, color = PrimaryColor)
            }
            Column {
                Text(author, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = OnSurface)
                Text(role, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
            }
        }
    }
}

@Composable
fun FaqAccordion(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }

    GlassCard(
        borderColor = Color(0x0FFFFFFF),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(question, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = OnSurface)
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = OnSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = Color(0x11FFFFFF))
                Spacer(modifier = Modifier.height(8.dp))
                Text(answer, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
            }
        }
    }
}

// --- Overlay components ---

@Composable
fun ProcessingOverlay(viewModel: AppViewModel) {
    val state = viewModel.processingState
    val context = LocalContext.current

    if (state !is ProcessingState.Idle) {
        Dialog(onDismissRequest = { if (state !is ProcessingState.Processing) viewModel.processingState = ProcessingState.Idle }) {
            GlassCard(
                borderColor = PrimaryColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (state) {
                        is ProcessingState.Processing -> {
                            CircularProgressIndicator(color = PrimaryColor)
                            Text("Processing Document Locally...", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Applying high-speed local engine operations. This will take a second.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = OnSurfaceVariant)
                        }

                        is ProcessingState.Success -> {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = OptimizeGreen, modifier = Modifier.size(48.dp))
                            Text("Success!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(state.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = OnSurface)

                            if (state.extraDetail != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0x0FFFFFFF), RoundedCornerShape(8.dp))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = state.extraDetail,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
                                        color = OnSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (state.savedUri != null) {
                                    Button(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "application/*"
                                                    putExtra(Intent.EXTRA_STREAM, state.savedUri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(intent, "Share Converted File"))
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Error sharing: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                                    ) {
                                        Text("Share", color = OnPrimaryColor)
                                    }
                                }

                                Button(
                                    onClick = { viewModel.processingState = ProcessingState.Idle },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x1FFFFFFF))
                                ) {
                                    Text("Done", color = OnSurface)
                                }
                            }
                        }

                        is ProcessingState.Error -> {
                            Icon(Icons.Default.Error, contentDescription = "Error", tint = ErrorColor, modifier = Modifier.size(48.dp))
                            Text("Operation Failed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ErrorColor)
                            Text(state.error, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = OnSurface)

                            Button(
                                onClick = { viewModel.processingState = ProcessingState.Idle },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorContainerColor)
                            ) {
                                Text("Close", color = OnErrorContainerColor)
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

// --- Screen 1: Convert Screen ---

@Composable
fun ConvertScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    var titleInput by remember { mutableStateOf("My Document") }
    var textInput by remember { mutableStateOf("") }
    var outputFileName by remember { mutableStateOf("converted_text") }

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var imageOutputName by remember { mutableStateOf("converted_image") }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
        }
    }

    var selectedDocxUri by remember { mutableStateOf<Uri?>(null) }
    var docxFileName by remember { mutableStateOf("") }
    var docxOutputName by remember { mutableStateOf("converted_docx") }

    val docxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedDocxUri = uri
            var name = "selected_document.docx"
            try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) {
                            name = it.getString(index)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            docxFileName = name
            val rawName = name.substringBeforeLast(".")
            if (rawName.isNotBlank()) {
                docxOutputName = "${rawName}_converted"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.currentScreen = Screen.Dashboard }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = PrimaryColor)
            }
            Text("Document Convert Suite", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = PrimaryColor
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Text("Image to PDF", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Text("Text to PDF", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
            }
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                Text("DOCX to PDF", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
            }
        }

        if (selectedTab == 0) {
            // Image to PDF tab
            GlassCard {
                Text("Select Input Image", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                if (selectedImageUri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x15FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = selectedImageUri,
                            contentDescription = "Selected Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { imagePicker.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x11FFFFFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Change Image", color = PrimaryColor)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x0FFFFFFF))
                            .clickable { imagePicker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Add image", tint = PrimaryColor, modifier = Modifier.size(48.dp))
                            Text("Tap to import image", color = OnSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = imageOutputName,
                    onValueChange = { imageOutputName = it },
                    label = { Text("Output PDF Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        val uri = selectedImageUri
                        if (uri == null) {
                            Toast.makeText(context, "Please select an image first.", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.convertImageToPdf(context, uri, imageOutputName)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                ) {
                    Text("Generate PDF Document", color = OnPrimaryColor, fontWeight = FontWeight.Bold)
                }
            }
        } else if (selectedTab == 1) {
            // Text to PDF tab
            GlassCard {
                Text("Enter Document Content", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("Document Header Title") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Body Paragraphs") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    maxLines = 10
                )

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = outputFileName,
                    onValueChange = { outputFileName = it },
                    label = { Text("Output PDF File Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (textInput.isBlank()) {
                            Toast.makeText(context, "Please enter some text content.", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.convertTextToPdf(context, textInput, titleInput, outputFileName)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                ) {
                    Text("Generate PDF Document", color = OnPrimaryColor, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // DOCX to PDF tab
            GlassCard {
                Text("Select Input DOCX File", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                if (selectedDocxUri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "Word Document",
                                tint = PrimaryColor,
                                modifier = Modifier.size(36.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = docxFileName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Ready to parse locally",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            try {
                                docxLauncher.launch("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                            } catch (e: Exception) {
                                docxLauncher.launch("*/*")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x11FFFFFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Change Word Document", color = PrimaryColor)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .clickable {
                                try {
                                    docxLauncher.launch("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                                } catch (e: Exception) {
                                    docxLauncher.launch("*/*")
                                }
                            }
                            .testTag("docx_picker_dropzone"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.UploadFile,
                                contentDescription = "Upload DOCX",
                                tint = PrimaryColor,
                                modifier = Modifier.size(48.dp)
                            )
                            Text("Tap to import .docx document", color = OnSurface, fontWeight = FontWeight.SemiBold)
                            Text("Supports modern Word files (.docx)", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = docxOutputName,
                    onValueChange = { docxOutputName = it },
                    label = { Text("Output PDF File Name") },
                    modifier = Modifier.fillMaxWidth().testTag("docx_output_name_field")
                )

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        val uri = selectedDocxUri
                        if (uri == null) {
                            Toast.makeText(context, "Please select a DOCX file first.", Toast.LENGTH_SHORT).show()
                        } else if (docxOutputName.isBlank()) {
                            Toast.makeText(context, "Please specify an output file name.", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.convertDocxToPdf(context, uri, docxOutputName)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("generate_docx_pdf_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                ) {
                    Text("Generate PDF Document", color = OnPrimaryColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// --- Screen 2: PDF Editor / Drawing Canvas Screen ---

data class DrawingLine(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float
)

@Composable
fun EditorScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    var canvasWidth by remember { mutableIntStateOf(0) }
    var canvasHeight by remember { mutableIntStateOf(0) }

    val lines = remember { mutableStateListOf<DrawingLine>() }
    val currentLinePoints = remember { mutableStateListOf<Offset>() }

    var selectedColor by remember { mutableStateOf(PrimaryColor) }
    var selectedStrokeWidth by remember { mutableFloatStateOf(8f) }
    var outputName by remember { mutableStateOf("sketch_annotation") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.currentScreen = Screen.Dashboard }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = PrimaryColor)
            }
            Text("Freehand Annotator", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        // Color & Brush Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(PrimaryColor, EditPurple, OptimizeGreen, Color.White).forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(color, CircleShape)
                            .border(if (selectedColor == color) 3.dp else 1.dp, OnSurface, CircleShape)
                            .clickable { selectedColor = color }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Stroke Size:", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = selectedStrokeWidth,
                    onValueChange = { selectedStrokeWidth = it },
                    valueRange = 4f..24f,
                    modifier = Modifier.width(100.dp),
                    colors = SliderDefaults.colors(thumbColor = PrimaryColor, activeTrackColor = PrimaryColor)
                )
            }
        }

        // The Drawing Canvas Board (Glass style)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF060E20))
                .border(2.dp, Color(0x33ADC6FF), RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentLinePoints.add(offset)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            currentLinePoints.add(change.position)
                        },
                        onDragEnd = {
                            if (currentLinePoints.isNotEmpty()) {
                                lines.add(DrawingLine(currentLinePoints.toList(), selectedColor, selectedStrokeWidth))
                                currentLinePoints.clear()
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                canvasWidth = size.width.toInt()
                canvasHeight = size.height.toInt()

                // Draw historic lines
                lines.forEach { line ->
                    if (line.points.isNotEmpty()) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(line.points.first().x, line.points.first().y)
                            for (i in 1 until line.points.size) {
                                lineTo(line.points[i].x, line.points[i].y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = line.color,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = line.strokeWidth,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                join = androidx.compose.ui.graphics.StrokeJoin.Round
                            )
                        )
                    }
                }

                // Draw active line
                if (currentLinePoints.isNotEmpty()) {
                    val activePath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(currentLinePoints.first().x, currentLinePoints.first().y)
                        for (i in 1 until currentLinePoints.size) {
                            lineTo(currentLinePoints[i].x, currentLinePoints[i].y)
                        }
                    }
                    drawPath(
                        path = activePath,
                        color = selectedColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = selectedStrokeWidth,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                        )
                    )
                }
            }
        }

        // Filename & Control Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = outputName,
                onValueChange = { outputName = it },
                label = { Text("Output JPG Name") },
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = { lines.clear() },
                colors = ButtonDefaults.buttonColors(containerColor = ErrorContainerColor),
                modifier = Modifier.height(56.dp)
            ) {
                Text("Clear", color = OnErrorContainerColor)
            }
        }

        Button(
            onClick = {
                if (lines.isEmpty()) {
                    Toast.makeText(context, "Draw something first!", Toast.LENGTH_SHORT).show()
                } else {
                    // Create bitmap in background
                    val bitmap = Bitmap.createBitmap(canvasWidth.coerceAtLeast(100), canvasHeight.coerceAtLeast(100), Bitmap.Config.ARGB_8888)
                    val androidCanvas = android.graphics.Canvas(bitmap)
                    androidCanvas.drawColor(android.graphics.Color.rgb(11, 19, 38))

                    val paint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        style = android.graphics.Paint.Style.STROKE
                        strokeCap = android.graphics.Paint.Cap.ROUND
                        strokeJoin = android.graphics.Paint.Join.ROUND
                    }

                    lines.forEach { line ->
                        paint.color = line.color.toArgb()
                        paint.strokeWidth = line.strokeWidth
                        val path = android.graphics.Path()
                        if (line.points.isNotEmpty()) {
                            path.moveTo(line.points.first().x, line.points.first().y)
                            for (i in 1 until line.points.size) {
                                path.lineTo(line.points[i].x, line.points[i].y)
                            }
                            androidCanvas.drawPath(path, paint)
                        }
                    }

                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                    viewModel.saveDrawing(context, stream.toByteArray(), outputName)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
        ) {
            Text("Export Sketch Annotation", color = OnPrimaryColor, fontWeight = FontWeight.Bold)
        }
    }
}

// --- Screen 3: Merge Documents Screen ---

@Composable
fun MergeScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val items = remember { mutableStateListOf<String>() }
    var currentItemText by remember { mutableStateOf("") }
    var outputFileName by remember { mutableStateOf("merged_document") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.currentScreen = Screen.Dashboard }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = PrimaryColor)
            }
            Text("Merge Documents", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        // Add Section
        GlassCard {
            Text("Add Document Segment", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = currentItemText,
                onValueChange = { currentItemText = it },
                label = { Text("Segment Text Content") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    if (currentItemText.isNotBlank()) {
                        items.add(currentItemText)
                        currentItemText = ""
                    }
                },
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Segment", color = OnPrimaryColor)
            }
        }

        // Segments List
        Text("DOCUMENT ASSEMBLY ORDER", style = MaterialTheme.typography.labelMedium, color = PrimaryColor)

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x0FFFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                Text("No segments added yet. Add text above to begin assembly.", color = OnSurfaceVariant, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items.size) { index ->
                    val segment = items[index]
                    GlassCard(borderColor = Color(0x1FBAE2FD)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Segment #${index + 1}", style = MaterialTheme.typography.labelSmall, color = PrimaryColor)
                                Text(segment, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }

                            Row {
                                if (index > 0) {
                                    IconButton(onClick = {
                                        val temp = items[index]
                                        items[index] = items[index - 1]
                                        items[index - 1] = temp
                                    }) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up")
                                    }
                                }
                                if (index < items.size - 1) {
                                    IconButton(onClick = {
                                        val temp = items[index]
                                        items[index] = items[index + 1]
                                        items[index + 1] = temp
                                    }) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down")
                                    }
                                }
                                IconButton(onClick = { items.removeAt(index) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorColor)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Export Actions
        GlassCard {
            OutlinedTextField(
                value = outputFileName,
                onValueChange = { outputFileName = it },
                label = { Text("Output PDF Name") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    if (items.isEmpty()) {
                        Toast.makeText(context, "Please add segments first.", Toast.LENGTH_SHORT).show()
                    } else {
                        val mergedText = items.joinToString("\n\n--- Document Segment Divider ---\n\n")
                        viewModel.convertTextToPdf(context, mergedText, "Merged Multi-Segment Document", outputFileName)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
            ) {
                Icon(Icons.Default.CallMerge, contentDescription = "Merge")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Merge and Generate PDF", color = OnPrimaryColor, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// --- Screen 4: Optimize / Compress Size Screen ---

@Composable
fun OptimizeScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var compressionQuality by remember { mutableFloatStateOf(50f) }
    var outputName by remember { mutableStateOf("optimized_image") }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.currentScreen = Screen.Dashboard }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = PrimaryColor)
            }
            Text("Optimize Document Size", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        GlassCard {
            Text("Select Image to Shrink", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            if (selectedImageUri != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x15FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = "Selected Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { imagePicker.launch("image/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x11FFFFFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Select Different Image", color = PrimaryColor)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x0FFFFFFF))
                        .clickable { imagePicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Add image", tint = PrimaryColor, modifier = Modifier.size(48.dp))
                        Text("Tap to import image", color = OnSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Compression Level:", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${(100 - compressionQuality).toInt()}% Aggressiveness", style = MaterialTheme.typography.labelSmall, color = OptimizeGreen)
            }

            Slider(
                value = compressionQuality,
                onValueChange = { compressionQuality = it },
                valueRange = 10f..90f,
                colors = SliderDefaults.colors(thumbColor = OptimizeGreen, activeTrackColor = OptimizeGreen)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("90% (High Quality, Larger size)", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                Text("10% (Low Quality, Tiny size)", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = outputName,
                onValueChange = { outputName = it },
                label = { Text("Output Filename") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    val uri = selectedImageUri
                    if (uri == null) {
                        Toast.makeText(context, "Please select an image first.", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.compressImage(context, uri, outputName, compressionQuality.toInt())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = OptimizeGreen)
            ) {
                Icon(Icons.Default.Compress, contentDescription = "Shrink")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Compress and Save Local Copy", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// --- Screen 5: AI Summary (Gemini 3.5) Screen ---

@Composable
fun AiSummaryScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    var textToSummarize by remember { mutableStateOf("") }
    var docName by remember { mutableStateOf("report_summary") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.currentScreen = Screen.Dashboard }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = PrimaryColor)
            }
            Text("AI Document Summary", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = "AI", tint = AiCyan)
                Text("Gemini 3.5 Assistant", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Paste text of any document to generate a high-precision summary locally in seconds.", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = textToSummarize,
                onValueChange = { textToSummarize = it },
                placeholder = { Text("Paste document paragraphs or data metrics here...", color = OnSurfaceVariant.copy(alpha = 0.4f)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                maxLines = 15
            )

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = docName,
                onValueChange = { docName = it },
                label = { Text("Summary File Title") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    if (textToSummarize.isBlank()) {
                        Toast.makeText(context, "Please enter text to summarize.", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.generateAiSummary(context, textToSummarize, docName)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AiCyan)
            ) {
                Icon(Icons.Default.Bolt, contentDescription = "AI Summarize")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate AI Report Summary", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// --- Screen 6: Security Portal Screen ---

@Composable
fun SecurityScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    // Encryption States
    var rawTextToEncrypt by remember { mutableStateOf("") }
    var encryptOutputName by remember { mutableStateOf("confidential_data") }
    var encryptionPassword by remember { mutableStateOf("") }

    // Decryption States
    var selectedEncryptedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedEncryptedFileName by remember { mutableStateOf("") }
    var decryptionPassword by remember { mutableStateOf("") }

    val encryptedFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedEncryptedFileUri = uri
            selectedEncryptedFileName = uri.lastPathSegment ?: "encrypted_data.enc"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.currentScreen = Screen.Dashboard }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = PrimaryColor)
            }
            Text("Security Portal AES-256", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = SecurityAmber
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Text("Encrypt File", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Text("Decrypt File", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
            }
        }

        if (selectedTab == 0) {
            // Encrypt Tab
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Lock, contentDescription = "Encrypt", tint = SecurityAmber)
                    Text("Secure AES-256 Encryption", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = rawTextToEncrypt,
                    onValueChange = { rawTextToEncrypt = it },
                    placeholder = { Text("Type or paste confidential notes/text here...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    maxLines = 8
                )

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = encryptOutputName,
                    onValueChange = { encryptOutputName = it },
                    label = { Text("Output Filename") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = encryptionPassword,
                    onValueChange = { encryptionPassword = it },
                    label = { Text("AES Security Password") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (rawTextToEncrypt.isBlank()) {
                            Toast.makeText(context, "Please enter some text to encrypt.", Toast.LENGTH_SHORT).show()
                        } else if (encryptionPassword.length < 4) {
                            Toast.makeText(context, "Password must be at least 4 characters.", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.encryptTextFile(context, rawTextToEncrypt, encryptOutputName, encryptionPassword)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SecurityAmber)
                ) {
                    Text("Encrypt and Save Secure File", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // Decrypt Tab
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.LockOpen, contentDescription = "Decrypt", tint = SecurityAmber)
                    Text("AES-256 Decryption Panel", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (selectedEncryptedFileUri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x15FFFFFF))
                            .padding(16.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Imported Encrypted File", style = MaterialTheme.typography.labelSmall, color = SecurityAmber)
                                Text(selectedEncryptedFileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { selectedEncryptedFileUri = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { encryptedFilePicker.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x11FFFFFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Select Different File", color = SecurityAmber)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x0FFFFFFF))
                            .clickable { encryptedFilePicker.launch("*/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Key, contentDescription = "Add file", tint = SecurityAmber, modifier = Modifier.size(36.dp))
                            Text("Tap to import encrypted .enc file", color = OnSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = decryptionPassword,
                    onValueChange = { decryptionPassword = it },
                    label = { Text("Enter Encryption Password") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        val uri = selectedEncryptedFileUri
                        if (uri == null) {
                            Toast.makeText(context, "Please select an encrypted file first.", Toast.LENGTH_SHORT).show()
                        } else if (decryptionPassword.isBlank()) {
                            Toast.makeText(context, "Please enter decryption password.", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.decryptSelectedFile(context, uri, decryptionPassword)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SecurityAmber)
                ) {
                    Text("Decrypt File Instantly", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
