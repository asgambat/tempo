# Plan 002 — Robustezza playback/UI: leak MediaBrowser, mutazioni lista, subList asincrone, sort NPE residui

**Stato: DONE (2026-10-15)** · Base commit: `50365b8c` · **Categoria:** correttezza / leak · **Sforzo:** M · **Rischio fix:** basso-medio
**Verifica effettuata:** `compileTempoDebugJavaWithJavac` + `compileNotquitemyDebugJavaWithJavac` + `compilePlayDebugJavaWithJavac` → `BUILD SUCCESSFUL`, exit 0.
**Attenzione:** tocca `service/MediaManager.java` → verificare **tutti e tre i flavor** (regola `AGENTS.md` §3/§6).

## Problemi (4, tutti verificati)

### 2a — Leak `MediaBrowser` in `MediaManager.continuousPlay()`
`MediaManager.java:311-332`: a ogni "riprodurre mix simile in coda" viene costruito un secondo `MediaBrowser` (`new MediaBrowser.Builder(...).buildAsync()`) che **non viene mai rilasciato** (`disconnect()`, release del future): una connessione al `MediaService` persa a ogni evento. In più, l'`observeForever` self-removing resta appeso per sempre se la LiveData non emette (richiesta fallita — `onFailure` vuoto in `SongRepository.getInstantMix`).

### 2b — `MediaManager.removeDatabase()` muta la lista del chiamante
`MediaManager.java:366-372`: `media.remove(toRemove)` e in `removeRangeDatabase` `media.removeAll(...)`. La `media` passata da `PlayerBottomSheetFragment` spesso è la listaLiveData-backed già mostrata in RecyclerView: la mutazione senza `setValue` crea divergenza UI/DB e possibili `ConcurrentModificationException` durante il rebind.

### 2c — `subList` view passate ad API asincrone
- `PlaylistPageFragment.java:200,206`: `MediaManager.startQueue(future, songs.subList(0, Math.min(100, songs.size())), 0)`
- `SongListPageFragment.java:180`: `MediaManager.startQueue(mediaBrowserListenableFuture, songs.subList(0, Math.min(25, songs.size())), 0)`

