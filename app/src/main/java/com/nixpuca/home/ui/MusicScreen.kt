package com.nixpuca.home.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nixpuca.home.NixViewModel
import kotlinx.coroutines.delay
import kotlin.math.sin

/* ------------------------------------------------------------------ *
 *  MUSIC — UI-only. No playback is wired yet: the albums, tracks and
 *  lyrics below are placeholder data standing in for the music API +
 *  Echo Connect service that are still being built. Everything on this
 *  page is a remote control for the Nix Dev (Echo Dot) — audio is meant
 *  to play on the unit, never on the phone.
 * ------------------------------------------------------------------ */

/** A placeholder album — gradient art stands in for real cover images. */
private data class Album(val title: String, val artist: String, val c1: Long, val c2: Long)

/** A placeholder queue track; `lyrics` maps seconds → line (empty = instrumental). */
private data class Track(
    val title: String,
    val artist: String,
    val album: Album,
    val durSec: Int,
    val lyrics: List<Pair<Int, String>> = emptyList(),
)

private val LIBRARY = listOf(
    Album("Neon Harbour", "Ada Vale", 0xFF0A2A6B, 0xFF00C2FF),
    Album("Static Bloom", "The Parallax", 0xFF6B0A5E, 0xFFFF4FD8),
    Album("Midnight Terminal", "Kori & the Satellites", 0xFF12123A, 0xFF5A5AFF),
    Album("Paper Planets", "Mira Sundial", 0xFF8A4B00, 0xFFFFC63F),
    Album("Low Tide Signals", "Coastal Engine", 0xFF003B34, 0xFF2BD9A4),
    Album("Violet Machine", "Halcyon Drive", 0xFF2E0A4A, 0xFFB44BFF),
    Album("Glass Fields", "NOVA CULT", 0xFF20304A, 0xFF9FD0FF),
    Album("Ember Season", "Juni Marlowe", 0xFF4A0A0A, 0xFFFF6A3D),
)

private val HARBOUR_LYRICS = listOf(
    8 to "Streetlights fold into the rain",
    16 to "Harbour water, electric blue",
    24 to "Every window holds a name",
    32 to "And every name is calling you",
    42 to "Hold the light, the light, the light",
    50 to "We are harbour lights tonight",
    60 to "Signals humming through the pier",
    68 to "Nothing lost and nothing dear",
    78 to "Hold the light, the light, the light",
    86 to "We are harbour lights tonight",
    98 to "And the tide pulls the static away",
    108 to "Come morning we will stay",
    120 to "Hold the light… the light…",
    132 to "We are harbour lights",
)

private val QUEUE = listOf(
    Track("Harbour Lights", "Ada Vale", LIBRARY[0], 222, HARBOUR_LYRICS),
    Track("Static Bloom", "The Parallax", LIBRARY[1], 245),
    Track("Platform 9", "Kori & the Satellites", LIBRARY[2], 198),
    Track("Golden Hourglass", "Mira Sundial", LIBRARY[3], 177),
    Track("Salt & Signal", "Coastal Engine", LIBRARY[4], 231),
    Track("Violet Machine", "Halcyon Drive", LIBRARY[5], 262),
    Track("White Noise Garden", "NOVA CULT", LIBRARY[6], 189),
    Track("Ember Season", "Juni Marlowe", LIBRARY[7], 215),
    Track("Tidepool", "Coastal Engine", LIBRARY[4], 168),
    Track("Afterglow Circuit", "Ada Vale", LIBRARY[0], 250),
)

private fun fmt(sec: Int): String = "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"

/** Two-stop diagonal gradient used as album art. */
private fun artBrush(a: Album) = Brush.linearGradient(listOf(Color(a.c1), Color(a.c2)))

@Composable
fun MusicScreen(vm: NixViewModel, modifier: Modifier = Modifier) {
    var index by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    var pos by remember { mutableFloatStateOf(0f) }
    val track = QUEUE[index]
    val album = track.album

    // Simulated playhead. This is the UI placeholder for the real player.
    LaunchedEffect(playing, index) {
        while (playing) {
            delay(250)
            pos += 0.25f
            if (pos >= track.durSec) {
                index = (index + 1) % QUEUE.size
                pos = 0f
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { MusicHeader() }
        item {
            NowPlayingCard(
                track = track, album = album, playing = playing, pos = pos,
                onToggle = { playing = !playing },
                onNext = { index = (index + 1) % QUEUE.size; pos = 0f },
                onPrev = { if (pos > 4f) pos = 0f else { index = (index - 1 + QUEUE.size) % QUEUE.size; pos = 0f } },
                ringState = vm.ringState,
            )
        }
        item { OutputDeviceCard(vm) }
        item { SectionCard(title = "Albums", subtitle = "${LIBRARY.size} in your library") {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.height(480.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(LIBRARY) { al ->
                    val idx = QUEUE.indexOfFirst { it.album == al }
                    AlbumCard(al) { if (idx >= 0) { index = idx; pos = 0f; playing = true } }
                }
            }
        } }
        item { LyricsCard(track, pos) }
        item { SectionCard(title = "Queue", subtitle = "Up next on Nix Dev HHKJ") {
            Column {
                QUEUE.forEachIndexed { i, t ->
                    QueueRow(t, i == index) { index = i; pos = 0f; playing = true }
                }
            }
        } }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun MusicHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Music", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Pick an album or song — it plays on the Echo Dot, never this phone.",
                style = MaterialTheme.typography.bodySmall, color = NixPalette.Muted,
            )
        }
        Surface(shape = RoundedCornerShape(50), color = Color.Transparent, border = BorderStroke(1.dp, NixPalette.Ok)) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF3BE07C)))
                Spacer(Modifier.width(8.dp))
                Text("Nix Dev HHKJ", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NixPalette.Ok)
            }
        }
    }
}

