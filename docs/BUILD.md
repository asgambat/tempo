# Build, varianti e release — Andropausa

> Istruzioni operative per compilare, firmare e rilasciare. Regole sintetiche in [`AGENTS.MD`](../AGENTS.MD).

## 1. Prerequisiti

- **JDK 17** (la CI usa Zulu 17). Il progetto usa AGP 8.8: versioni di Java diverse falliscono.
- Android SDK: **compileSdk 35**, `build-tools 35.0.0`.
- Gradle: usare il wrapper (`./gradlew`), versione **8.10.2**.
- Connessione per dipendenze (Maven Central / Google). L'AAR FFmpeg è **locale**: `libs/lib-decoder-ffmpeg-release.aar` (già nel repo, referenziata con `implementation files(...)`).

### 1.1 Host aarch64 (Raspberry Pi 4)

Su host aarch64 (es. Raspberry Pi 4) l'AAPT2 usato da AGP è un binario **x86-64**: senza il loader
multiarch ogni task `process*DebugResources` fallisce con
`x86_64-binfmt-P: Could not open '/lib64/ld-linux-x86-64.so.2'`. L'errore sembra di codice, è
d'ambiente: nessuna `compile*` parte. Procedura:

```bash
# prerequisito: il loader x86-64 multiarch deve esistere
ls /usr/x86_64-linux-gnu/ld-linux-x86-64.so.2

# 1. fermare il daemon: un daemon già avviato NON eredita le variabili del client
QEMU_LD_PREFIX=/usr/x86_64-linux-gnu bash gradlew --stop

# 2. compilare con il prefix (il nuovo daemon eredita la variabile)
QEMU_LD_PREFIX=/usr/x86_64-linux-gnu bash gradlew --offline --console=plain \
  :app:compileTempoDebugJavaWithJavac :app:compileNotquitemyDebugJavaWithJavac :app:compilePlayDebugJavaWithJavac
```

- In alcuni clone il wrapper non ha il bit di esecuzione: usare `bash gradlew` (`./gradlew`
  risponde *Permesso negato*).
- `--offline` evita attese di rete quando la cache Gradle è già popolata.
- Tempi misurati su Pi 4: **3–5 minuti** per i tre flavor in build incrementale (ultime misure:
  3m05s, 4m19s); la prima esecuzione può superare i 600 s. Se un comando va in timeout,
  **rilanciarlo**: risorse, Kotlin e javac già prodotti restano in cache e la seconda passata completa.
- Se serve solo una verifica veloce, compilare prima il solo flavor `tempo`, poi gli altri due.
- Con il `Taskfile.yml` il prefix non serve a mano: `task compile` e `task verify` rilevano
  aarch64 (`uname -m`) e impostano `QEMU_LD_PREFIX` da soli (path sovrascrivibile con
  `task QEMU_LD_PREFIX=/path ...`); su host non-aarch64 la variabile non viene impostata. Le
  chiamate dirette a `bash gradlew` richiedono invece il prefix come sopra.
- Non aggirare il problema con symlink a `/lib64` o sostituzioni dell'SDK: il workaround resta
  per-comando e non va committato.

#### Memoria ridotta (host con 2–4 GB)

Oltre ad AAPT2 sotto qemu, durante la build restano vivi il daemon Gradle (`org.gradle.jvmargs=-Xmx2048m`
nel `gradle.properties` del progetto) e un daemon Kotlin separato; fra una build e l'altra i daemon
tengono la RAM occupata. Per compilare con un tetto più basso:

```bash
# ferma i daemon: libera subito la RAM che trattengono
QEMU_LD_PREFIX=/usr/x86_64-linux-gnu bash gradlew --stop

QEMU_LD_PREFIX=/usr/x86_64-linux-gnu bash gradlew --offline --console=plain \
  --max-workers=1 \
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" \
  -Pkotlin.daemon.jvmargs="-Xmx768m -XX:MaxMetaspaceSize=384m" \
  :app:compileTempoDebugJavaWithJavac
```

- `--max-workers=1` (equivalente a `org.gradle.workers.max=1`) evita di eseguire in parallelo più
  AAPT2 sotto qemu, javac e Kotlin: è il parametro con l'effetto maggiore su un host single-board.
