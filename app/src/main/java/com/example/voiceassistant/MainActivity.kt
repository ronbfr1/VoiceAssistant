package com.example.voiceassistant

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.blendMode
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.drawCircle
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@ExperimentalMaterial3Api
class MainActivity : ComponentActivity() {
    private lateinit var voiceEngine: VoiceEngine
    private var isListening = false
    
    // Permission launchers
    private val requestOverlayPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Handle overlay permission result
    }
    
    private val requestMediaProjection = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            voiceEngine.onActivityResult(VoiceEngine.REQUEST_MEDIA_PROJECTION, result.resultCode, result.data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        voiceEngine = VoiceEngine(this, object : VoiceEngine.VoiceCallback {
            override fun onVoiceResult(result: String) {
                runOnUiThread {
                    addMessage(result, false)
                    speakingText = result
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    addMessage("Ошибка: $error", true)
                    isListening = false
                }
            }

            override fun onListeningStart() {
                runOnUiThread {
                    isListening = true
                    listeningAnimation = true
                }
            }

            override fun onListeningStop() {
                runOnUiThread {
                    isListening = false
                    listeningAnimation = false
                }
            }

            override fun onScreenCaptureRequested() {
                runOnUiThread {
                    requestMediaProjection.launch(Intent(MediaProjectionManager::class.java.getName()))
                }
            }
        })
        
        voiceEngine.setActivity(this)
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppScaffold(
                        voiceEngine = voiceEngine,
                        onNavigateToSettings = { showSettings = true },
                        showSettings = showSettings,
                        onSettingsClose = { showSettings = false }
                    )
                }
            }
        }
    }

    private var showSettings = false
    private var messages = mutableStateOf<MutableList<Message>>(mutableListOf())
    private var speakingText = mutableStateOf<String>("")
    private var listeningAnimation = mutableStateOf<Boolean>(false)

    private fun toggleListening() {
        if (isListening) {
            voiceEngine.stopListening()
        } else {
            voiceEngine.startListening()
        }
    }

    private fun addMessage(text: String, isError: Boolean) {
        val newMessage = Message(text, isError, System.currentTimeMillis())
        messages.value = messages.value + newMessage
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceEngine.destroy()
    }
}

data class Message(
    val text: String,
    val isError: Boolean,
    val timestamp: Long
)

@Composable
@ExperimentalMaterial3Api
fun AppScaffold(
    voiceEngine: VoiceEngine,
    onNavigateToSettings: () -> Unit,
    showSettings: Boolean,
    onSettingsClose: () -> Unit
) {
    if (showSettings) {
        SettingsScreen(
            voiceEngine = voiceEngine,
            onClose = onSettingsClose
        )
    } else {
        MainScreen(
            voiceEngine = voiceEngine,
            onSettingsClick = onNavigateToSettings
        )
    }
}

@Composable
@ExperimentalMaterial3Api
fun MainScreen(
    voiceEngine: VoiceEngine,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var listeningAnimation by remember { mutableStateOf(false) }
    var messages by remember { mutableStateOf<MutableList<Message>>(mutableListOf()) }
    var speakingText by remember { mutableStateOf("") }
    
    // Sync with voiceEngine callbacks would be handled via observer pattern in real app
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice Assistant", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "Настройки")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Text(
                    text = "Voice Assistant",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                )
                
                // Messages
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    messages.forEach { message ->
                        MessageBubble(message)
                    }
                    
                    if (speakingText.isNotEmpty()) {
                        SpeakingIndicator(text = speakingText)
                    }
                }
                
                // Mic button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MicrophoneButton(
                        isListening = isListening,
                        isAnimating = listeningAnimation,
                        onClick = {
                            if (isListening) {
                                voiceEngine.stopListening()
                            } else {
                                voiceEngine.startListening()
                            }
                        }
                    )
                }
            }
        }
    )
}

@Composable
@ExperimentalMaterial3Api
fun SettingsScreen(
    voiceEngine: VoiceEngine,
    onClose: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        content = { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(settingsSections(voiceEngine)) { section ->
                    SettingsSection(
                        title = section.title,
                        items = section.items
                    )
                }
            }
        }
    )
}

data class SettingsSection(
    val title: String,
    val items: List<SettingsItem>
)

sealed interface SettingsItem {
    data class ToggleItem(
        val title: String,
        val description: String,
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
        val enabled: Boolean = true
    ) : SettingsItem
    
    data class ActionItem(
        val title: String,
        val description: String,
        val icon: () -> Unit,
        val onClick: () -> Unit,
        val enabled: Boolean = true
    ) : SettingsItem
    
    data class InfoItem(
        val title: String,
        val description: String
    ) : SettingsItem
}

