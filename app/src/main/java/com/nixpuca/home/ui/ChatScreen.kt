package com.nixpuca.home.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nixpuca.home.ChatMessage
import com.nixpuca.home.ConversationItem
import com.nixpuca.home.ModelChoice
import com.nixpuca.home.NixViewModel
import com.nixpuca.home.data.NixConfig

/**
 * Chat: one conversation against Core's `POST /api/send`.
 *
 * The conversation id is stable across restarts, a pending reply shows a
 * progress bar instead of a guessed answer, and every reply carries the
 * route/rule/latency Core reported. All companion copy follows the model the
 * server says is active — with official Luna selected nothing may say Casper.
 */
@Composable
fun ChatScreen(vm: NixViewModel, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    var pendingModel by remember { mutableStateOf<ModelChoice?>(null) }
    var historyOpen by remember { mutableStateOf(false) }

    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) {
            listState.animateScrollToItem(vm.messages.size - 1)
        }
    }

    LaunchedEffect(Unit) {
        // The busy label must name the model that is actually active.
        if (vm.activeModelId == null || vm.modelChoices.isEmpty()) vm.loadModels()
        vm.loadConversations()
    }

    Column(modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (vm.messages.isEmpty()) "New conversation" else "Active conversation",
                    style = MaterialTheme.typography.labelSmall,
                    color = NixPalette.Muted,
                )
                Text(
                    text = when {
                        vm.activeModelId != null -> "Chatting with ${vm.companionName}"
                        vm.loading["models"] == true -> "Waking ${vm.companionName}…"
                        else -> vm.companionName
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = NixPalette.Ink,
                )
            }
            TextButton(onClick = {
                historyOpen = !historyOpen
                if (historyOpen && vm.conversations.isEmpty()) vm.loadConversations()
            }) {
                Text(
                    text = if (historyOpen) "Hide history" else "History",
                    style = MaterialTheme.typography.labelMedium,
                    color = NixPalette.Accent,
                )
            }
            IconButton(onClick = { vm.newConversation() }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "New conversation")
            }
        }

        // Built-in conversations section: past threads from /api/sessions.
        AnimatedVisibility(visible = historyOpen) {
            ConversationsPanel(vm)
        }

        if (vm.modelSelectable && vm.modelChoices.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    vm.modelChoices.forEach { choice ->
                        OutlinedButton(
                            onClick = { pendingModel = choice },
                            enabled = !vm.modelBusy && (choice.present || choice.active),
                            border = BorderStroke(
                                1.dp,
                                if (choice.active) NixPalette.Ink else NixPalette.Line,
                            ),
                        ) {
                            Text(
                                text = (if (choice.active) "● " else "") + choice.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (choice.active) NixPalette.Ink else NixPalette.Muted,
                            )
                        }
                    }
                }
                if (vm.modelBusy) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = NixPalette.Ink,
                    )
                }
                vm.modelNotice?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = NixPalette.Ok,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                vm.dataError["model-select"]?.let { ErrorNote(it) }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (vm.messages.isEmpty()) {
                item {
                    Text(
                        text = "Say something to ${vm.companionName} — ask anything, plan your day, or control your home.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NixPalette.Muted,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
            items(vm.messages, key = { it.id }) { message ->
                MessageBubble(message)
            }
            if (vm.chatBusy) {
                item {
                    Column {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = NixPalette.Ink,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "${vm.companionName} is thinking…",
                            style = MaterialTheme.typography.bodySmall,
                            color = NixPalette.Muted,
                        )
                    }
                }
            }
        }

        vm.dataError["chat"]?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = NixPalette.Bad,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = vm.draft,
                onValueChange = { vm.draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message ${vm.companionName}") },
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NixPalette.Accent,
                    unfocusedBorderColor = NixPalette.Line,
                ),
            )
            Spacer(Modifier.width(4.dp))

            // Voice typing: hand the mic to the system recognizer, drop the
            // transcript into the draft so it can be edited before sending.
            val speechLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                val spoken = result.data
                    ?.getStringArrayListExtra(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!spoken.isNullOrBlank()) {
                    vm.draft = (vm.draft.trim() + " " + spoken.trim()).trim()
                }
            }
            IconButton(
                onClick = {
                    val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                        )
                        putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Say your message")
                    }
                    try {
                        speechLauncher.launch(intent)
                    } catch (t: Throwable) {
                        vm.dataError["chat"] = "Voice input isn't available on this phone."
                    }
                },
                enabled = !vm.chatBusy,
            ) {
                Icon(
                    Icons.Outlined.Mic,
                    contentDescription = "Voice input",
                    tint = NixPalette.Accent,
                )
            }

            IconButton(
                onClick = { vm.sendDraft() },
                enabled = !vm.chatBusy && vm.draft.isNotBlank(),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (vm.draft.isNotBlank()) NixPalette.Accent else NixPalette.Muted,
                )
            }
        }
    }

    pendingModel?.let { choice ->
        ConfirmDialog(
            title = "Switch to ${choice.name}?",
            message = if (choice.active) {
                "${choice.name} is already the active model."
            } else {
                "${vm.companionName} will answer with ${choice.name} from now on. The first reply " +
                    "after switching can take a little longer."
            },
            confirmLabel = "Switch",
            onConfirm = {
                pendingModel = null
                vm.selectModel(choice.id)
            },
            onDismiss = { pendingModel = null },
        )
    }
}

