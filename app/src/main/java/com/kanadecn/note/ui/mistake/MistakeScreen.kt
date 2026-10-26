package com.kanadecn.note.ui.mistake

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.kanadecn.note.data.local.Mistake
import com.kanadecn.note.data.mistakes.CategoryStore
import com.kanadecn.note.data.mistakes.MistakeRecognizer
import com.kanadecn.note.data.mistakes.MistakeRepository
import com.kanadecn.note.data.mistakes.RecognizeOutcome
import com.kanadecn.note.data.remote.RecognizedMistake
import com.kanadecn.note.ui.components.DockDefaults
import com.kanadecn.note.ui.components.KanaDeckTitle
import com.kanadecn.note.ui.components.failureMessage
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MistakeScreen(
    mistakeRepository: MistakeRepository,
    categoryStore: CategoryStore,
    mistakeRecognizer: MistakeRecognizer,
    onOpenCategory: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var categories by remember { mutableStateOf(categoryStore.allCategories()) }
    var categoryCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var isRecognizing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var errorDetail by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<RecognizedMistake?>(null) }
    var draftImagePath by remember { mutableStateOf<String?>(null) }
    var cameraUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        categories = categoryStore.allCategories()
        categoryCounts = runCatching { mistakeRepository.countByCategory() }
            .getOrDefault(emptyMap())
    }

    fun recognizeImage(uri: Uri, tempFile: File? = null) {
        scope.launch {
            isRecognizing = true
            errorMessage = null
            errorDetail = null
            try {
                when (val outcome = mistakeRecognizer.recognize(uri)) {
                    is RecognizeOutcome.Success -> {
                        draft = outcome.draft
                        draftImagePath = outcome.imagePath
                    }

                    is RecognizeOutcome.Failure -> {
                        errorMessage = failureMessage(outcome.reason)
                        errorDetail = outcome.detail
                    }

                    RecognizeOutcome.NotConfigured -> {
                        errorMessage = "请先在设置里完成 AI 配置"
                        errorDetail = "需要 Base URL、API Key 和模型"
                    }
                }
            } catch (e: Exception) {
                errorMessage = "识别失败：${e.message ?: "未知错误"}"
            } finally {
                isRecognizing = false
                tempFile?.delete()
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) recognizeImage(uri)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = cameraUriString?.let(Uri::parse)
        val tempFile = cameraPath?.let(::File)
        if (success && uri != null) {
            recognizeImage(uri, tempFile)
        } else {
            tempFile?.delete()
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
                categories = categories,
                onAddCategory = { name ->
                    val updated = categoryStore.add(name)
                    categories = updated
                    updated
                },
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
                                category = edited.category,
                            ),
                        )
                        categoryCounts = mistakeRepository.countByCategory()
                        draft = null
                        draftImagePath = null
                    }
                },
                modifier = Modifier.padding(innerPadding),
                bottomContentPadding = DockDefaults.ContentBottomPadding,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = DockDefaults.ContentBottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    AddMistakeCard(
                        isRecognizing = isRecognizing,
                        errorMessage = errorMessage,
                        errorDetail = errorDetail,
                        onPickImage = {
                            imagePicker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                        onTakePhoto = {
                            try {
                                val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                                val file = File(
                                    dir,
                                    "mistake_${System.currentTimeMillis()}.jpg",
                                )
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file,
                                )
                                cameraPath = file.absolutePath
                                cameraUriString = uri.toString()
                                cameraLauncher.launch(uri)
                            } catch (e: Exception) {
                                errorMessage = "无法启动相机"
                                errorDetail = e.message
                            }
                        },
                    )
                }

                item {
                    Text(
                        text = "错题集",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                items(categories) { category ->
                    val count = categoryCounts[category] ?: 0
                    if (category != CategoryStore.UNCATEGORIZED || count > 0) {
                        CategoryCard(
                            category = category,
                            count = count,
                            onClick = { onOpenCategory(category) },
                            onRename = {
                                renameTarget = category
                                renameText = category
                            },
                            onDelete = { deleteTarget = category },
                        )
                    }
                }
            }
        }
    }

    renameTarget?.let { oldName ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名分类") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("分类名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newName = renameText.trim()
                        if (newName.isNotBlank() && newName != oldName) {
                            scope.launch {
                                mistakeRepository.updateCategory(oldName, newName)
                                categories = categoryStore.rename(oldName, newName)
                                categoryCounts = mistakeRepository.countByCategory()
                                renameTarget = null
                            }
                        } else {
                            renameTarget = null
                        }
                    },
                    enabled = renameText.isNotBlank(),
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("取消")
                }
            },
        )
    }

    deleteTarget?.let { category ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除分类") },
            text = {
                Text("「$category」下的错题会移动到「未分类」，确定删除吗？")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            mistakeRepository.updateCategory(
                                oldCategory = category,
                                newCategory = CategoryStore.UNCATEGORIZED,
                            )
                            categories = categoryStore.delete(category)
                            categoryCounts = mistakeRepository.countByCategory()
                            deleteTarget = null
                        }
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun AddMistakeCard(
    isRecognizing: Boolean,
    errorMessage: String?,
    errorDetail: String?,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "识别错题",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "选择错题图片，AI 会识别题目、判错、总结并推荐分类。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isRecognizing) {
                // 识别期间隐藏两个入口，避免重复选图；关闭编辑器后自动恢复
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "识别中，请勿离开页面…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onTakePhoto,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("拍照上传")
                    }
                    Button(
                        onClick = onPickImage,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("选择图片")
                    }
                }
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
}

@Composable
private fun CategoryCard(
    category: String,
    count: Int,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val canEdit = category != CategoryStore.UNCATEGORIZED

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = category,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "$count 道错题",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (canEdit) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "更多",
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