`startQueue` itera la lista (via `MappingUtil.mapMediaItems`) e poi riterene `media` **in un listener eseguito dopo** (listener del future, `directExecutor`): se nel frattempo il parent viene mutato (sort dell'adapter, re-emit LiveData) → `ConcurrentModificationException`. Nota: `SongHorizontalAdapter:219` mostra la convenzione corretta già in uso: `new ArrayList<>(MusicUtil.limitPlayableMedia(...))`.

### 2d — Sort NPE residui (stessa classe di bug della campagna fix in corso)
- `ui/adapter/ArtistHorizontalAdapter.java:162`: `artists.sort(Comparator.comparing(ArtistID3::getName));` → NPE su nome null.
- `ui/adapter/GenreCatalogueAdapter.java:119`: `genres.sort(Comparator.comparing(Genre::getGenre));` → NPE su genere null.

## Diario dell'executor (una sola deviazione dal testo del piano)

Il passo 1 della sezione 2a ("riuso del future del browser corrente") non era attuabile come scritto: `continuousPlay` è chiamato solo da `MediaService.kt` (in `onTracksChanged`, quando si arriva all'ultimo brano), contesto in cui esiste il `Player` ma **nessun** `ListenableFuture<MediaBrowser>`. Deviazione attuata (più forte del previsto):

- Nuovo overload `MediaManager.enqueue(Player player, List<Child> media, boolean playImmediatelyAfter)` che opera direttamente sul `Player` (stessa logica di `enqueueDatabase` dell'overload su MediaBrowser).
- Firma `continuousPlay` → `continuousPlay(Player player, MediaItem mediaItem)`: niente builder MediaBrowser interno → **leak eliminato alla radice** (nessuna seconda connessione creata, nemmeno potenzialmente a lungo termine).
- Observer/`observeForever` sostituito con `SongRepository.fetchInstantMix(id, count, Consumer<List<Child>>)` (callback in `onResponse`, `Log.e` in `onFailure`): nessun observer leak.
- Rimozione degli import morti in MediaManager (ComponentName, SessionToken, App, LiveData, Observer) e dell'`@OptIn(UnstableApi)` sul metodo (ora usa solo API stabili).
- `SongRepository.getInstantMix(String id, int count)` (LiveData) resta e serve PlayerBottomSheetViewModel/SongBottomSheetViewModel/HomeViewModel: nessun chiamante toccato.
- I 3 `MediaService.kt` aggiornati alla nuova firma: `MediaManager.continuousPlay(player, player.currentMediaItem)`, hunk identico ×3.
- Copie difensive: `PlaylistPageFragment` ×2 (+ import `java.util.{ArrayList, List}`), `SongListPageFragment` (+ import ArrayList), `MusicUtil.limitPlayableMedia` restituisce una copia.
- Sort null-safe: `ArtistHorizontalAdapter` (`ArtistID3::getName`) e `GenreCatalogueAdapter` (`Genre::getGenre`).
- **Non compiuto (opzionale, fuori build):** verifiche runtime facoltative (dumpsys MediaService) e test su device.

### 2a — continuousPlay (passi originali del piano, eseguiti con la deviazione sopra)
1. In `MediaManager.continuousPlay()`, riutilizzare il future del browser corrente invece di crearne uno: cambiare la firma in `continuousPlay(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, MediaItem mediaItem)` e dentro l'observer chiamare `enqueue(mediaBrowserListenableFuture, media, true)`.
2. Aggiornare l'**unica** chiamata esistente (cercare `continuousPlay(` nel progetto; è invocata da `MediaService.kt` nei source set `tempo`, `notquitemy`, `play` — aggiornare **tutti e tre** i file per-flavor, mantenendo le differenze di Cast descritte in `AGENTS.md` §6).
3. Via scelta per questo piano: niente LiveData in `continuousPlay`. Aggiungere in `SongRepository.java` un nuovo metodo:
   ```java
   public void fetchInstantMix(String id, int count, Consumer<List<Child>> onSuccess)
   ```
   che fa `enqueue` con `onResponse` → null-check a cascata (stile del resto del repo: `response.isSuccessful() && response.body() != null && ...getSubsonicResponse() != null && ...getSimilarSongs2() != null`) e chiama `onSuccess.accept(songs)`; in `onFailure` un `Log.e(TAG, "fetchInstantMix failed", t)`. Usare `java.util.function.Consumer` (project uses Java 8 syntax, Consumer disponibile).
4. In `MediaManager.continuousPlay()` sostituire `getSongRepository().getInstantMix(...)` + `observeForever` con una chiamata diretta a `fetchInstantMix(mediaItem.mediaId, 10, media -> { if (media != null) enqueue(mediaBrowserListenableFuture, media, true); })`. Rimuovere l'observer interno e l'import `Observer` se resta inutilizzato.

### 2b — removeDatabase / removeRangeDatabase
1. In `MediaManager.removeDatabase()`: non mutare l'argomento. Ricostruire:
   ```java
   List<Child> remaining = new ArrayList<>(media);
   remaining.remove(toRemove);
   getQueueRepository().insertAll(remaining, true, 0);
   ```
2. In `removeRangeDatabase()`: sostituire `subList + removeAll` con `remaining = new ArrayList<>(media); remaining.removeIf(idx -> idx >= fromItem && idx < toItem)` — (rimane O(n) una volta; o un semplice loop che copia tranne il range; preferire `IntStream.range(0, media.size()).filter(...)`? No — resta Java semplice).
3. Nessuna firma pubblica cambia.

### 2c — copie difensive delle subList
1. `PlaylistPageFragment.java:200` e `:206`: avvolgere in `new ArrayList<>(songs.subList(0, Math.min(100, songs.size())))`.
2. `SongListPageFragment.java:180`: avvolgere in `new ArrayList<>(songs.subList(0, Math.min(25, songs.size())))`.
3. (Difensivo) `MusicUtil.limitPlayableMedia`: sostituire `return toLimit.subList(from, to);` con `return new ArrayList<>(toLimit.subList(from, to));` — così nessun futuro caller riceve una view.

### 2d — sort null-safe
1. `ArtistHorizontalAdapter.java:162` → `Comparator.comparing(ArtistID3::getName, Comparator.nullsLast(Comparator.naturalOrder()))`
2. `GenreCatalogueAdapter.java:119` → `Comparator.comparing(Genre::getGenre, Comparator.nullsLast(Comparator.naturalOrder()))`
   (identico allo stile già usato in `AlbumCatalogueAdapter.sort()`, che fa riferimento).

## Confini

- **In scope:** `service/MediaManager.java`, `repository/SongRepository.java`, i tre `service/Media*` per-flavor SOLO per l'aggiornamento della firma di `continuousPlay`, `ui/fragment/PlaylistPageFragment.java`, `ui/fragment/SongListPageFragment.java`, `util/MusicUtil.java`, `ui/adapter/ArtistHorizontalAdapter.java`, `ui/adapter/GenreCatalogueAdapter.java`.
- **Out of scope:** `MediaService.kt` al di là della chiamata a `continuousPlay`, buffering/ReplayGain, `QueueRepository` (piano 001), ricerca di altri leak.

## Verifica

```bash
./gradlew :app:compileTempoDebugJavaWithJavac :app:compileNotquitemyDebugJavaWithJavac :app:compilePlayDebugJavaWithJavac
```
- Atteso: `BUILD SUCCESSFUL` per tutti e tre.
- Manuale facoltativo: avviare un mix istantaneo (dall'app o da un brano in riproduzione "riproduci mix simili") → appare in coda; ripetere 3 volte → nessuna crescita di connessioni a `MediaService` (verificare con `adb shell dumpsys activity services | grep -c MediaService` che non cresca oltre).

## Criteri di done

- [ ] Nessun `new MediaBrowser.Builder` in `MediaManager` (riuso del future passato).
- [ ] `MediaManager.removeDatabase`/`removeRangeDatabase` non chiamano `.remove(...)`/`.removeAll(...)` sull'argomento `media`.
- [ ] Tutte le `subList(...)` passate a `MediaManager.startQueue` sono avvolte in `new ArrayList<>(...)`.
- [ ] `ArtistHorizontalAdapter` e `GenreCatalogueAdapter`: ogni `Comparator.comparing` porta `nullsLast`.
- [ ] I tre flavor compilano.

## Manutenzione

- La firma di `continuousPlay` cambia: chiunque aggiunga un quarto flavor deve aggiornarla; `AGENTS.md` §6 va aggiornato nello stesso commit se cambia la tabella flavor.
- Le copie difensive valgono fino al rifacimento delle code; non avvolgere l'intera app.