- I due `-Xmx` valgono solo per quell'esecuzione e non cambiano il default del repository (2048m),
  pensato per CI e macchine più capienti. Se Gradle esce in `OutOfMemoryError`, portare il suo heap
  a `-Xmx1792m`.
- Alternativa senza daemon Kotlin: `-Dkotlin.compiler.execution.strategy=in-process`, che però
  richiede almeno 2 GB di heap Gradle (non combinarlo con `-Xmx1536m`).
- Dopo la verifica, `bash gradlew --stop` per rilasciare il daemon.
- `task qemu:stop` ferma i daemon e libera subito la RAM che trattengono; `task compile` e
  `task verify` esportano da soli il `QEMU_LD_PREFIX` su host aarch64 (`task QEMU_LD_PREFIX=/path ...`
  per cambiare il path). I tetti di memoria restano invece per-comando: se `task compile` esce in
  `OutOfMemoryError`, usare la chiamata diretta a `bash gradlew` qui sopra con le `-D`/`-P`.

## 2. Configurazione

- Build files in **Groovy DSL**: `build.gradle` (root) e `app/build.gradle`. Non convertirli a `.kts`.
- `gradle.properties`: `-Xmx2048m` al daemon, `android.useAndroidX=true`, `enableJetifier=false`, `nonTransitiveRClass=false`.

### 2.1 Flavor (dimension `default`)

| Flavor | applicationId | Casting (Chromecast) | Uso |
|---|---|---|---|
| `tempo` | `com.cappielloantonio.tempo` | ✅ (`media3-cast`) | Versione GitHub / F-Droid principale; usata dalla CI |
| `notquitemy` | `com.cappielloantonio.notquitemy.tempo` | ❌ | Variante F-Droid senza Play Services |
| `play` | `com.cappielloantonio.play.tempo` | ✅ (`media3-cast`) | Distribuzione Play Store |

Source set per flavor: `app/src/<flavor>/java|res` — contengono `service/MediaService.kt`, `ui/fragment/ToolbarFragment.java`, `util/Flavors.java` (per `notquitemy` anche `res/menu/main_page_menu.xml`). `MediaBrowserTree.kt` e `MediaLibraryServiceCallback.kt` vivono in `main` e sono condivisi da tutti i flavor.

### 2.1.1 Android Automotive OS (AAOS)

I flavor `tempo` e `notquitemy` dichiarano AAOS nei rispettivi manifest di flavor
(`app/src/<flavor>/AndroidManifest.xml`): meta-data `com.android.automotive` con
`@xml/auto_app_desc` e `<uses-feature android:name="android.hardware.type.automotive"
android:required="false"/>`. L'APK resta installabile su telefono; su AAOS si installa via sideload o
F-Droid. `android:appCategory="audio"` è nel manifest `main` per tutti i flavor.

Il flavor `play` **non** dichiara AAOS: su Google Play i media app sono ammessi solo nel track
"Android Automotive OS only", dove `android:required` deve essere `true` o assente, non nel track
mobile. Non esiste quindi un artefatto AAOS Play; l'eventuale distribuzione AAOS su Play
richiederebbe un APK/track separato con `required="true"`. `com.google.android.gms.car.application`
(Android Auto su telefono) resta nel manifest `main` per tutti i flavor. Nessun permesso aggiuntivo.

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
- **Generazione di un keystore nuovo** — solo se non ne possiedi già uno (una firma diversa impedisce gli aggiornamenti delle installazioni esistenti, v. note in §4.1). Comando con JDK 17 (quello della CI); chiede store/key password e dati del certificato:

  ```bash
  keytool -genkeypair -v \
    -keystore tempo-release.keystore \
    -storetype PKCS12 \
    -alias tempo \
    -keyalg RSA -keysize 2048 \
    -validity 10000
  ```

- Verifica e codifica del keystore prima di caricare/aggiornare il segreto (da fare **in locale**, il contenuto del segreto non è leggibile dalla CI):

  ```bash
  # controllo di leggibilità del file originale: deve elencare l'alias
  keytool -list -keystore tempo-release.keystore
  # genera la stringa base64 da mettere in KEYSTORE_BASE64 (una riga, senza newline)
  base64 -w0 tempo-release.keystore > tempo-release.b64
  # controllo del round-trip esattamente come lo farà la CI: decodifica e rilettura
  base64 -d tempo-release.b64 > /tmp/ks-check && keytool -list -keystore /tmp/ks-check
  ```

