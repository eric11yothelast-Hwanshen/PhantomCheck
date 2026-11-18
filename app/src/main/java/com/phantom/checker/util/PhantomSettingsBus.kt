package com.phantom.checker.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * #225 設定變更事件匯流排：
 * 當 AI 對話或設定搜尋透過 SettingsCatalog 修改偏好時，通知活躍的 UI 畫面立即重讀快取偏好，
 * 不需等待 Lifecycle.Event.ON_RESUME。
 */
object PhantomSettingsBus {
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    fun notifyChanged() {
        _version.update { it + 1L }
    }
}
