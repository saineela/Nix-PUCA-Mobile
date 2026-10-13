package com.nixpuca.home

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nixpuca.home.ui.AboutScreen
import com.nixpuca.home.ui.ChatScreen
import com.nixpuca.home.ui.DevicesScreen
import com.nixpuca.home.ui.HardwareScreen
import com.nixpuca.home.ui.HomeScreen
import com.nixpuca.home.ui.KnowledgeScreen
import com.nixpuca.home.ui.MusicScreen
import com.nixpuca.home.ui.NixDevScreen
import com.nixpuca.home.ui.NixPalette
import com.nixpuca.home.ui.PairDeviceScreen
import com.nixpuca.home.ui.RingEffectBackground
import com.nixpuca.home.ui.ReconnectScreen
import com.nixpuca.home.ui.ScheduleScreen
import com.nixpuca.home.ui.SettingsScreen
import com.nixpuca.home.ui.SetupFlow
import com.nixpuca.home.ui.SkillsScreen
import com.nixpuca.home.ui.SystemScreen
import com.nixpuca.home.ui.theme.NixHomeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("nix_home_prefs", MODE_PRIVATE)
        // Draw behind the system bars on every Android version. Without this
        // the window stops above the gesture area, so the bottom taskbar can
        // never reach the true screen edge and hangs mid-air above the
        // navigation strip. Insets are then applied in Compose instead.
        applySystemBarStyle(prefs.getBoolean("dark_theme", false))
        // Persist the stack trace of any uncaught exception so a crash (which
        // kills the process before anything can be logged off-device) is still
        // readable on the next launch, in the banner below.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val trace = android.util.Log.getStackTraceString(throwable)
            try {
                prefs.edit().putString(KEY_LAST_CRASH, trace).commit()
            } catch (_: Throwable) {
                // Never let the handler itself crash the process.
            }
            previous?.uncaughtException(thread, throwable)
        }
        val storedCrash = prefs.getString(KEY_LAST_CRASH, null)
        // Schedule times are shown the way this phone's own clock reads them
        // (12h vs 24h) — presentation only, the server still sends ISO time.
        com.nixpuca.home.data.TimeFormat.configure(
            android.text.format.DateFormat.is24HourFormat(this),
        )
        setContent {
            NixHomeTheme {
                // Keep the system-bar icon colours in step with the in-app
                // theme even when the dark switch flips at runtime.
                val barDark = NixPalette.dark
                SideEffect { applySystemBarStyle(barDark) }
                NixHomeApp(
                    initialCrash = storedCrash,
                    onDismissCrash = { prefs.edit().remove(KEY_LAST_CRASH).apply() },
                )
            }
        }
    }

    /** Last style applied, so recompositions don't restyle the bars. */
    private var styledForDark: Boolean? = null

    /**
     * Edge-to-edge with bar icon colours matching the in-app theme: the
     * light theme needs dark status icons, the dark theme light ones.
     */
    private fun applySystemBarStyle(dark: Boolean) {
        if (styledForDark == dark) return
        styledForDark = dark
        val style = if (dark) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            )
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    companion object {
        private const val KEY_LAST_CRASH = "last_crash"
    }
}

/**
 * The five-slot taskbar: Home · Devices · [big Chat orb] · Skills · Schedule.
 * Knowledge, System and Settings stay reachable from the top bar / Settings.
 */
private data class TabSpec(val screen: Screen, val icon: ImageVector)

private val TAB_SLOTS = listOf(
    TabSpec(Screen.Home, Icons.Outlined.Home),
    TabSpec(Screen.Devices, Icons.Outlined.PhoneAndroid),
    TabSpec(Screen.Chat, Icons.Outlined.ChatBubbleOutline),
    TabSpec(Screen.Skills, Icons.Outlined.Extension),
    TabSpec(Screen.Schedule, Icons.Outlined.CalendarMonth),
)

/**
 * App shell: title top bar (no URL/credentials), the screen content, and a
 * taskbar whose centre Chat button pops out of the bar as a circle. There is
 * no splash — the app opens straight to its content while the cold-start
 * probes fill state in behind it.
 */
