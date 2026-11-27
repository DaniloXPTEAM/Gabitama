package com.novoagentehb.app.ui.chat

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

private val RECEIPT_PATTERN = Regex("^Imagem criada e salva em (images/[a-f0-9-]{36}\\.(?:png|jpg)); SHA-256 [a-f0-9]{64}\\. Abra na Biblioteca\\.$")

/** Só aceita o formato exato do recibo de imagem; nunca caminhos arbitrários ou URLs. */
@Composable
fun WorkspaceImagePreview(summary: String) {
    val match = RECEIPT_PATTERN.matchEntire(summary) ?: return
    val path = match.groupValues[1]
    val context = LocalContext.current
    val file by produceState<File?>(null, path) {
        value = withContext(Dispatchers.IO) { resolveImageFile(context, path) }
    }
    val bitmap by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) {
            val target = file ?: return@withContext null
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(target.path, bounds)
                if (bounds.outWidth !in 1..8192 || bounds.outHeight !in 1..8192) return@runCatching null
                var sample = 1
                while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
                BitmapFactory.decodeFile(target.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    val image = bitmap ?: return
    Image(image, contentDescription = "Imagem gerada salva na biblioteca", modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp))
    Row {
        TextButton(onClick = {
            val target = file ?: return@TextButton
            runCatching { shareImage(context, target) }
                .onFailure { status = "Não foi possível compartilhar a imagem." }
        }) { Text("Compartilhar") }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = {
            val target = file ?: return@TextButton
            scope.launch {
                status = withContext(Dispatchers.IO) { saveToGallery(context, target) }
            }
        }) { Text("Salvar na galeria") }
    }
    if (status.isNotBlank()) Text(status)
}

/** Resolve o arquivo dentro de agent_workspace/images, sem links simbólicos e fora da raiz. */
internal fun resolveImageFile(context: Context, path: String): File? = runCatching {
    val root = File(context.filesDir, "agent_workspace").canonicalFile
    val directory = File(root, "images")
    val target = File(directory, path.substringAfter('/'))
    if (Files.isSymbolicLink(directory.toPath()) || Files.isSymbolicLink(target.toPath()) ||
        !target.canonicalPath.startsWith(root.path + File.separator) || !target.isFile || target.length() > 12L * 1024 * 1024
    ) null else target
}.getOrNull()

/** Compartilhamento acionado pelo usuário, via FileProvider (somente leitura concedida). */
private fun shareImage(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = if (file.name.endsWith(".png")) "image/png" else "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartilhar imagem").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/**
 * Salva cópia na galeria (Pictures/GabiTama) somente após toque do usuário.
 * Usa MediaStore sem permissão de armazenamento (Android 10+). Em versões anteriores informa a limitação.
 */
private fun saveToGallery(context: Context, file: File): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return "Salvar na galeria exige Android 10 ou superior nesta versão."
    }
    val mime = if (file.name.endsWith(".png")) "image/png" else "image/jpeg"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "gabitama-${file.name}")
        put(MediaStore.Images.Media.MIME_TYPE, mime)
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/GabiTama")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return "Não foi possível criar o arquivo na galeria."
    return runCatching {
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            ?: error("sem stream")
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        "Imagem salva na galeria (Pictures/GabiTama)."
    }.getOrElse {
        resolver.delete(uri, null, null)
        "Falha ao salvar na galeria; nada foi mantido."
    }
}
