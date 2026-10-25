package com.kanadecn.note.ui.mistake

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kanadecn.note.data.local.Mistake
import com.kanadecn.note.data.mistakes.MistakeRepository
import com.kanadecn.note.data.remote.AiClient
import com.kanadecn.note.data.remote.MistakeRecognitionResult
import com.kanadecn.note.data.remote.RecognizedMistake
import com.kanadecn.note.data.settings.SecureSettingsStore
import com.kanadecn.note.ui.components.DockDefaults
import com.kanadecn.note.ui.components.KanaDeckTitle
import com.kanadecn.note.ui.components.failureMessage
import com.kanadecn.note.ui.theme.appColors
import com.kanadecn.note.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MistakeScreen(
    mistakeRepository: MistakeRepository,
    aiClient: AiClient,
    settingsStore: SecureSettingsStore,
    onOpenDetail: (Mistake) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mistakes by remember { mutableStateOf<List<Mistake>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRecognizing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var errorDetail by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<RecognizedMistake?>(null) }
    var draftImagePath by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<Mistake?>(null) }

    LaunchedEffect(Unit) {
        mistakes = runCatching { mistakeRepository.getAll() }.getOrDefault(emptyList())
        isLoading = false
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        val settings = settingsStore.load()
        if (!settings.isConfigured) {
            errorMessage = "请先在设置里完成 AI 配置"
            errorDetail = "需要 Base URL、API Key 和模型"
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            isRecognizing = true
            errorMessage = null
            errorDetail = null
            try {
                val prepared = withContext(Dispatchers.IO) {
                    ImageUtils.prepare(context, uri)
                }
                if (prepared == null) {
                    errorMessage = "图片读取失败"
                    return@launch
                }

                when (val result = aiClient.recognizeMistake(settings, prepared.apiJpeg)) {
                    is MistakeRecognitionResult.Success -> {
                        draftImagePath = withContext(Dispatchers.IO) {
                            ImageUtils.saveMistakeImage(context, prepared.storedWebp)
                        }
                        draft = result.data
                    }

                    is MistakeRecognitionResult.Failure -> {
                        errorMessage = failureMessage(result.reason)
                        errorDetail = result.detail
                    }
                }
            } catch (e: Exception) {
                errorMessage = "识别失败：${e.message ?: "未知错误"}"
            } finally {
                isRecognizing = false
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { KanaDeckTitle(subtitle = "错题本") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "设置",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val currentDraft = draft
        val currentImagePath = draftImagePath

        if (currentDraft != null && currentImagePath != null) {
            MistakeEditor(
                initial = currentDraft,
                imagePath = currentImagePath,
                onCancel = {
                    draft = null
                    draftImagePath = null
                },
                onSave = { edited, path ->
                    scope.launch {
                        mistakeRepository.add(
                            Mistake(
                                imagePath = path,
                                question = edited.question,
                                myAnswer = edited.myAnswer,
                                correctAnswer = edited.correctAnswer,
                                isCorrect = edited.isCorrect,
                                errorType = edited.errorType,
                                knowledgePoint = edited.knowledgePoint,
                                explanation = edited.explanation,
                                createdAt = System.currentTimeMillis(),
                            ),
                        )
                        mistakes = mistakeRepository.getAll()
                        draft = null
                        draftImagePath = null
                    }
                },
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            MistakeList(
                mistakes = mistakes,
                isLoading = isLoading,
                isRecognizing = isRecognizing,
                errorMessage = errorMessage,
                errorDetail = errorDetail,
                onPickImage = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onRequestDelete = { pendingDelete = it },
                onOpenDetail = onOpenDetail,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    pendingDelete?.let { mistake ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除错题") },
            text = { Text("确定删除这道错题吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                runCatching { File(mistake.imagePath).delete() }
                            }
                            mistakeRepository.delete(mistake)
                            mistakes = mistakeRepository.getAll()
                            pendingDelete = null
                        }
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MistakeList(
    mistakes: List<Mistake>,
    isLoading: Boolean,
    isRecognizing: Boolean,
    errorMessage: String?,
    errorDetail: String?,
    onPickImage: () -> Unit,
    onRequestDelete: (Mistake) -> Unit,
    onOpenDetail: (Mistake) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = DockDefaults.ContentBottomPadding),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "识别错题",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "选择错题图片，AI 会识别题目、判错并总结。需要支持视觉的模型。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Button(
                    onClick = onPickImage,
                    enabled = !isRecognizing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isRecognizing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isRecognizing) "识别中…" else "选择错题图片")
                }

                errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                errorDetail?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        when {
            isLoading && mistakes.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            mistakes.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "还没有错题",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "左滑错题卡片可删除",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(mistakes, key = { it.id }) { mistake ->
                    @Suppress("DEPRECATION")
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                onRequestDelete(mistake)
                            }
                            false
                        },
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        },
                    ) {
                        MistakeCard(
                            mistake = mistake,
                            onClick = { onOpenDetail(mistake) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MistakeCard(
    mistake: Mistake,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = File(mistake.imagePath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.small),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = mistake.question,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (mistake.isCorrect) "判定：正确" else "错因：${mistake.errorType}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (mistake.isCorrect) {
                        MaterialTheme.appColors.success
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                if (mistake.knowledgePoint.isNotBlank()) {
                    Text(
                        text = "知识点：${mistake.knowledgePoint}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = formatTime(mistake.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MistakeEditor(
    initial: RecognizedMistake,
    imagePath: String,
    onCancel: () -> Unit,
    onSave: (RecognizedMistake, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var question by rememberSaveable(initial) { mutableStateOf(initial.question) }
    var myAnswer by rememberSaveable(initial) { mutableStateOf(initial.myAnswer) }
    var correctAnswer by rememberSaveable(initial) { mutableStateOf(initial.correctAnswer) }
    var isCorrect by rememberSaveable(initial) { mutableStateOf(initial.isCorrect) }
    var errorType by rememberSaveable(initial) { mutableStateOf(initial.errorType) }
    var knowledgePoint by rememberSaveable(initial) { mutableStateOf(initial.knowledgePoint) }
    var explanation by rememberSaveable(initial) { mutableStateOf(initial.explanation) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = DockDefaults.ContentBottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "确认识别结果",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "AI 可能识别有误，请检查后保存。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AsyncImage(
            model = File(imagePath),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(MaterialTheme.shapes.medium),
        )

        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            label = { Text("题目") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = myAnswer,
            onValueChange = { myAnswer = it },
            label = { Text("我的答案") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = correctAnswer,
            onValueChange = { correctAnswer = it },
            label = { Text("正确答案 / 正确解法") },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "这道题做对了",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.weight(1f))
            Switch(
                checked = isCorrect,
                onCheckedChange = { isCorrect = it },
            )
        }

        OutlinedTextField(
            value = errorType,
            onValueChange = { errorType = it },
            label = { Text("错因") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = knowledgePoint,
            onValueChange = { knowledgePoint = it },
            label = { Text("知识点") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = explanation,
            onValueChange = { explanation = it },
            label = { Text("讲解") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            ) {
                Text("取消")
            }
            Button(
                onClick = {
                    onSave(
                        RecognizedMistake(
                            question = question.ifBlank { "（未填写题目）" },
                            myAnswer = myAnswer,
                            correctAnswer = correctAnswer,
                            isCorrect = isCorrect,
                            errorType = errorType,
                            knowledgePoint = knowledgePoint,
                            explanation = explanation,
                        ),
                        imagePath,
                    )
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("保存")
            }
        }
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
