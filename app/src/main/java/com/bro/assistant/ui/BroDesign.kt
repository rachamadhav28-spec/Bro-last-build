import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object BroDesign {
    val Background = Color(0xFF121212)
    val Surface = Color(0xFF1E1E1E)
    val SurfaceElevated = Color(0xFF2A2A2A)
    val PrimaryAccent = Color(0xFF6200EE)
    val SecondaryAccent = Color(0xFF03DAC6)
    val CrispWhite = Color(0xFFFFFFFF)
    val MutedText = Color(0xFF9E9E9E)
    val BorderColor = Color(0xFF333333)

    val CardShape = RoundedCornerShape(16.dp)
    val ComposerShape = RoundedCornerShape(24.dp)
    val PillShape = RoundedCornerShape(50.dp)

    val HeaderStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        color = CrispWhite
    )
    val BodyStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = CrispWhite
    )
    val LabelStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        color = MutedText
    )
}

@Composable
fun MinimalCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(BroDesign.CardShape)
            .background(BroDesign.Surface)
            .border(1.dp, BroDesign.BorderColor, BroDesign.CardShape)
            .padding(16.dp),
        content = content
    )
}
EOF

cat << 'EOF' > app/src/main/java/com/bro/assistant/ui/Theme.kt
package com.bro.assistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MinimalDarkPalette = darkColorScheme(
    primary = BroDesign.PrimaryAccent,
    secondary = BroDesign.SecondaryAccent,
    background = BroDesign.Background,
    surface = BroDesign.Surface,
    onPrimary = BroDesign.CrispWhite,
    onSecondary = BroDesign.CrispWhite,
    onBackground = BroDesign.CrispWhite,
    onSurface = BroDesign.CrispWhite
)

@Composable
fun BroTheme(
    themeMode: String = "DARK",
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MinimalDarkPalette,
        content = content
    )
}
EOF

cat << 'EOF' > app/src/main/java/com/bro/assistant/MainActivity.kt
package com.bro.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bro.assistant.ai.BroSkill
import com.bro.assistant.ai.ProviderId
import com.bro.assistant.ai.Providers
import com.bro.assistant.proactive.Briefing
import com.bro.assistant.security.SecretBox
import com.bro.assistant.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String,
    val text: String,
    val provider: String? = null,
    val isStreaming: Boolean = false
)