@Composable
fun NixHomeApp(
    vm: NixViewModel = viewModel(),
    initialCrash: String? = null,
    onDismissCrash: () -> Unit = {},
) {
    val configured = vm.configured
    var shownCrash by remember { mutableStateOf(initialCrash) }
    val current = if (!configured) Screen.Settings else vm.screen

    // Apply the saved theme/accent once, and kick off the cold-start
    // probes. Keyed on `configured` so a brand-new user gets them the
    // moment setup saves a hub address — with LaunchedEffect(Unit) they
    // fired once while unconfigured, bailed, and never ran again.
    LaunchedEffect(configured) {
        NixPalette.applyTheme(vm.darkTheme)
        NixPalette.applyAccent(vm.accentId)
        vm.bootstrap()
    }

    Box(Modifier.fillMaxSize()) {
        // Optional ring-light effect mirror behind everything.
        if (vm.mirrorRing) {
            RingEffectBackground(vm.ringState)
        }

        if (vm.setupNeeded) {
            SetupFlow(vm = vm, Modifier.fillMaxSize())
        } else if (configured && !vm.sessionActive) {
            // Hub unreachable at open → signed out, but the connection stays
            // cached so the user can sign back in with one tap.
            ReconnectScreen(vm = vm, Modifier.fillMaxSize())
        } else {
            Scaffoldish(
                current = current,
                vm = vm,
                shownCrash = shownCrash,
                onDismissCrash = {
                    shownCrash = null
                    onDismissCrash()
                },
            )
        }

        // No splash layer here by design: bootstrap() probes above run in
        // the background, so health/profile/model state lands as it arrives.
    }
}

@Composable
private fun Scaffoldish(
    current: Screen,
    vm: NixViewModel,
    shownCrash: String?,
    onDismissCrash: () -> Unit,
) {
    // Layers, bottom to top: opaque Paper base, the optional ring-mirror
    // wash, then the app content. The mirror used to sit *behind* an opaque
    // background so it could never be seen — keep it between base and UI.
    Box(Modifier.fillMaxSize()) {
        Spacer(Modifier.matchParentSize().background(NixPalette.Paper))
        if (vm.mirrorRing) {
            RingEffectBackground(vm.ringState)
        }
        Column(Modifier.fillMaxSize()) {
            TopProfileBar(vm = vm, current = current)
            if (shownCrash != null) {
                CrashBanner(shownCrash, onDismissCrash)
            }
            Box(Modifier.weight(1f).fillMaxSize()) {
                // Animated content swap so tab changes feel smooth.
                AnimatedContent(
                    targetState = current,
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(initialScale = 0.985f)) togetherWith
                            fadeOut(tween(140))
                    },
                    label = "screen",
                ) { screen ->
                    when (screen) {
                        Screen.Home -> HomeScreen(vm, Modifier.fillMaxSize())
                        Screen.Chat -> ChatScreen(vm, Modifier.fillMaxSize())
                        Screen.Knowledge -> KnowledgeScreen(vm, Modifier.fillMaxSize())
                        Screen.Schedule -> ScheduleScreen(vm, Modifier.fillMaxSize())
                        Screen.Skills -> SkillsScreen(vm, Modifier.fillMaxSize())
                        Screen.Devices -> DevicesScreen(vm, Modifier.fillMaxSize())
                        Screen.Music -> MusicScreen(vm, Modifier.fillMaxSize())
                        Screen.NixDev -> NixDevScreen(vm, Modifier.fillMaxSize())
                        Screen.PairDevice -> PairDeviceScreen(vm, Modifier.fillMaxSize())
                        Screen.System -> SystemScreen(vm, Modifier.fillMaxSize())
                        Screen.Hardware -> HardwareScreen(vm, Modifier.fillMaxSize())
                        Screen.Settings -> SettingsScreen(vm, Modifier.fillMaxSize())
                        Screen.About -> AboutScreen(vm, Modifier.fillMaxSize())
                    }
                }
            }
            TaskBar(
                current = current,
                onSelect = { vm.navigate(it) },
            )
        }
    }
}

/**
 * Top bar: the current screen's title plus refresh and settings. No profile
 * chip — accounts were dropped from the app — and never the URL or
 * credential status; that lives inside Settings only.
 */
@Composable
private fun TopProfileBar(vm: NixViewModel, current: Screen) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(NixPalette.Paper)
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = current.label,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = NixPalette.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (current != Screen.Settings && current != Screen.About) {
            IconButton(onClick = { refresh(vm, current) }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
            }
        }
        IconButton(onClick = { vm.screen = Screen.Settings }) {
            Icon(Icons.Outlined.Settings, contentDescription = "Settings")
        }
    }
}

/**
 * Five-slot taskbar: every destination is an equal-weight peer with one
 * shared accent pill that slides behind the active tab.
 */
