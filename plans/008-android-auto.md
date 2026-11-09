# Plan 008 — Supporto Android Auto (+ dichiarazione AAOS)

**Base commit:** `9a69b8bd` (develop, 2026-10-26) · **Categoria:** feature + robustezza · **Sforzo:** L (4-6 giornate) · **Rischio:** medio-alto
**Stato:** COMPLETATO (2026-11-09) — F0.1–F0.9 + Fasi 1-6 + fix review P1/P2/P3; 3 flavor compilati; verifica in auto (DHU/emulatore AAOS) ancora da fare
**Decisioni chiuse:** 2026-11-09 (v. §4)

## Risposta breve

**Sì, è possibile, e l'app è già a metà strada.** L'impalcatura richiesta da Android Auto esiste
(`auto_app_desc.xml`, meta-data `com.google.android.gms.car.application`, `MediaService`
Media3 `MediaLibraryService` in tutti i flavor, albero browsable condiviso in `main`,
`AutomotiveRepository`). Non serve **nessuna dipendenza nuova** né una UI custom: per Android
Auto l'unica "GUI" controllata dall'app è l'**albero browsable** (tab di primo livello, titoli,
sottotitoli, copertine) più i comandi di sessione (shuffle, ripresa coda). I template grafici
(Car App Library) servono solo ad AAOS e restano fuori perimetro.

Oggi però la compatibilità è **dichiarata ma non affidabile**: ci sono difetti che lasciano l'auto
in caricamento indefinito, un percorso "cartelle" semanticamente sbagliato, un possibile crash nel
processo del servizio e un flavor (`notquitemy`) senza browsing. Il piano è quindi ~75%
correzione/consolidamento, ~25% presentazione e funzioni specifiche dell'auto.

## 1. Cosa esiste già

| Elemento | Dove | Stato |
|---|---|---|
| Dichiarazione Auto ("media app") | `res/xml/auto_app_desc.xml` + meta-data `com.google.android.gms.car.application` | ✅ |
| Servizio `MediaLibraryService` | `service/MediaService.kt` (uno per flavor) | ✅ |
| Azioni manifest (`MediaLibraryService`, `MediaBrowserService`, `MEDIA_PLAY_FROM_SEARCH`) | `AndroidManifest.xml` | ✅ |
| Sessione + callback browsing | `media3-session` 1.5.1 | ✅ |
| Albero browsable | `service/MediaBrowserTree.kt` (in `main`, tutti i flavor) | ✅ tre tab localizzate (2026-11-09) |
| Callback sessione + shuffle | `service/MediaLibraryServiceCallback.kt` (in `main`, tutti i flavor) | ✅ |
| Ponte Subsonic → `LibraryResult` | `repository/AutomotiveRepository.java` (in `main`) | ⚠️ |
| Metadati di sessione per la coda | Room `SessionMediaItem` + DAO | ⚠️ |
| Ricerca in auto | `onSearch`/`onGetSearchResult` | ⚠️ |
| Flavor `notquitemy` (F-Droid) | `MediaService.kt` (senza Cast) | ✅ browsing dal 2026-11-09 (callback condiviso) |
| Stringhe dell'albero | risorse `auto_*` in `values`/`values-it` | ✅ (2026-11-09) |
| Ripresa coda dall'auto | `onPlaybackResumption` | ✅ (2026-11-09) |
| Nodo offline/download | `MediaBrowserTree` → `DownloadDao.getAllSync()` | ✅ (2026-11-09) |
| Dichiarazione AAOS | meta-data `com.android.automotive`, `uses-feature automotive` (flavor `tempo`/`notquitemy`) | ✅ (2026-11-09) |

`MediaBrowserTree.kt` e `MediaLibraryServiceCallback.kt` vivono in `main` dal 2026-11-09: `tempo` e
`play` avevano copie byte-identiche, `notquitemy` un callback proprio. Resta una copia sola.

## 2. Difetti da correggere prima di dichiarare la compatibilità (P0)

### F0.1 — `ListenableFuture` mai completati → spinner infinito in auto
In `AutomotiveRepository` il `SettableFuture` viene valorizzato solo nel ramo "risposta buona".
Nei metodi `getIndexes`, `getDirectories`, `getArtistAlbum`, `getPlaylistSongs`, `getMadeForYou`,
`search` manca del tutto il ramo `else`; in `getStarredAlbums` l'`onFailure` fa solo `Log.e`.
Risultato: l'auto attende fino al timeout e mostra un errore al posto del contenuto.
**Fix:** helper privati (`completeError`, `completeException`) e completamento garantito in ogni
ramo; per gli errori di rete `LibraryResult.RESULT_ERROR_IO` invece di `RESULT_ERROR_BAD_VALUE`.

