# Architettura — Andropausa

> Doc tecnica di riferimento. Le regole operative sintetiche sono in [`AGENTS.MD`](../AGENTS.MD); le convenzioni di codice in [`CONVENTIONS.md`](CONVENTIONS.md).

## 1. Panoramica

App Android **single-module** (`:app`), UI View-based, nessuna DI. Ogni dominio (album, artisti, playlist, podcast, radio, download…) segue lo stesso schema a strati:

```
┌─────────────────────────────────────────────────────────────────┐
│ UI      MainActivity · Fragment · BottomSheetDialog · Dialog    │
│         Adapter (RecyclerView) · ViewBinding · nav_graph.xml    │
├─────────────────────────────────────────────────────────────────┤
│ STATE   ViewModel (AndroidViewModel) + LiveData/MutableLiveData │
├─────────────────────────────────────────────────────────────────┤
│ DATA    Repository (uno per dominio)                            │
│         ├─► Subsonic client (Retrofit → server Subsonic)        │
│         ├─► AppDatabase (Room: coda, preferiti, download, …)    │
│         └─► Preferences (SharedPreferences)                     │
├─────────────────────────────────────────────────────────────────┤
│ PLAYBACK MediaManager → MediaBrowser → MediaService (Media3)    │
│         ExoPlayer + FFmpeg AAR · SimpleCache (streaming/dl)     │
│         DownloaderService (download offline) · CastPlayer       │
└─────────────────────────────────────────────────────────────────┘
```

## 2. Layer UI

- **Singola Activity**: `MainActivity` (estende `BaseActivity`) ospita il `NavHostFragment` (`res/navigation/nav_graph.xml`), la bottom bar (`res/menu/main_page_menu.xml`) e il bottom sheet del player.
- **Fragment** in `ui/fragment/`: pagine della libreria. Il setup tipico (binding, ViewModel, metodi `init*()`) è documentato in `CONVENTIONS.md §4`.
- **Bottom sheet** in `ui/fragment/bottomsheetdialog/` (Song/Album/Artist/Playlist/Podcast/Share) navigati via `nav_graph.xml` con `Bundle` costruiti dagli adapter.
- **Dialog** in `ui/dialog/` (`DialogFragment`): editor playlist/radio/podcast, rating, storage download, alert server, ecc.
- **Adapter** in `ui/adapter/`: `RecyclerView.Adapter` (non `ListAdapter`), spesso `Filterable`; parlano con i fragment tramite le interfacce di `interfaces/`.
- **Ciclo MediaBrowser**: ogni fragment che interagisce col player crea `ListenableFuture<MediaBrowser>` in `onStart()` (via `SessionToken` su `MediaService`) e lo rilascia in `onStop()` con `MediaBrowser.releaseFuture(...)`.

## 3. Layer ViewModel

- Estendono `AndroidViewModel`, istanziano direttamente i repository nel costruttore (niente injection).
- Alcuni stati di navigazione sono campi pubblici popolati dal fragment prima dell'osservazione (es. `SongListPageViewModel.title`, `genre`, `album`): è la convenzione per passare il contesto di pagina.
- Esporgono `LiveData`/`MutableLiveData`; la paginazione a scroll (`PaginationScrollListener`) chiama metodi tipo `getSongsByPage(owner)` che concatenano `observe()`.

## 4. Layer Repository

Pattern canonico (es. `SongRepository`):

```java
public MutableLiveData<List<Child>> getStarredSongs(boolean random, int size) {
    MutableLiveData<List<Child>> data = new MutableLiveData<>(Collections.emptyList());
    App.getSubsonicClientInstance(false)
       .getAlbumSongListClient()
       .getStarred2()
       .enqueue(new Callback<ApiResponse>() {
           @Override public void onResponse(...) {
               if (response.isSuccessful() && response.body() != null && ... ) {
                   data.setValue(...);
               }
           }
           @Override public void onFailure(...) { /* log nel codice nuovo */ }
       });
    return data;
}
```

- `App.getSubsonicClientInstance(false)`: singleton del client Subsonico; `true` forza la ricostruzione (usato dal login dopo cambio credenziali).
- Metodi fire-and-forget (scrobble, rating, savePlayQueue): `void`, nessun dato esposto.
- I repository usano anche Room (`AppDatabase.getInstance().xDao()`) e `Preferences`.

## 5. Client Subsonic (`subsonic/`)

