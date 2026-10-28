package com.example.ui.arranger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MidiState
import com.example.domain.soundfont.repository.SoundFontStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Interactive Dialog that clearly explains where SoundFonts (SF2 / SFZ) are located,
 * how to import them via SAF Folder/File pickers, copy the direct filesystem path,
 * and view catalog & RAM consumption stats.
 */
@Composable
fun SoundFontHelpDialog(
    onDismiss: () -> Unit,
    onSoundFontsUpdated: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var triggerUpdate by remember { mutableStateOf(0) }
    val dummy = triggerUpdate

    val totalVoices = MidiState.soundFontRepository.availableVoices.size
    val ramUsedMb = MidiState.soundFontRepository.getLoadedRamMb()
    val defaultDir = remember { SoundFontStorageManager.getDefaultSoundFontsDir(context).absolutePath }

    // Folder Picker (SAF)
    val openFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { treeUri ->
            isProcessing = true
            statusText = "Indexando pasta selecionada..."
            scope.launch(Dispatchers.IO) {
                try {
                    val count = SoundFontStorageManager.importFolderTree(context, treeUri) { fileName, curr ->
                        launch(Dispatchers.Main) {
                            statusText = "Indexando ($curr): $fileName"
                        }
                    }
                    launch(Dispatchers.Main) {
                        isProcessing = false
                        statusText = "$count arquivos indexados com sucesso!"
                        triggerUpdate++
                        onSoundFontsUpdated()
                        Toast.makeText(context, "$count SoundFonts adicionados!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    launch(Dispatchers.Main) {
                        isProcessing = false
                        statusText = "Erro: ${e.message}"
                        Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // File Picker (SAF)
    val openFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            isProcessing = true
            statusText = "Importando ${uris.size} arquivos..."
            scope.launch(Dispatchers.IO) {
                try {
                    val count = SoundFontStorageManager.importFiles(context, uris) { fileName, curr, tot ->
                        launch(Dispatchers.Main) {
                            statusText = "Copiando ($curr/$tot): $fileName"
                        }
                    }
                    launch(Dispatchers.Main) {
                        isProcessing = false
                        statusText = "$count arquivos importados!"
                        triggerUpdate++
                        onSoundFontsUpdated()
                        Toast.makeText(context, "$count arquivos importados!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    launch(Dispatchers.Main) {
                        isProcessing = false
                        statusText = "Erro: ${e.message}"
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎵 Como Inserir SoundFonts (.sf2 / .sfz)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Status Bar / Quick Stats
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Biblioteca de Timbres",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "$totalVoices timbres indexados",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "RAM em uso: ${String.format("%.1f", ramUsedMb)} MB (Carregamento sob demanda)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        if (ramUsedMb > 0f) {
                            Button(
                                onClick = {
                                    MidiState.soundFontRepository.clearCache()
                                    triggerUpdate++
                                    Toast.makeText(context, "Memória RAM liberada!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Limpar RAM", fontSize = 11.sp)
                            }
                        }
                    }
                }

                if (statusText != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF38BDF8))
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(statusText ?: "", fontSize = 12.sp, color = Color.White)
                        }
                    }
                }

                // METHOD 1: OPEN FOLDER (SAF)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Método 1: Abrir Pasta Completa (Recomendado)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Selecione diretamente a pasta onde estão seus timbres do Korg Triton (no Google Drive, na memória interna ou cartão SD). O app lê os arquivos organizados por subpastas e categorias sem ocupar memória extra.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = { openFolderLauncher.launch(null) },
                            enabled = !isProcessing,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("📁 Abrir Pasta Completa (ex: Korg Triton)")
                        }
                    }
                }

                // METHOD 2: SELECT INDIVIDUAL FILES
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Método 2: Selecionar Arquivos Avulsos",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            "Escolha um ou vários arquivos .sf2 ou .sfz da sua pasta de Downloads ou WhatsApp.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = { openFilesLauncher.launch(arrayOf("*/*")) },
                            enabled = !isProcessing,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("📄 Selecionar Arquivos (.sf2, .sfz)")
                        }
                    }
                }

                // METHOD 3: COPY DIRECTORY PATH (USB / COMPUTER / FILES)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Método 3: Via Cabo USB / Computador",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            "Você pode conectar o celular no computador pelo cabo USB ou usar o aplicativo 'Arquivos / Files' do Android e colar os arquivos diretamente na pasta do aplicativo ou em 'Músicas/SoundFonts':",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Pasta padrão do aplicativo:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    defaultDir,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("SoundFonts Directory", defaultDir)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Caminho copiado para a área de transferência!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("📋 Copiar Caminho", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    isProcessing = true
                                    statusText = "Escaneando pastas..."
                                    scope.launch(Dispatchers.IO) {
                                        SoundFontStorageManager.scanAll(context)
                                        launch(Dispatchers.Main) {
                                            isProcessing = false
                                            statusText = "Varredura concluída!"
                                            triggerUpdate++
                                            onSoundFontsUpdated()
                                            Toast.makeText(context, "Varredura de SoundFonts concluída!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🔄 Re-escanear", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Fechar")
            }
        }
    )
}