@Composable
fun settingsSections(voiceEngine: VoiceEngine): List<SettingsSection> {
    return listOf(
        SettingsSection(
            title = "Общие",
            items = listOf(
                SettingsItem.InfoItem(
                    title = "Voice Assistant",
                    description = "Голосовой ассистент в стиле Gemini с офлайн-поддержкой"
                )
            )
        ),
        SettingsSection(
            title = "Интерфейс",
            items = listOf(
                SettingsItem.ToggleItem(
                    title = "Пузырек-ассистент (Overlay)",
                    description = "Плавающий пузырек поверх всех приложений для быстрого вызова",
                    checked = false, // Would read from SharedPreferences
                    onCheckedChange = { enabled ->
                        // Toggle overlay service
                    }
                ),
                SettingsItem.ToggleItem(
                    title = "Обводка экрана при прослушивании",
                    description = "Светящаяся рамка по краям экрана когда ассистент слушает",
                    checked = true,
                    onCheckedChange = { enabled ->
                        // Toggle edge glow
                    }
                ),
                SettingsItem.ToggleItem(
                    title = "Глобальный хоткей (Win+Space)",
                    description = "Вызов ассистента из любого приложения сочетанием клавиш",
                    checked = false,
                    onCheckedChange = { enabled ->
                        // Toggle accessibility service
                    }
                )
            )
        ),
        SettingsSection(
            title = "Распознавание речи (STT)",
            items = listOf(
                SettingsItem.ToggleItem(
                    title = "Whisper.cpp (Офлайн)",
                    description = "Приватное распознавание на устройстве, без интернета",
                    checked = voiceEngine.isWhisperAvailable(),
                    onCheckedChange = { enabled ->
                        voiceEngine.setUseWhisper(enabled)
                    },
                    enabled = voiceEngine.isWhisperAvailable()
                ),
                SettingsItem.ActionItem(
                    title = "Скачать модель Whisper (base, ~75MB)",
                    description = "Загрузить модель для офлайн-распознавания",
                    icon = { Icon(Icons.Filled.Download, contentDescription = "Скачать") },
                    onClick = {
                        // voiceEngine.downloadWhisperModel("base", ...)
                    },
                    enabled = !voiceEngine.isWhisperAvailable()
                ),
                SettingsItem.ToggleItem(
                    title = "Системный SpeechRecognizer (Онлайн)",
                    description = "Google/Android системное распознавание (требует интернет)",
                    checked = !voiceEngine.isWhisperAvailable(),
                    onCheckedChange = { enabled ->
                        voiceEngine.setUseWhisper(!enabled)
                    }
                )
            )
        ),
        SettingsSection(
            title = "Синтез речи (TTS)",
            items = listOf(
                SettingsItem.ToggleItem(
                    title = "Piper TTS (Офлайн)",
                    description = "Быстрый нейронный синтез на устройстве",
                    checked = voiceEngine.isPiperAvailable(),
                    onCheckedChange = { enabled ->
                        voiceEngine.setUsePiper(enabled)
                    },
                    enabled = voiceEngine.isPiperAvailable()
                ),
                SettingsItem.ActionItem(
                    title = "Скачать голос Piper (ru_RU, ~50MB)",
                    description = "Загрузить модель для офлайн-синтеза",
                    icon = { Icon(Icons.Filled.Download, contentDescription = "Скачать") },
                    onClick = {
                        // voiceEngine.downloadPiperModel("ru_RU", "medium", ...)
                    },
                    enabled = !voiceEngine.isPiperAvailable()
                ),
                SettingsItem.ToggleItem(
                    title = "Системный TTS (Онлайн)",
                    description = "Google/Android системный синтез",
                    checked = !voiceEngine.isPiperAvailable(),
                    onCheckedChange = { enabled ->
                        voiceEngine.setUsePiper(!enabled)
                    }
                )
            )
        ),
        SettingsSection(
            title = "Экран",
            items = listOf(
                SettingsItem.ActionItem(
                    title = "Выделить область экрана",
                    description = "Захват части экрана для анализа ассистентом",
                    icon = { Icon(Icons.Filled.Keyboard, contentDescription = "Экран") },
                    onClick = {
                        voiceEngine.requestScreenCapture()
                    }
                )
            )
        ),
        SettingsSection(
            title = "Разрешения",
            items = listOf(
                SettingsItem.ActionItem(
                    title = "Разрешение на отображение поверх приложений",
                    description = "Необходимо для пузырька-ассистента",
                    icon = { Icon(Icons.Filed.Info, contentDescription = "Инфо") },
                    onClick = {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = Uri.parse("package:${LocalContext.current.packageName}")
                        }
                        LocalContext.current.startActivity(intent)
                    }
                ),
                SettingsItem.ActionItem(
                    title = "Доступ к микрофону",
                    description = "Для голосового управления",
                    icon = { Icon(Icons.Filled.Mic, contentDescription = "Микрофон") },
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${LocalContext.current.packageName}")
                        }
                        LocalContext.current.startActivity(intent)
                    }
                ),
                SettingsItem.ActionItem(
                    title = "Специальные возможности (для хоткея)",
                    description = "Для глобального хоткея Win+Space",
                    icon = { Icon(Icons.Filled.Keyboard, contentDescription = "Доступность") },
                    onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        LocalContext.current.startActivity(intent)
                    }
                )
            )
        ),
        SettingsSection(
            title = "О приложении",
            items = listOf(
                SettingsItem.InfoItem(
                    title = "Версия 1.0.0",
                    description = "Нативный C++ движок + Jetpack Compose UI"
                ),
                SettingsItem.InfoItem(
                    title = "Приватность",
                    description = "Whisper.cpp и Piper работают полностью офлайн. Данные не покидают устройство."
                )
            )
        )
    )
}