### F0.2 — Crash in `MediaBrowserTree.getItems`
`toAdd.indexOfFirst { … }` può restituire `-1` e `toAdd.subList(-1, size)` lancia
`IndexOutOfBoundsException` nel processo del servizio. `sessionMediaItem.timestamp!!` è NPE
potenziale.
**Fix:** guardie, fallback all'item originale, deduplica, `Log.w`.

### F0.3 — NPE sul bottone shuffle in auto
`MediaLibraryServiceCallback.onCustomCommand` chiama `session.mediaNotificationControllerInfo!!`:
se il comando arriva dal controller automotive e non c'è notifica attiva → NPE.
**Fix:** memorizzare il controller della notifica in `onConnect`/`onPostConnect` e aggiornare il
layout solo se esiste. Rinominare i comandi da `android.media3.session.demo.SHUFFLE_*` a
`com.cappielloantonio.tempo.SHUFFLE_*`.

### F0.4 — Metadati di sessione cancellati troppo presto e query non deterministica
`MediaService.releasePlayer()` chiama `automotiveRepository.deleteMetadata()`: se il servizio
viene ricreato mentre l'auto ha una coda costruita, `getItems` non trova la riga `SessionMediaItem`
e i brani vengono **silenziosamente scartati**. Inoltre `insertAll` usa `IGNORE` con PK
`autoGenerate`, quindi lo stesso `id` può avere più righe e `get(id)` (senza `ORDER BY`) ne
restituisce una arbitraria.
**Fix:** `SELECT … WHERE id = :id ORDER BY timestamp DESC LIMIT 1` (nessuna migrazione Room),
fallback in `getItems`, pulizia con TTL (D5) invece di `deleteAll()` a ogni `onDestroy`.

### F0.5 — Servizio raggiungibile prima del login
Il servizio è `exported` e Android Auto può collegarsi quando l'app non è configurata:
`Preferences.getServerId()` è `null`, il client Subsonic viene costruito con URL vuoto e
`getRecentlyPlayedSongs(getServerId(), …)` interroga Room con `null`.
**Fix:** guardia unica (`getServerId() == null || getServer() == null`) che restituisce un albero
minimo con un solo item non browsable/non playable ("Configura il server nell'app"), localizzato;
messaggio dedicato anche per server irraggiungibile.

### F0.6 — Leak di observer nella cronologia
`getRecentlyPlayedSongs` usa `observeForever` e rimuove l'observer da una **seconda** istanza di
`LiveData` (`chronologyDao.getLastPlayed(...)` chiamato due volte): la rimozione non ha effetto e
l'observer resta attaccato, moltiplicandosi a ogni browsing.
**Fix:** una sola variabile locale, rimozione nel ramo corretto.

### F0.7 — Paging ignorato
`onGetChildren` e `onGetSearchResult` non usano `page`/`pageSize`. Con `getDirectories`
(migliaia di figli) la risposta è enorme e l'auto tronca o ritarda.
**Fix:** slicing della pagina nel callback (`Futures.transform` su `MoreExecutors.directExecutor()`).
Nota: i 60 risultati dichiarati da `onSearch` sono esatti per costruzione (`search3` con
20+20+20), quindi il conteggio resta invariato.

### F0.8 — Percorso "cartelle" rotto a livello artista
`getIndexes` restituisce gli artisti con `mediaId = INDEX_ID + artistId`; per quell'id
`getChildren` instrada su `getDirectories(DIRECTORY_ID, artistId)` — cioè `getMusicDirectory` con
un **id artista**: lista vuota o errore.
**Fix:** `INDEX_ID` → `getArtistAlbum(ALBUM_ID, artistId)`; verificare a mano i prefissi di ogni
livello (`FOLDER → INDEX → ARTIST → ALBUM → track`) contro il codice reale.

### F0.9 — "Made for you" non fa quello che dice
`MADE_FOR_YOU_ID` è mappato su `getStarredArtists`: mostra gli artisti preferiti, non un mix.
**Decisione D3:** nodo rimosso (un nodo che mente è peggio di un nodo in meno).

## 3. Fasi di intervento

