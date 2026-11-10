# Plan 009 — Ottimizzazione Android Auto (proiezione dal telefono)

**Base commit:** `1f9f1ffe` (develop, 2026-11-09) · **Categoria:** qualità + robustezza · **Sforzo:** M (2-4 giornate, di cui almeno mezza in auto/DHU) · **Rischio:** medio
**Stato:** PROPOSTO (2026-11-09) — decisioni chiuse il 2026-11-09 (v. §4); codice non ancora toccato
**Sostituisce:** `plans/009-aaos-full-support.md` (supporto AAOS-only), ritirato lo stesso giorno: **non** vogliamo un'app Android Automotive, vogliamo che Andropausa funzioni bene su **Android Auto** (proiezione dal telefono).

## Risposta breve

Andropausa resta **un'app telefono**. Android Auto è una superficie di proiezione: l'app espone il
`MediaLibraryService` e l'albero browsable, l'host disegna la UI. Questa impalcatura è già stata
costruita e consolidata dal piano 008 — non serve un flavor nuovo, una UI templated, una
dipendenza nuova o un artefatto separato.

Il lavoro che resta è quindi **taratura e verifica**, non architettura:

1. **verificare davvero in auto** ciò che finora è solo compilato (la 008 lascia aperti proprio i
   due criteri "browsing end-to-end" e "download + ripresa senza UI telefono");
2. chiudere i residui che si vedono solo su strada: metadati mancanti, albero che resta sul server
   precedente dopo un cambio server, errori non azionabili, comportamento con Cast attivo;
3. decidere cosa fare della **dichiarazione AAOS** aggiunta dalla 008 (D1): se non vogliamo un'app
   AAOS, va rimossa dai manifest di flavor.

## 1. Perimetro

**Dentro**

- Proiezione **Android Auto** su auto reale e su **DHU** (Desktop Head Unit).
- Albero, ricerca (testuale e vocale), ripresa coda, nodo Scaricati, copertine, comandi di sessione.
- Metadati e messaggi mostrati dall'host, gestione errori/offline, invalidazione dell'albero.
- Pulizia della dichiarazione AAOS e allineamento documentazione.

**Fuori**

- Artefatto **AAOS-only**, track Play "AAOS only", settings activity sull'auto.
- **Car App Library / template** (`MediaPlaybackTemplate`): richiede dipendenza nuova, API in RC ed
  è in beta su Play. Se un giorno servisse, sarà un piano dedicato.
- UI dell'app sul display dell'auto senza telefono; download avviabili dall'auto.
- Nuove dipendenze, nuovi flavor, cambi a `versionCode`/`applicationId`/firma.

## 2. Stato attuale (eredità della 008)

| Elemento | Dove | Stato |
|---|---|---|
| Dichiarazione media app Android Auto | `main/AndroidManifest.xml` → `com.google.android.gms.car.application` + `res/xml/auto_app_desc.xml` | ✅ |
| `MediaLibraryService` + azioni `MediaLibraryService`/`MediaBrowserService`/`MEDIA_PLAY_FROM_SEARCH` | manifest `main`, `service/MediaService.kt` per flavor | ✅ |
| Albero browsable `Home / Library / Downloads` | `service/MediaBrowserTree.kt` (in `main`) | ✅ localizzato |
| Callback sessione, paging, shuffle | `service/MediaLibraryServiceCallback.kt` (in `main`) | ✅ |
| Ricerca con conteggio reale dei risultati | `onSearch` (due richieste per query, pattern Media3) | ✅ |
| Ripresa coda dall'auto | `onPlaybackResumption` + `AutomotiveRepository.getPlaybackResumption` | ✅ (posizione 0) |
| Nodo Scaricati anche senza server | `AutomotiveRepository.getDownloads` | ✅ |
| Copertine senza credenziali | URL pubblici + `BitmapLoader` autenticato lato app | ✅ codice, ❌ resa mai vista in auto |
| Errori `LibraryResult` sempre completati | `AutomotiveRepository` (`completeError`/`completeFailure`) | ✅ |
| Sessione activity | `MediaSession.Builder.setSessionActivity(...)` verso la UI telefono | ✅ |
| Dichiarazione AAOS | manifest di flavor `tempo`/`notquitemy`, `required="false"` | ⚠️ da decidere (D1) |
| Verifica su auto/DHU | — | ❌ **mai eseguita** |

