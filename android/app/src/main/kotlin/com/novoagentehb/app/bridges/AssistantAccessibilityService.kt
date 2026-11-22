package com.novoagentehb.app.bridges

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Serviço de acessibilidade com parada imediata e verificação de janela ativa:
 * - Suporta WhatsApp padrão e WhatsApp Business.
 * - Nunca automatiza campos de senha ou OTP.
 */
class AssistantAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isEnabled.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.toString()?.let { _activePackage.value = it }
        }
    }

    override fun onInterrupt() {
        stopRequested.set(true)
    }

    override fun onDestroy() {
        instance = null
        _isEnabled.value = false
        super.onDestroy()
    }

    fun stopCurrentAutomation() {
        stopRequested.set(true)
    }

    fun isStopRequested(): Boolean = stopRequested.get()

    fun clearStopRequest() {
        stopRequested.set(false)
    }

    fun readVisibleTextSummary(maxNodes: Int = 20): String {
        val root = rootInActiveWindow ?: return "Sem janela ativa acessível."
        val collected = mutableListOf<String>()
        collectText(root, collected, maxNodes)
        return collected.joinToString(" | ").take(600)
    }

    fun attemptTypeAndSendWhatsApp(message: String): Boolean {
        if (stopRequested.get()) return false
        val root = rootInActiveWindow ?: return false
        val rootPkg = root.packageName?.toString().orEmpty()
        val pkg = when {
            rootPkg == WA_PACKAGE || rootPkg == WA_BUSINESS_PACKAGE -> rootPkg
            _activePackage.value == WA_PACKAGE || _activePackage.value == WA_BUSINESS_PACKAGE -> _activePackage.value
            else -> return false
        }

        val entryNode = root.findAccessibilityNodeInfosByViewId("$pkg:id/entry").firstOrNull()
            ?: return false
        val args = Bundle().apply {
            putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, message)
        }
        entryNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        if (!entryNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return false
        if (stopRequested.get()) return false

        val sendNode = root.findAccessibilityNodeInfosByViewId("$pkg:id/send").firstOrNull()
            ?: return false
        return sendNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun collectText(node: AccessibilityNodeInfo?, out: MutableList<String>, limit: Int) {
        if (node == null || out.size >= limit) return
        if (node.isPassword) return
        node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        for (i in 0 until node.childCount) {
            collectText(node.getChild(i), out, limit)
        }
    }

    companion object {
        const val WA_PACKAGE = "com.whatsapp"
        const val WA_BUSINESS_PACKAGE = "com.whatsapp.w4b"

        private var instance: AssistantAccessibilityService? = null
        private val stopRequested = AtomicBoolean(false)

        private val _isEnabled = MutableStateFlow(false)
        val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

        private val _activePackage = MutableStateFlow("")
        val activePackage: StateFlow<String> = _activePackage.asStateFlow()

        fun getInstance(): AssistantAccessibilityService? = instance
    }
}
