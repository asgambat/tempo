# Build, varianti e release — Tempo

> Istruzioni operative per compilare, firmare e rilasciare. Regole sintetiche in [`AGENTS.MD`](../AGENTS.MD).

## 1. Prerequisiti

- **JDK 17** (la CI usa Zulu 17). Il progetto usa AGP 8.8: versioni di Java diverse falliscono.
- Android SDK: **compileSdk 35**, `build-tools 35.0.0`.
- Gradle: usare il wrapper (`./gradlew`), versione **8.10.2**.
- Connessione per dipendenze (Maven Central / Google). L'AAR FFmpeg è **locale**: `libs/lib-decoder-ffmpeg-release.aar` (già nel repo, referenziata con `implementation files(...)`).

## 2. Configurazione

- Build files in **Groovy DSL**: `build.gradle` (root) e `app/build.gradle`. Non convertirli a `.kts`.
- `gradle.properties`: `-Xmx2048m` al daemon, `android.useAndroidX=true`, `enableJetifier=false`, `nonTransitiveRClass=false`.

### 2.1 Flavor (dimension `default`)

| Flavor | applicationId | Casting (Chromecast) | Uso |
|---|---|---|---|
| `tempo` | `com.cappielloantonio.tempo` | ✅ (`media3-cast`) | Versione GitHub / F-Droid principale; usata dalla CI |
| `notquitemy` | `com.cappielloantonio.notquitemy.tempo` | ❌ | Variante F-Droid senza Play Services |
| `play` | `com.cappielloantonio.play.tempo` | ✅ (`media3-cast`) | Distribuzione Play Store |

Source set per flavor: `app/src/<flavor>/java|res` — contengono `service/MediaService.kt`, `ui/fragment/ToolbarFragment.java`, `util/Flavors.java` (e per `tempo`/`play` anche `MediaBrowserTree.kt` + `MediaLibrarySessionCallback.kt`; per `notquitemy` anche `res/menu/main_page_menu.xml`).

### 2.2 Build types

- `debug`: default.
- `release`: `minifyEnabled true` + `shrinkResources true`, ProGuard con `proguard-android-optimize.txt` + `app/proguard-rules.pro`.

## 3. Comandi

```bash
# Build debug
./gradlew assembleTempoDebug
./gradlew assembleNotquitemyDebug
./gradlew assemblePlayDebug

# Build release (richiede firma per installare)
./gradlew assembleTempoRelease

# Installazione rapida su device connesso
./gradlew installTempoDebug

# Compilazione rapida (verifica minima in assenza di test)
./gradlew :app:compileTempoDebugJavaWithJavac
```

Verifica minima di ogni modifica: **compilare ogni flavor toccato** (se tocchi codice per-flavor, tutti e tre). Non esistono unit test né lint configurati.

Gli stessi comandi sono incapsulati nel `Taskfile.yml` della root ([go-task](https://taskfile.dev)): `task build`, `task install`, `task build:release`, `task compile`, `task verify`, `task clean` — `task --list` per l'elenco completo (include anche `bump`, `changelog`, `release:checklist`, `release:sign`, `release:tag`).

## 4. Firma

- Nessun keystore è nel repo (correttamente). La CI firma con segreti GitHub: `KEYSTORE_BASE64`, `KEY_ALIAS_GITHUB`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD_GITHUB`.
- Per firmare in locale: configurare `signingConfigs` **solo localmente** (non committare credenziali) o usare `apksigner` sull'APK non firmato.

## 5. CI e release

Workflow: `.github/workflows/github_release.yml` — scatta su **tag `x.y.z`**:

1. Setup JDK 17 (Zulu) + cache Gradle.
2. `bash ./gradlew assembleTempoRelease`.
3. Firma con `r0adkll/sign-android-release`.
4. Upload artifact + creazione GitHub Release (asset `app-tempo-release.apk`).

### Build continua (develop)

Workflow: `.github/workflows/build_develop.yml` — scatta su ogni **push su `develop`**:

1. Un'unica esecuzione Gradle: `assembleNotquitemyDebug assemblePlayDebug assembleTempoRelease`.
2. Firma dell'APK release `tempo` con i segreti GitHub (gli stessi di `github_release.yml`).
3. Upload artifact scaricabili dalla pagina del run: `Tempo-<versione>-signed-release`, `Notquitemy-<versione>-debug`, `Play-<versione>-debug` (retenzione 14 giorni, concorrenza: i push successivi annullano il run in corso).

⚠️ La firma richiede i segreti `KEYSTORE_BASE64`, `KEY_ALIAS_GITHUB`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD_GITHUB`: su fork senza segreti il passaggio di firma fallisce (i due APK debug restano validi).

### Checklist release

1. Bump `versionCode` (intero, +1) e `versionName` (`x.y.z`) in `app/build.gradle`.
2. Aggiungere `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (breve, inglese).
3. Aggiornare schermate in `fastlane/metadata/android/en-US/images/` se la UI è cambiata (o in `mockup/` per il README).
4. Commit (`feat:`/`fix:` + bump, o commit dedicato), push su `main`, poi `git tag x.y.z && git push --tags`.
5. Verificare il workflow su GitHub Actions e l'asset della release.

## 6. Note su dipendenze delicate

- **Media3 1.5.1**: API instabili → `@UnstableApi`. Le versioni delle librerie Media3 sono allineate.
- **`libs/lib-decoder-ffmpeg-release.aar`**: modulo `media3-decoder-ffmpeg` precompilato; abilitato da `DownloadUtil.buildRenderersFactory()` con `EXTENSION_RENDERER_MODE_*`. Se si aggiorna Media3, va rigenerato/aggiornato anche l'AAR.
- **OkHttp logging-interceptor `5.0.0-alpha.14`**: alpha deliberata (compatibilità); il logging BODY è attivo anche in release — debito noto, non "sistemarlo" di passata.
- **Room 2.6.1**: `annotationProcessor` (non kapt/KSP); schemaLocation `app/schemas` — committare i JSON di schema.
- **Glide 4.16**: `@GlideModule` via `annotationProcessor` (`com.github.bumptech.glide:compiler`).

## 7. Problemi comuni

| Sintomo | Causa tipica |
|---|---|
| `Unsupported class file major version` / AGP error | JDK ≠ 17 → impostare `JAVA_HOME` |
| `AAR lib-decoder-ffmpeg-release missing` | build avviata fuori dalla root del repo o file rimosso da `.gitignore`/clean |
| Errori risorse duplicate tra flavor | simboli definiti sia in `main` sia nel source set del flavor |
| `Duplicate class` OkHttp | conflitto di versioni con la `5.0.0-alpha.14` — non miscelare con 4.x |
| Release CrashIndex/NoClassDefFound su Gson | regole ProGuard mancanti per classi riflettite |
