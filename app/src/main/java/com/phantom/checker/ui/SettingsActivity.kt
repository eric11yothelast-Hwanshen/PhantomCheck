package com.phantom.checker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.phantom.checker.data.FilterRule
import com.phantom.checker.service.AppDatabaseHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                FilterSettingsScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSettingsScreen(onBack: () -> Unit) {
    val appCtx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    var rules by remember { mutableStateOf(listOf<FilterRule>()) }
    var newKeyword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoading = true
        rules = withContext(Dispatchers.IO) {
            AppDatabaseHolder.db(appCtx).filterDao().all()
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("過濾關鍵字管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
        ) {
            Text(
                text = "符合以下啟用關鍵字的通知將直接標記為已過濾，不送交 AI 分析。",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newKeyword,
                    onValueChange = { newKeyword = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("輸入過濾關鍵字…") }
                )
                Spacer(Modifier.width(8.dp))
                val trimmed = newKeyword.trim()
                val canAdd = trimmed.isNotEmpty() && rules.none { it.keyword.equals(trimmed, ignoreCase = true) }
                Button(
                    onClick = {
                        scope.launch {
                            rules = withContext(Dispatchers.IO) {
                                val dao = AppDatabaseHolder.db(appCtx).filterDao()
                                dao.insert(FilterRule(keyword = trimmed, isActive = true))
                                dao.all()
                            }
                            newKeyword = ""
                        }
                    },
                    enabled = canAdd
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "新增"
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("新增")
                }
            }

            Spacer(Modifier.height(12.dp))

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                rules.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "目前尚無過濾關鍵字",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(rules, key = { "${it.id}_${it.keyword}" }) { rule ->
                            FilterRuleCard(
                                rule = rule,
                                onToggleActive = { checked ->
                                    scope.launch {
                                        val updated = rule.copy(isActive = checked)
                                        withContext(Dispatchers.IO) {
                                            AppDatabaseHolder.db(appCtx).filterDao().update(updated)
                                        }
                                        rules = rules.map {
                                            if (it.id == rule.id && it.keyword == rule.keyword) updated else it
                                        }
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            AppDatabaseHolder.db(appCtx).filterDao().delete(rule)
                                        }
                                        rules = rules.filterNot {
                                            it.id == rule.id && it.keyword == rule.keyword
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRuleCard(
    rule: FilterRule,
    onToggleActive: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = rule.keyword,
                style = MaterialTheme.typography.bodyLarge,
                color = if (rule.isActive) MaterialTheme.colorScheme.onSurface else Color.Gray,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = rule.isActive,
                onCheckedChange = onToggleActive
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "刪除關鍵字",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