### Fase 1 — Consolidamento flavor (tutti e tre) — **DONE 2026-11-09**
`MediaBrowserTree.kt` e `MediaLibraryServiceCallback.kt` non usano Cast né costanti di flavor:
spostarli in `app/src/main/java/com/cappielloantonio/tempo/service/` (2 file invece di 4) e
lasciare `MediaService.kt` per flavor (la differenza Cast è reale).
`notquitemy` abbandona `CustomMediaLibrarySessionCallback` interno e usa il callback condiviso:
guadagna browsing, shuffle e ricerca. Va riconciliata la logica `onPostConnect`/`setCustomLayout`
che oggi vive solo nel servizio `notquitemy`.
Aggiornare `AGENTS.MD` §6 e `docs/ARCHITECTURE.md` §6.

### Fase 2 — Presentazione: albero minimale `Home / Library / Downloads` — **DONE 2026-11-09**
- Primo livello (le tab in alto nell'auto) ridotto a tre nodi; playlist, radio, podcast e cartelle
  restano annidati dentro `Library`.
- Tutti i titoli in `values/strings.xml` (+ `values-it`; altre lingue quando disponibili): oggi
  sono hardcoded in inglese.
- `MEDIA_TYPE_FOLDER_ALBUMS` / `FOLDER_ARTISTS` / `FOLDER_PLAYLISTS` invece di `FOLDER_MIXED`
  ovunque; `setIsBrowsable(false)` + `setIsPlayable(true)` per le tracce.
- Seconda riga utile in auto: `setArtist`/`setAlbumTitle` sui brani, `setArtist` sugli album,
  `setGenre` solo dove aggiunge informazione.
- Ricerca: ordine artisti → album → brani, tetto per categoria (20+20+20; il dedupe non serve, le categorie sono disgiunte).

### Fase 3 — Nodo "Scaricati" (offline) — **DONE 2026-11-09**
- Fonte: `DownloadDao.getAllSync()` (filtra `download_state = 1`, ordinato per artista/album/
  disco/traccia; solo query, nessuna migrazione). La lettura avviene su un `Thread` dedicato dentro
  `AutomotiveRepository.getDownloads()` e il futuro viene completato in background: nessuna query
  Room sul main thread.
- Mappatura: `MappingUtil.mapDownloadedItems`/`mapDownloadedItem` usano l'`downloadUri` salvato nel
  record (fallback a `MusicUtil.getDownloadUri(id)`), così la chiave della cache Media3 non cambia
  tra sessioni; `mapDownload` resta invariato per la coda del telefono.
- Nodo browsable mappato su `MEDIA_TYPE_FOLDER_MIXED` con titolo localizzato (`auto_downloads`);
  i brani sono `MEDIA_TYPE_MUSIC`, playable, con `RequestMetadata.mediaUri` = URI offline.
- Se l'utente non ha download: un item non playable con messaggio (`auto_no_downloads`), non un
  errore.

### Fase 4 — Ripresa coda dall'auto (`onPlaybackResumption`) — **DONE 2026-11-09**
- `QueueDao.getAllSimple()` ora ha `ORDER BY track_order` (solo query, nessuna migrazione).
- `AutomotiveRepository.getPlaybackResumption()` costruisce `MediaSession.MediaItemsWithStartPosition`
  su un `Thread` dedicato: righe `Queue` (sono `Child`), indice dall'ultimo brano riprodotto
  (`QueueDao.getLastPlayed()`, clampato sui limiti della coda) e coda limitata a 100 elementi
  attorno all'indice (`MusicUtil.limitPlayableMedia` + `getPlayableMediaPosition`).
- Posizione in ms: **0**. La posizione nel brano non è persistita localmente (in `Queue` ci sono
  solo `last_play`/`playing_changed` come timestamp) e interrogare la play queue del server
  aggiungerebbe una dipendenza di rete al riavvio in auto: a freddo si riparte dall'inizio del
  brano corrente, come previsto dal fallback del piano.
- Senza server configurato il futuro restituisce `null` (nessuna ripresa): gli URI di streaming non
  sono costruibili. I brani scaricati restano raggiungibili dal nodo `Downloads`.
- `MediaLibraryServiceCallback.onPlaybackResumption` delega a `MediaBrowserTree`, che inoltra al
  repository: il servizio costruisce la coda da Room senza UI del telefono.

### Fase 5 — Copertine senza credenziali (D4) — **DONE 2026-11-09**
Obiettivo: l'host auto non riceve mai URL con `u`/`t`/`s`.
- `MusicUtil.getCoverArtUrl(id, size, withCredentials)` è l'unica fonte dell'URL `getCoverArt`: con
  `false` restano solo `v`, `c`, `size`, `id`; `CustomGlideRequest.createUrl` delega con `true`
  (Glide continua a usare l'URL autenticato).
- Item esposti all'auto senza credenziali: `MappingUtil.mapMediaItems`/`mapDownloadedItems` hanno
  l'overload `publicArtwork` usato da `AutomotiveRepository` (browse, ricerca, download, ripresa);
  gli artwork inline di album/artisti/brani/cartelle/podcast passano da
  `MappingUtil.getPublicArtworkUri`; `SessionMediaItem.getMediaItem()` (metadati di sessione usati
  solo dall'auto) usa `MusicUtil.getCoverArtUri`.
- `DownloadUtil.buildArtworkBitmapLoader()` (`DataSourceBitmapLoader` su un executor dedicato) è
  impostato col `.setBitmapLoader(...)` della sessione in tutti e tre i flavor; la
  `ResolvingDataSource` aggiunge `u`/`p`/`s`/`t` al volo **solo** per `getCoverArt` sul server
  configurato (`MusicUtil.withCoverArtCredentials`), così l'artwork arriva all'host come `artworkData`.
  Nessuna dipendenza nuova (`media3-datasource` è già transitiva di `media3-exoplayer`).
- La coda del telefono e la notifica restano sugli URI autenticati: il BitmapLoader personalizzato
  li lascia passare invariati (nessuna doppia autenticazione).
- Gli `extras` dei soli item auto non contengono più la chiave `uri` (era l'URL di streaming con
  credenziali, trasmesso ai controller): `MappingUtil.buildMusicExtras(..., false)` sul percorso
  pubblico e `SessionMediaItem.getMediaItem()` senza `uri`. `requestMetadata.mediaUri` resta
  autenticato perché è l'URI che il player deve aprire.
- Fallback documentato: se un host ignora `artworkData` e carica direttamente l'`artworkUri`, la
  richiesta senza token risponde 401 e mostra il segnaposto. In quel caso si torna all'URI con
  credenziali solo per i `MediaItem` esposti all'auto, annotandolo in `docs/ARCHITECTURE.md`.
- Non verificato sul campo (DHU/emulatore AAOS): resa delle copertine e copertina della notifica.

### Fase 6 — Dichiarazione AAOS e distribuzione — **DONE 2026-11-09**
- `tempo` e `notquitemy` hanno un `AndroidManifest.xml` di flavor con
  `<meta-data android:name="com.android.automotive" android:resource="@xml/auto_app_desc"/>` e
  `<uses-feature android:name="android.hardware.type.automotive" android:required="false"/>`.
  `required="false"` mantiene l'APK installabile su telefono e su AAOS via sideload/F-Droid.
- `android:appCategory="audio"` sull'`<application>` in `main` (vale per tutti i flavor).
- ⚠️ **Vincolo Play (decisione D7):** i media app sono ammessi su Play solo nel track "Android
  Automotive OS only", dove `required` deve essere `true` o assente. Il flavor `play` **non**
  dichiara AAOS (nessun manifest di flavor) e resta nel solo track mobile: nessun artefatto AAOS
  su Play. `com.google.android.gms.car.application` (Android Auto) resta in `main` per tutti.
- Nessun nuovo permesso; `auto_app_desc.xml` non è stato rinominato (il nome è interno).

## 4. Decisioni chiuse (2026-11-09)

| ID | Domanda | Scelta |
|---|---|---|
| D1 | Perimetro | **Android Auto + dichiarazione AAOS** (niente Car App Library, niente template UI) |
| D2 | Flavor | **Tutti e tre**, con `MediaBrowserTree`/`MediaLibraryServiceCallback` spostati in `main` |
| D3 | Primo livello | **Minimale `Home / Library / Downloads`**; "Made for you" rimosso |
| D4 | Copertine | **URL senza token** + artwork caricato dall'app (`BitmapLoader`); fallback documentato |
| D5 | Extra auto | **Nodo Scaricati + ripresa coda** (`onPlaybackResumption`) |
| D6 | Cast vs auto | Comportamento attuale: se c'è una sessione Cast, il Cast vince e l'auto fa da telecomando |
| D7 | AAOS su Play | **Nessun artefatto AAOS**: dichiarazione solo in `tempo`/`notquitemy`; `play` resta mobile |

**Residui aperti (non bloccanti):** conferma sul campo che l'host auto usi `artworkData`; resa
reale in auto da verificare (DHU/emulatore AAOS).

## 5. Verifica

Il repo non ha test: la verifica minima è la compilazione dei flavor toccati.

```bash
# tocca service/ e AutomotiveRepository -> tutti e tre i flavor
./gradlew :app:compileTempoDebugJavaWithJavac \
          :app:compileNotquitemyDebugJavaWithJavac \
          :app:compilePlayDebugJavaWithJavac
```

Verifica manuale (macchina dell'utente, non disponibile su questo host): Android Auto sul telefono
in modalità sviluppatore + **Desktop Head Unit** dell'AA SDK, `adb forward tcp:5277 tcp:5277`;
per AAOS un emulatore con immagine Automotive. Checklist:

- [ ] Browsing completo con l'app **chiusa** sul telefono (root → 3 livelli → brano).
- [ ] Tre tab in alto, etichette localizzate, nessun nodo "Made for you".
- [ ] Tap su album/playlist in auto → coda corretta, non solo il primo brano.
- [ ] Copertine visibili **e** nessun URL con `t`/`s` negli artwork URI né negli `extras` esposti all'auto (l'URI di streaming in `requestMetadata.mediaUri` resta autenticato per necessità).
- [ ] Bottone shuffle premuto dall'auto senza notifica attiva → nessun crash.
- [ ] Scrobble/chronology: un brano ascoltato in auto compare in "Recentemente riprodotto".
- [ ] Rotta `Cartelle → artista → album → brano` (caso F0.8).
- [ ] Nodo Scaricati in modalità aereo: riproduce i file locali.
- [ ] Ripresa: con app chiusa e coda salvata, l'auto propone la ripresa dal brano corrente.
- [ ] Server spento: messaggio, non tab vuote né attesa infinita.
- [ ] App mai configurata: item "configura sull'app", nessun crash.
- [ ] Disconnessione/riconnessione auto con coda in corso: la coda non perde brani (F0.4).
- [ ] Ricerca dall'auto: risultati pertinenti e paginati.

## 6. Criteri di done

- [x] Nessun `SettableFuture` senza completamento in `AutomotiveRepository`.
- [x] Nessun `!!` su dati di sessione `SessionMediaItem` né su `mediaNotificationControllerInfo`.
- [ ] Albero navigabile end-to-end in tutti e tre i flavor, etichette localizzate.
- [ ] Nodo Scaricati e ripresa coda funzionanti senza UI del telefono.
- [x] Nessuna credenziale Subsonic negli URI di artwork consegnati all'auto (bitmap fornito dal `BitmapLoader` della sessione).
- [x] Meta-data AAOS + `uses-feature` presenti in `tempo`/`notquitemy`, con il vincolo Play documentato (`docs/BUILD.md` §2.1.1).
- [x] Compilazione dei 3 flavor verde.
- [x] `AGENTS.MD`, `docs/ARCHITECTURE.md`, `README.md`, `plans/README.md` allineati.

## 7. Diario

**2026-11-09 — Fix review P1/P2 (sessione auto).**
- `MediaBrowserTree.getItems` restituisce un item per ogni item richiesto, nello stesso ordine:
  rimossa la deduplica per `mediaId`, che perdeva i brani ripetuti in coda/playlist. I metadati di
  sessione arrivano da `AutomotiveRepository.getSessionMediaItems` (una query `getByIds` su un
  thread dedicato, chunk da 500) al posto di due `Thread.join()` per item e della ricostruzione
  dell'intero batch; il callback usa `Futures.transform` + `AsyncFunction` per non bloccare il main
  thread della sessione.
- `getIndexes` distingue directory (`DIRECTORY_ID`, browsable) e brani (`MappingUtil`, id reale,
  extras) e riceve il prefisso delle directory: i brani sotto un indice tornano a fare scrobble e
  chronology.
- Gli `extras` degli item esposti all'auto non contengono l'URI di streaming (credenziali); resta
  `requestMetadata.mediaUri`, necessario al player. Documentazione allineata.
- `MediaLibraryServiceCallback.onPostConnect` imposta il layout shuffle solo per controller
  notifica/automotive/auto-companion, non per il `MediaController` del telefono.
- Note di build per host aarch64 spostate in `docs/BUILD.md` §1.1 (erano in `AGENTS.MD`, fuori tema).
- Verifica: compilazione dei 3 flavor, `task docs:check`.

**2026-11-09 — Fase 6 (AAOS + distribuzione).**
- `appCategory="audio"` in `main`; nuovi manifest di flavor `tempo`/`notquitemy` con meta-data
  `com.android.automotive` e `uses-feature android.hardware.type.automotive required="false"`.
- Decisione D7: `play` senza dichiarazione AAOS, quindi fuori dal track Play "AAOS only";
  nessun artefatto separato. Vincolo documentato in `docs/BUILD.md` §2.1.1 e `AGENTS.MD` §6.
- Verifica: compilazione dei 3 flavor verde; manifest finale ispezionato (AAOS presente solo in
  `tempo`/`notquitemy`); `task docs:check` verde.
- Non verificato: installazione su AAOS reale (emulatore Automotive non disponibile su questo host).

**2026-11-09 — Fase 5 (copertine senza credenziali).**
- `MusicUtil.getCoverArtUrl`: unica costruzione dell'URL cover (parametri non sensibili oppure
  completi); `CustomGlideRequest.createUrl` e `SessionMediaItem.getMediaItem()` la riusano.
- `MappingUtil` overload `publicArtwork` + `getPublicArtworkUri`; `AutomotiveRepository` espone solo
  artwork URI senza credenziali (browse, ricerca, download, ripresa).
- Sessione: `.setBitmapLoader(DataSourceBitmapLoader)` in `tempo`/`notquitemy`/`play`, alimentato da
  una `ResolvingDataSource` che autentica solo le richieste `getCoverArt` del server configurato.
- Verifica: compilazione dei 3 flavor verde (4m19s), `task docs:check` verde.
- Non verificato: resa in auto e copertina della notifica (serve DHU/emulatore AAOS).

**2026-11-09 — Fase 4 (ripresa coda dall'auto).**
- `QueueDao.getAllSimple()` ordinato per `track_order`; `AutomotiveRepository.getPlaybackResumption()`
  legge la coda su un thread e restituisce `MediaSession.MediaItemsWithStartPosition` (indice =
  ultimo brano riprodotto, coda limitata a 100 elementi, `null` con coda vuota o server assente).
- `MediaLibraryServiceCallback.onPlaybackResumption` + passthrough in `MediaBrowserTree`.
- Posizione nel brano: 0 (non persistita localmente; nessuna chiamata alla play queue del server
  per non dipendere dalla rete all'avvio in auto).
- Verifica: compilazione dei 3 flavor verde, `task docs:check` verde.

**2026-11-09 — Fix P1/P3 (review).**
- P1 `MediaBrowserTree.getItems`: restituisce un item per ogni item richiesto, deduplicato per
  `mediaId` (`LinkedHashMap`). La vecchia `subList(index, metadata.size)` accodava tutto il resto
  della sessione: l'host riceveva duplicati quando inviava più item dello stesso gruppo o ripeteva
  la chiamata.
- P3 extras sui download: `MappingUtil.buildMusicExtras` (estratto da `mapMediaItem`) applicato in
  `mapDownloadedItem` su metadata e request metadata, così `MediaService` riconosce
  `type == MEDIA_TYPE_MUSIC` e scrobble/chronology funzionano anche scegliendo un brano dal nodo
  Scaricati.
- `MediaManager.scrobble`: esce senza server configurato (ora anche i download portano gli extras,
  quindi lo scrobble può essere invocato offline).
- Verifica: 3 flavor `BUILD SUCCESSFUL` (1m51s), `task docs:check` verde.

**2026-11-09 — Fasi 2-3 (presentazione + Scaricati).**
- Albero ridotto a `Home / Library / Downloads`: playlist, cartelle, podcast e radio stanno dentro
  `Library`; rimosso il nodo `Other` e i testi hardcoded.
- 16 stringhe `auto_*` in `values` e `values-it` (titoli dei nodi + `auto_no_downloads`); il root
  usa `app_name`.
- Tipi media: artisti `MEDIA_TYPE_ARTIST` (starred + ricerca, erano `PLAYLIST`), figli delle
  cartelle `MUSIC` per i brani e `FOLDER_MIXED` per le directory, tracce di `MappingUtil`
  `MEDIA_TYPE_MUSIC`.
- Nodo Scaricati: `DownloadDao.getAllSync()` (solo query, nessuna migrazione) e
  `AutomotiveRepository.getDownloads()` che legge su un thread e completa il futuro in background;
  `MappingUtil.mapDownloadedItems` usa l'`downloadUri` salvato per non invalidare la cache Media3.
  Nessun download → item non playable con `auto_no_downloads` (non errore).
- `docs/ARCHITECTURE.md` §6 documenta il primo livello e il nodo offline.
- Follow-up review: download accessibili anche senza server configurato (artwork omesso se mancano
  le credenziali); rami `else` di `AutomotiveRepository` uniformati a `completeError`; paging
  reso overflow-safe.
- Verifica: compilazione dei 3 flavor verde, `task docs:check` verde.
- Non verificato: resa reale in auto (serve DHU/emulatore AAOS).

**2026-11-09 — Fase 1 (consolidamento flavor).**
- `MediaBrowserTree.kt` e `MediaLibraryServiceCallback.kt` spostati in
  `app/src/main/java/com/cappielloantonio/tempo/service/`; eliminate le copie in `tempo` e `play`.
- `notquitemy/MediaService.kt`: rimosso `CustomMediaLibrarySessionCallback`, ora usa il callback
  condiviso e `AutomotiveRepository`; `pruneMetadata()` al rilascio. Guadagna browsing, shuffle
  e ricerca in auto.
- Callback condiviso: `onPostConnect` imposta il layout shuffle per il controller che si connette
  (logica che prima viveva solo nel servizio `notquitemy`).
- Classe allineata al nome del file: `MediaLibrarySessionCallback` → `MediaLibraryServiceCallback`.
- `AGENTS.MD` §4/§6 e `docs/ARCHITECTURE.md` §6 aggiornati.
- Verifica: compilazione dei 3 flavor (v. sotto).

**2026-11-09 — F0.1–F0.9 corretti.**
- `AutomotiveRepository`: helper `completeError`/`completeFailure`, completamento garantito in tutti
i rami (6 `else` aggiunti + `getStarredAlbums.onFailure`), `RESULT_ERROR_IO` per gli errori di rete,
guardia su `server` nullo, observer della cronologia rimosso dall'istanza corretta,
`deleteMetadata()` → `pruneMetadata()` con TTL 24 h.
- `SessionMediaItemDao`: `get(id)` deterministico (`ORDER BY timestamp DESC LIMIT 1`) e
`deleteOlderThan`.
- `MediaBrowserTree` (tempo+play): guardie in `getItems` (niente più `subList(-1)`/`!!`), fix del
percorso `INDEX_ID → getArtistAlbum`, nodo "Made for you" rimosso, item guida pre-configurazione,
guardia in `search`.
- `MediaLibraryServiceCallback` (tempo+play): `notificationController` al posto di
`mediaNotificationControllerInfo!!`, comandi rinominati nel namespace dell'app, paging applicato.
- `MediaService` (tempo+play): `pruneMetadata()`.
- Stringa `auto_not_configured` in `values` e `values-it`.
- `AutomotiveRepository.getMadeForYou` resta disponibile (nessun chiamante) per un futuro mix.
- Verifica: `compileTempoDebugJavaWithJavac`, `compileNotquitemyDebugJavaWithJavac`,
`compilePlayDebugJavaWithJavac` → `BUILD SUCCESSFUL`, exit 0. Su questo host è servito
`QEMU_LD_PREFIX=/usr/x86_64-linux-gnu` per far girare l'AAPT2 x86-64 (host aarch64).
- Non verificato: comportamento reale in auto (serve DHU/emulatore AAOS sulla macchina dell'utente).

## 8. Rischi

- **Artwork**: se l'host non usa `artworkData`, serve il fallback con URL autenticato (D4) — costo
  di test sul campo, non di codice.
- **Ciclo di vita del servizio**: la pulizia TTL aggiunge stato; sbagliarla significa coda che si
  svuota in auto (F0.4). Test dedicato obbligatorio.
- **Doppio percorso di autenticazione** (Glide con URL, BitmapLoader con resolver): due punti da
  tenere allineati quando cambia l'autenticazione Subsonic (token/salt contro password).
- **Play/AAOS**: la regola del track AAOS-only può sorprendere in fase di pubblicazione.
- **Cast**: con sessione Cast attiva l'auto controlla il dispositivo Cast, non il telefono; è una
  scelta (D6) ma va comunicata agli utenti.
