package com.kanadecn.note.ui.mistake

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kanadecn.note.data.local.Mistake
import com.kanadecn.note.data.mistakes.CategoryStore
import com.kanadecn.note.data.remote.RecognizedMistake
import java.io.File

fun Mistake.toRecognized(): RecognizedMistake = RecognizedMistake(
    question = question,
    myAnswer = myAnswer,
    correctAnswer = correctAnswer,
    isCorrect = isCorrect,
    errorType = errorType,
    knowledgePoint = knowledgePoint,
    explanation = explanation,
    category = category,
)

/**
 * 新增/编辑错题共用编辑器。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MistakeEditor(
    initial: RecognizedMistake,
    imagePath: String,
    categories: List<String>,
    onAddCategory: (String) -> List<String>,
    onCancel: () -> Unit,
    onSave: (RecognizedMistake, String) -> Unit,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 0.dp,
) {
    var question by rememberSaveable(initial) { mutableStateOf(initial.question) }
    var myAnswer by rememberSaveable(initial) { mutableStateOf(initial.myAnswer) }
    var correctAnswer by rememberSaveable(initial) { mutableStateOf(initial.correctAnswer) }
    var isCorrect by rememberSaveable(initial) { mutableStateOf(initial.isCorrect) }
    var errorType by rememberSaveable(initial) { mutableStateOf(initial.errorType) }
    var knowledgePoint by rememberSaveable(initial) { mutableStateOf(initial.knowledgePoint) }
    var explanation by rememberSaveable(initial) { mutableStateOf(initial.explanation) }

    var categoryOptions by remember(categories) { mutableStateOf(categories) }
    var selectedCategory by rememberSaveable(initial) {
        mutableStateOf(initial.category.ifBlank { categories.firstOrNull().orEmpty() })
    }
    var newCategory by remember { mutableStateOf("") }
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = bottomContentPadding),
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

        ExposedDropdownMenuBox(
            expanded = categoryMenuExpanded,
            onExpandedChange = { categoryMenuExpanded = it },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = selectedCategory,
                onValueChange = {},
                readOnly = true,
                label = { Text("错题集") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(categoryMenuExpanded)
                },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = categoryMenuExpanded,
                onDismissRequest = { categoryMenuExpanded = false },
            ) {
                categoryOptions.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category) },
                        onClick = {
                            selectedCategory = category
                            categoryMenuExpanded = false
                        },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newCategory,
                onValueChange = { newCategory = it },
                label = { Text("新增分类") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    val name = newCategory.trim()
                    if (name.isNotBlank()) {
                        categoryOptions = onAddCategory(name)
                        selectedCategory = name
                        newCategory = ""
                    }
                },
                enabled = newCategory.isNotBlank(),
            ) {
                Text("添加")
            }
        }

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
                            category = selectedCategory.ifBlank {
                                CategoryStore.UNCATEGORIZED
                            },
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