Nessun test nel repo: la verifica minima resta la compilazione; quella funzionale è manuale.

## 3. Gap e ottimizzazioni (ordinati per impatto)

### A1 — Verifica reale mai fatta (P0)
Tutto ciò che riguarda la resa in auto (copertine, tab, tempi di caricamento, voce, ripresa) è
compilato ma non osservato. Senza questa verifica il piano non può chiudersi: è la prima attività,
non l'ultima.

**Stato verifiche (2026-11-09):** né DHU né auto reale sono disponibili ora, quindi la verifica è
**differita**. Le fasi di solo codice (F2, F3, F5) possono procedere; F1 (preparazione), F4 e F6
restano aperte con checklist pronta. Il piano non va dichiarato concluso senza verifica.

### A2 — Albero non invalidato al cambio server/logout (P1)
Android Auto tiene in cache i figli dei nodi. `Preferences.switchInUseServerAddress` /
`setServerId` e il logout in `LoginFragment` cambiano il server, ma l'host continua a mostrare i
contenuti del server precedente finché non si riconnette.
**Fix proposto:** `MediaManager.notifyLibraryChanged()` statico → se la
`MediaLibrarySession` è viva, `notifyChildrenChanged(rootId)` sul main thread; chiamato dai punti
di cambio server e di logout. Da non confondere con il refresh della UI telefono.

### A3 — Metadati incompleti nella lista auto (P2)
`MappingUtil` popola titolo, artista, album, genere, track/disc/year; **la durata no**
(`setDurationMs` non è usato né lì né in `SessionMediaItem.getMediaItem()`). Nelle liste l'host non
può mostrare la durata e la coda appare "senza tempo".
**Fix proposto:** passare la durata (secondi → millisecondi) dove il dato esiste (`Song.duration`,
`Download`), lasciando `0`/omesso quando manca.

### A4 — Errori non azionabili (P2)
- App non configurata: item non browsable "Configura sull'app" — testo giusto, ma nessuna azione.
- Server irraggiungibile: errore `RESULT_ERROR_IO` propagato; da verificare che l'host mostri un
  messaggio e non una lista vuota.
**Valutare:** messaggio distinto per "non configurato" vs "server irraggiungibile" (due stringhe) e,
se Media3 1.5.1 lo consente, azione di risoluzione che apre la UI telefono (session activity).
In assenza di supporto, **documentare** il limite: Android Auto non permette la configurazione da
fermo come AAOS.

### A5 — Cast attivo e Android Auto (P2, decisione D5)
Con una sessione Cast in corso l'auto comanda il ricevitore, non il telefono: comportamento attuale
(decisione D6 della 008) ma non comunicato. Da decidere: lasciarlo e documentarlo, oppure evitare
che l'auto mostri una sessione "telefonica" quando il Cast è attivo.
Nessuna modifica al Cast player senza misura: è la parte più delicata dei flavor `tempo`/`play`.

### A6 — Ricerca vocale (P2, verifica)
L'azione `MEDIA_PLAY_FROM_SEARCH` è dichiarata nel manifest. Da verificare in auto che "riproduci <album/brano> su Andropausa" produca risultati e avvii la riproduzione, e che una query senza risultati
dia un messaggio invece del silenzio. Se Media3 non copre il caso, valutare un
`onPlayFromSearch` esplicito che riusa `MediaBrowserTree.search` — solo dopo la verifica.

### A7 — Latenza della prima pagina (P3, da misurare)
`Home`/`Random`/`Recent songs` chiedono fino a 100 item al server **prima** di servire la prima
pagina all'host; la paginazione è poi lato client (`pageOf`). Con server lenti la prima schermata
in auto può sembrare ferma.
**Ipotesi:** ridurre il tetto (es. 50) o, dove l'API lo consente (`getAlbumList2` ha `size`/`offset`),
mappare `page`/`pageSize` sul server. Da fare **dopo** la misura con DHU, non a naso.

