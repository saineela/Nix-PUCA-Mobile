package com.nixpuca.home.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nixpuca.home.NixViewModel

/**
 * First-run welcome + guided setup: an animated PUCA logo intro, then three
 * friendly steps (connection, name, look) before dropping the user into the
 * app. Everything is skippable and nothing here shows raw endpoints.
 */

/** Where "run your own NIX" links point — the project's setup repo. */
private const val GITHUB_SETUP_URL = "https://github.com/saineela/puca"

@Composable
fun SetupFlow(vm: NixViewModel, modifier: Modifier = Modifier) {
    var step by remember { mutableIntStateOf(0) }
    // Step 0 = animated welcome, 1..3 = guided questions, 4 = all done.
    val totalSteps = 3

    Box(
        modifier
            .fillMaxSize()
            .background(NixPalette.Paper)
            .systemBarsPadding(),
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (targetState > initialState) {
                    (fadeIn(tween(320)) + slideInHorizontally { it / 3 }) togetherWith
                        fadeOut(tween(180))
                } else {
                    (fadeIn(tween(320)) + slideInHorizontally { -it / 3 }) togetherWith
                        fadeOut(tween(180))
                }
            },
            label = "setup-step",
        ) { current ->
            when (current) {
                0 -> WelcomeStep(onStart = { step = 1 })
                1 -> ConnectionStep(vm = vm, onNext = { step = 2 }, onBack = { step = 0 })
                2 -> NameStep(vm = vm, onNext = { step = 3 }, onBack = { step = 1 })
                else -> LookStep(
                    vm = vm,
                    onBack = { step = 2 },
                    onFinish = {
                        vm.saveSettings()
                        vm.finishSetup()
                    },
                )
            }
        }

        // Step dots, hidden on the intro.
        if (step in 1..totalSteps) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 30.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(totalSteps) { i ->
                    Box(
                        Modifier
                            .size(if (i + 1 == step) 10.dp else 7.dp)
                            .background(
                                if (i + 1 == step) NixPalette.Accent else NixPalette.Line,
                                CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

/** Animated PUCA logo intro with the call to action. */
@Composable
private fun WelcomeStep(onStart: () -> Unit) {
    val context = LocalContext.current
    fun open(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
    val transition = rememberInfiniteTransition(label = "welcome")
    val breathe by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1900, easing = EaseInOutCubic), RepeatMode.Reverse),
        label = "breathe",
    )

    Column(
        Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(Modifier.size(140.dp * breathe)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(NixPalette.Accent.copy(alpha = 0.18f), Color.Transparent),
                    center = c,
                    radius = r,
                ),
                radius = r,
            )
            drawCircle(NixPalette.Accent, radius = r * 0.82f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 7f))
            drawCircle(NixPalette.Accent.copy(alpha = 0.9f), radius = r * 0.48f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f))
        }
        Spacer(Modifier.height(30.dp))
        Text(
            text = "Welcome to NIX Home",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Your personal companion, your schedule, your home — all in one calm place. Let's set it up together.",
            style = MaterialTheme.typography.bodyLarge,
            color = NixPalette.Muted,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(36.dp))
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = NixPalette.Ink,
                contentColor = NixPalette.Paper,
            ),
        ) {
            Icon(Icons.Outlined.RocketLaunch, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Get started", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(22.dp))

        Text(
            text = "Want to run your own NIX instance?",
            style = MaterialTheme.typography.bodyMedium,
            color = NixPalette.Muted,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { open(GITHUB_SETUP_URL) },
                border = BorderStroke(1.dp, NixPalette.Accent),
                shape = RoundedCornerShape(50),
            ) {
                Text("Setup on GitHub", color = NixPalette.Accent)
            }
            OutlinedButton(
                onClick = { open("$GITHUB_SETUP_URL#readme") },
                border = BorderStroke(1.dp, NixPalette.Line),
                shape = RoundedCornerShape(50),
            ) {
                Text("Self-host guide", color = NixPalette.Muted)
            }
        }
    }
}

/** Step 1: where the hub lives. */
@Composable
private fun ConnectionStep(vm: NixViewModel, onNext: () -> Unit, onBack: () -> Unit) {
    SetupScaffold(
        title = "Where does your hub live?",
        subtitle = "Enter the address your NIX PUCA hub is reachable at. Your admin can share this with you.",
        onBack = onBack,
        onNext = onNext,
        nextEnabled = vm.serverUrl.isNotBlank(),
        nextLabel = "Continue",
    ) {
        OutlinedTextField(
            value = vm.serverUrl,
            onValueChange = { vm.serverUrl = it; vm.saved = false },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Hub address") },
            placeholder = { Text("https://nix.example.com") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Uri),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NixPalette.Accent,
                unfocusedBorderColor = NixPalette.Line,
            ),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = vm.location,
            onValueChange = { vm.location = it; vm.saved = false },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Home name") },
            placeholder = { Text("My home") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NixPalette.Accent,
                unfocusedBorderColor = NixPalette.Line,
            ),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { vm.testConnection() },
            enabled = !vm.testBusy && vm.serverUrl.isNotBlank(),
            border = BorderStroke(1.dp, NixPalette.Accent),
            shape = RoundedCornerShape(50),
        ) {
            Text(if (vm.testBusy) "Checking…" else "Check connection", color = NixPalette.Accent)
        }
        vm.testLines.lastOrNull()?.let { line ->
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = if (line.ok != false) NixPalette.Ok else NixPalette.Bad,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(line.text, style = MaterialTheme.typography.bodySmall, color = NixPalette.Muted)
            }
        }
    }
}

