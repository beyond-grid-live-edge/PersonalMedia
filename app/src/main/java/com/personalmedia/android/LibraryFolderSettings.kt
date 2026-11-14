package com.personalmedia.android

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Mirrors the HarmonyOS separate-source settings while using Android SAF permissions. */
@Composable
internal fun LibraryFolderSettings(
    folders: List<SavedMediaFolder>,
    counts: Map<String, Int>,
    accessibleTrees: Set<String>,
    scanningTree: String?,
    notice: String,
    onChoose: (category: String, reconnect: String?) -> Unit,
    onImport: (category: String) -> Unit,
    onScan: (treeUri: String) -> Unit,
    onRescanAll: () -> Unit,
    onRemove: (treeUri: String) -> Unit
) {
    var removeCandidate by remember { mutableStateOf<SavedMediaFolder?>(null) }
    val allBusy = scanningTree != null
    Text("Choose a location for each media library. Android remembers permissions for the selected folders. " +
        "Files stay in their original locations; PersonalMedia only indexes them.",
        color = Color(0xFFA9ACC0), fontSize = 12.sp)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Button(onClick = onRescanAll, enabled = !allBusy) { Text("↻ Rescan all sources") }
        TextButton(onClick = { onChoose("Movies", null) }, enabled = !allBusy) { Text("＋ Add folder") }
    }
    if (notice.isNotBlank()) Text(notice, color = Color(0xFF91CAFF), fontSize = 12.sp)
    LibraryFolderPolicy.categories.forEach { category ->
        val categoryFolders = folders.filter { it.category == category }
        val description = when (category) {
            "Movies" -> "Videos imported here stay in Movies."
            "TV Shows" -> "Videos belong to TV Shows; SxxExx filenames help group episodes."
            "Music" -> "Audio-only imports for your Music library."
            else -> "Images from your selected Pictures folders."
        }
        Column(Modifier.fillMaxWidth().background(Color(0xFF202334), RoundedCornerShape(12.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(category, color = Color.White, fontWeight = FontWeight.Bold)
                Text("${counts[category] ?: 0} local files", color = Color(0xFFACD4FF), fontSize = 11.sp)
            }
            Text(description, color = Color(0xFFBEC2D3), fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !allBusy, onClick = { onChoose(category, null) }) {
                    Text("＋ Choose $category folder", fontSize = 12.sp)
                }
                OutlinedButton(enabled = !allBusy, onClick = { onImport(category) }) {
                    Text("Import $category files", fontSize = 12.sp)
                }
            }
            if (categoryFolders.isEmpty()) {
                Text("No folder selected yet", color = Color(0xFFB2B5C5), fontSize = 12.sp)
            }
            categoryFolders.forEach { folder ->
                val canRead = folder.treeUri in accessibleTrees
                val busy = scanningTree == folder.treeUri
                Column(Modifier.fillMaxWidth().background(Color(0xFF171B2A), RoundedCornerShape(10.dp))
                    .padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(folder.label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(folder.treeUri, color = Color(0xFF9FA9C6), fontSize = 10.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (!canRead) "Permission unavailable — reconnect this folder; existing library entries are preserved"
                        else if (busy) "Scanning selected folder…" else folder.lastScan,
                        color = if (canRead) Color(0xFF9EDDB0) else Color(0xFFF4BC81), fontSize = 11.sp)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(enabled = !allBusy && canRead, onClick = { onScan(folder.treeUri) }) {
                            Text("Scan", fontSize = 11.sp)
                        }
                        OutlinedButton(enabled = !allBusy, onClick = { onChoose(folder.category, folder.treeUri) }) {
                            Text("Reconnect", fontSize = 11.sp)
                        }
                        TextButton(enabled = !allBusy, onClick = { removeCandidate = folder }) {
                            Text("Remove", fontSize = 11.sp, color = Color(0xFFFFB1BD))
                        }
                    }
                }
            }
        }
    }
    val candidate = removeCandidate
    if (candidate != null) AlertDialog(
        onDismissRequest = { removeCandidate = null },
        title = { Text("Remove ${candidate.category} folder?") },
        text = { Text("Remove ${candidate.label} and its indexed entries from PersonalMedia? " +
            "Your actual files will not be deleted.") },
        confirmButton = { TextButton(onClick = {
            onRemove(candidate.treeUri)
            removeCandidate = null
        }) { Text("Remove folder") } },
        dismissButton = { TextButton(onClick = { removeCandidate = null }) { Text("Cancel") } }
    )
}
