package com.kanadecn.note.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kanadecn.note.data.notes.NoteFolderImporter
import com.kanadecn.note.data.notes.NoteRepository
import com.kanadecn.note.ui.components.DockDefaults
import com.kanadecn.note.ui.components.KanaDeckTitle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    noteRepository: NoteRepository,
    noteFolderImporter: NoteFolderImporter,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var noteCount by remember { mutableStateOf<Int?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var importStatus by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var syncFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 每次进入笔记页：先读数量，再自动重新扫描已保存的文件夹
    LaunchedEffect(Unit) {
        noteCount = runCatching { noteRepository.count() }.getOrNull()

        val folderUri = noteFolderImporter.savedFolderUri() ?: return@LaunchedEffect
        isRefreshing = true
        syncFailed = false
        try {
            noteFolderImporter.import(folderUri)
            noteCount = noteRepository.count()
        } catch (_: Exception) {
            syncFailed = true
        } finally {
            isRefreshing = false
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            isImporting = true
            importStatus = null
            try {
                val outcome = noteFolderImporter.import(uri)
                noteCount = noteRepository.count()
                importStatus = buildString {
                    append("已导入 ${outcome.imported} 篇")
                    if (outcome.failed > 0) append("，失败 ${outcome.failed} 篇")
                }
            } catch (e: Exception) {
                importStatus = "导入失败：${e.message ?: "未知错误"}"
            } finally {
                isImporting = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { KanaDeckTitle(subtitle = "笔记") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (syncFailed) {
                Text(
                    text = "笔记同步失败，请到设置重新选择文件夹",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 24.dp,
                        top = 24.dp,
                        end = 24.dp,
                        bottom = DockDefaults.ContentBottomPadding,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    noteCount == null -> CircularProgressIndicator()

                    noteCount == 0 && isRefreshing -> CircularProgressIndicator()

                    noteCount == 0 -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "还没有导入笔记",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "选择你的 Obsidian 笔记文件夹开始",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(
                                onClick = { folderPicker.launch(null) },
                                enabled = !isImporting,
                            ) {
                                if (isImporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(if (isImporting) "导入中…" else "选择笔记文件夹")
                            }
                            importStatus?.let { status ->
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = status,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    else -> {
                        // 导入完成后，主页只保留搜索入口
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenSearch),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "搜索笔记",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
