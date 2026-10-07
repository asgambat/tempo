# AGENTS.MD — Linee guida per lo sviluppo (umano e agentico)

> **Scopo del file**: dare a chiunque lavori su questo repo — in particolare agli agenti AI nelle iterazioni future — il contesto minimo per intervenire *senza rompere le convenzioni del progetto*.
> Approfondimenti in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md), [`docs/CONVENTIONS.md`](docs/CONVENTIONS.md), [`docs/BUILD.md`](docs/BUILD.md).
> **Regola di manutenzione**: ogni volta che cambia stack, struttura o convenzioni, aggiorna questo file e i doc in `docs/` nello stesso commit.

---

## 1. Il progetto in 30 secondi

- **Tempo** — client musicale open source (GPLv3) per server **Subsonic / OpenSubsonic**, nativo Android, UI *View-based* (niente Jetpack Compose), single-activity + Fragment + ViewBinding.
- Single module `:app`, namespace `com.cappielloantonio.tempo`. Versione di riferimento: **3.9.0 (versionCode 26)**.
- **Nessun framework DI** (niente Hilt/Dagger), **nessuna coroutine/RxJava**: si usano singleton espliciti (`App`, `AppDatabase`), `LiveData`/`MutableLiveData`, Retrofit in enqueue e `Thread` espliciti per le scritture Room.
- Server remoto: branch `main` (stabile) e `develop` (lavoro corrente).
- Il client REST per GitHub (check aggiornamenti) è separato, nel package `github/`.

## 2. Stack e vincoli di toolchain

| Componente | Valore | Note operative |
|---|---|---|
| JDK | **17** | richiesto da AGP 8.8 (la CI usa zulu 17) |
| Gradle | 8.10.2 (wrapper) | build files in **Groovy DSL** (`build.gradle`, NON `.kts`) |
| AGP | 8.8.0 | |
| Kotlin | 1.8.0 + `kotlin-parcelize` | `jvmTarget = 1.8` → nei `.java` solo sintassi Java 8 |
| compile / target SDK | 35 | `buildToolsVersion 35.0.0` |
| minSdk | 24 | |
| UI | ViewBinding + Navigation 2.8.6 + Material 1.10 | `buildFeatures.viewBinding true` |
| Persistenza | Room 2.6.1 | schema esportato in `app/schemas/` |
| Rete | Retrofit 2.11 + Gson + OkHttp | client Subsonic in `subsonic/`, client GitHub in `github/` |
| Player | Media3 1.5.1 (ExoPlayer + MediaLibraryService) | API instabili → annotare `@UnstableApi` |
| Immagini | Glide 4.16 | `@GlideModule` in `glide/CustomGlideModule.java` |
| Decoder extra | `libs/lib-decoder-ffmpeg-release.aar` | AAR locale, decodifica ALAC/ecc. via `DownloadUtil.buildRenderersFactory()` |

⚠️ Non aggiornare dipendenze/SDK/target come effetto collaterale di un task: in questo repo gli upgrade sono deliberati e committati con prefisso `gradle:`.

## 3. Comandi essenziali e verifica

```bash
./gradlew assembleTempoDebug        # flavor GitHub/F-Droid principale
./gradlew assembleNotquitemyDebug   # flavor F-Droid secondario
./gradlew assemblePlayDebug         # flavor Play Store
./gradlew installTempoDebug         # installazione su device/emulatore
./gradlew :app:compileTempoDebugJavaWithJavac   # verifica di compilazione rapida
```

- **Non esistono test** (nessun source set `test`/`androidTest`): la verifica minima e obbligatoria di ogni modifica è la **compilazione riuscita di ogni flavor toccato**.
- ⚠️ **Ambiente di sviluppo a risorse limitate (Raspberry Pi 4)**: non lanciare build complete, assemblaggio APK o Gradle in generale se non strettamente necessario; preferisci `task --dry <task>` per ispezionare i comandi e le verifiche più leggere possibili. I comandi pesanti (`build`, `build:all`, `build:release`, `verify`) sono per la macchina dell'utente o per la CI.
- Se tocchi `service/`, `ui/fragment/ToolbarFragment.java` o `util/Flavors.java`, compila **tutti e tre** i flavor (v. §6).

