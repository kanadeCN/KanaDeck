package com.kanadecn.note.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kanadecn.note.data.notes.NoteFolderImporter
import com.kanadecn.note.data.notes.NoteRepository
import com.kanadecn.note.data.remote.AiClient
import com.kanadecn.note.data.remote.ModelListResult
import com.kanadecn.note.data.settings.AiProvider
import com.kanadecn.note.data.settings.LlmSettings
import com.kanadecn.note.data.settings.SecureSettingsStore
import com.kanadecn.note.data.settings.ThemeMode
import com.kanadecn.note.ui.components.failureMessage
import com.kanadecn.note.ui.theme.appColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsStore: SecureSettingsStore,
    aiClient: AiClient,
    noteRepository: NoteRepository,
    noteFolderImporter: NoteFolderImporter,
    selectedThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
) {
    // 系统返回手势 / 返回键：回到上一级（主页），而不是退出 App
    BackHandler(onBack = onBack)

    val initial = remember { settingsStore.load() }

    // apiKey 不放 rememberSaveable，避免进 SavedState；初始值从加密存储读
    var selectedProvider by remember { mutableStateOf(AiProvider.fromBaseUrl(initial.baseUrl)) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var selectedModel by remember { mutableStateOf(initial.model) }
    var manualModel by remember { mutableStateOf("") }
    var maxTokens by rememberSaveable { mutableStateOf(initial.maxTokens) }
    var maxTokensText by rememberSaveable { mutableStateOf(initial.maxTokens.toString()) }

    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingModels by remember { mutableStateOf(false) }
    var modelsError by remember { mutableStateOf<String?>(null) }
    var modelsErrorDetail by remember { mutableStateOf<String?>(null) }

    var providerMenuExpanded by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var themeMenuExpanded by remember { mutableStateOf(false) }

    var isSaving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saveErrorDetail by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    var noteCount by remember { mutableStateOf<Int?>(null) }
    var folderName by remember { mutableStateOf(noteFolderImporter.savedFolderName()) }
    var isImportingNotes by remember { mutableStateOf(false) }
    var importStatus by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        noteCount = runCatching { noteRepository.count() }.getOrNull()
    }

    val noteFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            isImportingNotes = true
            importStatus = null
            try {
                val outcome = noteFolderImporter.import(uri)
                folderName = outcome.folderName
                noteCount = noteRepository.count()
                importStatus = buildString {
                    append("已导入 ${outcome.imported} 篇")
                    if (outcome.failed > 0) append("，失败 ${outcome.failed} 篇")
                }
            } catch (e: Exception) {
                importStatus = "导入失败：${e.message ?: "未知错误"}"
            } finally {
                isImportingNotes = false
            }
        }
    }

    fun resetModelState() {
        availableModels = emptyList()
        modelsError = null
        modelsErrorDetail = null
        modelMenuExpanded = false
        saveMessage = null
        saveError = null
        saveErrorDetail = null
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ===== 笔记库 =====
            Text(
                text = "笔记库",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = folderName?.let { "当前文件夹：$it" } ?: "还没有选择文件夹",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "已导入 ${noteCount ?: 0} 篇笔记",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { noteFolderPicker.launch(null) },
                enabled = !isImportingNotes,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isImportingNotes) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    when {
                        isImportingNotes -> "导入中…"
                        folderName == null -> "选择笔记文件夹"
                        else -> "切换文件夹"
                    },
                )
            }
            importStatus?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            Text(
                text = "AI 配置",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "选择服务商并填入 Key，密钥会加密保存在本机。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ===== 服务商下拉（Base URL 固定） =====
            ExposedDropdownMenuBox(
                expanded = providerMenuExpanded,
                onExpandedChange = { providerMenuExpanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = selectedProvider.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("服务商") },
                    supportingText = { Text(selectedProvider.baseUrl) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(providerMenuExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = providerMenuExpanded,
                    onDismissRequest = { providerMenuExpanded = false },
                ) {
                    AiProvider.entries.forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(provider.label) },
                            onClick = {
                                selectedProvider = provider
                                providerMenuExpanded = false
                                manualModel = ""
                                resetModelState()
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    resetModelState()
                },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            // ===== 获取模型列表 =====
            val canFetchModels = apiKey.isNotBlank() && !isLoadingModels
            Button(
                onClick = {
                    scope.launch {
                        isLoadingModels = true
                        modelsError = null
                        modelsErrorDetail = null
                        availableModels = emptyList()
                        saveMessage = null
                        saveError = null
                        saveErrorDetail = null

                        try {
                            when (val result = aiClient.listModels(selectedProvider.baseUrl, apiKey)) {
                                is ModelListResult.Success -> {
                                    availableModels = result.models
                                    if (selectedModel !in result.models) {
                                        selectedModel = result.models.firstOrNull().orEmpty()
                                    }
                                }

                                is ModelListResult.Failure -> {
                                    modelsError = failureMessage(result.reason)
                                    modelsErrorDetail = result.detail
                                }
                            }
                        } finally {
                            // 无论成功、失败还是超时，按钮都恢复
                            isLoadingModels = false
                        }
                    }
                },
                enabled = canFetchModels,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isLoadingModels) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isLoadingModels) "获取中…" else "获取模型列表")
            }

            modelsError?.let { message ->
                Column {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    modelsErrorDetail?.let { detail ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ===== 模型下拉 =====
            ExposedDropdownMenuBox(
                expanded = modelMenuExpanded,
                onExpandedChange = {
                    if (availableModels.isNotEmpty()) modelMenuExpanded = it
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = selectedModel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("模型") },
                    placeholder = { Text("先点上方「获取模型列表」") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(modelMenuExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = modelMenuExpanded,
                    onDismissRequest = { modelMenuExpanded = false },
                ) {
                    availableModels.forEach { modelId ->
                        DropdownMenuItem(
                            text = { Text(modelId) },
                            onClick = {
                                selectedModel = modelId
                                manualModel = ""
                                modelMenuExpanded = false
                                saveMessage = null
                                saveError = null
                                saveErrorDetail = null
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = manualModel,
                onValueChange = {
                    manualModel = it
                    saveMessage = null
                    saveError = null
                    saveErrorDetail = null
                },
                label = { Text("手动输入模型（可选）") },
                placeholder = { Text("列表里没有模型再填这里！") },
                supportingText = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "警示",
                            tint = MaterialTheme.appColors.warning,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("备选项，填了就以这里为准。模型列表无选项大概率上方配置错误。")
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // ===== 最大输出 Token =====
            Text(
                text = "最大输出 Token",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "回答被截断时可调高（256–32768），修改后点「测试并保存」生效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Slider(
                    value = maxTokens.toFloat(),
                    onValueChange = { value ->
                        val rounded = (value / TOKEN_STEP).roundToInt() * TOKEN_STEP
                        maxTokens = rounded.coerceIn(
                            LlmSettings.MIN_MAX_TOKENS,
                            LlmSettings.MAX_MAX_TOKENS,
                        )
                        maxTokensText = maxTokens.toString()
                    },
                    valueRange = LlmSettings.MIN_MAX_TOKENS.toFloat()..LlmSettings.MAX_MAX_TOKENS.toFloat(),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = maxTokensText,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }.take(5)
                        maxTokensText = digits
                        digits.toIntOrNull()?.let { value ->
                            if (value in LlmSettings.MIN_MAX_TOKENS..LlmSettings.MAX_MAX_TOKENS) {
                                maxTokens = value
                            }
                        }
                    },
                    label = { Text("Token") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(104.dp),
                )
            }

            // ===== 测试并保存（合并） =====
            Button(
                onClick = {
                    val tokensToSave = maxTokensText.toIntOrNull()
                        ?.coerceIn(
                            LlmSettings.MIN_MAX_TOKENS,
                            LlmSettings.MAX_MAX_TOKENS,
                        )
                        ?: maxTokens
                    maxTokens = tokensToSave
                    maxTokensText = tokensToSave.toString()

                    scope.launch {
                        isSaving = true
                        saveMessage = null
                        saveError = null
                        saveErrorDetail = null

                        try {
                            when (val result = aiClient.listModels(selectedProvider.baseUrl, apiKey)) {
                                is ModelListResult.Success -> {
                                    availableModels = result.models
                                    val modelToUse = manualModel.trim().ifBlank {
                                        selectedModel.takeIf { result.models.contains(it) }
                                            ?: result.models.firstOrNull().orEmpty()
                                    }

                                    if (modelToUse.isBlank()) {
                                        saveError = "没有可用模型"
                                    } else {
                                        selectedModel = modelToUse
                                        settingsStore.save(
                                            LlmSettings(
                                                baseUrl = selectedProvider.baseUrl,
                                                apiKey = apiKey,
                                                model = modelToUse,
                                                maxTokens = tokensToSave,
                                            ),
                                        )
                                        saveMessage = "已保存：${selectedProvider.label} · $modelToUse"
                                    }
                                }

                                is ModelListResult.Failure -> {
                                    saveError = failureMessage(result.reason)
                                    saveErrorDetail = result.detail
                                }
                            }
                        } finally {
                            // 无论成功、失败还是超时，按钮都恢复
                            isSaving = false
                        }
                    }
                },
                enabled = apiKey.isNotBlank() && !isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isSaving) "测试中…" else "测试并保存")
            }

            saveMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.appColors.success,
                )
            }

            saveError?.let { message ->
                Column {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    saveErrorDetail?.let { detail ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            HorizontalDivider()

            // ===== 外观下拉 =====
            Text(
                text = "偏好",
                style = MaterialTheme.typography.titleMedium,
            )
            ExposedDropdownMenuBox(
                expanded = themeMenuExpanded,
                onExpandedChange = { themeMenuExpanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = selectedThemeMode.label(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("外观") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(themeMenuExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = themeMenuExpanded,
                    onDismissRequest = { themeMenuExpanded = false },
                ) {
                    ThemeMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label()) },
                            onClick = {
                                onThemeModeChange(mode)
                                themeMenuExpanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

private const val TOKEN_STEP = 256

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