- **`Subsonic.java`** — facade con getter lazy per 13 client di area: `System`, `Browsing`, `MediaRetrieval`, `Playlist`, `Searching`, `AlbumSongList`, `MediaAnnotation`, `Podcast`, `MediaLibraryScanning`, `Bookmarks`, `InternetRadio`, `Sharing`, `Open`.
- **`SubsonicPreferences.java`** — URL server, username, autenticazione: `token` + `salt` (default Subsonic) oppure password in chiaro (`p`) quando `low_security = true`.
- **Parametri comuni** — `Subsonic.getParams()` inietta in ogni richiesta: `u` (user), `p`/`s`/`t` (auth), `v` (versione API **1.15.0**), `c` (client name), `f=json`.
- **`RetrofitClient.kt`** — Retrofit + Gson, OkHttp con: timeout call 2 min / connect 20 s / read-write 30 s, logging a livello BODY (⚠️ attivo anche in release — debito noto), cache HTTP 10 MB, `CacheUtil.offlineInterceptor` (TTL 30 giorni per le risposte cacheabili).
- **Envelope** — ogni risposta è `ApiResponse` (`subsonic-response`): i null-check a cascata (`body().getSubsonicResponse().getX()`) sono obbligatori perché i server Subsonic variano molto.
- **Modelli** — `subsonic/models/*.kt`: data class Kotlin "Gson-friendly" (campi nullable); `Child` è la traccia canonica ed è `Parcelable` (kotlin-parcelize). Quirk storico: `Playlist` è l'unico DTO che è anche entità Room.
- **OpenSubsonic** — estensioni rilevate via `SystemClient`/`OpenSubsonicExtensionsUtil` e salvate in `Preferences`.

## 6. Playback (`service/`, `util/DownloadUtil`, `util/StreamingCacheDataSource.kt`)

