package com.kanadecn.note.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kanadecn.note.data.remote.AiClient
import com.kanadecn.note.data.remote.ConnectionResult
import com.kanadecn.note.data.remote.FailureReason
import com.kanadecn.note.data.remote.ModelListResult
import com.kanadecn.note.data.settings.LlmSettings
import com.kanadecn.note.data.settings.SecureSettingsStore
import com.kanadecn.note.data.settings.ThemeMode
import com.kanadecn.note.ui.theme.appColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsStore: SecureSettingsStore,
    aiClient: AiClient,
    selectedThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
) {
    // 系统返回手势 / 返回键：回到上一级（主页），而不是退出 App
    BackHandler(onBack = onBack)

    val initial = remember { settingsStore.load() }

    var baseUrl by rememberSaveable { mutableStateOf(initial.baseUrl) }
    var apiKey by rememberSaveable { mutableStateOf(initial.apiKey) }
    var selectedModel by rememberSaveable { mutableStateOf(initial.model) }

    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingModels by remember { mutableStateOf(false) }
    var modelsError by remember { mutableStateOf<String?>(null) }
    var modelsErrorDetail by remember { mutableStateOf<String?>(null) }
    var modelMenuExpanded by remember { mutableStateOf(false) }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<ConnectionResult?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val canFetchModels = baseUrl.isNotBlank() && apiKey.isNotBlank() && !isLoadingModels
    val canSubmit = baseUrl.isNotBlank() && apiKey.isNotBlank() && selectedModel.isNotBlank()

    fun resetModelState() {
        availableModels = emptyList()
        modelsError = null
        modelsErrorDetail = null
        modelMenuExpanded = false
        testResult = null
    }

    Scaffold(
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "AI 配置",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "填入 Base URL 和 API Key，密钥会加密保存在本机。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = baseUrl,
                onValueChange = {
                    baseUrl = it
                    resetModelState()
                },
                label = { Text("Base URL") },
                placeholder = { Text("https://api.deepseek.com/v1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

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

            OutlinedButton(
                onClick = {
                    scope.launch {
                        isLoadingModels = true
                        modelsError = null
                        modelsErrorDetail = null
                        availableModels = emptyList()

                        when (val result = aiClient.listModels(baseUrl, apiKey)) {
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
                        isLoadingModels = false
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
                Text(if (isLoadingModels) "获取中…" else "刷新可用模型")
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
                    placeholder = { Text("先点上方「刷新可用模型」") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuExpanded)
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
                                modelMenuExpanded = false
                                testResult = null
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isTesting = true
                            testResult = null
                            testResult = aiClient.testConnection(
                                LlmSettings(baseUrl, apiKey, selectedModel),
                            )
                            isTesting = false
                        }
                    },
                    enabled = canSubmit && !isTesting,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isTesting) "测试中…" else "测试连接")
                }

                Button(
                    onClick = {
                        settingsStore.save(LlmSettings(baseUrl, apiKey, selectedModel))
                        scope.launch { snackbarHostState.showSnackbar("已保存") }
                    },
                    enabled = canSubmit,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("保存")
                }
            }

            testResult?.let { result ->
                when (result) {
                    is ConnectionResult.Success -> Text(
                        text = "连接成功（${result.latencyMs} ms）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.appColors.success,
                    )

                    is ConnectionResult.Failure -> Column {
                        Text(
                            text = failureMessage(result.reason),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        result.detail?.let { detail ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            Text(
                text = "外观",
                style = MaterialTheme.typography.titleMedium,
            )
            ThemeModeRow(
                label = "跟随系统",
                selected = selectedThemeMode == ThemeMode.SYSTEM,
                onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
            )
            ThemeModeRow(
                label = "浅色",
                selected = selectedThemeMode == ThemeMode.LIGHT,
                onClick = { onThemeModeChange(ThemeMode.LIGHT) },
            )
            ThemeModeRow(
                label = "深色",
                selected = selectedThemeMode == ThemeMode.DARK,
                onClick = { onThemeModeChange(ThemeMode.DARK) },
            )
        }
    }
}

@Composable
private fun ThemeModeRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun failureMessage(reason: FailureReason): String = when (reason) {
    FailureReason.INVALID_URL -> "Base URL 格式不正确"
    FailureReason.INSECURE_URL -> "只允许 https:// 地址"
    FailureReason.UNAUTHORIZED -> "认证失败：API Key 无效或无权限"
    FailureReason.NOT_FOUND -> "地址不对（404），检查 Base URL"
    FailureReason.BAD_REQUEST -> "请求被拒绝（400），检查模型名"
    FailureReason.RATE_LIMIT -> "请求过于频繁（429），稍后再试"
    FailureReason.SERVER -> "服务端错误（5xx），稍后再试"
    FailureReason.TIMEOUT -> "连接超时，检查网络"
    FailureReason.NETWORK -> "网络错误，检查网络或地址"
    FailureReason.UNKNOWN -> "未知错误"
}