@Composable
private fun NowPlayingCard(
    track: Track, album: Album, playing: Boolean, pos: Float,
    onToggle: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit,
    ringState: NixViewModel.RingState?,
) {
    SectionCard(title = "Now playing", subtitle = "On Nix Dev HHKJ") {
        Row {
            AlbumArt(album, Modifier.size(108.dp).clip(RoundedCornerShape(16.dp)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = MaterialTheme.typography.bodySmall, color = NixPalette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${album.title} · ${fmt(track.durSec)}", style = MaterialTheme.typography.labelSmall, color = NixPalette.Muted, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                LiveBars(playing = playing, color = Color(album.c2))
            }
        }
        Spacer(Modifier.height(12.dp))
        val progress = (pos / track.durSec).coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
            color = Color(album.c2), trackColor = NixPalette.Line,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(fmt(pos.toInt()), style = MaterialTheme.typography.labelSmall, color = NixPalette.Muted)
            Spacer(Modifier.weight(1f))
            Text(fmt(track.durSec), style = MaterialTheme.typography.labelSmall, color = NixPalette.Muted)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) { Icon(Icons.Outlined.SkipPrevious, "Previous", tint = NixPalette.Ink) }
            Surface(shape = CircleShape, color = NixPalette.Accent, modifier = Modifier.size(64.dp)) {
                IconButton(onClick = onToggle) {
                    Icon(
                        if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        tint = NixPalette.OnAccent, modifier = Modifier.size(30.dp),
                    )
                }
            }
            IconButton(onClick = onNext) { Icon(Icons.Outlined.FastForward, "Next", tint = NixPalette.Ink) }
        }
    }
}

@Composable
private fun AlbumArt(album: Album, modifier: Modifier = Modifier) {
    Box(modifier.background(artBrush(album))) {
        // Soft top-left sheen so flat gradients read as album art.
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.26f), Color.Transparent),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
            ),
        )
        Text(
            album.title.take(2).uppercase(),
            Modifier.align(Alignment.Center),
            color = Color.White.copy(alpha = 0.92f), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp,
        )
    }
}

/** Animated equaliser bars — a UI stand-in for real audio analysis. */
@Composable
private fun LiveBars(playing: Boolean, color: Color) {
    val transition = rememberInfiniteTransition(label = "bars")
    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Canvas(Modifier.fillMaxWidth().height(28.dp)) {
        val n = 28
        val gap = 3.dp.toPx()
        val bw = (size.width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val base = if (playing) 0.35f + 0.65f * kotlin.math.abs(sin(phase + i * 0.5f)) else 0.06f
            val bh = (base * size.height)
            drawRoundRect(
                color = color.copy(alpha = 0.4f + 0.6f * base),
                topLeft = Offset(i * (bw + gap), size.height - bh),
                size = androidx.compose.ui.geometry.Size(bw, bh),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(bw / 2),
            )
        }
    }
}

@Composable
private fun OutputDeviceCard(vm: NixViewModel) {
    val ring = vm.ringState
    SectionCard(title = "Playing on Nix Dev HHKJ", subtitle = "Echo Dot 2 (rooted) · 192.168.0.55") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DeviceGlyph(state = ring, connected = true, modifier = Modifier.size(56.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Bluetooth, null, tint = NixPalette.Ok, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Bluetooth · keep the unit nearby", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Text("BLE 18:74:2E:AF:D7:2D", style = MaterialTheme.typography.labelSmall, color = NixPalette.Muted)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Audio plays on the unit itself. This phone is the remote control.",
            style = MaterialTheme.typography.labelSmall, color = NixPalette.Muted,
        )
    }
}

@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit) {
    Column(Modifier.tapScale(onClick = onClick)) {
        AlbumArt(album, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)))
        Spacer(Modifier.height(8.dp))
        Text(album.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(album.artist, style = MaterialTheme.typography.bodySmall, color = NixPalette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LyricsCard(track: Track, pos: Float) {
    SectionCard(title = "Lyrics", subtitle = if (track.lyrics.isEmpty()) "Instrumental" else "Synced") {
        if (track.lyrics.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.MusicNote, null, tint = NixPalette.Line, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text("Instrumental — no lyrics", fontWeight = FontWeight.SemiBold)
                Text("“${track.title}” has no lyric data yet.", style = MaterialTheme.typography.bodySmall, color = NixPalette.Muted)
            }
        } else {
            var active by remember(track) { mutableIntStateOf(-1) }
            LaunchedEffect(pos, track) {
                active = track.lyrics.indexOfLast { pos >= it.first }
            }
            Column {
                track.lyrics.forEachIndexed { i, (_, line) ->
                    val isActive = i == active
                    val color by animateColorAsState(
                        if (isActive) NixPalette.Ink else if (i < active) NixPalette.Muted.copy(alpha = 0.6f) else NixPalette.Line,
                        label = "lyricColor",
                    )
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                        color = color,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueRow(track: Track, current: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (current) {
            Icon(Icons.Filled.GraphicEq, null, tint = NixPalette.Accent, modifier = Modifier.size(18.dp))
        } else {
            Box(Modifier.width(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        AlbumArt(track.album, Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                fontWeight = if (current) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 14.sp,
                color = if (current) NixPalette.Accent else NixPalette.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(track.artist, style = MaterialTheme.typography.bodySmall, color = NixPalette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(fmt(track.durSec), style = MaterialTheme.typography.labelSmall, color = NixPalette.Muted)
    }
}
