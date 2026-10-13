package com.nixpuca.home.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nixpuca.home.NixViewModel
import com.nixpuca.home.data.optStr
import org.json.JSONObject
import java.util.Calendar

/**
 * Home: a big warm greeting, then a live chat box front and centre — typing
 * a message and hitting send jumps straight into Chat with it sent. The
 * companion orb carries the personality and quick links do the navigating, so
 * the first screen feels welcoming, not like a dashboard.
 */
@Composable
fun HomeScreen(vm: NixViewModel, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) {
        vm.ensureHomeCopy()
        if (vm.healthChips.isEmpty()) vm.refreshHome()
    }

    val copy = vm.homeCopy ?: com.nixpuca.home.NixViewModel.DEFAULT_HOME_COPY

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        // ---------------------------------------------------------- greeting
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = greeting(),
                    style = MaterialTheme.typography.titleLarge,
                    color = NixPalette.Muted,
                )
                Text(
                    text = "Hey, ${vm.profileName.ifBlank { "there" }}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 42.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${vm.companionName} is ready when you are.",
                    style = MaterialTheme.typography.titleMedium,
                    color = NixPalette.Muted,
                )
            }
            CompanionOrb(companion = vm.companionName, model = vm.activeModelId)
        }

        // ----------------------------------------------------- direct chat
        DirectChatBox(vm = vm, copy = copy)

        // ------------------------------------------------------- quick links
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickLink(
                title = "Knowledge",
                subtitle = "Memory · records · people",
                icon = "🧠",
                modifier = Modifier.weight(1f),
                onClick = { vm.navigate(com.nixpuca.home.Screen.Knowledge) },
            )
            QuickLink(
                title = "Schedule",
                subtitle = "Today & upcoming",
                icon = "📅",
                modifier = Modifier.weight(1f),
                onClick = { vm.navigate(com.nixpuca.home.Screen.Schedule) },
            )
            QuickLink(
                title = "Devices",
                subtitle = "Lights & smart home",
                icon = "💡",
                modifier = Modifier.weight(1f),
                onClick = { vm.navigate(com.nixpuca.home.Screen.Devices) },
            )
            QuickLink(
                title = "Music",
                subtitle = "Play on Echo Dot",
                icon = "🎵",
                modifier = Modifier.weight(1f),
                onClick = { vm.navigate(com.nixpuca.home.Screen.Music) },
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

/**
 * The home chat box: type anything and send — the app jumps into Chat with
 * the message already on its way. Before typing, suggestion chips from the
 * companion copy fill it with one tap.
 */
@Composable
private fun DirectChatBox(vm: NixViewModel, copy: JSONObject) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = NixPalette.Paper,
        border = BorderStroke(1.5.dp, NixPalette.Accent),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = copy.optStr("headline").ifBlank { "Talk to ${vm.companionName}" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = copy.optStr("subtitle").ifBlank { "Type anything — Luna answers right here." },
                style = MaterialTheme.typography.bodyLarge,
                color = NixPalette.Muted,
            )
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = vm.draft,
                    onValueChange = { vm.draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = "Message ${vm.companionName}…",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    maxLines = 3,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NixPalette.Accent,
                        unfocusedBorderColor = NixPalette.Line,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (vm.draft.isNotBlank()) NixPalette.Accent else NixPalette.Mist,
                        )
                        .clickable(enabled = vm.draft.isNotBlank()) {
                            // Hand the typed message to Chat and fire it.
                            vm.screen = com.nixpuca.home.Screen.Chat
                            vm.sendDraft()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (vm.draft.isNotBlank()) NixPalette.OnAccent else NixPalette.Muted,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            // Suggestion chips — tap to fill the box. Curated so every chip
            // does something the app can finish without failing, and shuffled
            // so Home offers a fresh set each visit.
            val chips = remember(copy) { HOME_SUGGESTIONS.shuffled().take(3) }
            AnimatedVisibility(visible = chips.isNotEmpty() && vm.draft.isBlank(), enter = fadeIn(), exit = fadeOut()) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = copy.optStr("section_title").ifBlank { "Try asking" },
                        style = MaterialTheme.typography.labelMedium,
                        color = NixPalette.Muted,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    chips.forEach { suggestion ->
                        SuggestionChip(suggestion = suggestion) {
                            vm.draft = suggestion.prompt
                        }
                    }
                }
            }
        }
    }
}

/** Small pulsing orb that shows which companion is answering. */
@Composable
private fun CompanionOrb(companion: String, model: String?) {
    val transition = rememberInfiniteTransition(label = "orb")
    val breathe by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = EaseInOutCubic), RepeatMode.Reverse),
        label = "breathe",
    )
    Surface(
        shape = RoundedCornerShape(50),
        color = NixPalette.Mist,
        border = BorderStroke(1.dp, NixPalette.Line),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(9.dp * breathe)
                    .clip(CircleShape)
                    .background(NixPalette.Accent),
            )
            Spacer(Modifier.width(7.dp))
            Column {
                Text(companion, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = model ?: "detecting…",
                    style = MaterialTheme.typography.labelSmall,
                    color = NixPalette.Muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun QuickLink(
    title: String,
    subtitle: String,
    icon: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = NixPalette.Mist,
        border = BorderStroke(1.dp, NixPalette.Line),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(icon, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = NixPalette.Muted)
        }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Still up"
}

/** One home suggestion: glyph, label, and the prompt it fills in. */
private data class Suggestion(val icon: String, val title: String, val prompt: String)

/**
 * Prompts Home rotates through — only things that already work end-to-end:
 * chat, memory, schedule, health and the ring light's own declared controls.
 */
private val HOME_SUGGESTIONS = listOf(
    Suggestion("📅", "What's on my schedule today?", "What's on my schedule today?"),
    Suggestion("➕", "Add a calendar event for tomorrow at 5pm", "Add a calendar event for tomorrow at 5pm"),
    Suggestion("💡", "Turn on the ring light", "Turn on the ring light"),
    Suggestion("💡", "Dim the ring light to 40%", "Dim the ring light to 40%"),
    Suggestion("🌈", "Set the ring light to Rainbow Twinkle", "Set the ring light to the Rainbow Twinkle effect"),
    Suggestion("🧠", "Remember I take my coffee black", "Remember that I take my coffee black"),
    Suggestion("🧠", "What do you remember about me?", "What do you remember about me?"),
    Suggestion("♡", "Help me reset for a minute", "Help me reset for a minute"),
    Suggestion("⚙", "How is everything running?", "How is everything running right now?"),
    Suggestion("✦", "What have you been up to?", "What have you been up to today?"),
)

/** Compact tappable suggestion pill for the home chat box. */
@Composable
private fun SuggestionChip(suggestion: Suggestion, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = NixPalette.Mist,
        border = BorderStroke(1.dp, NixPalette.Line),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = NixPalette.Accent,
                modifier = Modifier.size(26.dp),
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = suggestion.icon.ifBlank { "✦" },
                        color = NixPalette.OnAccent,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = suggestion.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