- **`MediaService`** (una versione per flavor, v. sotto) è un Media3 `MediaLibraryService`:
  - `ExoPlayer` con `DefaultLoadControl` scalato da `Preferences.getBufferingStrategy()`, `handleAudioBecomingNoisy`, wake mode network, audio attributes default.
  - `MediaLibraryServiceCallback` + `MediaBrowserTree` (in `main`, condivisi da tutti i flavor) gestiscono browsable/playable per **Android Auto** e controller esterni; i dati arrivano da `AutomotiveRepository`.
    - Primo livello: `Home / Library / Downloads`; playlist, cartelle, podcast e radio stanno dentro `Library`.
    - **Ramo Cartelle** (`Library → Cartelle`, id `[folderID]`): il `MediaItem` del nodo e tutti i suoi discendenti (music folder da `getMusicFolders`, artisti e cartelle dell'indice da `getIndexes`, figli da `getMusicDirectory`) portano negli `extras` dei `MediaMetadata` i content style hint `android.media.browse.CONTENT_STYLE_BROWSABLE_HINT` e `CONTENT_STYLE_PLAYABLE_HINT` a `1` (= lista). L'indice alfabetico che Android Auto disegna nella barra di scorrimento compare solo sulle liste: senza gli hint l'host può mostrare il ramo come griglia, dove l'indice non c'è. Le costanti sono replicate in locale (`MediaBrowserTree.kt` per il nodo, `AutomotiveRepository.folderListStyle()` per gli item) perché `androidx.media:media` non è tra le dipendenze del progetto. Restano suggerimenti: l'host può ignorarli, quindi la resa va verificata su auto o emulatore AAOS.
    - **Music folder unica**: se `getMusicFolders` restituisce esattamente una cartella, `MediaBrowserTree.foldersListing()` salta il livello intermedio e mostra direttamente il suo contenuto (stesso percorso del ramo cartelle: `getIndexes`, con ripiego su `getMusicDirectory`, e le due azioni di raccolta in testa), senza perdere "Riproduci tutto"/"Riproduci casuale". Con più cartelle il comportamento resta quello di sempre. Il salto evita un passaggio inutile e allunga la lista visibile, che è la condizione perché l'host disegni l'indice.
    - `Downloads` legge `DownloadDao` in background e usa l'URI offline salvato nel record `Download` (non lo rigenera), così la cache Media3 risolve il contenuto senza server; gli item portano gli stessi extras dei brani, quindi scrobble e chronology restano attivi anche offline (`MediaManager.scrobble` esce se manca il server). Senza server l'artwork pubblico non è costruibile: l'item porta un content URI `content://<applicationId>.artwork/<coverArtId>` (opaco, senza path: non espone le directory dell'app ai controller, v. sotto), servito in sola lettura da `provider/ArtworkProvider`, che risponde `openFile` con la copia per id Subsonic di `cache/artwork_cache` (`LocalArtwork`) e `image/*` dedotto dai primi byte. Così l'`artworkUri` è risolvibile sia dall'host di Android Auto, che carica le miniature delle liste da sé, sia dal `CachingBitmapLoader` della sessione, che per le copertine locali legge il file direttamente dalla cache senza passare dal provider. La ricerca della copia avviene solo quando serve (decodifica o `openFile`), mai mentre si costruisce la lista dei download: se la copertina non è in cache l'artwork resta mancante. Il provider è `exported` perché l'host è un'altra app e l'URI non trasporta grant: non consente enumerazione né scrittura, ma qualsiasi app che conosca l'id può leggere quella copertina — scelta accettata per far funzionare le copertine offline in auto.
    - `MediaBrowserTree.getItems` restituisce **un item per ogni item richiesto, nello stesso ordine** (un brano può comparire due volte in coda) e risolve i metadati di sessione con una sola query su un thread dedicato, mai sul main thread della sessione.
    - Ogni raccolta ha in testa due azioni riproducibili, "Riproduci tutto" e "Riproduci casuale" (`[playAllID]`/`[shuffleAllID]` + id raccolta): non sono brani, e `onSetMediaItems` le espande nei brani della raccolta, mescolati per il comando shuffle. L'id dell'azione porta il tipo: cartella (`[folderID]`/`[directoryID]` → `AutomotiveRepository.getFolderSongs` → `DirectoryRepository.getSongsIncludingSubfolders`, cartella + sottocartelle, tetti 100 brani / 3 livelli), album (`[albumID]` → `getAlbumTracks`) e playlist (`[playlistID]` → `getPlaylistSongs`). Le azioni compaiono solo se la raccolta ha almeno un brano e, se l'espansione non produce nulla (raccolta svuotata, errore di rete), `onSetMediaItems` lascia in riproduzione la coda corrente invece di azzerarla.
    - La coda impostata dall'auto (`onSetMediaItems`) viene salvata in Room: `QueueMapping` converte gli extras dei `MediaItem` in `Child` e `QueueRepository.replaceAll` sostituisce la coda conservando `last_play`/`playing_changed` dei brani già presenti. Solo per i controller automotive/auto-companion: il telefono passa da `MediaManager`. La coda viene sostituita quando l'host ne imposta una con più brani o quando il brano singolo è il risultato dell'espansione di una raccolta; un singolo brano non espanso (ricerca, random) lascia intatta la coda salvata.
    - `onPlaybackResumption` ricostruisce la coda salvata (`Queue` ordinata per `track_order`, massimo 100 elementi) e riparte dall'ultimo brano riprodotto, dall'inizio del brano (la posizione non è persistita); senza server configurato non viene proposta alcuna ripresa.
    - Ricerca: `MediaBrowserTree` tiene le ultime 4 query (`searchCache`). L'host chiama `onSearch` (conteggio) e poi `onGetSearchResult` (pagina): senza cache ogni ricerca produceva due richieste identiche a `search3`.
    - Shuffle: il comando custom aggiorna il layout su tutti i controller notifica/automotive/auto-companion connessi; prima veniva aggiornato solo il controller della notifica, lasciando l'icona sbagliata in auto. La modalità resta un'impostazione del player (`shuffleModeEnabled`), l'ordine salvato in Room non cambia.
    - Artwork: i `MediaItem` esposti all'auto hanno URI `getCoverArt` senza credenziali (`MusicUtil.getCoverArtUri`) a `Constants.SESSION_ARTWORK_SIZE` (500 px: una risoluzione dedicata alla sessione, indipendente da quella scelta per il telefono). La sessione usa un `CachingBitmapLoader` sopra `DataSourceBitmapLoader`: cache LRU in memoria (8 MB), deduplica delle richieste in volo per URI (prefetch e sessione condividono un solo download) e prefetch dell'artwork corrente più quello del brano successivo (accodato solo al termine, l'executor è a thread singolo; su rete a consumo il prefetch è saltato, perché la sessione carica comunque l'artwork che serve). Sotto, `ResolvingDataSource` autentica solo le richieste `getCoverArt` del server configurato e una `SimpleCache` dedicata (50 MB, LRU, `cache/artwork_cache`) tiene le copertine su disco: chiave = URL pubblico, quindi sopravvive al riavvio del servizio e ai cambi di token. Gli `extras` di questi item non contengono l'URI di streaming; `requestMetadata.mediaUri` resta autenticato perché è l'URI che il player deve aprire. La coda del telefono e la UI telefono continuano a usare gli URI autenticati e la cache Glide, invariata.
    - **AAOS**: i flavor `tempo`/`notquitemy` dichiarano `com.android.automotive` e `<uses-feature android:name="android.hardware.type.automotive" android:required="false"/>` nei manifest di flavor; il flavor `play` ne resta fuori per il vincolo Play sui media app (v. `docs/BUILD.md` §2.1.1).
  - **Eventi player** → side-effect: transizione/discontinuità/ended → `MediaManager.scrobble()`, `MediaManager.saveChronology()`, timestamp ripresa (`setLastPlayedTimestamp`, `setPlayingPausedTimestamp`).
  - **ReplayGain** applicato su `onTracksChanged` (`ReplayGainUtil`).
  - Flavor `tempo`/`play`: `CastPlayer` con switch automatico (`onCastSessionAvailable/Unavailable`).