### A8 — Copertine: `artworkData` vs `artworkUri` (residuo 008)
La sessione fornisce `artworkData` tramite `DownloadUtil.buildArtworkBitmapLoader()`; se un host
ignora i bitmap e carica l'URI pubblico, la richiesta senza token risponde 401 e appare il
segnaposto. Va verificato in auto; se succede, si applica il fallback già documentato (URI con
credenziali **solo** per gli item esposti all'auto, annotandolo in `docs/ARCHITECTURE.md` §6).
Non toccare il percorso Glide/telefono.

### A9 — Scrobble, cronologia, ripresa (verifica)
Un brano scelto in auto deve comparire in "Recentemente riprodotto", contare nello scrobble e
alimentare la ripresa; shuffle/ripetizione e controlli della notifica devono restare coerenti con
quelli del telefono. Sono comportamenti agganciati agli stati del player in `MediaService`: da
verificare, non da riscrivere.

### A10 — Dichiarazione AAOS (decisione D1)
La 008 ha aggiunto `com.android.automotive` + `uses-feature automotive required="false"` nei
manifest di `tempo`/`notquitemy` per permettere il sideload su AAOS. Se l'obiettivo è solo Android
Auto, queste righe sono rumore: non danno nulla all'auto proiettata e suggeriscono un supporto che
non vogliamo mantenere. **Proposta: rimuoverle** (2 file) e aggiornare `docs/BUILD.md` §2.1.1,
`docs/ARCHITECTURE.md` §6, `AGENTS.MD` §6, `README.md`. `com.google.android.gms.car.application`
resta: è la dichiarazione Android Auto, in `main`, per tutti i flavor.

## 4. Decisioni chiuse (2026-11-09)

| ID | Domanda | Scelta |
|---|---|---|
| D1 | Dichiarazione AAOS nei flavor `tempo`/`notquitemy` | **Rimuoverla** (A10): niente app AAOS, niente supporto implicito. Restano `com.google.android.gms.car.application` e `auto_app_desc.xml`, che sono Android Auto. |
| D2 | Dove si verifica | **Nessuna verifica disponibile ora** (né DHU né auto): checklist e procedura pronte, F1/F4/F6 differite. Le fasi di solo codice procedono. |
| D3 | Durata nei metadati | **Aggiungerla** dove il dato esiste (A3). |
| D4 | Invalidazione albero | **Implementare** `MediaManager.notifyLibraryChanged()` (A2). |
| D5 | Cast + auto | **Documentare** il comportamento attuale; nessuna modifica al percorso Cast. |
| D6 | Latenza lista | **Ridurre a 50** il tetto dei nodi che oggi chiedono 100 (A7), con tetto documentato; paging lato server valutato in futuro, non ora. |
| D7 | Errori non azionabili | **Due messaggi distinti** (non configurato / server irraggiungibile); intent di risoluzione solo se Media3 1.5.1 lo supporta. |
| D8 | Ricerca vocale | **Verificare**; l'override `onPlayFromSearch` si introduce solo se la verifica mostra che la voce non funziona (evita di duplicare il percorso Media3). |

**Nota distribuzione:** Andropausa è distribuita solo via F-Droid/GitHub, quindi non ci sono vincoli di
quality review Play su questo piano. Il flavor `play` resta com'è; la pulizia AAOS riguarda
`tempo`/`notquitemy` e non tocca `applicationId` né `versionCode`.

## 5. Fasi

### Fase 0 — Baseline e strumenti
- Branch dedicato da `develop`; nessun cambio a `versionCode`/`applicationId`/firma.
- `task verify` (3 flavor) per la baseline verde.
- Documentare in `docs/BUILD.md` (sezione Android Auto) la procedura DHU: modalità sviluppatore
  nell'app Android Auto, "Unknown sources", `adb forward tcp:5277 tcp:5277`, avvio di
  `desktop-head-unit`. Oggi la procedura non è nel repo.

### Fase 1 — Preparazione della verifica (eseguibile ora)
- Procedure DHU e checklist §6 in `docs/BUILD.md` (modalità sviluppatore, "Unknown sources",
  `adb forward tcp:5277 tcp:5277`, avvio di `desktop-head-unit`).
- Build installabile (`task compile FLAVOR=Tempo` qui; `assembleTempoDebug` su macchina adeguata).
- La verifica vera e la chiusura dei due criteri aperti della 008 (browsing end-to-end; offline +
  ripresa senza UI telefono) sono **differite** finché non c'è DHU/auto.

### Fase 2 — Metadati, presentazione e latenza (A3, A7, A8)
- Durata (secondi → millisecondi) in `MappingUtil` (browse, ricerca, download) e in
  `SessionMediaItem.getMediaItem()`; `0`/omessa se `Song.duration` è nullo.
- Latenza: i nodi che oggi chiedono 100 item (`MOST_PLAYED`, `LAST_PLAYED`, `RECENTLY_ADDED`,
  `RECENT_SONGS`, `RANDOM`) scendono a 50. È l'unico intervento di latenza eseguibile senza DHU;
  il tetto va documentato (oltre 50 il nodo non mostra altro) e il paging del callback resta
  invariato.
- Copertine: la resa va verificata in auto; se l'host ignora `artworkData`, applicare il fallback
  documentato (URI con credenziali **solo** per gli item esposti all'auto).
- Sottotitoli/tipi media già a posto: non reintrodurre `FOLDER_MIXED` dove serve un tipo specifico.

### Fase 3 — Affidabilità della sessione (A2, A4, A5)
- `MediaManager.notifyLibraryChanged()` + chiamate da cambio server (`MainActivity`,
  `Preferences.setServerId`) e logout (`LoginFragment`).
- Messaggi distinti per "non configurato" e "server irraggiungibile" (`values` + `values-it`).
- Se supportato da Media3 1.5.1, azione di risoluzione verso la session activity; altrimenti nota in
  `docs/ARCHITECTURE.md` §6.
- Comportamento Cast documentato (D5).

### Fase 4 — Ricerca vocale (A6, differita)
- Test vocale su DHU/auto ("riproduci <album/brano> su Andropausa"), incluso il caso senza risultati.
- Solo se la ricerca non parte: `onPlayFromSearch` che riusa `MediaBrowserTree.search` e accoda i
  risultati. Senza verifica manuale non si introduce l'override: rischia di duplicare il percorso
  già gestito da Media3.
- Tetto 20+20+20 invariato; nessuna seconda query oltre a quella già documentata.

### Fase 5 — Pulizia AAOS e documentazione (D1, confermata)
- Rimozione di `com.android.automotive` e `uses-feature automotive` dai manifest
  `tempo`/`notquitemy`; `auto_app_desc.xml` resta perché usato da Android Auto.
- Aggiornare nello stesso commit: `AGENTS.MD` §6, `docs/BUILD.md` §2.1.1, `docs/ARCHITECTURE.md` §6,
  `README.md` (Android Auto, non AAOS), `plans/README.md`, `plans/008-android-auto.md` (nota che la
  Fase 6 AAOS è stata ritirata dal 009).
- `plans/009-aaos-full-support.md` non deve restare nel repo una volta confermata la direzione.

### Fase 6 — Verifica sul campo e chiusura (differita)
- `task verify` (3 flavor) + `task docs:check` + `git diff --check` eseguibili ora.
- Checklist §6 rifatta per intero dopo l'ultima modifica, quando DHU/auto saranno disponibili;
  criteri §7 spuntati solo con evidenza, mai per deduzione.

## 6. Verifica

```bash
# tocca service/ e manifest -> tutti e tre i flavor
task verify            # 3 flavor, prefix qemu automatico su host aarch64
task compile FLAVOR=Tempo
task docs:check
```

**Stato:** nessun DHU/auto disponibile al 2026-11-09 — la checklist è pronta ma **non eseguita**.
I comandi `task` restano la verifica eseguibile su questo host.

Checklist manuale (DHU, auto reale dove possibile):

- [ ] L'app appare tra le media app di Android Auto e si apre **senza** avviare la UI sul telefono.
- [ ] Tab `Home / Library / Downloads`, etichette nella lingua del telefono.
- [ ] Browse completo: root → 3 livelli → brano; album/playlist accodano tutto, non solo il primo.
- [ ] Ricerca testuale: risultati pertinenti, paginati, conteggio reale (nessun "0" fasullo).
- [ ] Ricerca vocale "riproduci …" → risultati e riproduzione (o messaggio, mai silenzio).
- [ ] Copertine visibili nella lista e nella schermata ora in riproduzione.
- [ ] Cambio server e logout: l'albero in auto si aggiorna, niente contenuti del vecchio server.
- [ ] Server spento / app non configurata: messaggio chiaro, nessun caricamento infinito.
- [ ] Modalità aereo + brani scaricati: riproduzione offline dal nodo Downloads.
- [ ] App chiusa + coda salvata: l'auto propone la ripresa dal brano corrente.
- [ ] Shuffle dal volante/notifica: nessun crash, stato coerente col telefono.
- [ ] Brano ascoltato in auto → compare in "Recentemente riprodotto" e viene scrobbled.
- [ ] Durata visibile nella lista (dopo A3).

## 7. Criteri di done

- [ ] Checklist §6 eseguita almeno su DHU, con esito annotato nel diario (**bloccata**: nessun DHU
  disponibile al 2026-11-09).
- [ ] Nessuna regressione sui 3 flavor (`task verify` verde) e `task docs:check` verde.
- [ ] Cambio server/logout invalida l'albero in auto (se D4 confermata).
- [ ] Metadati con durata (se D3 confermata).
- [ ] Messaggi di errore distinti e comprensibili dall'auto.
- [ ] Dichiarazione AAOS rimossa o esplicitamente mantenuta (D1) e documentazione coerente.
- [ ] Nessuna nuova dipendenza, nessun nuovo flavor, nessun cambio a versioni/firma.

## 8. Rischi

- **Verifica in auto**: senza DHU/auto reale il piano resta "compilato ma non provato"; è il rischio
  principale e va dichiarato, non aggirato. Al 2026-11-09 la verifica non è disponibile.
- **DHU su host aarch64**: questo ambiente non ha emulatore/AA SDK; Fase 1, 4 e 6 sono per la
  macchina dell'utente o la CI.
- **Invalidazione albero**: chiamare `notifyChildrenChanged` dal thread sbagliato o con sessione
  morta; deve essere no-op se la sessione non è attiva.
- **Cast**: qualsiasi modifica al percorso Cast ricade su `tempo` e `play` e sul comportamento
  documentato dalla 008; preferire documentare a modificare (D5).
- **Paginazione lato server** (A7): `getAlbumList2` supporta `size`/`offset` ma il repository oggi
  restituisce liste complete; un cambio di semantica tocca il paging del callback e va misurato.
- **Durata**: `Song.duration` è in secondi e può essere `null`; `setDurationMs` con 0 è comunque
  meglio di un valore inventato.

## 9. Riferimenti

- Android Auto per media app: <https://developer.android.com/training/cars/apps/media>
- Media3 `MediaLibraryService`: <https://developer.android.com/media/media3/session/background-playback>
- Desktop Head Unit: <https://developer.android.com/training/cars/testing/dhu>
- Qualità/distrazione (Android Auto): <https://developer.android.com/docs/quality-guidelines/car-app-quality>

## 10. Diario

**2026-11-09 — Decisioni chiuse.**
- D1 rimozione dichiarazione AAOS; D3 durata; D4 invalidazione albero; D5 Cast solo documentato;
  D6 tetto 100 → 50; D7 due messaggi d'errore; D8 voce da verificare prima di ogni override.
- D2: verifica in auto **differita** (né DHU né auto disponibili ora). F2/F3/F5 eseguibili senza
  auto; F1 (preparazione), F4 e F6 restano aperte.
- Distribuzione: solo F-Droid/GitHub, nessun vincolo Play da considerare in questo piano.

**2026-11-09 — Piano riscritto.**
- Direzione cambiata su richiesta: niente app AAOS-only; obiettivo è l'ottimizzazione di Andropausa su
  Android Auto (proiezione). Ritirato `plans/009-aaos-full-support.md`, che proponeva flavor
  `aaos`, artifact Play AAOS-only e settings in auto.
- La base tecnica resta quella della 008 (albero condiviso, paging, ricerca, ripresa, offline,
  artwork senza credenziali): il piano 009 si limita a verifica, rifiniture e pulizia AAOS.
- Nessun file di codice toccato.
