package com.personalmedia.android

import android.content.Context
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

// The same explicit-English-fallback strategy as HarmonyOS PMStrings. No media,
// account, tuner, transport, or file identifiers are ever changed in storage.
internal val LocalPmUiLanguage = compositionLocalOf { "en" }

internal class PmUiLanguageStore(context: Context) {
    private val preferences = context.getSharedPreferences("personalmedia_display_language_v1", Context.MODE_PRIVATE)
    fun read(): String = if (preferences.getString("ui_language", "en") == "gd") "gd" else "en"
    fun save(value: String): Boolean {
        if (value != "en" && value != "gd") return false
        return preferences.edit().putString("ui_language", value).commit()
    }
}

internal object PmI18n {
    // UI-only Scottish Gaelic preview; unrecognised text is deliberately English.
    // Names of films, TV stations, streaming providers and user files remain untouched.
    private val gd = mapOf(
        "Home" to "Dachaigh",
        "Movies" to "Filmichean",
        "TV Shows" to "Prògraman Tbh",
        "Stremio" to "Stremio",
        "Live TV" to "Tbh beò",
        "Music" to "Ceòl",
        "Pictures" to "Dealbhan",
        "Favourites" to "Na rudan as fheàrr leat",
        "Settings" to "Roghainnean",
        "English" to "Beurla",
        "Gàidhlig (Alba)" to "Gàidhlig (Alba)",
        "INTERFACE LANGUAGE" to "CÀNAN AN EADAR-AGHAIDH",
        "PersonalMedia display language" to "Cànan taisbeanaidh PersonalMedia",
        "Changes the PersonalMedia interface only. Artwork metadata and audio/subtitle language preferences stay independent." to
            "Chan atharraich seo ach eadar-aghaidh PersonalMedia. Tha meata-dàta nan dealbhan agus roghainnean cànan fuaime is fo-thiotalan fa leth.",
        "LANGUAGES" to "CÀNANAN",
        "Audio and subtitle preferences" to "Roghainnean fuaime is fo-thiotalan",
        "Preferred audio language" to "Cànan fuaime as fheàrr leat",
        "Preferred subtitle language" to "Cànan fo-thiotalan as fheàrr leat",
        "Show subtitles by default" to "Seall fo-thiotalan mar bhun-roghainn",
        "Available embedded tracks are selected when their language matches. Players retain their manual Audio/CC controls." to
            "Thèid slighean fuaime is fo-thiotalan a thaghadh a rèir a' chànain. Tha na smachdan Audio/CC fhathast rim faighinn.",
        "Auto" to "Fèin-obrachail",
        "French" to "Fraingis",
        "German" to "Gearmailtis",
        "Original / Auto" to "Tùsail / Fèin-obrachail",
        "PersonalMedia" to "PersonalMedia",
        "● Native Library" to "● Leabharlann dhùthchasach",
        "PERSONALMEDIA" to "PERSONALMEDIA",
        "YOUR CINEMATIC LIBRARY" to "AN LEABHARLANN FHILM AGAD",
        "Your library,\nbeautifully yours." to "Do leabharlann,\nmar a thogras tu.",
        "Choose a movie or TV folder in Settings to start your cinematic library." to
            "Tagh pasgan fhilmichean no phrògraman Tbh sna Roghainnean gus do leabharlann a thòiseachadh.",
        "＋ Add your media" to "＋ Cuir meadhanan ris",
        "Your media, beautifully yours. Open details or start playing." to
            "Na meadhanan agad, mar a thogras tu. Fosgail fiosrachadh no tòisich air cluich.",
        "YOUR COLLECTION" to "DO CHRUINNEACHADH",
        "FEATURED FROM YOUR LIBRARY" to "AIR A MHOALADH BHON LEABHARLANN AGAD",
        "PICK UP WHERE YOU LEFT OFF" to "LEAN AIR FAR AN DO SGUR THU",
        "YOUR LIBRARY" to "DO LEABHARLANN",
        "YOUR LIBRARY / TV SHOW" to "DO LEABHARLANN / PRÒGRAM TBH",
        "YOUR LIBRARY / TV COLLECTION" to "DO LEABHARLANN / CRUINNEACHADH TBH",
        "MOVIE" to "FILM",
        "MUSIC" to "CEÒL",
        "SERIES" to "SREATH",
        "Movie" to "Film",
        "Series" to "Sreath",
        "TV" to "TBH",
        "ⓘ More Info" to "ⓘ Barrachd fiosrachaidh",
        "Browse Movies" to "Rùraich na filmichean",
        "Explore Movies" to "Rannsaich filmichean",
        "Explore Music" to "Rannsaich ceòl",
        "Your Pictures" to "Na dealbhan agad",
        "TV Collections" to "Cruinneachaidhean Tbh",
        "↻ Rescan" to "↻ Sgan a-rithist",
        "Title" to "Tiotal",
        "Recent" to "O chionn ghoirid",
        "＋ Add" to "＋ Cuir ris",
        "＋ Add to a category" to "＋ Cuir ri roinn",
        "Unwatched" to "Gun choimhead fhathast",
        "Show all" to "Seall na h-uile",
        "Your library" to "Do leabharlann",
        "♡ Favourites" to "♡ Na rudan as fheàrr leat",
        "TV SHOW" to "PRÒGRAM TBH",
        "PICTURES" to "DEALBHAN",
        "CONTINUE WATCHING" to "LEAN AIR COIMHEAD",
        "Continue Watching" to "Lean air coimhead",
        "Franchises & Collections" to "Sreathan is cruinneachaidhean",
        "Your TV Shows" to "Na prògraman Tbh agad",
        "Your library is empty" to "Tha do leabharlann falamh",
        "This native app does not include demo films. Choose media folders in Settings to begin." to
            "Chan eil filmichean taisbeanaidh san aplacaid. Tagh pasganan mheadhanan sna Roghainnean gus tòiseachadh.",
        "Nothing here yet" to "Chan eil dad an seo fhathast",
        "Add files to this category from Settings." to "Cuir faidhlichean ris an roinn seo sna Roghainnean.",
        "Search local library" to "Rannsaich an leabharlann ionadail",
        "⌕ Search your PersonalMedia library" to "⌕ Rannsaich leabharlann PersonalMedia",
        "Watch" to "Coimhead",
        "Sort" to "Seòrsaich",
        "Search" to "Rannsaich",
        "Recently Added" to "Air a chur ris o chionn ghoirid",
        "All" to "Uile",
        "Seasons" to "Ràithean",
        "Episodes" to "Earrannan",
        "Play episode" to "Cluich an earrann",
        "Play" to "Cluich",
        "▶ Play" to "▶ Cluich",
        "▶ RESUME" to "▶ LEAN AIR",
        "▶ Resume" to "▶ Lean air",
        "Resume" to "Lean air",
        "Pause" to "Cuir air stad",
        "Ⅱ Pause" to "Ⅱ Cuir air stad",
        "Stop" to "Stad",
        "■ Stop" to "■ Stad",
        "Close" to "Dùin",
        "Cancel" to "Sguir dheth",
        "Dismiss" to "Dùin",
        "Back" to "Air ais",
        "← Back" to "← Air ais",
        "← Back to TV Shows" to "← Air ais gu prògraman Tbh",
        "‹ Back to TV Shows" to "‹ Air ais gu prògraman Tbh",
        "‹ Back to Movies" to "‹ Air ais gu filmichean",
        "Local file" to "Faidhle ionadail",
        "File" to "Faidhle",
        "About this movie" to "Mun fhilm seo",
        "Available editions" to "Tionndaidhean rim faighinn",
        "Each edition is a separate original file; changing editions never deletes media." to
            "Tha gach tionndadh na fhaidhle tùsail air leth. Cha sguab atharrachadh tionndaidh às meadhanan.",
        "Metadata" to "Meata-dàta",
        "Source" to "Tùs",
        "Stored in" to "Air a stòradh ann an",
        "Ready to watch." to "Deiseil airson coimhead.",
        "Mark watched" to "Comharraich mar a chaidh fhaicinn",
        "Watched" to "Air fhaicinn",
        "Favourited" to "Air a chur ris na rudan as fheàrr leat",
        "Add to Library" to "Cuir ris an leabharlann",
        "Remove" to "Thoir air falbh",
        "Refresh" to "Ùraich",
        "Retry" to "Feuch a-rithist",
        "Loading" to "Ga luchdadh",
        "Next" to "Air adhart",
        "Previous" to "Air ais",
        "Now" to "An-dràsta",
        "Guide" to "Iùl",
        "EPG" to "EPG",
        "UK Freeview · T405" to "Freeview na RA · T405",
        "🇺🇸 US Internet TV" to "🇺🇸 Tbh eadar-lìn nan SA",
        "Live television" to "Telebhisean beò",
        "Connect T405" to "Ceangail T405",
        "Connect August T405" to "Ceangail August T405",
        "Connected" to "Ceangailte",
        "Disconnected" to "Gun cheangal",
        "Disconnect" to "Dì-cheangail",
        "Reconnect" to "Ath-cheangail",
        "Scan" to "Sgan",
        "Rescan" to "Sgan a-rithist",
        "Scan UK Freeview" to "Sgan Freeview na RA",
        "Signal" to "Comharra",
        "Battery" to "Bataraidh",
        "Channel" to "Seanail",
        "Channels" to "Seanailean",
        "Programme" to "Prògram",
        "Programmes" to "Prògraman",
        "Tuner settings" to "Roghainnean an ghlacadair",
        "Fullscreen" to "Làn-sgrìn",
        "Exit fullscreen" to "Fàg làn-sgrìn",
        "Subtitles" to "Fo-thiotalan",
        "Audio" to "Fuaim",
        "Audio tracks" to "Slighean fuaime",
        "Subtitle tracks" to "Slighean fo-thiotalan",
        "Caption" to "Fo-thiotal",
        "Captions" to "Fo-thiotalan",
        "Playback" to "Cluich",
        "PLAYBACK" to "CLUICH",
        "Playback and display" to "Cluich agus taisbeanadh",
        "Keep screen awake during video" to "Cum an sgrìn air fhad 's a tha bhidio ga chluich",
        "Keeps the screen awake while the player is on screen. Android handles the normal timeout after closing playback." to
            "Cumaidh seo an sgrìn air fhad 's a tha an cluicheadair fosgailte. Cleachdaidh Android an ùine àbhaisteach às dèidh sin.",
        "NATIVE STATUS" to "INBHE DHÙTHCHASACH",
        "Movie editions and remakes are not duplicate files. Only an identical file URI and media type is eligible for repair; the index can be restored with Undo." to
            "Chan eil tionndaidhean agus ath-dhèanaidhean fhilmichean nan dùblaidhean. Chan urrainnear ach clàran leis an aon URI agus seòrsa meadhain a chàradh; gabhaidh an clàr-innse aisig le Neo-dhèan.",
        "Disconnected or unreadable folders are never automatically cleaned. Only a completed folder scan can mark a record as possibly missing. A final permission check is required before removing its index entry; the original file is never deleted." to
            "Cha ghlanar pasganan gun cheangal no nach gabh leughadh gu fèin-obrachail. Cha chomharraich ach sgan crìochnaichte clàr mar dhìth. Feumar cead a sgrùdadh mus tèid clàr-innse a thoirt air falbh; cha tèid am faidhle tùsail a sguabadh às.",
        "PersonalMedia media library" to "Leabharlann mheadhanan PersonalMedia",
        "Runtime" to "Àrainneachd ruith",
        "Native local files" to "Faidhlichean ionadail",
        "Selected library folders" to "Pasganan leabharlainn taghte",
        "Stremio account" to "Cunntas Stremio",
        "Signed in" to "Air logadh a-steach",
        "Not signed in" to "Gun logadh a-steach",
        "Storage" to "Stòradh",
        "Android document providers" to "Solaraichean sgrìobhainnean Android",
        "Check local catalog" to "Thoir sùil air a' chatalog ionadail",
        "Folder scanning is permission-dependent. Optional metadata and artwork enrichment is managed separately below." to
            "Tha sganadh phasganan an urra ri ceadan. Tha meata-dàta agus dealbhan air an riaghladh air leth gu h-ìosal.",
        "LIBRARY INTEGRITY" to "IONRACAS NA LEABHARLAINN",
        "Catalog repair & safe reconciliation" to "Càradh a' chatalog agus rèiteachadh sàbhailte",
        "Exact duplicate file records" to "Clàran faidhle dùblaichte co-ionann",
        "Possibly missing after full scans" to "Dh'fhaodadh iad a bhith a dhìth às dèidh sgan",
        "Check missing files" to "Thoir sùil air faidhlichean a tha a dhìth",
        "Undo last catalog repair" to "Neo-dhèan càradh mu dheireadh a' chatalog",
        "MEDIA SOURCES" to "TÙSAN MHEADHANAN",
        "Separate library folders" to "Pasganan leabharlainn fa leth",
        "Choose a location for each media library. Android remembers permissions for the selected folders." to
            "Tagh àite airson gach leabharlann. Cuimhnichidh Android na ceadan airson na pasganan taghte.",
        "＋ Add folder" to "＋ Cuir pasgan ris",
        "↻ Rescan all sources" to "↻ Sgan na tùsan uile a-rithist",
        "Selected folders" to "Pasganan taghte",
        "No folder selected yet." to "Cha deach pasgan a thaghadh fhathast.",
        "No folder selected yet" to "Cha deach pasgan a thaghadh fhathast",
        "Choose a location for each media library. Android remembers permissions for the selected folders. Files stay in their original locations; PersonalMedia only indexes them." to
            "Tagh àite airson gach leabharlann. Cuimhnichidh Android ceadan nam pasganan taghte. Fuirichidh na faidhlichean nan àite fhèin; cha dèan PersonalMedia ach clàr-innse dhiubh.",
        "Videos imported here stay in Movies." to "Fuirichidh bhideothan a chuirear an-seo ann am Filmichean.",
        "Videos belong to TV Shows; SxxExx filenames help group episodes." to
            "Buinidh bhideothan do Phrògraman Tbh; cuidichidh ainmean SxxExx le bhith a' cur earrannan ann am buidhnean.",
        "Audio-only imports for your Music library." to "Faidhlichean fuaime airson do leabharlann Ceòl.",
        "Images from your selected Pictures folders." to "Dealbhan bho na pasganan Dealbhan a thagh thu.",
        "Remove folder" to "Thoir am pasgan air falbh",
        "Scanning selected folder…" to "A' sganadh a' phasgain thaghte…",
        "Permission unavailable — reconnect this folder; existing library entries are preserved" to
            "Chan eil cead ri fhaighinn — ath-cheangail am pasgan; thèid clàran na leabharlainn a ghleidheadh",
        "Import files" to "Ion-phortaich faidhlichean",
        "Choose folder" to "Tagh pasgan",
        "Open folder" to "Fosgail pasgan",
        "Rescan folder" to "Sgan am pasgan a-rithist",
        "QUALITY" to "CÀILEACHD",
        "Preferred streaming quality" to "Càileachd sruthaidh as fheàrr leat",
        "NATIVE ANDROID COMPATIBILITY" to "CO-FHREAGARRACHD ANDROID DHÙTHCHASACH",
        "Media capability diagnostics" to "Fiosrachadh comasan mheadhanan",
        "Local playback" to "Cluich ionadail",
        "Native MPV" to "MPV dùthchasach",
        "Available" to "Ri fhaighinn",
        "Binary not bundled" to "Chan eil am faidhle dà-bhitheach sa phacaid",
        "Verified-piece torrent" to "Torrent le pìosan dearbhte",
        "Native engine available" to "Einnsean dùthchasach ri fhaighinn",
        "Libtorrent dependency not bundled" to "Chan eil libtorrent sa phacaid",
        "Native UDP receiver · hardware required" to "Glacadair UDP dùthchasach · feumar bathar-cruaidh",
        "Decoder formats and video output depend on your Android device." to
            "Tha cruthan dì-chòdaidh agus toradh bhidio an urra ris an inneal Android agad.",
        "BACKUP & RECOVERY" to "LETHBHREAC-GLEIDHIDH IS ATH-LEASACHADH",
        "Clearing this index only removes PersonalMedia's saved entries; it never deletes your original media files." to
            "Cha toir falamhachadh a' chlàir-innse air falbh ach clàran sàbhailte PersonalMedia; cha sguab e às na faidhlichean tùsail agad.",
        "Your original media files remain untouched." to
            "Chan atharraichear na faidhlichean meadhain tùsail agad.",
        "Export your library, watched/resume positions, saved folder registrations, UK Freeview lineup, US favourites, playback preferences and remembered Audio/CC choices." to
            "Às-phortaich do leabharlann, àiteachan coimhead is ath-thòiseachaidh, pasganan sàbhailte, seanailean Freeview na RA, roghainnean nan SA, roghainnean cluiche agus roghainnean Audio/CC.",
        "Portable backups are UNENCRYPTED JSON and contain media titles and storage document IDs. Store them privately. No Stremio login tokens, passwords, signed stream URLs, torrent trackers, download credentials or downloaded video files are included." to
            "Tha na lethbhreacan-glèidhidh seo ann an JSON GUN CHRIOPTACHADH; tha tiotalan meadhain agus IDan sgrìobhainnean annta. Cùm prìobhaideach iad. Chan eil tòcan logaidh Stremio, faclan-faire, URLan srutha soidhnichte, lorgairean torrent, teisteanasan luchdaidh a-nuas no bhideothan luchdaichte annta.",
        "Restoring merges library records and never deletes media. Folder permissions belong to Android and must be granted again when needed. Stremio Continue Watching is restored only for the same signed-in account; two-way sync pending edits are never imported." to
            "Bidh aisig a' co-aonachadh clàran na leabharlainn; cha sguab e às meadhanan. Feumar ceadan phasganan Android a thoirt seachad a-rithist ma bhios feum. Cha tèid 'Lean air coimhead' Stremio aisig ach dhan aon chunntas; cha tèid deasachaidhean sioncronachaidh ri feitheamh ion-phortadh.",
        "Save a count-only capability report for troubleshooting. No media names, folder paths, private IPs, stream URLs or account credentials are included." to
            "Sàbhail aithisg comasan le àireamhan a-mhàin airson duilgheadasan fhuasgladh. Chan eil ainmean meadhain, slighean phasganan, IPan prìobhaideach, URLan srutha no teisteanasan cunntais ann.",
        "Portable library backup" to "Lethbhreac-glèidhidh so-ghiùlain",
        "Create backup" to "Cruthaich lethbhreac-glèidhidh",
        "Restore backup" to "Aisig lethbhreac-glèidhidh",
        "Undo restore" to "Neo-dhèan an t-aiseag",
        "Merge backup" to "Co-aonaich an lethbhreac-glèidhidh",
        "Restore PersonalMedia backup?" to "Aisig lethbhreac-glèidhidh PersonalMedia?",
        "SUPPORT" to "TAIC",
        "Redacted diagnostics" to "Fiosrachadh breithneachaidh gun dàta prìobhaideach",
        "Export support diagnostics" to "Às-phortaich fiosrachadh taice",
        "STABILITY & BACKUP" to "SEASMACHD IS LETHBHREAC-GLEIDHIDH",
        "Protect this installation" to "Dìon an stàladh seo",
        "App database" to "Stòr-dàta na h-aplacaid",
        "Private local preferences" to "Roghainnean ionadail prìobhaideach",
        "Clear local library index" to "Falamhaich clàr-innse na leabharlainn ionadail",
        "Clear local index?" to "Falamhaich an clàr-innse ionadail?",
        "Clear" to "Falamhaich",
        "METADATA & ARTWORK" to "MEATA-DÀTA IS DEALBHAN",
        "Artwork providers" to "Solaraichean dhealbhan",
        "Fetch missing posters and descriptions for your local Movies and TV Shows. Title/year matching protects remakes and editions; original files and watch history stay untouched." to
            "Faigh postairean is tuairisgeulan a tha a dhìth airson filmichean is prògraman Tbh ionadail. Thèid tiotal is bliadhna a choimeas gus ath-dhèanaidhean is tionndaidhean a dhìon; chan atharraichear na faidhlichean no an eachdraidh coimhead.",
        "Auto: TMDB → OMDb when credentials are entered, then Wikipedia (movies) / TVmaze (series). Free never uses keyed providers." to
            "Fèin-obrachail: TMDB → OMDb nuair a chuirear iuchraichean a-steach, an uair sin Wikipedia (filmichean) / TVmaze (sreathan). Cha chleachd Free solaraichean a dh'fheumas iuchair.",
        "TMDB v3 key or read token · session only" to "Iuchair TMDB v3 no tòcan leughaidh · an seisean seo a-mhàin",
        "OMDb API key · session only" to "Iuchair API OMDb · an seisean seo a-mhàin",
        "Credentials remain in app memory, are never added to the library or saved in settings, and are cleared when the app process ends." to
            "Fuirichidh teisteanasan ann an cuimhne na h-aplacaid a-mhàin, cha tèid an sàbhaladh san leabharlann no sna Roghainnean, agus thèid an sguabadh nuair a dhùineas pròiseas na h-aplacaid.",
        "Existing artwork is never overwritten. Wikipedia and TVmaze require no API key. OMDb does not supply backdrops. Internet required." to
            "Cha tèid dealbhan a th' ann a sgrìobhadh thairis. Chan fheum Wikipedia no TVmaze iuchair API. Cha sholaraich OMDb cùl-dhealbhan. Feumar eadar-lìon.",
        "Preferred provider" to "Solaraiche as fheàrr leat",
        "Metadata language" to "Cànan a' mheata-dàta",
        "Language (e.g. en-US)" to "Cànan (m.e. en-US)",
        "Posters" to "Postairean",
        "Backdrops where available" to "Cùl-dhealbhan far a bheil iad rim faighinn",
        "Auto-fetch after a completed folder scan (up to 25 titles)" to
            "Luchdaich dealbhan gu fèin-obrachail às dèidh sgan (suas ri 25 tiotal)",
        "25-title batch" to "Baids de 25 tiotal",
        "Entire library · up to 2,000 titles" to "An leabharlann gu lèir · suas ri 2,000 tiotal",
        "Fetch artwork" to "Faigh dealbhan",
        "Cancel scan" to "Sguir den sgan",
        "Retry missing" to "Feuch na tha a dhìth a-rithist",
        "Clear TMDB key" to "Falamhaich iuchair TMDB",
        "Clear OMDb key" to "Falamhaich iuchair OMDb",
        "TMDB credits" to "Taing do TMDB",
        "TVmaze credits" to "Taing do TVmaze",
        "Wikimedia credits" to "Taing do Wikimedia",
        "Add-ons" to "Tuilleadain",
        "Discover" to "Lorg",
        "Discover titles" to "Lorg tiotalan",
        "Discover Movies" to "Lorg filmichean",
        "Discover Series" to "Lorg sreathan",
        "Library" to "Leabharlann",
        "All catalogues" to "Na catalogan uile",
        "Refresh library" to "Ùraich an leabharlann",
        "Import history" to "Ion-phortaich eachdraidh",
        "Official watched history imported" to "Eachdraidh oifigeil coimhead air a h-ion-phortadh",
        "Official history not imported" to "Cha deach an eachdraidh oifigeil ion-phortadh",
        "Refresh add-ons" to "Ùraich tuilleadain",
        "Search all add-ons" to "Rannsaich na tuilleadain uile",
        "Use auth key" to "Cleachd iuchair dearbhaidh",
        "Log out" to "Log a-mach",
        "Stremio Add-ons" to "Tuilleadain Stremio",
        "Search all installed add-ons" to "Rannsaich na tuilleadain stàlaichte",
        "Install" to "Stàlaich",
        "Uninstall" to "Dì-stàlaich",
        "Installed" to "Stàlaichte",
        "Official" to "Oifigeil",
        "Community" to "Coimhearsnachd",
        "Anime" to "Anime",
        "Podcasts" to "Pod-chraolaidhean",
        "Others" to "Eile",
        "Browse add-ons and install or uninstall them directly on your Stremio account. Changes sync with other Stremio devices." to
            "Rùraich tuilleadain agus stàlaich no dì-stàlaich iad air a' chunntas Stremio agad. Thèid atharrachaidhean a shioncronachadh le innealan Stremio eile.",
        "No installed add-ons match these filters." to "Chan eil tuilleadain stàlaichte a' freagairt ris na criathragan.",
        "No add-ons found. Check the filters or refresh." to "Cha deach tuilleadain a lorg. Thoir sùil air na criathragan no ùraich.",
        "Installed · Protected" to "Stàlaichte · Dìonta",
        "Sign in" to "Log a-steach",
        "Sign out" to "Log a-mach",
        "Email" to "Post-d",
        "Password" to "Facal-faire",
        "Account" to "Cunntas",
        "Stream" to "Sruth",
        "Streams" to "Sruthan",
        "Download" to "Luchdaich a-nuas",
        "Downloads" to "Luchdaidhean a-nuas",
        "Download queue" to "Ciudha luchdaidh a-nuas",
        "Remove job" to "Thoir an obair air falbh",
        "Progress" to "Adhartas",
        "Queue" to "Ciudha",
        "Status" to "Inbhe",
        "Size" to "Meud",
        "Save" to "Sàbhail",
        "Open" to "Fosgail",
        "More info" to "Barrachd fiosrachaidh",
        "Details" to "Mion-fhiosrachadh",
        "Manage add-ons" to "Stiùirich tuilleadain",
        "View all  ›" to "Seall na h-uile  ›",
        "FEATURED FROM YOUR ADD-ONS" to "AIR A MHOALADH LEIS NA TUILLEADAIN AGAD",
        "Artwork unavailable" to "Chan eil dealbhan rim faighinn",
        "Watched history sync" to "Sioncronachadh eachdraidh coimhead",
        "Two-way account sync (opt-in)" to "Sioncronachadh dà-shligheach (roghainneil)",
        "Off by default. Import is read-only; only Sync uploads changes." to
            "Dheth mar bhun-roghainn. Chan atharraich ion-phortadh dad; cha chuir ach sioncronachadh atharrachaidhean suas.",
        "Your cinema" to "An taigh-dhealbh agad",
        "Your add-ons · Native playback" to "Na tuilleadain agad · Cluich dùthchasach",
        "Sign in to your Stremio account" to "Log a-steach dhan chunntas Stremio agad",
        "Or use an existing auth key" to "No cleachd iuchair dearbhaidh a th' agad",
        "Stremio auth key" to "Iuchair dearbhaidh Stremio",
        "Your credentials are protected by Android Keystore-backed encrypted preferences." to
            "Tha an dàta logaidh a-steach agad air a dhìon le roghainnean crioptaichte Android Keystore.",
        "● Connected" to "● Ceangailte",
        "No episode list supplied by the connected add-ons." to
            "Cha tug na tuilleadain ceangailte liosta de dh'earrannan seachad.",
        "No episodes match this watched filter." to "Chan eil earrannan a' freagairt ris a' chriathrag seo.",
        "Sources are supplied by your installed add-ons and checked when selected." to
            "Tha tùsan gan solarachadh leis na tuilleadain stàlaichte agus gan sgrùdadh nuair a thèid an taghadh.",
        "Available streams" to "Sruthan rim faighinn",
        "Allow peer-to-peer torrent streaming (your IP is visible to peers)" to
            "Ceadaich sruthadh torrent P2P (chì co-aoisean an seòladh IP agad)",
        "Allow peer-to-peer torrent streaming" to "Ceadaich sruthadh torrent P2P",
        "Find streams supplied by your installed add-ons." to
            "Lorg sruthan bho na tuilleadain stàlaichte agad.",
        "Enable P2P above before Play or Download" to "Cuir P2P an comas gu h-àrd mus cluich no luchdaich thu a-nuas",
        "Native torrent engine unavailable in this APK" to "Chan eil an t-einnsean torrent dùthchasach ri fhaighinn san APK seo",
        "Discover something to watch" to "Lorg rudeigin ri choimhead",
        "No featured titles yet. Open Discover or refresh your installed add-ons." to
            "Chan eil tiotalan molaidh ann fhathast. Fosgail Lorg no ùraich na tuilleadain agad.",
        "Saved on this device" to "Air a shàbhaladh air an inneal seo",
        "Search your add-ons" to "Rannsaich na tuilleadain agad",
        "Search movies and series" to "Rannsaich filmichean is sreathan",
        "No matching titles from the installed add-ons." to
            "Cha deach tiotalan a lorg sna tuilleadain stàlaichte.",
        "Discover movies and series" to "Lorg filmichean is sreathan",
        "Your Stremio library" to "Leabharlann Stremio agad",
        "Your library is empty or has not synced yet." to
            "Tha do leabharlann falamh no cha deach a sioncronachadh fhathast.",
        "Stremio settings" to "Roghainnean Stremio",
        "Search add-ons" to "Rannsaich tuilleadain",
        "↻ Refresh" to "↻ Ùraich",
        "Protected official add-on" to "Tuilleadan oifigeil dìonta",
        "Show more add-ons" to "Seall barrachd thuilleadan",
        "Install using a configured manifest URL" to "Stàlaich le URL manifest rèitichte",
        "HTTPS add-on manifest URL" to "URL manifest HTTPS an tuilleadain",
        "Preview manifest" to "Ro-shealladh a' mhanifest",
        "US channels" to "Seanailean nan SA",
        "← All US channels" to "← Gach seanail nan SA",
        "Global Free TV · United States" to "Global Free TV · Na Stàitean Aonaichte",
        "United States · Global Free TV" to "Na Stàitean Aonaichte · Global Free TV",
        "Available channel sources" to "Tùsan sheanailean rim faighinn",
        "Allow this unencrypted HTTP source" to "Ceadaich an tùs HTTP seo gun chrioptachadh",
        "Change sources in the player if one is unavailable." to
            "Atharraich tùsan sa chluicheadair mura h-eil fear dhiubh ri fhaighinn.",
        "Open on Global Free TV" to "Fosgail air Global Free TV",
        "Search US channels" to "Rannsaich seanailean nan SA",
        "No US channels currently listed" to "Chan eil seanailean nan SA air an liostadh an-dràsta",
        "Open Global Free TV · United States" to "Fosgail Global Free TV · Na Stàitean Aonaichte",
        "Persistent downloads" to "Luchdaidhean a-nuas maireannach",
        "Transfers continue while you leave this screen. Paused/interrupted tasks can resume after reopening the app." to
            "Leanaidh gluasadan às dèidh dhut an sgrìn seo fhàgail. Gabhaidh gnìomhan air stad ath-thòiseachadh nuair a dh'fhosglas tu an aplacaid a-rithist.",
        "No downloads folder selected" to "Cha deach pasgan luchdaidhean a-nuas a thaghadh",
        "Choose downloads folder" to "Tagh pasgan luchdaidhean a-nuas",
        "Choose a writable folder on the phone or an attached storage device. Existing downloads keep their original folder when you change this default." to
            "Tagh pasgan a ghabhas sgrìobhadh air a' fòn no air inneal stòraidh ceangailte. Fuirichidh luchdachaidhean a-nuas a th' ann sna pasganan tùsail aca.",
        "No downloads yet. Choose Play or Download on a Stremio stream card to add a saved download." to
            "Chan eil luchdadh a-nuas ann fhathast. Tagh Cluich no Luchdaich a-nuas air sruth Stremio airson fear a shàbhaladh.",
        "Android 15 limits background data-sync services to six hours per 24 hours. If the system pauses a transfer, reopen PersonalMedia and select Resume. Torrent downloads require P2P consent and do not run concurrently with torrent playback." to
            "Tha Android 15 a' cuingealachadh seirbheisean sioncronachaidh cùl-fhiosrachaidh gu sia uairean gach 24 uairean. Ma chuireas an siostam gluasad air stad, fosgail PersonalMedia a-rithist agus tagh Lean air. Feumar cead P2P airson torrentan a luchdadh a-nuas; cha ruith iad aig an aon àm ri cluich torrent.",
        "Download storage" to "Stòradh luchdaidhean a-nuas",
        "Enable download notifications" to "Cuir brathan luchdaidh a-nuas an comas",
        "Pause all" to "Cuir stad air a h-uile gin",
        "Resume all" to "Lean air a h-uile gin",
        "Restart" to "Tòisich a-rithist",
        "Check file" to "Thoir sùil air an fhaidhle",
        "Delete file" to "Sguab às am faidhle",
        "Delete permanently" to "Sguab às gu buan",
        "Keep file" to "Cùm am faidhle",
        "Keep" to "Cùm",
        "Remove record" to "Thoir air falbh an clàr",
        "Show more downloads" to "Seall barrachd luchdaidhean a-nuas",
        "Permanently delete this saved media file from the chosen storage folder?" to
            "A bheil thu airson am faidhle meadhain seo a sguabadh às gu buan às a' phasgan taghte?",
        "Remove from the queue? Files already saved on storage will NOT be deleted." to
            "Thoir air falbh às a' chiudha? CHA tèid faidhlichean a chaidh a shàbhaladh a sguabadh às.",
        "T405 WI-FI TUNER" to "GLACADAIR WI-FI T405",
        "The device must be on the tuner Wi-Fi, or able to reach its IP address." to
            "Feumaidh an t-inneal a bhith air Wi-Fi a' ghlacadair no ruigsinneachd air an t-seòladh IP aige.",
        "Tuner IPv4" to "IPv4 a' ghlacadair",
        "Control UDP" to "Smachd UDP",
        "Receive UDP" to "Faigh UDP",
        "Frequency MHz" to "Tricead MHz",
        "FREEVIEW GUIDE" to "IÙL FREEVIEW",
        "No saved Freeview services yet. Scan while connected to your T405." to
            "Chan eil seirbheisean Freeview sàbhailte ann fhathast. Dèan sgan fhad 's a tha T405 ceangailte.",
        "LIVE TV  /  AUGUST T405" to "TBH BEÒ  /  AUGUST T405",
        "● LIVE" to "● BEÒ",
        "Not reported" to "Gun fhiosrachadh",
        "TS rate" to "Ìre TS",
        "T405 TUNER SETTINGS" to "ROGHAINNEAN GLACADAIR T405",
        "LIVE TV" to "TBH BEÒ",
        "READY WHEN YOU ARE" to "DEISEIL NUAIR A THA THUSA",
        "Programme information not yet broadcast" to "Cha deach fiosrachadh a' phrògraim a chraoladh fhathast",
        "Choose a saved channel in the Sky guide below to watch live Freeview television" to
            "Tagh seanail shàbhailte ann an iùl Sky gu h-ìosal gus Freeview beò fhaicinn",
        "○ Tuner disconnected" to "○ Glacadair gun cheangal",
        "● Socket ready · no control reply" to "● Socaid deiseil · gun fhreagairt smachd",
        "● Tuner replied" to "● Fhreagair an glacadair",
        "● Freeview transport receiving" to "● A' faighinn sruth Freeview",
        "○ Disconnected" to "○ Gun cheangal",
        "● Transport receiving" to "● A' faighinn sruth",
        "● Socket ready" to "● Socaid deiseil",
        "● Guardian" to "● Neach-dìona",
        "○ Guardian unavailable" to "○ Neach-dìona gun ruigsinneachd",
        "⚙ Tuner settings" to "⚙ Roghainnean a' ghlacadair",
        "✕ Close settings" to "✕ Dùin roghainnean",
        "✕ Cancel scan" to "✕ Sguir den sgan",
        "Probe" to "Deuchainn",
        "Check Android Wi-Fi route" to "Sgrùd slighe Wi-Fi Android",
        "Copy T405 diagnostics" to "Dèan lethbhreac de dh'fhiosrachadh T405",
        "Connect" to "Ceangail",
        "Tune" to "Gleus",
        "Scan EU UHF" to "Sgan UHF na h-Eòrpa",
        "← Earlier" to "← Nas tràithe",
        "Later +4h" to "Nas fhaide air adhart +4u",
        "Tomorrow" to "A-màireach",
        "Not checked" to "Gun sgrùdadh",
        "Locked" to "Glaiste",
        "No lock" to "Gun ghlas",
        "No broadcast subtitles detected" to "Cha deach fo-thiotalan craolaidh a lorg",
        "Tuner disconnected — reconnect before tuning." to "Tha an glacadair gun cheangal — ath-cheangail e mus gleus thu.",
        "Waiting for fresh transport-stream packets from the tuner." to
            "A' feitheamh ri pacaidean srutha ùra bhon ghlacadair.",
        "Transport packets received · waiting for PAT (PID 0)." to
            "Pacaidean srutha air am faighinn · a' feitheamh ri PAT (PID 0).",
        "PAT received · waiting for this service's PMT PID." to
            "PAT air fhaighinn · a' feitheamh ri PMT PID na seirbheise seo.",
        "PMT received · looking for audio/video elementary PIDs." to
            "PMT air fhaighinn · a' lorg PID fuaime is bhidio.",
        "⌕ Search Sky guide channels or programmes…" to "⌕ Rannsaich seanailean no prògraman iùil Sky…",
        "No downloads yet." to "Chan eil luchdadh a-nuas ann fhathast.",
        "No results" to "Cha deach toradh a lorg",
        "Error" to "Mearachd",
        "Success" to "Soirbheachas",
        "Off" to "Dheth",
        "On" to "Air"
    )

