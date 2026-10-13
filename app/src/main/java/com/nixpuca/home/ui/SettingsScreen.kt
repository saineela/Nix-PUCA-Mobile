package com.nixpuca.home.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.nixpuca.home.NixViewModel
import com.nixpuca.home.TestLine

/**
 * Settings: server URL, Cloudflare Access service token, optional bearer key
 * and the connection test. Secrets stay masked and are only ever written to
 * the device credential store.
 */
@Composable
fun SettingsScreen(vm: NixViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        SectionCard(
            title = "Appearance",
            subtitle = "Theme, accent colour and dark mode",
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Dark theme",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "Ink-black surfaces with the official inverted palette.",
                        style = MaterialTheme.typography.labelSmall,
                        color = NixPalette.Muted,
                    )
                }
                Switch(
                    checked = vm.darkTheme,
                    onCheckedChange = { vm.setDarkThemeEnabled(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = NixPalette.Accent, checkedTrackColor = NixPalette.Accent.copy(alpha = 0.35f)),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Accent",
                style = MaterialTheme.typography.labelSmall,
                color = NixPalette.Muted,
            )
            Spacer(Modifier.height(6.dp))
            NixPalette.AccentOptions.chunked(3).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { (id, name) ->
                        AccentDot(
                            id = id,
                            name = name,
                            selected = vm.accentId == id,
                            modifier = Modifier.weight(1f),
                        ) { vm.setAccent(id) }
                    }
                    // Keep the row width stable when the last chunk is short.
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        SectionCard(
            title = "Connection",
            subtitle = "Where your NIX Home hub lives",
        ) {
            Field(
                label = "Hub address",
                value = vm.serverUrl,
                onChange = { vm.serverUrl = it; vm.saved = false },
                placeholder = "https://nix.example.com  or  http://192.168.1.10:49117",
                keyboardType = KeyboardType.Uri,
            )
            Field(
                label = "Home name",
                value = vm.location,
                onChange = { vm.location = it; vm.saved = false },
                placeholder = "My home",
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Advanced — only fill these in if your hub asks for them",
                style = MaterialTheme.typography.labelSmall,
                color = NixPalette.Muted,
            )
            Field(
                label = "Access ID",
                value = vm.cfClientId,
                onChange = { vm.cfClientId = it; vm.saved = false },
                placeholder = "leave empty if not needed",
            )
            Field(
                label = "Access key",
                value = vm.cfClientSecret,
                onChange = { vm.cfClientSecret = it; vm.saved = false },
                placeholder = "leave empty if not needed",
                masked = true,
            )
            Field(
                label = "Backup key",
                value = vm.bearerToken,
                onChange = { vm.bearerToken = it; vm.saved = false },
                placeholder = "leave empty if not needed",
                masked = true,
            )
            if (vm.config.insecureCredentialStore) {
                ErrorNote("Encrypted credential storage is unavailable on this device; secrets are kept in app-private storage only.")
            }
        }

        Button(
            onClick = { vm.saveSettings() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !vm.saved,
            colors = ButtonDefaults.buttonColors(
                containerColor = NixPalette.Ink,
                contentColor = NixPalette.Paper,
            ),
        ) {
            Text(if (vm.saved) "Saved" else "Save connection")
        }

        OutlinedButton(
            onClick = { vm.testConnection() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !vm.testBusy,
        ) {
            Text(if (vm.testBusy) "Checking…" else "Check connection")
        }

        if (vm.testLines.isNotEmpty()) {
            SectionCard(title = "Connection check", subtitle = "Live results from your hub") {
                vm.testLines.forEach { line -> TestLineRow(line) }
            }
        }

        SectionCard(
            title = "About",
            subtitle = "What NIX PUCA and this app are",
        ) {
            KeyValueList(
                listOf(
                    "App" to "NIX Home 1.0.0",
                    "Made for" to "the NIX PUCA system",
                    "Companion" to "Casper, built by Sai Neela",
                ),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.navigate(com.nixpuca.home.Screen.About) },
                border = BorderStroke(1.dp, NixPalette.Accent),
            ) {
                Text("Open about page", color = NixPalette.Accent)
            }
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

        SectionCard(
            title = "Skills",
            subtitle = "Marketplace catalog and update checks",
        ) {
            Text(
                text = "Check whether any installed skill has a newer package " +
                    "on the console's marketplace.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Runs on the hub and can take a moment — the result shows up right here.",
                style = MaterialTheme.typography.labelSmall,
                color = NixPalette.Muted,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.loadSkills(refresh = true) },
                enabled = vm.loading["skills-refresh"] != true,
                border = BorderStroke(1.dp, NixPalette.Accent),
            ) {
                Text(
                    text = if (vm.loading["skills-refresh"] == true) "Checking…" else "Check for skill updates",
                    color = NixPalette.Accent,
                )
            }
            vm.updateNotice?.let {
                Text(it, color = NixPalette.Ok, style = MaterialTheme.typography.bodySmall)
            }
            vm.dataError["skills-refresh"]?.let { ErrorNote(it) }
        }

        SectionCard(
            title = "Hardware monitor",
            subtitle = "GPU, memory and the silicon behind your hub",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "See the host's GPU live",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "A rotating card graphic, load and temperature gauges, " +
                            "memory fill bars and this phone's own stats.",
                        style = MaterialTheme.typography.labelSmall,
                        color = NixPalette.Muted,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.navigate(com.nixpuca.home.Screen.Hardware) },
                border = BorderStroke(1.dp, NixPalette.Accent),
            ) {
                Text("Open hardware monitor", color = NixPalette.Accent)
            }
        }

        SectionCard(
            title = "Developer options",
            subtitle = "Diagnostics and low-level system status",
        ) {
            Text(
                text = "System status",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Health, models, devices, sessions, pipeline stages and the raw trace feed.",
                style = MaterialTheme.typography.labelSmall,
                color = NixPalette.Muted,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.navigate(com.nixpuca.home.Screen.System) },
                border = BorderStroke(1.dp, NixPalette.Accent),
            ) {
                Text("Open system diagnostics", color = NixPalette.Accent)
            }
            if (vm.config.insecureCredentialStore) {
                Spacer(Modifier.height(8.dp))
                ErrorNote("Encrypted credential storage is unavailable on this device; secrets are kept in app-private storage only.")
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AccentDot(id: String, name: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val color = when (id) {
        "mono" -> NixPalette.Ink
        "violet" -> Color(0xFF6C4CF1)
        "cyan" -> Color(0xFF0E7CE8)
        "amber" -> Color(0xFFC77700)
        "rose" -> Color(0xFFD6336C)
        "teal" -> Color(0xFF0F7F78)
        "indigo" -> Color(0xFF3D4FD6)
        "crimson" -> Color(0xFFB4182C)
        else -> Color(0xFF1B8A4B)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (selected) Modifier.border(3.dp, NixPalette.Accent, CircleShape) else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = "Selected",
                    tint = if (color.luminance() > 0.6f) Color.Black else Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(name, style = MaterialTheme.typography.labelSmall, color = NixPalette.Muted)
    }
}

@Composable
private fun TestLineRow(line: TestLine) {
    androidx.compose.foundation.layout.Row(
        Modifier.padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (line.ok) {
            true -> Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = "pass",
                Modifier.size(16.dp),
                tint = NixPalette.Ok,
            )
            false -> Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = "fail",
                Modifier.size(16.dp),
                tint = NixPalette.Bad,
            )
            else -> Icon(
                Icons.Outlined.Info,
                contentDescription = "info",
                Modifier.size(16.dp),
                tint = NixPalette.Muted,
            )
        }
        Text(
            text = line.text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    masked: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = ImeAction.Next,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NixPalette.Ink,
            unfocusedBorderColor = NixPalette.Line,
        ),
    )
}