enum class AppScreen {
    CHAT, SETTINGS
}

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Main)

    private val micLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListeningVoice()
    }

    var isListeningState by mutableStateOf(false)
    var currentStatusText by mutableStateOf("Ready")
    var userTypedInput by mutableStateOf("")
    var selectedSkill by mutableStateOf(BroSkill.GENERAL)
    var useWebGrounding by mutableStateOf(false)
    var currentActiveProvider by mutableStateOf("Auto")
    var currentScreen by mutableStateOf(AppScreen.CHAT)

    val chatMessages = mutableStateListOf<ChatMessage>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        if (chatMessages.isEmpty()) {
            val briefing = try {
                Briefing.generateMorningBriefing(this)
            } catch (_: Exception) {
                "Hello Madhav, BRO is online and ready."
            }
            chatMessages.add(ChatMessage(sender = "BRO", text = briefing, provider = "Assistant"))
        }

        setContent {
            BroTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BroDesign.Background) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BroDesign.Surface)
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("BRO Assistant", style = BroDesign.HeaderStyle)
                                Text(currentStatusText, style = BroDesign.LabelStyle)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(BroDesign.PillShape)
                                        .background(if (currentScreen == AppScreen.CHAT) BroDesign.PrimaryAccent else BroDesign.SurfaceElevated)
                                        .clickable { currentScreen = AppScreen.CHAT }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text("Chat", style = BroDesign.LabelStyle.copy(color = Color.White))
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(BroDesign.PillShape)
                                        .background(if (currentScreen == AppScreen.SETTINGS) BroDesign.PrimaryAccent else BroDesign.SurfaceElevated)
                                        .clickable { currentScreen = AppScreen.SETTINGS }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text("Settings", style = BroDesign.LabelStyle.copy(color = Color.White))
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            when (currentScreen) {
                                AppScreen.CHAT -> CleanChatView(
                                    messages = chatMessages,
                                    typedInput = userTypedInput,
                                    onInputChanged = { userTypedInput = it },
                                    isListening = isListeningState,
                                    skill = selectedSkill,
                                    onSkillSelected = { selectedSkill = it },
                                    useWebGrounding = useWebGrounding,
                                    onGroundingToggled = { useWebGrounding = it },
                                    activeProvider = currentActiveProvider,
                                    onSend = { sendPrompt(userTypedInput) },
                                    onStop = {
                                        Providers.cancelActiveJob()
                                        currentStatusText = "Halted"
                                    },
                                    onMicClicked = {
                                        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                            startListeningVoice()
                                        } else {
                                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                )
                                AppScreen.SETTINGS -> SettingsView(context = this@MainActivity)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale.US
    }

    private fun startListeningVoice() {
        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListeningState = true; currentStatusText = "Listening..." }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListeningState = false; currentStatusText = "Processing..." }
            override fun onError(error: Int) { isListeningState = false; currentStatusText = "Ready" }
            override fun onResults(results: Bundle?) {
                isListeningState = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    userTypedInput = matches[0]
                    sendPrompt(userTypedInput)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer?.startListening(intent)
    }

    private fun sendPrompt(prompt: String) {
        val cleaned = prompt.trim()
        if (cleaned.isBlank()) return

        chatMessages.add(ChatMessage(sender = "USER", text = cleaned))
        userTypedInput = ""

        val botId = java.util.UUID.randomUUID().toString()
        chatMessages.add(ChatMessage(id = botId, sender = "BRO", text = "Thinking...", isStreaming = true))

        scope.launch {
            val result = Providers.streamChat(
                context = this@MainActivity,
                prompt = cleaned,
                skill = selectedSkill,
                enableWebGrounding = useWebGrounding,
                onStatus = { currentStatusText = it },
                onToken = { token ->
                    val idx = chatMessages.indexOfFirst { it.id == botId }
                    if (idx != -1) {
                        val curr = chatMessages[idx]
                        val nextText = if (curr.text == "Thinking...") token else curr.text + token
                        chatMessages[idx] = curr.copy(text = nextText)
                    }
                }
            )

            val idx = chatMessages.indexOfFirst { it.id == botId }
            if (idx != -1) {
                result.fold(
                    onSuccess = { res ->
                        currentActiveProvider = res.providerUsed.displayName
                        currentStatusText = "Ready"
                        chatMessages[idx] = chatMessages[idx].copy(provider = res.providerUsed.displayName, isStreaming = false)
                        if (res.fullText.length < 300) {
                            tts?.speak(res.fullText.replace(Regex("[#*`_]"), ""), TextToSpeech.QUEUE_FLUSH, null, "tts")
                        }
                    },
                    onFailure = { err ->
                        currentStatusText = "Error"
                        chatMessages[idx] = chatMessages[idx].copy(text = "❌ ${err.localizedMessage}", isStreaming = false)
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        tts?.stop(); tts?.shutdown()
        speechRecognizer?.destroy()
        super.onDestroy()
    }
}

@Composable
fun CleanChatView(
    messages: List<ChatMessage>,
    typedInput: String,
    onInputChanged: (String) -> Unit,
    isListening: Boolean,
    skill: BroSkill,
    onSkillSelected: (BroSkill) -> Unit,
    useWebGrounding: Boolean,
    onGroundingToggled: (Boolean) -> Unit,
    activeProvider: String,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMicClicked: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BroSkill.values().forEach { s ->
                val sel = s == skill
                Box(
                    modifier = Modifier
                        .clip(BroDesign.PillShape)
                        .background(if (sel) BroDesign.PrimaryAccent else BroDesign.Surface)
                        .clickable { onSkillSelected(s) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(text = s.name, style = BroDesign.LabelStyle.copy(color = if (sel) Color.White else BroDesign.MutedText))
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(BroDesign.PillShape)
                    .background(if (useWebGrounding) BroDesign.SecondaryAccent else BroDesign.Surface)
                    .clickable { onGroundingToggled(!useWebGrounding) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(text = if (useWebGrounding) "Web: ON" else "Web: OFF", style = BroDesign.LabelStyle.copy(color = if (useWebGrounding) Color.Black else BroDesign.MutedText))
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isUser = msg.sender == "USER"
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                    MinimalCard {
                        Text(
                            text = if (isUser) "You" else "BRO (${msg.provider ?: activeProvider})",
                            style = BroDesign.LabelStyle.copy(color = if (isUser) BroDesign.SecondaryAccent else BroDesign.PrimaryAccent)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = msg.text, style = BroDesign.BodyStyle)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BroDesign.Surface)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(BroDesign.ComposerShape)
                    .background(BroDesign.Background)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (typedInput.isEmpty()) {
                    Text("Type a message...", style = BroDesign.BodyStyle.copy(color = BroDesign.MutedText))
                }
                BasicTextField(
                    value = typedInput,
                    onValueChange = onInputChanged,
                    textStyle = BroDesign.BodyStyle,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onMicClicked) {
                Icon(Icons.Default.Mic, contentDescription = "Voice", tint = if (isListening) BroDesign.SecondaryAccent else BroDesign.CrispWhite)
            }
            IconButton(onClick = onSend) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = BroDesign.SecondaryAccent)
            }
            IconButton(onClick = onStop) {
                Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.Red)
            }
        }
    }
}

@Composable
fun SettingsView(context: android.content.Context) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            MinimalCard {
                Text("API Key Vault", style = BroDesign.HeaderStyle)
                Text("Keys are securely encrypted on your device.", style = BroDesign.LabelStyle)
            }
        }
        items(ProviderId.values().toList(), key = { it.name }) { provider ->
            if (provider != ProviderId.OPENROUTER_CLAUDE) {
                var keyInput by remember { mutableStateOf(SecretBox.getKey(context, provider.name)) }
                MinimalCard {
                    Text(provider.displayName, style = BroDesign.HeaderStyle.copy(fontSize = 14.sp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(BroDesign.ComposerShape)
                                .background(BroDesign.Background)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            if (keyInput.isEmpty()) {
                                Text("Enter key...", style = BroDesign.BodyStyle.copy(color = BroDesign.MutedText, fontSize = 13.sp))
                            }
                            BasicTextField(
                                value = keyInput,
                                onValueChange = { keyInput = it },
                                textStyle = BroDesign.BodyStyle.copy(fontSize = 13.sp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(BroDesign.PillShape)
                                .background(BroDesign.PrimaryAccent)
                                .clickable {
                                    SecretBox.saveKey(context, provider.name, keyInput)
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text("Save", style = BroDesign.LabelStyle.copy(color = Color.White))
                        }
                    }
                }
            }
        }
    }
}

git commit -m "Apply clean minimalist UI"
git pus