/** Step 2: the name the companion should use. */
@Composable
private fun NameStep(vm: NixViewModel, onNext: () -> Unit, onBack: () -> Unit) {
    SetupScaffold(
        title = "What should we call you?",
        subtitle = "Your companion greets you by this name. You can change it any time in Settings.",
        onBack = onBack,
        onNext = onNext,
        nextEnabled = true,
        nextLabel = "Continue",
    ) {
        OutlinedTextField(
            value = vm.profileName,
            onValueChange = { vm.profileName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Your name") },
            placeholder = { Text("Enter your name") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NixPalette.Accent,
                unfocusedBorderColor = NixPalette.Line,
            ),
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = vm.darkTheme,
                onCheckedChange = { vm.setDarkThemeEnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NixPalette.Accent,
                    checkedTrackColor = NixPalette.Accent.copy(alpha = 0.35f),
                ),
            )
            Spacer(Modifier.width(10.dp))
            Text("Dark theme", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Step 3: pick the accent colour, then finish. */
@Composable
private fun LookStep(vm: NixViewModel, onBack: () -> Unit, onFinish: () -> Unit) {
    SetupScaffold(
        title = "Make it yours",
        subtitle = "Pick an accent colour for the app. The classic black & white look is the default.",
        onBack = onBack,
        onNext = onFinish,
        nextEnabled = true,
        nextLabel = "Open NIX Home",
    ) {
        // Three tidy rows of colour dots.
        val perRow = 3
        NixPalette.AccentOptions.chunked(perRow).forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { (id, name) ->
                    val selected = vm.accentId == id
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { vm.setAccent(id) },
                    ) {
                        val swatch = when (id) {
                            "mono" -> NixPalette.Ink
                            else -> androidx.compose.ui.graphics.Color(
                                when (id) {
                                    "violet" -> 0xFF6C4CF1
                                    "cyan" -> 0xFF0E7CE8
                                    "amber" -> 0xFFC77700
                                    "rose" -> 0xFFD6336C
                                    "green" -> 0xFF1B8A4B
                                    "teal" -> 0xFF0F7F78
                                    "indigo" -> 0xFF3D4FD6
                                    else -> 0xFFB4182C
                                },
                            )
                        }
                        Box(
                            Modifier
                                .size(44.dp)
                                .background(swatch, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = if (swatch.luminance() > 0.6f) Color.Black else Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) NixPalette.Ink else NixPalette.Muted,
                        )
                    }
                }
            }
        }
    }
}

/** Shared frame for the guided steps: title, body, back/next actions. */
@Composable
private fun SetupScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
    nextEnabled: Boolean,
    nextLabel: String,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(40.dp))
        // Small PUCA mark so each step feels part of the same family.
        Box(
            Modifier
                .size(46.dp)
                .background(NixPalette.Accent.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "P",
                color = NixPalette.Accent,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Serif,
                fontSize = 22.sp,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = NixPalette.Muted,
        )
        Spacer(Modifier.height(24.dp))
        content()
        Spacer(Modifier.height(34.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("Back", color = NixPalette.Muted)
            }
            Button(
                onClick = onNext,
                enabled = nextEnabled,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NixPalette.Ink,
                    contentColor = NixPalette.Paper,
                    disabledContainerColor = NixPalette.Line,
                    disabledContentColor = NixPalette.Muted,
                ),
            ) {
                Text(nextLabel, style = MaterialTheme.typography.titleSmall)
            }
        }
        Spacer(Modifier.height(46.dp))
    }
}

/**
 * Signed-out landing shown when a configured hub was unreachable at open.
 * The whole app is locked behind it, but the connection is still cached — the
 * fields below are prefilled from [NixViewModel.serverUrl], so signing back in
 * is one tap once the hub is reachable again. Nothing is cleared while offline.
 */
@Composable
fun ReconnectScreen(vm: NixViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .background(NixPalette.Paper)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Small PUCA mark, same family as the setup steps.
        Box(
            Modifier
                .size(46.dp)
                .background(NixPalette.Accent.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "P",
                color = NixPalette.Accent,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Serif,
                fontSize = 22.sp,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Can't reach your hub",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "You're signed out because the hub didn't answer. Your connection is saved below — sign back in once it's reachable.",
            style = MaterialTheme.typography.bodyLarge,
            color = NixPalette.Muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = vm.serverUrl,
            onValueChange = { vm.serverUrl = it; vm.saved = false },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Hub address") },
            placeholder = { Text("https://nix.example.com") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Uri),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NixPalette.Accent,
                unfocusedBorderColor = NixPalette.Line,
            ),
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { vm.reconnect() },
            enabled = !vm.testBusy && vm.serverUrl.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = NixPalette.Ink,
                contentColor = NixPalette.Paper,
                disabledContainerColor = NixPalette.Line,
                disabledContentColor = NixPalette.Muted,
            ),
        ) {
            Text(
                text = if (vm.testBusy) "Signing in…" else "Sign back in",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        vm.testLines.lastOrNull()?.let { line ->
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = if (line.ok != false) NixPalette.Ok else NixPalette.Bad,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(line.text, style = MaterialTheme.typography.bodySmall, color = NixPalette.Muted)
            }
        }
    }
}