### Taskfile (go-task)

`Taskfile.yml` nella root incapsula i comandi del ciclo di vita (`task --list` per l'elenco). Punti d'ingresso per gli agenti:
- `task verify` — verifica minima (compilazione dei 3 flavor), equivalente alla regola sopra;
- `task docs:check` — coerenza dei link tra i documenti;
- `task agents:context` — riepilogo rapido del progetto (bootstrap);
- `task flavors` — matrice dei flavor.

Ciclo di release: `task bump [VERSION=x.y.z]` → `task changelog` → `task release:checklist` → `task build:release` → `task release:tag VERSION=x.y.z` (chiede conferma). Variabile `FLAVOR` ammette `Tempo|Notquitemy|Play` (default `Tempo`).

## 4. Architettura in breve

Flusso dati canonico di una lettura:

```
Fragment (UI, ViewBinding)
   │  observe(getViewLifecycleOwner())
   ▼
ViewModel (AndroidViewModel)
   │
   ▼
Repository  ──enqueue──► Subsonic Client (Retrofit, Call<ApiResponse>)
   ▲                            │
   └── MutableLiveData ◄── onResponse: setValue(...) ◄── server Subsonic (JSON)
```

Per il **playback** la UI non parla mai direttamente con ExoPlayer: passa da `MediaManager` (facade statica) → `MediaBrowser` (connesso in `onStart`, rilasciato in `onStop`) → `MediaService` (Media3 `MediaLibraryService`, un'implementazione per flavor). Dettagli completi in `docs/ARCHITECTURE.md`.

Mappa dei package sotto `app/src/main/java/com/cappielloantonio/tempo/`:

| Package | Contenuto | Lingua |
|---|---|---|
| `ui/` | `activity/` (MainActivity, base/BaseActivity), `fragment/` (+`pager/`, `bottomsheetdialog/`), `dialog/`, `adapter/` | Java |
| `viewmodel/` | 38 ViewModel `AndroidViewModel` | Java |
| `repository/` | 19 repository (uno per dominio + `AutomotiveRepository` per Android Auto) | Java |
| `subsonic/` | client REST Subsonic: `Subsonic.java` (facade), `SubsonicPreferences`, `RetrofitClient.kt`, `api/` (13 aree, ciascuna con `XService` + `XClient`), `models/` (DTO Gson) | Java + Kotlin (modelli) |
| `github/` | check aggiornamenti: `Github.java`, `GithubRetrofitClient.kt`, `api/release/`, `models/`, `utils/UpdateUtil.java` | Java + Kotlin |
| `database/` | `AppDatabase.java` (Room v10), `dao/` (8 DAO), `converter/DateConverters.kt` | Java |
| `model/` | entità Room + modelli locali (Queue, Server, Download, Chronology, Favorite, RecentSearch, SessionMediaItem, HomeSector, ReplayGain) | Kotlin |
| `service/` | `MediaManager.java` (facade playback), `DownloaderManager.java`, `DownloaderService.java`; `MediaService` è **per flavor** | Java + Kotlin |
| `util/` | `Constants.kt`, `Preferences.kt`, `MusicUtil`, `DownloadUtil`, `MappingUtil`, `NetworkUtil`, `UIUtil`, `IndexUtil`, `ReplayGainUtil`, `StreamingCacheDataSource.kt`, `OpenSubsonicExtensionsUtil` | Java + Kotlin |
| `interfaces/` | callback di click/diaolog usate da fragment e adapter (`ClickCallback`, `DialogClickCallback`, …) | Java |
| `helper/` | `ThemeHelper.java`, helper RecyclerView (`helper/recyclerview/`) | Java + Kotlin |
| `glide/` | `CustomGlideModule.java` (cache disco, RGB_565), `CustomGlideRequest.java` | Java |
| `broadcast/receiver/` | receiver (es. boot) | Java |

## 5. Convenzioni irrinunciabili

1. **Java vs Kotlin**: il core legacy è Java (UI, ViewModel, repository, DAO, client REST). Il Kotlin recente vive in: modelli (`subsonic/models/`, `model/`), `util/Constants.kt`, `util/Preferences.kt`, `RetrofitClient.kt`, servizi di flavor (`MediaService.kt`, `MediaBrowserTree.kt`, `MediaLibrarySessionCallback.kt`). Quando modifichi un file, resta nel suo linguaggio; per file nuovi segui il dominio di pertinenza (modelli/utility nuove → Kotlin ammesso; UI/ViewModel/repo → Java).
2. **ViewBinding**: nei fragment il binding si chiama sempre `bind`, viene azzerato in `onDestroyView()` (`bind = null`) e ogni callback asincrona lo null-checka (`if (bind != null)`). Non introdurre DataBinding né Compose.
3. **Repository**: pattern canonico = creare `MutableLiveData`, chiamare `App.getSubsonicClientInstance(false).getXClient().metodo().enqueue(new Callback<ApiResponse>() {...})`, valorizzare in `onResponse` con i null-check a cascata. Metodi "fire and forget" (scrobble, rating, savePlayQueue) restituiscono `void`.
4. **Room**: scritture bloccanti tramite `Thread` + `Runnable` interno + `thread.join()` (v. `QueueRepository`); letture reattive via `LiveData` dal DAO. Non eseguire mai query non-LiveData sul main thread.
5. **Media3**: ogni classe che tocca API instabili Media3 porta `@UnstableApi` (classe o pacchetto). Il player è configurato in `MediaService` (buffering da `Preferences.getBufferingStrategy()`, ReplayGain, handleAudioBecomingNoisy, wake network).
6. **Costanti e navigazione**: tutte le chiavi di Bundle, tipi di lista, tipi media ecc. vivono in `util/Constants.kt`. Navigazione in un unico `res/navigation/nav_graph.xml`; argomenti letti con `requireArguments()`.
7. **Click & callback**: gli adapter ricevono un'interfaccia di `interfaces/` nel costruttore e producono `Bundle` con le costanti di `Constants.kt`; le interfacce hanno metodi `default` vuoti e `@Keep`.
8. **Preferenze**: un solo punto di accesso, `util/Preferences.kt` (object con `@JvmStatic`, chiavi `private const val`). Le schermate impostazioni usano `res/xml/global_preferences.xml` + `SettingsFragment`.
9. **Immagini**: caricare le cover sempre con `CustomGlideRequest.Builder.from(context, coverArtId, ResourceType.X).build().into(view)`, mai Glide grezzo.
10. **Risorse**: prefissi fissi — layout `activity_`, `fragment_`, `item_`, `dialog_`, `bottom_sheet_*_dialog`; menu `*_menu`/`*_popup_menu`; drawable icone `ic_*`; stringhe in `snake_case` con prefisso del dominio (es. `song_list_page_*`).
11. **i18n**: la base inglese è `values/strings.xml` (ogni nuova stringa DEVE finire lì); traduzioni in `values-it`, `values-de`, `values-fr`, `values-ko`, `values-pt`, `values-ru`, `values-zh`. Per-app language via `locale_config.xml`.
12. **Flavor**: nessuna costante di flavor nel `main`; le differenze vivono nei source set `app/src/{tempo,notquitemy,play}` (v. §6).

## 6. Flavor — matrice di replica

| Aspetto | `tempo` (GitHub/F-Droid) | `notquitemy` (F-Droid alt.) | `play` (Play Store) |
|---|---|---|---|
| applicationId | `com.cappielloantonio.tempo` | `com.cappielloantonio.notquitemy.tempo` | `com.cappielloantonio.play.tempo` |
| `service/MediaService.kt` | ✅ con CastPlayer | ✅ senza Cast | ✅ con CastPlayer |
| `service/MediaBrowserTree.kt` + `MediaLibrarySessionCallback.kt` | ✅ | ❌ | ✅ |
| `ui/fragment/ToolbarFragment.java` | ✅ | ✅ | ✅ |
| `util/Flavors.java` | ✅ (init Cast) | ✅ (stub) | ✅ (init Cast) |
| dipendenza `media3-cast` | ✅ | ❌ | ✅ |
| risorse proprie | icone launcher, `ic_splash_logo`, `ic_toolbar_tempo` | icone launcher + `menu/main_page_menu.xml` | icone launcher |

**Regola**: ogni modifica a `MediaService`, `ToolbarFragment`, `Flavors` o al menu va replicata/coordinata su tutti i flavor, mantenendo le differenze di tabella (il flavor `notquitemy` non ha Cast e non dipende da Google Play Services).

## 7. Checklist per task tipici

### 7.1 Nuovo endpoint Subsonic
1. Eventuale modello in `subsonic/models/*.kt` (data class Gson, campi **nullable**, no annotation Gson custom).
2. Metodo nell'interfaccia `subsonic/api/<area>/<Area>Service.java`: primo parametro sempre `@QueryMap Map<String, String> params`, poi `@Query` espliciti, ritorno `Call<ApiResponse>`.
3. Wrapper in `<Area>Client.java`: `Log.d(TAG, ...)` + passa `subsonic.getParams()`; se serve una nuova area, creare package + getter lazy in `subsonic/Subsonic.java`.
4. Consumare da un repository (pattern §5.3) e poi da ViewModel/Fragment.
5. Se l'endpoint è OpenSubsonic, verificare supporto con `OpenSubsonicExtensionsUtil`.

### 7.2 Nuova schermata
1. `ui/fragment/XFragment.java` + `viewmodel/XViewModel.java` (+ repository se manca).
2. Layout `fragment_x.xml` (gestire `layout-land` se la schermata è scrollabile).
3. Destinazione in `res/navigation/nav_graph.xml`.
4. Costanti Bundle in `Constants.kt`; stringhe in `values/strings.xml` (+ `values-it`).
5. Il fragment implementa `ClickCallback` per ricevere click dagli adapter.

### 7.3 Nuova preferenza
1. Chiave + getter/setter `@JvmStatic` in `util/Preferences.kt`.
2. Controllo in `res/xml/global_preferences.xml` (ivi incluso eventuale nuovo `ListPreference`/`SwitchPreferenceCompat`).
3. Uso a valle via `Preferences.x()`; default documentati nel codice, non cambiare i default esistenti senza motivazione.

### 7.4 Nuova entità Room
1. Entità in `model/*.kt` (eccezione storica: `subsonic.models.Playlist` è anche entità — non spostarla leggiermente).
2. DAO in `database/dao/`, abstract getter in `AppDatabase`.
3. Bump `version` in `AppDatabase` + `@AutoMigration(from = X, to = X+1)`; lo schema viene esportato in `app/schemas/` e va committato.
4. ⚠️ È attivo `fallbackToDestructiveMigration()`: senza migrazione i dati utente (coda, preferiti offline, download metadata) vengono **cancellati**.

### 7.5 Stringhe e traduzioni
1. Nuova stringa sempre in `values/strings.xml` (inglese).
2. Aggiornare almeno `values-it` quando possibile; non modificare i file delle altre lingue se non per correzioni segnalate.
3. Per le release: `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.

### 7.6 Release
1. Bump `versionCode`/`versionName` in `app/build.gradle`.
2. Changelog fastlane (§7.5) + eventuali screenshot in `fastlane/metadata/android/en-US/images/`.
3. Tag `x.y.z` → workflow `.github/workflows/github_release.yml` (build `assembleTempoRelease`, firma, GitHub Release). Dettagli in `docs/BUILD.md`.
4. Build continua: ogni push su `develop` → `.github/workflows/build_develop.yml` produce gli artifact (tempo release firmato + notquitemy/play debug, 14 giorni di retenzione); il workflow di release non va modificato senza richiesta esplicita.

## 8. Insidie note e debito tecnico (leggere prima di rifattorizzare)

- `onFailure` dei callback Retrofit è spesso **vuoto**: è lo stato di fatto, ma nel codice nuovo logga almeno l'errore (`Log.e(TAG, ...)`), non copiare il vuoto.
- L'interceptor di logging OkHttp è a livello **BODY e sempre attivo** (anche in release): non loggare dati sensibili in più; eventuale fix va valutato come task dedicato.
- Scritture Room via `Thread + join()` (`QueueRepository`): pattern legacy bloccante — non estenderlo; per codice nuovo preferisci semplici thread/executor espliciti o LiveData.
- Il fragment si riconnette al `MediaBrowser` in `onStart` e lo rilascia in `onStop` (`ListenableFuture<MediaBrowser>`): rispetta il ciclo, altrimenti il playback remoto si disallinea.
- `MusicUtil.limitPlayableMedia` limita la coda a 100 elementi (`PLAYABLE_MEDIA_LIMIT`, pre-play 15): tenersene conto negli adapter.
- Cambio server locale/remoto (`Preferences.switchInUseServerAddress`) con cooldown 15 s (`isServerSwitchable`): non rimuovere i timer.
- Scrobbling/chronology: eventi agganciati agli stati del player in `MediaService` (transition, discontinuity, ended) — le modifiche al player possono impattare statistiche e "recently played".
- `Constants.MUSIC_INDEX_OBJECT` punta allo stesso valore di `MUSIC_DIRECTORY_OBJECT` (storico): non "correggere" senza verificare l'uso.
- `isLowScurity()` (typo storico) in `Preferences.kt`: rinominarlo significa toccare `App` e login; fallo solo in un task dedicato con renaming completo.
- La release è `minifyEnabled + shrinkResources`: ogni Riflesson/Gson/reflect nuovo va verificato con regole ProGuard (`app/proguard-rules.pro`, già `keep` per `retrofit2.**` e `TypeToken`).

## 9. Cosa NON fare

- ❌ Non introdurre: Compose, Hilt/Dagger, RxJava/Coroutines, KSP/kapt (il progetto usa `annotationProcessor`), multidex extra, altri DB.
- ❌ Non convertire in massa Java→Kotlin: revisioni illeggibili e conflitti a monte.
- ❌ Non toccare `main` direttamente: si lavora su branch (remoto attivo: `develop`).
- ❌ Non cambiare `applicationId`, `versionCode` o firmare release senza richiesta esplicita.
- ❌ Non aggiungere permission nel manifest se non richiesto (`usesCleartextTraffic` è una scelta per server HTTP locali).
- ❌ Non modificare i file in `mockup/` (branding) né `fastlane/` se non per release/changelog.

## 10. Convenzioni di commit

- Subject breve, inglese, imperativo, con prefissi in uso nel repo: `feat:`, `fix:`, `gradle:` (dipendenze/build), testo nudo ammesso per docs/readme.
- Un tema per commit; gli upgrade di dipendenze sono commit dedicati (`gradle: dependencies update`).

## 11. Documentazione di approfondimento

| Documento | Contenuto |
|---|---|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Layer, flussi, playback, client Subsonic, database, download/offline, Android Auto |
| [`docs/CONVENTIONS.md`](docs/CONVENTIONS.md) | Convenzioni di codice, naming, pattern con esempi, risorse, commit |
| [`docs/BUILD.md`](docs/BUILD.md) | Prerequisiti, varianti, firma, CI e checklist di release |
| [`README.md`](README.md) | Presentazione pubblica dell'app (features, screenshot, licenza) |
