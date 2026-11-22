package com.novoagentehb.app.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novoagentehb.app.agent.memory.MemoryStore
import com.novoagentehb.app.data.local.MemoryCategory
import com.novoagentehb.app.data.local.MemoryItemEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryStore: MemoryStore
) : ViewModel() {

    val memories: StateFlow<List<MemoryItemEntity>> = memoryStore.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addMemory(key: String, value: String, category: MemoryCategory) {
        if (key.isBlank() || value.isBlank()) return
        viewModelScope.launch {
            memoryStore.upsertFact(key, value, category, provenance = "manual_ui")
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch { memoryStore.forgetById(id) }
    }

    fun clearAll() {
        viewModelScope.launch { memoryStore.clearAll() }
    }
}
