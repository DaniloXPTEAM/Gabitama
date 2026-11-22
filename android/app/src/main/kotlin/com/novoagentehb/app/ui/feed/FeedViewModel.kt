package com.novoagentehb.app.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novoagentehb.app.data.local.FeedEventDao
import com.novoagentehb.app.data.local.FeedEventEntity
import com.novoagentehb.app.data.local.TaskReceiptDao
import com.novoagentehb.app.data.local.TaskReceiptEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val feedDao: FeedEventDao,
    private val receiptDao: TaskReceiptDao
) : ViewModel() {

    val notifications: StateFlow<List<FeedEventEntity>> = feedDao.observeRecent(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val receipts: StateFlow<List<TaskReceiptEntity>> = receiptDao.observeRecent(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearNotifications() {
        viewModelScope.launch { feedDao.clearAll() }
    }
}
