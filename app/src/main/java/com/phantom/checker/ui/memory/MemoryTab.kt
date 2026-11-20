package com.phantom.checker.ui.memory

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState

@Composable
internal fun MemoryTab(
    glassState: HazeState,
    onOpenMessageDetail: (Long) -> Unit,
    onOpenMemorySettings: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("記憶庫載入中…")
    }
}
