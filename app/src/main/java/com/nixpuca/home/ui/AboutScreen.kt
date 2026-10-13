package com.nixpuca.home.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nixpuca.home.NixViewModel

// Official NIX PUCA console identity (nix_core/console.py splash): the ◎
// double-circle mark in the sage accent over the near-black canvas, with the
// serif "Nix PUCA" wordmark and the small uppercase tagline.
private val PucaMark = Color(0xFF8BB8A0)
private val PucaGlow = Color(0x598BB8A0)
private val HeroBg = Color(0xFF090B10)
private val HeroText = Color(0xFFEDF1F5)
private val HeroDim = Color(0xFF8995A5)

/** One open-source repository linked from About and Settings. */
data class ProjectLink(
    val title: String,
    val subtitle: String,
    val url: String,
)

/** The project's public repositories on github.com/saineela. */
val PROJECT_LINKS = listOf(
    ProjectLink(
        title = "saineela/puca",
        subtitle = "NIX PUCA — the modular Personal User Companion Agent foundation (Python)",
        url = "https://github.com/saineela/puca",
    ),
    ProjectLink(
        title = "saineela/ring-light",
        subtitle = "Control your rooted Linux Echo Dot ring light system remotely (Python)",
        url = "https://github.com/saineela/ring-light",
    ),
    ProjectLink(
        title = "saineela/ring-light-skill",
        subtitle = "Nix PUCA skill for Echo Dot ring light control using the ESPHOME API (Python)",
        url = "https://github.com/saineela/ring-light-skill",
    ),
    ProjectLink(
        title = "saineela",
        subtitle = "Sai Neela on GitHub — more projects and profiles",
        url = "https://github.com/saineela",
    ),
)

/**
 * About: what NIX PUCA is, what the four modules do, what this app is for,
 * and links to the public repositories. Pure static copy — nothing to load.
 */
