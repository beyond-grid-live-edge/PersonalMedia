# PersonalMedia — Android

**Gàidhlig (Alba)** · [English README](README.md) · [Stiùireadh ceadachais sa Ghàidhlig](LICENSE.gd.md) · [GNU GPLv3 — an teacsa laghail Beurla](LICENSE)

Tha **PersonalMedia airson Android** na phort Android dùthchasach, neo-eisimeileach de dh'aplacaid PersonalMedia airson HarmonyOS. Tha e a' toirt còmhla leabharlann mheadhanan ionadail, Stremio, Freeview na RA, telebhisean eadar-lìn nan SA agus luchdadh a-nuas meadhanan a ghabhas leantainn.

**An còd tùsail làithreach:** STR-7D (Samhain 2026). Kotlin/Jetpack Compose agus Media3, le JNI/C++ airson glacadair August, MPV dùthchasach agus sruthan libtorrent le pìosan air an dearbhadh le hash. **Seo còd leasachaidh; chan e sgaoileadh APK foillsichte a tha seo.**

## Feartan

| Pàirt | Na tha san tionndadh Android |
| --- | --- |
| Meadhanan ionadail | Filmichean, prògraman Tbh, ceòl agus dealbhan; taghadh phasganan le SAF, cruinneachaidhean, tionndaidhean fhilmichean, ràithean agus earrannan Tbh, roghainnean as fheàrr leat, eachdraidh coimhead, agus ath-sganadh/càradh sàbhailte |
| Stremio | Stiùireadh cunntais is thuilleadan, rùrachadh, liostaichean iomlan de shruthan, leabharlann is sioncronachadh coimhead, HTTPS tro MPV dùthchasach agus sruthan torrent le dearbhadh |
| Manaidsear luchdaidhean a-nuas | Ciudha maireannach crioptaichte, pasgan stòraidh taghte, leantainn air adhart tro HTTP Range, sàbhaladh phìosan torrent dearbhte, stad/lean air adhart, ath-bheothachadh agus sgrùdaidhean |
| Tbh beò na RA | Glacadair Wi-Fi August DVB-T405, sruth MPEG-TS dùthchasach, iùl EIT coltach ri Sky agus Neach-dìona a' cheangail |
| Tbh beò nan SA | Liosta sheanailean **Global Free TV airson nan SA a-mhàin**, rannsachadh, seanalan as fheàrr leat, cluich Media3 agus tùsan eile (Source 1/2/msaa.) ma dh'fhàillig fear; **chan eil iùl phrògraman nan SA ann** |
| Cluich is dealbhan | Roghainnean coitcheann, cuimhne air fuaim/fo-thiotalan (Audio/CC) airson gach tiotal, agus meata-dàta roghainneil bho TMDb/OMDb/Wikipedia/TVmaze |
| Aisig is taic | Lethbhreacan-glèidhidh JSON so-ghiùlain, co-aonachadh agus neo-dhèanamh thaghte, agus fiosrachadh breithneachaidh gun dàta prìobhaideach |

### Dearbhadh

A rèir deuchainnean a rinn an leasaiche air **Honor 90**, tha bhidio **agus fuaim** libtorrent ag obair, a bharrachd air gluasad gu àite eile sa bhidio, stad a chur air torrent agus atharrachadh tùs, luchdadh a-nuas torrent Backrooms a chaidh a chrìochnachadh, agus cluich sheanailean nan SA le Media3. Tha cuid de fheartan STR-7 nas ùire — gu sònraichte lethbhreacan-glèidhidh/aisig agus cuimhne roghainnean fuaime airson gach tiotal — fhathast feumach air deuchainnean sònraichte air inneal. **Chan eil còd a bhith an làthair a' gealltainn gu bheil feart deiseil airson sgaoileadh.**

## Innealan a thathar a' moladh

