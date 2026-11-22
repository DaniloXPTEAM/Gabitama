package com.novoagentehb.app.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novoagentehb.app.bridges.LibraryEntry
import com.novoagentehb.app.bridges.SafLibraryManager
import com.novoagentehb.app.data.prefs.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val safManager: SafLibraryManager,
    private val prefs: AppPreferences
) : ViewModel() {

    val safTreeUri: StateFlow<String> = prefs.safTreeUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _entries = MutableStateFlow<List<LibraryEntry>>(emptyList())
    val entries: StateFlow<List<LibraryEntry>> = _entries.asStateFlow()

    private val _previewText = MutableStateFlow<String?>(null)
    val previewText: StateFlow<String?> = _previewText.asStateFlow()

    private val _statusNotice = MutableStateFlow<String?>(null)
    val statusNotice: StateFlow<String?> = _statusNotice.asStateFlow()

    private val safStack = mutableListOf<Pair<String, String>>()

    init {
        refresh()
    }

    fun onTreeAuthorized(uri: Uri) {
        viewModelScope.launch {
            safStack.clear()
            prefs.setSafTreeUri(uri.toString())
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val workspace = safManager.listWorkspaceFiles()
            val rootUri = prefs.safTreeUri.first()
            val currentSafUri = safStack.lastOrNull()?.second ?: rootUri
            val safItems = if (safStack.isEmpty()) {
                safManager.listSafTree(currentSafUri)
            } else {
                safManager.listSafSubfolder(currentSafUri)
            }
            _entries.value = workspace + safItems
        }
    }

    fun enterSafFolder(entry: LibraryEntry) {
        if (!entry.isDirectory) return
        safStack.add(entry.name to entry.uriOrPath)
        refresh()
    }

    fun navigateUpSaf(): Boolean {
        if (safStack.isEmpty()) return false
        safStack.removeLast()
        refresh()
        return true
    }

    fun safBreadcrumb(): String =
        if (safStack.isEmpty()) "Raiz" else "Raiz / " + safStack.joinToString(" / ") { it.first }

    fun createNote(filename: String, content: String) {
        if (filename.isBlank()) return
        viewModelScope.launch {
            val out = safManager.writeWorkspaceFile(filename, content)
            _statusNotice.value = if (out.success) {
                "Salvo no Workspace (${out.sizeBytes} bytes)."
            } else {
                out.error ?: "Erro ao salvar arquivo."
            }
            refresh()
        }
    }

    fun createSafFolder(folderName: String) {
        viewModelScope.launch {
            val parent = safStack.lastOrNull()?.second ?: prefs.safTreeUri.first()
            val ok = safManager.createSafDirectory(parent, folderName)
            _statusNotice.value = if (ok) "Pasta \"$folderName\" criada." else "Selecione uma pasta SAF autorizada antes."
            refresh()
        }
    }

    fun deleteEntry(entry: LibraryEntry) {
        viewModelScope.launch {
            val ok = if (entry.scopeLabel.contains("Workspace")) {
                safManager.deleteWorkspaceFile(entry.name)
            } else {
                safManager.deleteSafEntry(entry.uriOrPath)
            }
            _statusNotice.value = if (ok) "Removido: ${entry.name}" else "Não foi possível remover ${entry.name}."
            refresh()
        }
    }

    fun renameSafEntry(entry: LibraryEntry, newName: String) {
        viewModelScope.launch {
            val ok = safManager.renameSafEntry(entry.uriOrPath, newName)
            _statusNotice.value = if (ok) "Renomeado para $newName." else "Falha ao renomear."
            refresh()
        }
    }

    fun openEntry(entry: LibraryEntry) {
        if (entry.scopeLabel.contains("Workspace")) {
            if (entry.mimeType.startsWith("image/")) {
                if (!safManager.openWorkspaceFile(entry.name)) _statusNotice.value = "Nenhum aplicativo disponível para abrir a imagem."
            } else {
                _previewText.value = safManager.readWorkspaceFile(entry.name)
            }
        } else if (entry.isDirectory) {
            enterSafFolder(entry)
        } else {
            val ok = safManager.openSafEntry(entry.uriOrPath, entry.mimeType)
            if (!ok) _statusNotice.value = "Nenhum aplicativo instalado para abrir ${entry.name}."
        }
    }

    fun shareEntry(entry: LibraryEntry) {
        val ok = if (entry.scopeLabel.contains("Workspace")) {
            safManager.shareWorkspaceFile(entry.name)
        } else {
            safManager.shareSafEntry(entry.uriOrPath, entry.mimeType, entry.name)
        }
        if (!ok) _statusNotice.value = "Não foi possível compartilhar ${entry.name}."
    }

    fun openWorkspacePreview(name: String) {
        _previewText.value = safManager.readWorkspaceFile(name)
    }

    fun clearPreview() {
        _previewText.value = null
    }

    fun clearNotice() {
        _statusNotice.value = null
    }
}
