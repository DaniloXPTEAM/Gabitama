package com.novoagentehb.app.ui.chat

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

/** Only previews the exact private image-artifact receipt format, never arbitrary paths or URLs. */
@Composable
fun WorkspaceImagePreview(summary: String) {
    val match = Regex("^Imagem criada e salva em (images/[a-f0-9-]{36}\\.(?:png|jpg)); SHA-256 [a-f0-9]{64}\\. Abra na Biblioteca\\.$").matchEntire(summary) ?: return
    val path = match.groupValues[1]
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val root = File(context.filesDir, "agent_workspace").canonicalFile
                val directory = File(root, "images")
                val target = File(directory, path.substringAfter('/'))
                if (Files.isSymbolicLink(directory.toPath()) || Files.isSymbolicLink(target.toPath()) ||
                    !target.canonicalPath.startsWith(root.path + File.separator) || !target.isFile || target.length() > 12L * 1024 * 1024) return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(target.path, bounds)
                if (bounds.outWidth !in 1..8192 || bounds.outHeight !in 1..8192) return@runCatching null
                var sample = 1
                while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
                BitmapFactory.decodeFile(target.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    bitmap?.let { Image(it, contentDescription = "Imagem gerada salva na biblioteca", modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) }
}
