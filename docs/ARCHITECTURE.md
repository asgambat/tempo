# Architettura — Tempo

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
  - `MediaLibrarySessionCallback` + `MediaBrowserTree` (flavor `tempo`/`play`) gestiscono browsable/ playable per **Android Auto** e controller esterni; i dati arrivano da `AutomotiveRepository`.
  - **Eventi player** → side-effect: transizione/discontinuità/ended → `MediaManager.scrobble()`, `MediaManager.saveChronology()`, timestamp ripresa (`setLastPlayedTimestamp`, `setPlayingPausedTimestamp`).
  - **ReplayGain** applicato su `onTracksChanged` (`ReplayGainUtil`).
  - Flavor `tempo`/`play`: `CastPlayer` con switch automatico (`onCastSessionAvailable/Unavailable`).
- **`MediaManager.java`** — facade statica usata dalla UI: `startQueue(future, tracks, position)`, `scrobble`, `saveChronology`, `continuousPlay`, ecc.Opera su `ListenableFuture<MediaBrowser>` con `MoreExecutors.directExecutor()`.
- **Sorgenti dati** — `DownloadUtil.getDataSourceFactory()` compone: upstream (streaming) → `StreamingCacheDataSource` (cache streaming dimensionabile via preferenze) → cache download (`SimpleCache` con `NoOpCacheEvictor` in directory scelta dall'utente).
- **Renderer** — `DownloadUtil.buildRenderersFactory()` usa `DefaultRenderersFactory` con `EXTENSION_RENDERER_MODE_ON/PREFER`: abilita il decodificatore **FFmpeg** dell'AAR locale (`libs/lib-decoder-ffmpeg-release.aar`, es. ALAC).
- **Persistenza coda** — `QueueRepository` salva la coda in Room e la sincronizza col server via `getPlayQueue`/`savePlayQueue` (Bookmarks API).

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
