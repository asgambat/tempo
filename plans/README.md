# plans/ — Indice piani di miglioramento

Audit del **2026-10-15** (base commit `50365b8c`), sessione di sola analisi.
Stato del repository al momento dell'audit: esiste già un diff di lavoro **non committato** (campagna anti-NPE su repository/adapter + refactor `SongListPageViewModel`). Ogni piano contiene lo snapshot del codice rilevante e resta valido anche dopo il commit di quel diff, tranne dove indicato.

## Ordine di esecuzione consigliato

| Piano | Titolo | Dipendenze | Stato |
|---|---|---|---|
| 001 | Guardie su `QueueRepository` (crash NPE + IOOBE) | — | DONE (2026-10-15, verificato con `compileTempoDebugJavaWithJavac`) |
| 002 | Robustezza playback/UI (leak `MediaBrowser`, mutazioni lista, `subList` asincrone, sort NPE residui) | — (dopo 001 consigliato, tocca lo stesso dominio) | DONE (2026-10-15, 3 flavor compilati, v. diario nel plan) |
| 003 | `savePlayQueue` con posizione reale | — | DONE (2026-10-25: già implementato nel codice, `PlayerBottomSheetViewModel.savePlayQueue(long)`; compilazione da confermare in CI) |
| 004 | Connectivity: `NetworkCallback` al posto del broadcast morto | — | DONE (2026-10-15, `compileTempoDebugJavaWithJavac` exit 0; receiver cancellato) |
| 005 | Logging senza credenziali (OkHttp level condizionato al debug) | — | DONE (2026-10-15, `compileTempoDebugJavaWithJavac` exit 0) |
| 006 | Observer accumulation + retry-on-failure | NON prima di 003 (①toccano lo stesso flusso bottom-sheet) | DONE (2026-10-25: 6a `observeOnce`, retry lista e sweep `Log.e` sugli `onFailure` dei repository; compilazione `tempo` OK; `notquitemy`/`play` non verificate — build interrotte su richiesta per instabilità dell'host) |
| 007 | "Riproduci con le sottocartelle" nella schermata cartella | — | DONE (2026-10-25, `compileTempoDebugJavaWithJavac` exit 0; `notquitemy`/`play` non verificate) |

## Regole trasversali per ogni executor

- **Verifica minima obbligatoria** (repo senza test): `./gradlew :app:compileTempoDebugJavaWithJavac`.
- Se il piano tocca `service/` o `util/Flavors.java` → compilare **tutti e tre** i flavor: `./gradlew :app:compileTempoDebugJavaWithJavac :app:compileNotquitemyDebugJavaWithJavac :app:compilePlayDebugJavaWithJavac`.
- Ambiente a risorse limitate (Raspberry Pi 4): **non** lanciare `assemble*` o `build` completi, solo i task `compile*` sopra.
- Lingua: modifiche a file esistenti → mantenere il linguaggio del file (Java dove è Java, Kotlin dove è Kotlin). Fonte: `AGENTS.md` §5.1.
- Nessuna nuova dipendenza. Nessuna modifica a `versionCode`, `applicationId`, manifest (salvo indicazioni del piano).
- Commit: un tema per piano, subject inglese con prefisso usato nel repo (`fix:` / `feat:`).

## Considerati e scartati (non ripescare)

- `getActiveNetworkInfo()` deprecata in `MainActivity.checkConnectionType()` — funziona ancora, fix non necessario adesso.
- Race benigna in `App.getSubsonicClientInstance()` (init statica non sincronizzata) — impatto pratico nullo.
- `MusicUtil.limitPlayableMedia()` che restituisce la `subList` raw — l'unico caller (`SongHorizontalAdapter`) la copia già; coperto dal piano 002 solo come protezione difensiva.