@Composable
@ExperimentalMaterial3Api
fun SettingsSection(title: String, items: List<SettingsItem>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp)
        )
        
        items.forEach { item ->
            when (item) {
                is SettingsItem.ToggleItem -> {
                    SettingsToggleRow(
                        title = item.title,
                        description = item.description,
                        checked = item.checked,
                        onCheckedChange = item.onCheckedChange,
                        enabled = item.enabled
                    )
                }
                is SettingsItem.ActionItem -> {
                    SettingsActionRow(
                        title = item.title,
                        description = item.description,
                        icon = item.icon,
                        onClick = item.onClick,
                        enabled = item.enabled
                    )
                }
                is SettingsItem.InfoItem -> {
                    SettingsInfoRow(
                        title = item.title,
                        description = item.description
                    )
                }
            }
            
            Divider(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

@Composable
@ExperimentalMaterial3Api
fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(vertical = 12.dp)
            .alpha(if (enabled) 1f else 0.5f),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            if (description.isNotEmpty()) {
                Text(text = description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled
        )
    }
}

@Composable
@ExperimentalMaterial3Api
fun SettingsActionRow(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(vertical = 12.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            if (description.isNotEmpty()) {
                Text(text = description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        
        Icon(Icons.Filled.Check, contentDescription = "", tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
@ExperimentalMaterial3Api
fun SettingsInfoRow(
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Info, contentDescription = "", tint = MaterialTheme.colorScheme.primary)
        
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            if (description.isNotEmpty()) {
                Text(text = description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
@ExperimentalMaterial3Api
fun MessageBubble(message: Message) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = if (message.isError) Alignment.Center else Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (message.isError) 
                MaterialTheme.colorScheme.errorContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant,
            elevation = 2.dp
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(16.dp),
                color = if (message.isError) 
                    MaterialTheme.colorScheme.onErrorContainer 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
@ExperimentalMaterial3Api
fun SpeakingIndicator(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpeakingDots()
            
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = "Говорит...",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun SpeakingDots() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { index ->
            AnimatedDot(delay = index * 150L)
        }
    }
}

@Composable
fun AnimatedDot(delay: Long) {
    var scale by remember { mutableStateOf(0.5f) }
    val scope = rememberCoroutineScope()
    
    androidx.lifecycle.LifecycleEventEffect(Lifecycle.Event.ON_START) {
        scope.launch {
            while (true) {
                delay(delay)
                scale = 1f
                delay(200)
                scale = 0.5f
                delay(400)
            }
        }
    }
    
    Box(
        modifier = Modifier
            .size(8.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(Color.Blue, CircleShape)
    )
}

@Composable
@ExperimentalMaterial3Api
fun MicrophoneButton(
    isListening: Boolean,
    isAnimating: Boolean,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center
    ) {
        // Outer ripple ring when listening
        if (isAnimating) {
            ListeningRing()
        }
        
        // Main mic button
        Surface(
            shape = CircleShape,
            color = if (isListening) 
                MaterialTheme.colorScheme.primary 
            else 
                MaterialTheme.colorScheme.secondaryContainer,
            elevation = 8.dp,
            modifier = Modifier
                .size(80.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = onClick)
                }
                .graphicsLayer {
                    scaleX = if (isListening) 1.1f else 1f
                    scaleY = if (isListening) 1.1f else 1f
                }
                .animateContentSize()
        ) {
            Icon(
                imageVector = if (isListening) 
                    Icons.Filled.Mic 
                else 
                    Icons.Filled.MicNone,
                contentDescription = if (isListening) "Остановить прослушивание" else "Начать прослушивание",
                modifier = Modifier.size(32.dp),
                tint = if (isListening) 
                    MaterialTheme.colorScheme.onPrimary 
                else 
                    MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun ListeningRing() {
    var radius by remember { mutableStateOf(40f) }
    var alpha by remember { mutableStateOf(0.6f) }
    val scope = rememberCoroutineScope()
    
    androidx.lifecycle.LifecycleEventEffect(Lifecycle.Event.ON_START) {
        scope.launch {
            while (true) {
                radius = 40f
                alpha = 0.6f
                delay(1000)
                radius = 80f
                alpha = 0f
            }
        }
    }
    
    Box(
        modifier = Modifier
            .size((radius * 2).dp)
            .graphicsLayer {
                this.scaleX = radius / 40f
                this.scaleY = radius / 40f
                this.alpha = alpha
            }
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                shape = CircleShape
            )
    )
}