@Composable
private fun TaskBar(current: Screen, onSelect: (Screen) -> Unit) {
    val tabs = TAB_SLOTS
    // Sub-screens (pairing, Nix Dev detail) light up their owning tab so the
    // active destination always reads as a stable anchor.
    fun ownerOf(screen: Screen): Screen =
        if (screen == Screen.PairDevice || screen == Screen.NixDev) Screen.Devices else screen
    val activeTab = ownerOf(current)
    val selectedIndex = tabs.indexOfFirst { it.screen == activeTab }.coerceAtLeast(0)

    // The surface reaches the true screen edge; the nav inset pads only the
    // content so the bar anchors to the bottom like a real system tab bar.
    Surface(
        color = NixPalette.Paper,
        shadowElevation = 16.dp,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NixPalette.Line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val horizontalPadding = 10.dp
            val innerWidth = maxWidth - horizontalPadding * 2
            val tabWidthPx = with(density) { (innerWidth / tabs.size).toPx() }
            val insetPx = with(density) { 11.dp.toPx() }

            // One shared pill that eases to the active tab, so switching reads
            // as a single continuous motion instead of a colour pop.
            val indicatorX by androidx.compose.animation.core.animateFloatAsState(
                targetValue = selectedIndex * tabWidthPx + insetPx,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                ),
                label = "tabIndicatorX",
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding)
                    .height(64.dp),
            ) {
                // Sliding active pill, drawn behind the tabs.
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .graphicsLayer { translationX = indicatorX }
                        .width(with(density) { (tabWidthPx - insetPx * 2).toDp() })
                        .height(44.dp)
                        .clip(RoundedCornerShape(16.dp))
                        // nuxt-ui Tabs pill default: solid `bg-primary` indicator
                        // with inverted trigger text, so selection reads as a
                        // filled pill rather than a faint tint.
                        .background(NixPalette.Accent),
                )
                Row(
                    Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEach { tab ->
                        TaskTab(
                            tab = tab,
                            selected = tab.screen == activeTab,
                            onClick = { onSelect(tab.screen) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun TaskTab(
    tab: TabSpec,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The active pill lives in TaskBar as one shared, sliding indicator, so a
    // tab only renders its icon + label. That keeps all five peers identically
    // weighted — no tab is louder than another — and the filled pill + weight
    // contrast (not colour alone) signals selection.
    val iconScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
        ),
        label = "tabIcon",
    )
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            tab.icon,
            contentDescription = tab.screen.label,
            tint = if (selected) NixPalette.OnAccent else NixPalette.Muted,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
        )
        Text(
            text = tab.screen.label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = if (selected) NixPalette.OnAccent else NixPalette.Muted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/**
 * Diagnostic banner for the persisted uncaught-exception trace.
 *
 * This must stay height-bounded: an unbounded banner lets a long stack trace
 * consume the whole content column and starve the screen below it to zero
 * height, which renders every page blank. Each trace line is its own [Text]
 * so the tree stays readable line by line.
 */
@Composable
private fun CrashBanner(trace: String, onDismiss: () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .heightIn(max = 168.dp)
            .verticalScroll(rememberScrollState())
            .background(Color(0xFFFFEBEE), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Last crash:",
                color = Color(0xFFB00020),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = Color(0xFFB00020))
            }
        }
        trace.split('\n').forEach { line ->
            Text(
                text = line.ifEmpty { " " },
                color = Color(0xFFB00020),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun refresh(vm: NixViewModel, screen: Screen) {
    when (screen) {
        Screen.Home -> vm.refreshHome()
        Screen.Knowledge -> vm.loadKnowledge()
        Screen.Schedule -> vm.loadSchedule()
        Screen.Skills -> vm.loadSkills()
        Screen.Devices -> vm.loadDevices()
        // Music is UI-only until the API + skill land — nothing to refresh yet.
        Screen.Music -> Unit
        // The detail page probes its own unit — a refresh re-probes it.
        Screen.NixDev -> vm.nixDevTick++
        Screen.PairDevice -> Unit
        Screen.System -> vm.loadSystem()
        Screen.Hardware -> vm.loadHealth()
        // Chat keeps its conversation; refresh re-reads the active model.
        Screen.Chat -> vm.loadModels()
        Screen.Settings -> vm.testConnection()
        // Static copy — nothing to reload.
        Screen.About -> Unit
    }
}