- **`MediaManager.java`** — facade statica usata dalla UI: `startQueue(future, tracks, position)`, `scrobble`, `saveChronology`, `continuousPlay`, ecc.Opera su `ListenableFuture<MediaBrowser>` con `MoreExecutors.directExecutor()`.
- **Sorgenti dati** — `DownloadUtil.getDataSourceFactory()` compone: upstream (streaming) → `StreamingCacheDataSource` (cache streaming dimensionabile via preferenze) → cache download (`SimpleCache` con `NoOpCacheEvictor` in directory scelta dall'utente).
- **Renderer** — `DownloadUtil.buildRenderersFactory()` usa `DefaultRenderersFactory` con `EXTENSION_RENDERER_MODE_ON/PREFER`: abilita il decodificatore **FFmpeg** dell'AAR locale (`libs/lib-decoder-ffmpeg-release.aar`, es. ALAC).
- **Persistenza coda** — `QueueRepository` salva la coda in Room e la sincronizza col server via `getPlayQueue`/`savePlayQueue` (Bookmarks API). Tutte le mutazioni (incluse `replaceAll` per la coda impostata dall'auto) passano dall'`ExecutorService` a thread singolo del repository.

## 7. Download offline

- `DownloaderService` = Media3 `DownloadService` (`foregroundServiceType="dataSync"`), riavvio automatico su boot (`RECEIVE_BOOT_COMPLETED`).
- `DownloaderManager` gestisce richieste/rimozioni download; `DownloadUtil.getDownloadTracker()` è interrogato dagli adapter per il badge "downloaded".
- Raggruppamento in gruppi (album/artista/genre/anno) con le costanti `DOWNLOAD_*` di `Constants.kt`.
- Sync preferiti offline opzionale (`StarredSyncViewModel` + preferenza `sync_starred_tracks_for_offline_use`).

## 8. Database (Room)

- `AppDatabase.java`, **versione 10**, `fallbackToDestructiveMigration()` attivo, schema esportato in `app/schemas/`.
- Entità: `Queue`, `Server`, `RecentSearch`, `Download`, `Chronology`, `Favorite`, `SessionMediaItem` (in `model/`) + `subsonic.models.Playlist` (DTO riusato come entità — quirk storico).
- DAO (8): `QueueDao`, `ServerDao`, `RecentSearchDao`, `DownloadDao`, `ChronologyDao`, `FavoriteDao`, `SessionMediaItemDao`, `PlaylistDao` — Java, ritorni `LiveData` per letture, metodi sync per scritture.
- Converter: `DateConverters.kt`.
- Threading: scritture via `Thread + join()` nei repository (pattern legacy, v. AGENTS.MD §8).

## 9. Preferenze, tema, i18n

- `util/Preferences.kt` — unico accesso alle `SharedPreferences` di default; chiavi `const val`; valori tipici: bitrates/transcode per rete, cache, replay gain, buffering, sezioni home, sync coda, scrobbling.
- `helper/ThemeHelper.java` — applica il tema in `App.onCreate` (modalità default/light/dark), temi in `res/values/styles.xml` + `values-night*`.
- Lingue: inglese base + de/fr/it/ko/pt/ru/zh; per-app language con `AppLocalesMetadataHolderService` e `xml/locale_config.xml`.

## 10. Multi-server e indirizzi

- Supporto a più server (`ServerDao` + `ServerRepository`), con login da `LoginFragment`.
- **Indirizzo locale vs remoto**: `Preferences.getInUseServerAddress()` sceglie l'indirizzo in uso; se il server è irraggiungibile si passa all'alternativo (`isServerSwitchable`, cooldown 15 s); dialog `ServerUnreachableDialog`.

## 11. Check aggiornamenti GitHub

- `github/Github.java` + `GithubRetrofitClient.kt` → `api.github.com` (`ReleaseClient`/`ReleaseService`), modello `LatestRelease`.
- `UpdateUtil` confronta versioni; `GithubTempoUpdateDialog` mostra l'alert; cooldown 24 h (`NEXT_UPDATE_CHECK`).

## 12. Immagini

- `glide/CustomGlideModule.java` (`@GlideModule`): cache disco dimensionata da preferenze, `PREFER_RGB_565`.
- `glide/CustomGlideRequest.java`: builder con `ResourceType` (Song/Album/Artist/…) e fallback grafici; le cover arrivano dall'id `coverArtId` (`getCoverArtId`) via endpoint `getCoverArt`.

## 13. Sicurezza

- `android:allowBackup="false"`, `usesCleartextTraffic="true"` + `network_security_config` (necessario per server HTTP self-hosted).
- Autenticazione Subsonic preferenzialmente token+salt; credenziali in SharedPreferences (scelta di progetto, non cifrate).