/**
 * Built-in conversations section: past threads from GET /api/sessions with
 * expandable transcripts and a one-tap continue action.
 */
@Composable
private fun ConversationsPanel(vm: NixViewModel) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = NixPalette.Mist,
        border = BorderStroke(1.dp, NixPalette.Line),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "CONVERSATIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = NixPalette.Muted,
                    modifier = Modifier.weight(1f),
                )
                if (vm.conversationsBusy) {
                    androidx.compose.material3.CircularProgressIndicator(
                        Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = NixPalette.Accent,
                    )
                } else {
                    TextButton(onClick = { vm.loadConversations() }) {
                        Text("Refresh", style = MaterialTheme.typography.labelSmall, color = NixPalette.Accent)
                    }
                }
            }
            vm.dataError["sessions"]?.let { ErrorNote(it) }
            if (!vm.conversationsBusy && vm.conversations.isEmpty() && vm.dataError["sessions"] == null) {
                Text(
                    text = "No past conversations yet — your threads appear here once you chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NixPalette.Muted,
                )
            }
            vm.conversations.take(12).forEach { conversation ->
                ConversationRow(vm, conversation)
            }
        }
    }
}

@Composable
private fun ConversationRow(vm: NixViewModel, item: ConversationItem) {
    val expanded = vm.openConversationId == item.id
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (expanded) NixPalette.Paper else Color.Transparent)
            .clickable { vm.openConversation(item.id) }
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = "${item.source} · ${item.turnCount} turns" +
                        (item.lastTurn.takeIf { it.isNotBlank() }?.let { " · ${it.take(16)}" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = NixPalette.Muted,
                )
            }
            Icon(
                if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = NixPalette.Muted,
                modifier = Modifier.size(18.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                if (item.preview.isNotBlank()) {
                    Text(
                        text = item.preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = NixPalette.Muted,
                        modifier = Modifier.padding(top = 6.dp),
                        maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                Row(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { vm.continueConversation(item) }) {
                        Text("Continue", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(onClick = { vm.newConversation(); vm.openConversation(null) }) {
                        Text("Open new", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val fromUser = message.role == "user"
    val isError = message.role == "error"
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (fromUser) 16.dp else 4.dp,
                bottomEnd = if (fromUser) 4.dp else 16.dp,
            ),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    fromUser -> NixPalette.Ink
                    isError -> NixPalette.Paper
                    else -> NixPalette.Mist
                },
            ),
            border = if (isError) BorderStroke(1.dp, NixPalette.Bad) else null,
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    fromUser -> NixPalette.Paper
                    isError -> NixPalette.Bad
                    else -> NixPalette.Ink
                },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        if (!fromUser && !isError) {
            val meta = buildString {
                message.assistant?.takeIf { it.isNotBlank() }?.let { append(it) }
                message.latencyMs?.let {
                    if (isNotEmpty()) append(" · ")
                    append("answered in %.0f ms".format(it))
                }
            }
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = NixPalette.Muted,
                    modifier = Modifier.padding(start = 6.dp, top = 2.dp),
                )
            }
            if (message.pipeline.isNotEmpty()) {
                var expanded by remember(message.id) { mutableStateOf(false) }
                androidx.compose.material3.TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        text = if (expanded) "Hide details" else "How it was answered · ${message.pipeline.size} steps",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (expanded) {
                    Column(Modifier.padding(start = 6.dp)) {
                        message.pipeline.forEach { (label, status) ->
                            Text(
                                text = "$label — $status",
                                style = MaterialTheme.typography.bodySmall,
                                color = NixPalette.Muted,
                            )
                        }
                    }
                }
            }
        }
    }
}