Thathar a' moladh **tablaid Android no inneal Android eile le sgrìn mhòr**, gu h-àraidh ann am modh còmhnard. Tha PersonalMedia air a dhealbhadh **gu sònraichte airson tablaidean**, mar an tionndadh HarmonyOS: tha iùl Freeview coltach ri Sky, sgeilpichean na leabharlainn, catalog Stremio agus na smachdan cluiche a' faighinn buannachd bho sgrìn nas motha.

**Chan e fònaichean am prìomh thargaid airson a' chruth.** Chaidh feartan bunaiteach sruthaidh agus luchdaidh a-nuas a dhearbhadh air Honor 90, ach faodaidh sgrìnean beaga dìreach a bhith caran dùmhail; cha deach an aon ìre de dh'atharrachadh cruth airson fònaichean a dhèanamh. **Chan eil seo a' ciallachadh nach urrainn don aplacaid ruith air fònaichean.**

## Togail

Tha **an còd tùsail ciad-phàrtaidh gu lèir** san ionad-stòraidh seo, ach **chan eil** leabharlannan MPV dùthchasach, tasglannan libtorrent/Boost/OpenSSL airson ABI sònraichte, teisteanasan soidhnidh inneil no dàta na h-aplacaid ann. Feumar eisimeileachdan a rèiteachadh mus gabh APK le taic torrent a thogail à clon ùr.

**[Stiùireadh airson clon ùr agus togail (Beurla)](docs/BUILDING.md)** · **[Ailtireachd (Beurla)](docs/ARCHITECTURE.md)** · **[Eachdraidh nan clachan-mìle (Beurla)](docs/history/README.md)**

Airson pròiseact Windows ARM64 a th' ann mar-thà, far an deach na h-eisimeileachdan neòil a thoirt a-steach:

~~~powershell
cd "C:\Users\WindowsVM\Downloads\PersonalMedia"
.\tools\STR5C-Build-With-Cloud-Libraries.ps1 -Arm64Only
~~~

Bu chòir gum bi an APK aig `app/build/outputs/apk/debug/app-debug.apk`. **Na stàlaich APK seann-fhasanta ma dh'fhàillig an togail.** Cùm an dearbh-aithne soidhnidh is pacaid thùsail agus stàlaich **thairis air** an aplacaid a th' ann; dh'fhaodadh dì-stàladh na seanailean sàbhailte, an leabharlann, an ciudha agus dàta a' chunntais a chall.

## Cruth an ionaid-stòraidh

- `app/src/main/java/com/personalmedia/android/` — eadar-aghaidh Compose, leabharlann ionadail, Stremio, Media3, luchdadh a-nuas, roghainnean is lethbhreacan-glèidhidh.
- `app/src/main/cpp/` — drochaid JNI, dealbhachadh MPV dùthchasach, einnsean torrent le dearbhadh agus glacadair DVB-T405.
- `app/src/test/` agus `tests/` — deuchainnean ais-cheumnachaidh Kotlin agus CMake/dùthchasach.
- `tools/` — togail ARM64, toirt a-steach phàirtean dùthchasach, ullachadh MPV, tòiseachadh Gradle wrapper agus dearbhadh.
- `.github/workflows/personalmedia-android-libtorrent.yml` — sruth-obrach airson eisimeileachdan dùthchasach a thogail le làimh.
- `docs/history/` — notaichean STR/LTV bho chlachan-mìle roimhe.

## Tèarainteachd, ceadan is ceadachas

ID na h-aplacaid: `com.personalmedia.android`. Tha an leabharlann a' cleachdadh **Storage Access Framework** aig Android seach ceadan farsaing air an t-siostam fhaidhlichean. Chan eil teisteanasan Stremio no ceanglaichean srutha soidhnichte ann an lethbhreacan-glèidhidh so-ghiùlain; **chan eil lethbhreacan JSON crioptaichte**, agus faodaidh tiotalan meadhain is URIan sgrìobhainnean a bhith annta. Faodaidh co-aoisean torrent an seòladh IP agad fhaicinn. Na cleachd ach meadhanan agus solaraichean a tha ceadaichte dhut.