@Composable
fun AboutScreen(vm: NixViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        PucaHero()

        SectionCard(
            title = "What is NIX PUCA?",
            subtitle = "A local-first Personal User Companion Agent foundation",
        ) {
            Text(
                text = "NIX PUCA is a modular software foundation for a " +
                    "Personal User Companion Agent (PUCA): a self-hosted " +
                    "personal assistant built around explicit boundaries " +
                    "between conversation, durable memory, temporal " +
                    "interpretation, and scheduled actions. It is a Python " +
                    "project with local SQLite persistence and optional " +
                    "local or separately operated model backends.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "The design is intentionally more structured than a " +
                    "single prompt wrapped around an LLM. A configured " +
                    "conversational model produces natural-language " +
                    "responses, while deterministic application code " +
                    "validates, resolves, stores, schedules, and enforces " +
                    "policy — so a model can never claim an action " +
                    "succeeded before the scheduler confirms it.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(10.dp))
            QuoteBox(
                "Neural models propose; deterministic Python validates, " +
                    "resolves, stores, schedules, and enforces policy.",
            )
        }

        SectionCard(
            title = "The four modules",
            subtitle = "Each with one job and one boundary",
        ) {
            ModuleRow(
                name = "NIX Core",
                text = "Routes and composes every request. A deterministic " +
                    "route engine runs first, and Core remains the final " +
                    "response boundary.",
            )
            ModuleRow(
                name = "NIX Knowledge",
                text = "Durable local memory: facts, people, relationships, " +
                    "events and preferences persisted in SQLite and returned " +
                    "as structured records.",
            )
            ModuleRow(
                name = "NIX Actions",
                text = "Scheduled work done deterministically — pending, " +
                    "fired, failed, cancelled and rescheduled operations, " +
                    "linked back to the records that caused them.",
            )
            ModuleRow(
                name = "NIX Decision",
                text = "A gated prototype for proactive behaviour. " +
                    "Foundation only: it has no registered proactive " +
                    "calling rules yet.",
            )
        }

        SectionCard(
            title = "What is a PUCA?",
            subtitle = "The project term behind the name",
        ) {
            Text(
                text = "PUCA means Personal User Companion Agent. In NIX it " +
                    "describes a personal-assistant architecture where " +
                    "conversational language generation is only one part of " +
                    "the system. Personal facts, event times, reminders, " +
                    "device access and external integrations are handled by " +
                    "separately defined services and policies — never " +
                    "assumed safe or accurate just because a model " +
                    "generated text.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "It is a project term, not a claim that NIX is the " +
                    "first PUCA or a formally recognised product category.",
                style = MaterialTheme.typography.bodySmall,
                color = NixPalette.Muted,
            )
        }

        SectionCard(
            title = "Nix Home",
            subtitle = "What this app is for",
        ) {
            Text(
                text = "Nix Home is the Android companion for your own " +
                    "self-hosted NIX PUCA hub. It puts the whole system on " +
                    "your phone — everything talks directly to the hub " +
                    "address you configure in Settings, and nothing goes " +
                    "through a third party.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(10.dp))
            BulletRow("Chat with your companion and watch the pipeline that produced each reply")
            BulletRow("Search Knowledge — memory, facts, people and the graph")
            BulletRow("Build your Schedule on a graphical wheel-picker calendar, no typing")
            BulletRow("Browse, install and trust skills from the marketplace")
            BulletRow("Drive the Echo Dot ring light with the full 38-effect engine")
            BulletRow("Pair Nix Dev units over Bluetooth, monitor hub hardware and diagnostics")
        }

        SectionCard(
            title = "Projects on GitHub",
            subtitle = "The open-source pieces this app is built on",
        ) {
            PROJECT_LINKS.forEachIndexed { index, link ->
                if (index > 0) Spacer(Modifier.height(4.dp))
                ProjectLinkRow(link)
            }
        }

        SectionCard(title = "Credits") {
            KeyValueList(
                listOf(
                    "App" to "Nix Home 1.0.0",
                    "System" to "NIX PUCA",
                    "Companion" to "Casper — created and built by Sai Neela, " +
                        "living in NIX's PUCA system",
                    "Hub" to vm.config.baseUrl.ifBlank { "not configured" },
                ),
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

/** Dark hero card with the official ◎ mark, serif wordmark and tagline. */
@Composable
private fun PucaHero() {
    // Gentle breathing on the halo only — the page is content, not a gate.
    val transition = rememberInfiniteTransition(label = "aboutHero")
    val breathe by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2300, easing = EaseInOutCubic), RepeatMode.Reverse),
        label = "breathe",
    )

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(HeroBg)
            .padding(vertical = 26.dp, horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(96.dp)) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = size.minDimension / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(PucaGlow.copy(alpha = PucaGlow.alpha * breathe), Color.Transparent),
                        center = c,
                        radius = r,
                    ),
                    radius = r,
                )
                drawArc(
                    color = PucaMark,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(c.x - r * 0.82f, c.y - r * 0.82f),
                    size = Size(r * 1.64f, r * 1.64f),
                    style = Stroke(width = 7f, cap = StrokeCap.Round),
                )
                drawArc(
                    color = PucaMark.copy(alpha = 0.92f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(c.x - r * 0.48f, c.y - r * 0.48f),
                    size = Size(r * 0.96f, r * 0.96f),
                    style = Stroke(width = 6f, cap = StrokeCap.Round),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Nix PUCA",
                color = HeroText,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Serif,
                fontSize = 30.sp,
                letterSpacing = 0.5.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "PERSONAL COMPANION ARCHITECTURE",
                color = HeroDim,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                letterSpacing = 2.4.sp,
            )
        }
    }
}

/** Tappable repository row used by both About and Settings. */
@Composable
fun ProjectLinkRow(link: ProjectLink, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier
            .fillMaxWidth()
            .tapScale {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                }
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(NixPalette.Accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Code,
                contentDescription = null,
                tint = NixPalette.Accent,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = link.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = link.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = NixPalette.Muted,
            )
        }
        Icon(
            Icons.Outlined.OpenInNew,
            contentDescription = "Open ${link.title} in the browser",
            tint = NixPalette.Muted,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** One module line: sage dot, name, description. */
@Composable
private fun ModuleRow(name: String, text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(PucaMark),
        )
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = NixPalette.Muted,
            )
        }
    }
}

/** One feature bullet for the "what this app is for" card. */
@Composable
private fun BulletRow(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "·",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = NixPalette.Accent,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Accent-tinted pull quote for the project's design rule. */
@Composable
private fun QuoteBox(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NixPalette.Accent.copy(alpha = 0.08f))
            .padding(12.dp),
    ) {
        Text(
            text = "“$text”",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            color = NixPalette.Ink,
        )
    }
}