### 4.1 Checklist secrets GitHub

Quattro segreti **di repository** (Settings → Secrets and variables → Actions → Secrets → New repository secret), usati identici da `build_develop.yml` e `github_release.yml`:

| Secret | Valore da inserire |
|---|---|
| `KEYSTORE_BASE64` | Il keystore in base64 **su una riga** (contenuto di `tempo-release.b64`) |
| `KEYSTORE_PASSWORD` | Store password del keystore |
| `KEY_ALIAS_GITHUB` | Alias della chiave dentro il keystore (deve apparire in `keytool -list`) |
| `KEY_PASSWORD_GITHUB` | Con PKCS#12 coincide con la store password |

Procedura:

1. Verificare il keystore e generare il base64 in locale (v. comandi sopra): `keytool -list` deve elencare l'alias senza errori.
2. Aprire il repository → **Settings** → **Secrets and variables** → **Actions** → scheda **Secrets**.
3. **New repository secret**, uno per volta, con i nomi esatti della tabella:
   - `KEYSTORE_BASE64` → incollare il contenuto di `tempo-release.b64` (una sola riga, senza spazi extra né virgolette; il limite GitHub è 64 KB, un keystore 2048-bit sta in ~3–10 KB).
   - `KEYSTORE_PASSWORD` → la store password scelta alla creazione del keystore.
   - `KEY_ALIAS_GITHUB` → l'alias della chiave (es. `tempo`).
   - `KEY_PASSWORD_GITHUB` → la stessa store password (con PKCS#12 coincidono).
4. Push successivo su `develop` (o nuovo tag): lo step `Verify keystore secret` conferma in ~30 s che il segreto è valido, prima di lanciare la build.

Note:

- I segreti **non sono più visualizzabili** dopo il salvataggio, solo sostituibili: conservare file, alias e password in un backup sicuro (disco cifrato / password manager). Perdere keystore+password = nessun aggiornamento installabile sopra le versioni esistenti.
- **Mai** committare keystore o password; se un keystore esiste già **non rigenerarlo**: una firma diversa impedisce l'aggiornamento dell'app installata.
- Su fork senza segreti: `Verify keystore secret` salta con un warning, la firma fallisce come previsto e restano validi i due APK debug.

## 5. CI e release

Workflow: `.github/workflows/github_release.yml` — scatta su **tag `x.y.z`**:

1. Setup JDK 17 (Zulu) + cache Gradle.
2. Verifica rapida del segreto `KEYSTORE_BASE64` (decodifica + `keytool -list`, saltata sui fork senza segreti).
3. `bash ./gradlew assembleTempoRelease`.
4. Firma con `ilharp/sign-android-release@v2`.
5. Upload artifact + creazione GitHub Release (asset `app-tempo-release.apk`).

⚠️ **Segreto `KEYSTORE_BASE64` corrotto**: se il keystore decodificato non è leggibile, `apksigner` fallisce con `java.io.IOException: Tag number over 30 is not supported` (parser DER della JVM sul file keystore). Verificare sempre il base64 in locale con `keytool -list` prima di caricare il segreto (v. §4).

### Build continua (develop)

Workflow: `.github/workflows/build_develop.yml` — scatta su ogni **push su `develop`** e costruisce **solo il flavor `tempo`**:

1. Un'unica esecuzione Gradle: `assembleTempoRelease`.
2. Verifica rapida del segreto `KEYSTORE_BASE64` (decodifica + `keytool -list`, saltata sui fork senza segreti).
3. Firma dell'APK release `tempo` con i segreti GitHub (gli stessi di `github_release.yml`, azione `ilharp/sign-android-release@v2`).
4. Upload dell'artifact `Tempo-<versione>-signed-release`, scaricabile dalla pagina del run (retenzione 14 giorni, concorrenza: i push successivi annullano il run in corso). Gli altri flavor restano compilabili in locale (`assembleNotquitemyDebug`, `assemblePlayDebug`) ma non sono più prodotti dalla CI.

⚠️ La firma richiede i segreti `KEYSTORE_BASE64`, `KEY_ALIAS_GITHUB`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD_GITHUB`: su fork senza segreti il passaggio di firma fallisce e il run non produce artifact.

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