Tha ceadachasan is cumhaichean fa leth aig MPV/FFmpeg dùthchasach, libtorrent, Boost, OpenSSL agus solaraichean Tbh/meata-dàta. Thoir sùil orra mus dèan thu ath-sgaoileadh. Dh'fhaodadh cuingeachaidhean no mearachdan seann-fhasanta a bhith ann an notaichean nan clachan-mìle; airson an tionndaidh seo, cleachd an README làithreach agus an stiùireadh togail.

## Luchdaich a-nuas eisimeileachdan libtorrent Android ARM64

**[Luchdaich a-nuas pasgan dearbhte libtorrent / Boost / OpenSSL ARM64 (artifact GitHub Actions)](https://github.com/beyond-grid-live-edge/PersonalMedia/actions/runs/43246458194/artifacts/13958421987)**

Seo **pasgan nan eisimeileachdan dùthchasach**, **chan e APK Android**. Is e an artifact `PersonalMedia-libtorrent-arm64-v8a` a chaidh a chruthachadh le [sruth-obrach soirbheachail #2](https://github.com/beyond-grid-live-edge/PersonalMedia/actions/runs/43246458194) air **12 Samhain 2026**, airson Android **arm64-v8a / API 26 / c++_static**. Thig an artifact gu crìch air **26 Samhain 2026 aig 15:31 UTC**; feumaidh tu logadh a-steach do GitHub le cead air an ionad-stòraidh phrìobhaideach sin gus a luchdadh a-nuas.

Nuair a thig e gu crìch, **[ruith sruth-obrach togail libtorrent a-rithist](https://github.com/beyond-grid-live-edge/PersonalMedia/actions/workflows/personalmedia-android-libtorrent.yml)** le `targets = arm64-only`, agus luchdaich a-nuas an artifact ùr bhon ruith shoirbheachail.

Gus an ZIP **a-muigh** a chaidh a luchdadh a-nuas a thoirt a-steach do phròiseact PersonalMedia a th' ann agus APK ARM64 le torrent a thogail:

~~~powershell
.\tools\STR5C-Install-GitHub-Dependencies.ps1 -ArtifactZip "$env:USERPROFILE\Downloads\PersonalMedia-libtorrent-arm64-v8a.zip" -Arm64Only -Build
~~~

Nì am prògram toirt a-steach sgrùdadh air SHA-256 an ZIP a-staigh agus air na tasglannan dùthchasach airson gach ABI. Ma tha na leabharlannan ann mu thràth, tog a-rithist le `.\tools\STR5C-Build-With-Cloud-Libraries.ps1 -Arm64Only`. Faic [BUILDING.md](docs/BUILDING.md) airson na tha a dhìth agus ullachadh MPV.

## Ceadachas còd-fhosgailte

**Còraichean glèidhte © 2026 Shaun Hargadon.**

Tha an còd tùsail **PersonalMedia Android** san ionad-stòraidh seo a bhuineas do Shaun Hargadon fo **GNU General Public License, tionndadh 3.0 a-mhàin (`GPL-3.0-only`)**. Leugh [an teacsa laghail Beurla iomlan](LICENSE). Feumaidh atharrachaidhean agus ath-sgaoileadh de stuth fo GPL cumail ri cumhaichean a' cheadachais sin.

Tha [stiùireadh neo-oifigeil mun cheadachas sa Ghàidhlig](LICENSE.gd.md) ann airson cuideachadh le tuigse; **chan eil e an àite an teacsa Beurla** agus chan atharraich e an ceadachas.

**Cha toir** ceadachas a' phròiseict seo ceadachas ùr no còraichean a bharrachd do phàirtean treas-phàrtaidh, a' gabhail a-steach MPV/FFmpeg, libtorrent, Boost, OpenSSL, leabharlannan Android, sruthan Tbh, dealbhan no meata-dàta. Tha na ceadachasan is cumhaichean seirbheis aca fhèin fhathast an sàs. Chan eil pròiseact HarmonyOS air a cheadachadh le ceadachas an ionaid-stòraidh Android seo dìreach air sgàth 's gun deach am port Android fhoillseachadh.
