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
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bro.assistant.ai.AiStreamResult
import com.bro.assistant.ai.BroSkill
import com.bro.assistant.ai.Providers
import com.bro.assistant.security.SecretBox
import com.bro.assistant.ui.BroDesign
import com.bro.assistant.ui.BroTheme
import com.bro.assistant.ui.CyberRadarCore
import com.bro.assistant.ui.TacticalCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "USER" or "BRO"
    val text: String,
    val provider: String? = null,
    val isStreaming: Boolean = false
)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Main)

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListeningVoice()
    }

    var isListeningState by mutableStateOf(false)
    var isSpeakingState by mutableStateOf(false)
    var currentStatusText by mutableStateOf("SYSTEM ARMED")
    var userTypedInput by mutableStateOf("")
    var selectedSkill by mutableStateOf(BroSkill.GENERAL)
    var useWebGrounding by mutableStateOf(false)
    var activeThemeMode by mutableStateOf("DARK")
    var currentActiveProvider by mutableStateOf("None")

    val chatMessages = mutableStateListOf<ChatMessage>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        activeThemeMode = SecretBox.getSetting(this, "theme_mode", "DARK")

        setContent {
            BroTheme(themeMode = activeThemeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BroDesign.ObsidianBg
                ) {
                    MainChatHudScreen(
                        messages = chatMessages,
                        statusText = currentStatusText,
                        typedInput = userTypedInput,
                        onInputChanged = { userTypedInput = it },
                        isListening = isListeningState,
                        isSpeaking = isSpeakingState,
                        skill = selectedSkill,
                        onSkillSelected = { selectedSkill = it },
                        useWebGrounding = useWebGrounding,
                        onGroundingToggled = { useWebGrounding = it },
                        activeProvider = currentActiveProvider,
                        onSend = { sendPrompt(userTypedInput) },
                        onStop = {
                            Providers.cancelActiveJob()
                            currentStatusText = "JOB HALTED BY OPERATOR"
                        },
                        onMicClicked = {
                            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                startListeningVoice()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    private fun startListeningVoice() {
        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListeningState = true; currentStatusText = "LISTENING..." }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListeningState = false; currentStatusText = "PROCESSING SPEECH..." }
            override fun onError(error: Int) { isListeningState = false; currentStatusText = "MIC ERROR" }
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

        val botMsgId = java.util.UUID.randomUUID().toString()
        val botMessage = ChatMessage(id = botMsgId, sender = "BRO", text = "CONNECTING...", isStreaming = true)
        chatMessages.add(botMessage)

        scope.launch {
            val result = Providers.streamChat(
                context = this@MainActivity,
                prompt = cleaned,
                skill = selectedSkill,
                enableWebGrounding = useWebGrounding,
                onStatus = { status -> currentStatusText = status },
                onToken = { token ->
                    val index = chatMessages.indexOfFirst { it.id == botMsgId }
                    if (index != -1) {
                        val current = chatMessages[index]
                        val updatedText = if (current.text == "CONNECTING...") token else current.text + token
                        chatMessages[index] = current.copy(text = updatedText)
                    }
                }
            )

            val index = chatMessages.indexOfFirst { it.id == botMsgId }
            if (index != -1) {
                result.fold(
                    onSuccess = { res ->
                        currentActiveProvider = res.providerUsed.displayName
                        currentStatusText = "ONLINE [${res.providerUsed.name}]"
                        chatMessages[index] = chatMessages[index].copy(
                            provider = res.providerUsed.displayName,
                            isStreaming = false
                        )
                        speakOut(res.fullText)
                    },
                    onFailure = { err ->
                        currentStatusText = "ERROR"
                        chatMessages[index] = chatMessages[index].copy(
                            text = "❌ ${err.localizedMessage}",
                            isStreaming = false
                        )
                    }
                )
            }
        }
    }

    private fun speakOut(text: String) {
        val cleanText = text.replace(Regex("```[\s\S]*?```"), "code block omitted.")
            .replace(Regex("[#*`_]"), "")
        if (cleanText.length < 400) {
            isSpeakingState = true
            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "bro_tts")
            scope.launch {
                kotlinx.coroutines.delay(2500L)
                isSpeakingState = false
            }
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
        super.onDestroy()
    }
}

@Composable
fun MainChatHudScreen(
    messages: List<ChatMessage>,
    statusText: String,
    typedInput: String,
    onInputChanged: (String) -> Unit,
    isListening: Boolean,
    isSpeaking: Boolean,
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
    LaunchedEffect(messages.size, messages.lastOrNull()?.text) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BroDesign.ObsidianBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Tactical Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BroDesign.CarbonSurface)
                .border(1.dp, BroDesign.BorderGrid, BroDesign.CardShape)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                CyberRadarCore(isActive = isListening, isSpeaking = isSpeaking, size = 48.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = "BRO TACTICAL HUD", style = BroDesign.HeaderStyle.copy(fontSize = 16.sp))
                    Text(text = "STAT: $statusText", style = BroDesign.HudLabelStyle.copy(color = BroDesign.VoltLime, fontSize = 10.sp))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "ACTIVE: $activeProvider", style = BroDesign.HudLabelStyle.copy(color = BroDesign.CyberCyan, fontSize = 9.sp))
                Text(text = "WEB: ${if (useWebGrounding) "ON" else "OFF"}", style = BroDesign.HudLabelStyle.copy(color = if (useWebGrounding) BroDesign.VoltLime else BroDesign.MutedSteel, fontSize = 9.sp))
            }
        }

        // Skill Selector Bar & Grounding Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BroSkill.values().forEach { s ->
                val selected = s == skill
                Box(
                    modifier = Modifier
                        .clip(BroDesign.PillShape)
                        .background(if (selected) BroDesign.VoltLime else BroDesign.CarbonSurface)
                        .border(1.dp, if (selected) BroDesign.VoltLime else BroDesign.BorderGrid, BroDesign.PillShape)
                        .clickable { onSkillSelected(s) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = s.name,
                        style = BroDesign.HudLabelStyle.copy(
                            color = if (selected) Color.Black else BroDesign.CrispWhite,
                            fontSize = 10.sp
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { onGroundingToggled(!useWebGrounding) }, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (useWebGrounding) Icons.Default.Public else Icons.Default.PublicOff,
                    contentDescription = "Web Grounding",
                    tint = if (useWebGrounding) BroDesign.VoltLime else BroDesign.MutedSteel,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Chat Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isUser = msg.sender == "USER"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    TacticalCard(
                        modifier = Modifier.widthIn(max = 320.dp),
                        accentColor = if (isUser) BroDesign.CyberCyan else BroDesign.VoltLime
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isUser) "OPERATOR" else "BRO [${msg.provider ?: "AI"}]",
                                style = BroDesign.HudLabelStyle.copy(
                                    color = if (isUser) BroDesign.CyberCyan else BroDesign.VoltLime,
                                    fontSize = 10.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = msg.text,
                            style = BroDesign.BodyStyle
                        )
                    }
                }
            }
        }

        // Tactical Composer (Status left, mic, send, stop right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BroDesign.CarbonSurface)
                .border(1.5.dp, BroDesign.BorderGrid, BroDesign.ComposerShape)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(BroDesign.PillShape)
                    .background(BroDesign.ObsidianBg)
                    .border(1.dp, BroDesign.BorderGrid, BroDesign.PillShape)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                if (typedInput.isEmpty()) {
                    Text(text = "Enter tactical prompt...", style = BroDesign.BodyStyle.copy(color = BroDesign.MutedSteel, fontSize = 14.sp))
                }
                BasicTextField(
                    value = typedInput,
                    onValueChange = onInputChanged,
                    textStyle = BroDesign.BodyStyle.copy(fontSize = 14.sp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Mic Button
            IconButton(
                onClick = onMicClicked,
                modifier = Modifier
                    .size(40.dp)
                    .clip(BroDesign.PillShape)
                    .background(if (isListening) BroDesign.HyperOrange else BroDesign.ElevatedTactile)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Mic",
                    tint = if (isListening) Color.Black else BroDesign.VoltLime,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Send Button
            IconButton(
                onClick = onSend,
                modifier = Modifier
                    .size(40.dp)
                    .clip(BroDesign.PillShape)
                    .background(BroDesign.VoltLime)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Stop Button (Instant cancel for long jobs)
            IconButton(
                onClick = onStop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(BroDesign.PillShape)
                    .background(BroDesign.DangerRed)
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