    fun translate(english: String, code: String): String {
        if (code != "gd") return english
        gd[english]?.let { return it }
        // Preserve punctuation and spacing (including many icon-prefixed buttons).
        val trimmed = english.trim()
        gd[trimmed]?.let { return english.replace(trimmed, it) }
        Regex("(\\d+) seasons? +[·•] +(\\d+) episodes?").matchEntire(trimmed)?.let { match ->
            val (seasons, episodes) = match.destructured
            return "$seasons ${if (seasons == "1") "ràith" else "ràithean"} · " +
                "$episodes ${if (episodes == "1") "earrann" else "earrannan"}"
        }
        Regex("(\\d+) local files?").matchEntire(trimmed)?.let { match ->
            val count = match.groupValues[1]
            return "$count ${if (count == "1") "faidhle ionadail" else "faidhlichean ionadail"}"
        }
        Regex("(\\d+) items?").matchEntire(trimmed)?.let { match ->
            val count = match.groupValues[1]
            return "$count ${if (count == "1") "nì" else "nithean"}"
        }
        Regex("(\\d+) add-ons").matchEntire(trimmed)?.let { match ->
            return "${match.groupValues[1]} tuilleadain"
        }
        Regex("(\\d+) installed").matchEntire(trimmed)?.let { match ->
            return "${match.groupValues[1]} stàlaichte"
        }
        Regex("(\\d+) saved services").matchEntire(trimmed)?.let { match ->
            return "${match.groupValues[1]} seirbheisean sàbhailte"
        }
        Regex("(\\d+) titles · refreshes in background").matchEntire(trimmed)?.let { match ->
            return "${match.groupValues[1]} tiotalan · ag ùrachadh sa chùlaibh"
        }
        Regex("(\\d+) add-ons in (installed|official|community) · (\\d+) shown").matchEntire(trimmed)?.let { match ->
            val group = when (match.groupValues[2]) {
                "installed" -> "stàlaichte"
                "official" -> "oifigeil"
                else -> "coimhearsnachd"
            }
            return "${match.groupValues[1]} tuilleadain ($group) · ${match.groupValues[3]} air an sealltainn"
        }
        Regex("Official history imported · (\\d+) series · (\\d+) local change\\(s\\) queued").matchEntire(trimmed)?.let { match ->
            val count = match.groupValues[1]
            return "Eachdraidh oifigeil air a h-ion-phortadh · $count ${if (count == "1") "sreath" else "sreathan"} · ${match.groupValues[2]} atharrachaidhean ionadail sa chiudha"
        }
        Regex("Official history not imported · (\\d+) local change\\(s\\) queued").matchEntire(trimmed)?.let { match ->
            return "Cha deach eachdraidh oifigeil ion-phortadh · ${match.groupValues[1]} atharrachaidhean ionadail sa chiudha"
        }
        Regex("Scanned (\\d+) saved folder\\(s\\)\\. (.+)").matchEntire(trimmed)?.let { match ->
            return "Chaidh ${match.groupValues[1]} pasganan sàbhailte a sganadh. ${translate(match.groupValues[2], "gd")}"
        }
        Regex("([^:]+): (\\d+) matching files, (\\d+) new, (\\d+) refreshed · (\\d+) possibly missing record\\(s\\); scan again to confirm before cleanup")
            .matchEntire(trimmed)?.let { match ->
                val category = gd[match.groupValues[1]] ?: match.groupValues[1]
                return "$category: ${match.groupValues[2]} faidhlichean co-fhreagarrach, ${match.groupValues[3]} ùr, " +
                    "${match.groupValues[4]} air an ùrachadh · ${match.groupValues[5]} clàran 's dòcha a dhìth; " +
                    "sgan a-rithist gus dearbhadh mus glanar iad"
            }
        Regex("(\\d+) completed · (\\d+) active · (\\d+) queued · (\\d+) paused").matchEntire(trimmed)?.let { match ->
            return "${match.groupValues[1]} crìochnaichte · ${match.groupValues[2]} gnìomhach · ${match.groupValues[3]} sa chiudha · ${match.groupValues[4]} air stad"
        }
        Regex("(\\d+) shown · (\\d+) unique guide channels · (\\d+) secondary/duplicate services hidden · (\\d+) current/upcoming listings")
            .matchEntire(trimmed)?.let { match ->
                return "${match.groupValues[1]} air an sealltainn · ${match.groupValues[2]} seanailean sònraichte · " +
                    "${match.groupValues[3]} seirbheisean dùblaichte falaichte · ${match.groupValues[4]} prògraman làithreach / ri teachd"
            }
        return when {
            Regex("\\d+ films").matches(trimmed) ->
                trimmed.replace("films", if (trimmed.startsWith("1 ")) "film" else "filmichean")
            Regex("\\d+ series").matches(trimmed) ->
                trimmed.replace("series", if (trimmed.startsWith("1 ")) "sreath" else "sreathan")
            Regex("\\d+ episodes").matches(trimmed) ->
                trimmed.replace("episodes", if (trimmed.startsWith("1 ")) "earrann" else "earrannan")
            Regex("\\d+ Episodes").matches(trimmed) ->
                trimmed.replace("Episodes", if (trimmed.startsWith("1 ")) "Earrann" else "Earrannan")
            english.startsWith("Metadata: ") -> english.replaceFirst("Metadata: ", "Meata-dàta: ")
            english.startsWith("Resume at ") -> english.replaceFirst("Resume at ", "Lean air aig ")
            english.startsWith("▶ Resume · ") -> english.replaceFirst("Resume", "Lean air")
            english.startsWith("Available editions · ") ->
                english.replaceFirst("Available editions", "Tionndaidhean rim faighinn")
            english.startsWith("🎬 Movies · ") -> english.replaceFirst("Movies", "Filmichean")
            english.startsWith("📺 TV Shows · ") -> english.replaceFirst("TV Shows", "Prògraman Tbh")
            english.startsWith("▶ ") && english.endsWith(" Episodes") ->
                english.replace("Episodes", if (Regex("▶ 1 Episodes").matches(english)) "Earrann" else "Earrannan")
            english.startsWith("Movies · ") -> english.replaceFirst("Movies", "Filmichean")
            english.startsWith("TV Shows · ") -> english.replaceFirst("TV Shows", "Prògraman Tbh")
                .replace(Regex("(\\d+) series$")) { m ->
                    "${m.groupValues[1]} ${if (m.groupValues[1] == "1") "sreath" else "sreathan"}"
                }
            english.startsWith("Music · ") -> english.replaceFirst("Music", "Ceòl")
            english.startsWith("Pictures · ") -> english.replaceFirst("Pictures", "Dealbhan")
            english.startsWith("Favourites · ") -> english.replaceFirst("Favourites", "Na rudan as fheàrr leat")
            english.startsWith("YOUR LIBRARY / ") -> english.replaceFirst("YOUR LIBRARY", "DO LEABHARLANN")
                .replace("MOVIE", "FILM").replace("MUSIC", "CEÒL").replace("PICTURE", "DEALBH")
            english.endsWith("YOUR COLLECTION") -> english.replace("MOVIE", "FILM")
                .replace("TV SHOW", "PRÒGRAM TBH").replace("YOUR COLLECTION", "DO CHRUINNEACHADH")
            english.startsWith("＋ Choose ") && english.endsWith(" folder") ->
                "＋ Tagh pasgan " + (gd[english.removePrefix("＋ Choose ").removeSuffix(" folder")] ?: english.removePrefix("＋ Choose ").removeSuffix(" folder"))
            english.startsWith("Import ") && english.endsWith(" files") ->
                "Ion-phortaich faidhlichean " + (gd[english.removePrefix("Import ").removeSuffix(" files")] ?: english.removePrefix("Import ").removeSuffix(" files"))
            english.startsWith("Index contains ") -> english.replaceFirst("Index contains", "Tha san chlàr-innse")
            else -> english
        }
    }
}

/**
 * A source-compatible Compose Text front-end: every current screen automatically
 * renders supported UI labels in Gaelic while data identifiers remain in English.
 * The Material3 implementation is called explicitly to avoid recursion.
 */
@Composable
internal fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    androidx.compose.material3.Text(
        text = PmI18n.translate(text, LocalPmUiLanguage.current),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}
