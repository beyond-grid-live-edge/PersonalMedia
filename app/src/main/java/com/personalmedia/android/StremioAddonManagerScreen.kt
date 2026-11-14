package com.personalmedia.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Native Compose add-on catalogue; account mutations are real addonCollectionSet writes. */
@Composable
internal fun StremioAddonManagerScreen(
    client: StremioClient,
    onUpdated: () -> Unit,
    onMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var group by remember { mutableStateOf("Installed") }
    var type by remember { mutableStateOf("All") }
    var query by remember { mutableStateOf("") }
    var installed by remember(client.email) { mutableStateOf(client.installedManageAddons) }
    var official by remember { mutableStateOf(emptyList<StremioManageAddon>()) }
    var community by remember { mutableStateOf(emptyList<StremioManageAddon>()) }
    var loading by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var directoryStatus by remember { mutableStateOf("") }
    var displayCount by remember { mutableIntStateOf(45) }
    var customUrl by remember { mutableStateOf("") }
    var customPreview by remember { mutableStateOf<StremioManageAddon?>(null) }
    var removeTarget by remember { mutableStateOf<StremioManageAddon?>(null) }

    suspend fun reload() {
        loading = true
        directoryStatus = ""
        val account = client.email
        try {
            client.refreshAddons()
            if (client.email == account) {
                installed = client.installedManageAddons
                onUpdated()
            }
        } catch (_: Exception) { directoryStatus = "Account add-ons could not be refreshed." }
        coroutineScope {
            val officialJob = async { runCatching { StremioAddonDirectory.browse("Official") }.getOrNull() }
            val communityJob = async { runCatching { StremioAddonDirectory.browse("Community") }.getOrNull() }
            val a = officialJob.await()
            val b = communityJob.await()
            if (client.email == account) {
                if (a != null) official = a
                if (b != null) community = b
                if (a == null || b == null) {
                    directoryStatus = "One catalogue is temporarily unavailable. Installed add-ons remain accessible."
                }
            }
        }
        loading = false
    }
    LaunchedEffect(client.email) { if (client.loggedIn) reload() }

    fun change(addon: StremioManageAddon, install: Boolean) {
        if (working) return
        working = true
        status = if (install) "Installing add-on to Stremio account…" else "Removing add-on from Stremio account…"
        scope.launch {
            try {
                client.setAddonInstalled(addon, install)
                installed = client.installedManageAddons
                onUpdated()
                status = if (install) "Installed to your Stremio account ✓" else "Removed from your Stremio account ✓"
                onMessage(status)
                customPreview = null
            } catch (e: Exception) {
                status = e.message?.takeIf { !it.contains("http", true) }?.take(180)
                    ?: "Stremio did not accept the change. Your previous add-ons were preserved."
            } finally { working = false }
        }
    }

    val all = when (group) {
        "Installed" -> installed
        "Official" -> official
        else -> community
    }
    val shown = all.filter { StremioAddonManagePolicy.matchesType(it.types, type) &&
        (query.isBlank() || it.name.contains(query.trim(), true) ||
            it.description.contains(query.trim(), true))
    }.take(displayCount)
    val cardStyle = RoundedCornerShape(13.dp)
    val faint = Color(0xFFB6C6DD)
    val card = Color(0xFF172234)
    val green = Color(0xFF20AA69)

    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Stremio Add-ons", color = Color.White, fontSize = 23.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${installed.size} installed", color = faint, fontSize = 12.sp)
        }
        Text("Browse add-ons and install or uninstall them directly on your Stremio account. " +
            "Changes sync with other Stremio devices.", color = faint, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Installed", "Official", "Community").forEach { tab ->
                Box(Modifier.background(if (group == tab) Color(0xFF246ACD) else Color(0xFF283348), cardStyle)
                    .clickable { group = tab; displayCount = 45 }.padding(horizontal = 14.dp, vertical = 11.dp)) {
                    Text(tab, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("All", "Movies", "Series", "Anime", "Channels", "Podcasts", "Others")) { filter ->
                Box(Modifier.background(if (type == filter) Color(0xFF265CBD) else card, cardStyle)
                    .clickable { type = filter; displayCount = 45 }
                    .padding(horizontal = 13.dp, vertical = 9.dp)) {
                    Text(filter, color = Color.White, fontSize = 12.sp)
                }
            }
        }
        OutlinedTextField(value = query, onValueChange = { query = it; displayCount = 45 },
            label = { Text("Search add-ons") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (directoryStatus.isNotBlank()) Text(directoryStatus, color = Color(0xFFFFC37A), fontSize = 12.sp)
        if (status.isNotBlank()) Text(status, color = Color(0xFF9AE6C0), fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${all.size} add-ons in ${group.lowercase()} · ${shown.size} shown", color = faint, fontSize = 11.sp,
                modifier = Modifier.weight(1f))
            Text("↻ Refresh", color = Color(0xFF8CC0FF), modifier = Modifier.clickable(enabled = !working && !loading) {
                scope.launch { reload() }
            }.padding(6.dp), fontSize = 12.sp)
        }
        if (!loading && shown.isEmpty()) Text(if (group == "Installed")
            "No installed add-ons match these filters." else "No add-ons found. Check the filters or refresh.",
            color = faint, fontSize = 13.sp)

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = when {
                maxWidth >= 1000.dp -> 3
                maxWidth >= 620.dp -> 2
                else -> 1
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                shown.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { addon ->
                            val isInstalled = installed.any { it.accountKey == addon.accountKey }
                            val serverAddon = installed.firstOrNull { it.accountKey == addon.accountKey }
                            val protected = serverAddon?.protected == true || addon.protected
                            Column(Modifier.weight(1f).heightIn(min = 195.dp)
                                .background(card, cardStyle).border(1.dp, Color(0xFF293750), cardStyle)
                                .padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (addon.logo.isNotBlank()) {
                                        AsyncImage(model = addon.logo, contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(46.dp))
                                    } else {
                                        Box(Modifier.size(46.dp).background(Color(0xFF33435C), cardStyle),
                                            contentAlignment = Alignment.Center) {
                                            Text("✦", color = Color.White, fontSize = 22.sp)
                                        }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(addon.name, color = Color.White, fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold, maxLines = 2,
                                            overflow = TextOverflow.Ellipsis)
                                        Text(addon.types.joinToString(", ").ifBlank { "Other" }, color = faint,
                                            fontSize = 11.sp, maxLines = 2)
                                    }
                                    if (addon.version.isNotBlank()) Text("v${addon.version}", color = faint, fontSize = 10.sp)
                                }
                                Text(addon.description.ifBlank { "Stremio add-on" }, color = faint,
                                    fontSize = 11.sp, modifier = Modifier.weight(1f, fill = false),
                                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                                if (addon.configurable && !addon.configurationRequired && !isInstalled)
                                    Text("Optional configuration available via a configured manifest URL.",
                                        color = faint, fontSize = 10.sp)
                                if (addon.configurationRequired && !isInstalled) Text(
                                    "Configuration required. Paste the configured manifest URL below.",
                                    color = Color(0xFFFFD28E), fontSize = 11.sp)
                                if (protected && isInstalled) Text("Protected official add-on", color = faint, fontSize = 11.sp)
                                Box(Modifier.fillMaxWidth().background(
                                    if (isInstalled) Color(0xFF334058) else green, cardStyle)
                                    .clickable(enabled = !working && !protected &&
                                        !(addon.configurationRequired && !isInstalled)) {
                                        if (isInstalled) removeTarget = addon else change(addon, true)
                                    }.padding(10.dp), contentAlignment = Alignment.Center) {
                                    Text(when {
                                        protected && isInstalled -> "Installed · Protected"
                                        isInstalled -> "Uninstall"
                                        addon.configurationRequired -> "Configure first"
                                        else -> "Install"
                                    }, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                }
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (all.size > shown.size && shown.size >= displayCount) {
            Box(Modifier.fillMaxWidth().background(Color(0xFF25354B), cardStyle)
                .clickable { displayCount += 45 }.padding(12.dp), contentAlignment = Alignment.Center) {
                Text("Show more add-ons", color = Color.White)
            }
        }
        HorizontalDivider(color = Color(0xFF304057))
        Text("Install using a configured manifest URL", color = Color.White, fontWeight = FontWeight.SemiBold)
        Text("Paste an HTTPS /manifest.json address for a custom or configurable add-on. " +
            "Preview its details before installing. Configuration URLs may contain private keys.",
            color = faint, fontSize = 12.sp)
        OutlinedTextField(value = customUrl, onValueChange = { customUrl = it; customPreview = null },
            modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("HTTPS add-on manifest URL") })
        Box(Modifier.background(Color(0xFF273C58), cardStyle).clickable(enabled = !working &&
            StremioAddonManagePolicy.acceptableManifestUrl(customUrl)) {
            working = true; status = "Reading add-on manifest…"
            scope.launch {
                try {
                    customPreview = StremioAddonDirectory.previewCustom(customUrl.trim())
                    status = "Manifest loaded. Review the add-on below."
                } catch (_: Exception) { status = "Unable to load a valid HTTPS Stremio manifest." }
                finally { working = false }
            }
        }.padding(horizontal = 16.dp, vertical = 11.dp)) {
            Text("Preview manifest", color = Color.White)
        }
        customPreview?.let { preview ->
            Column(Modifier.fillMaxWidth().background(card, cardStyle).padding(13.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(preview.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(preview.description, color = faint, fontSize = 12.sp)
                Text("Resources and metadata come from this third-party add-on provider.",
                    color = faint, fontSize = 11.sp)
                Box(Modifier.background(green, cardStyle).clickable(enabled = !working &&
                    !installed.any { it.accountKey == preview.accountKey }) {
                    change(preview, true)
                }.padding(11.dp)) {
                    Text(if (installed.any { it.accountKey == preview.accountKey }) "Already installed" else "Install to Stremio account",
                        color = Color.White)
                }
            }
        }
    }
    removeTarget?.let { target ->
        AlertDialog(onDismissRequest = { removeTarget = null },
            title = { Text("Uninstall ${target.name}?") },
            text = { Text("This removes the add-on from your Stremio account and other synced devices. Your local movies and Freeview channels are not deleted.") },
            confirmButton = { TextButton(onClick = { removeTarget = null; change(target, false) }) {
                Text("Uninstall") } },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Cancel") } })
    }
}
