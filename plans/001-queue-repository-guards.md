# Plan 001 — Guardie su QueueRepository: NPE e IndexOutOfBounds

**Base commit:** `50365b8c` · **Categoria:** correttezza (crash) · **Sforzo:** S · **Rischio fix:** basso

## Problema

`app/src/main/java/com/cappielloantonio/tempo/repository/QueueRepository.java` ha due crash latenti:

1. `getLastPlayedMediaIndex()` e `getLastPlayedMediaTimestamp()` fanno `lastMediaPlayed.getTrackOrder()` / `lastMediaPlayed.getPlayingChanged()` **senza null-check**. Se la coda Room è vuota (o desincronizzata, v. punto 2) `queueDao.getLastPlayed()` restituisce null → `NullPointerException`. Entrambi i metodi vengono chiamati da `MediaManager.init()` (vedi `MediaManager.check()`) sul main thread: il crash colpisce semplicemente riaprendo l'app con coda stortata.
2. `insert(Child media, boolean reset, int afterIndex)` e `insertAll(List<Child>, boolean, int afterIndex)` fanno `mediaList.add(afterIndex, queueItem)`: `afterIndex` arriva dal player (`getMediaItemCount()` in `MediaManager.enqueue`), mentre la lista viene rilegata dal DB su un thread separato. Se i due conteggi desincronizzano (es. `deleteAll()` concorrente da `MediaManager.reset`, o un insert fallito) → `IndexOutOfBoundsException`.

## Stato attuale (esatti, da copiare come riferimento)

In `QueueRepository.java`:

```java
public int getLastPlayedMediaIndex() {
    int index = 0;
    GetLastPlayedMediaThreadSafe getLastPlayedMediaThreadSafe = new GetLastPlayedMediaThreadSafe(queueDao);
    Thread thread = new Thread(getLastPlayedMediaThreadSafe);
    thread.start();
    try {
        thread.join();
        Queue lastMediaPlayed = getLastPlayedMediaThreadSafe.getQueueItem();
        index = lastMediaPlayed.getTrackOrder();          // ← NPE se null
    } catch (InterruptedException e) { e.printStackTrace(); }
    return index;
}
```

(il corpo di `getLastPlayedMediaTimestamp()` è identico con `getPlayingChanged()`.)

```java
mediaList.add(afterIndex, queueItem);                     // ← in insert()
media.add(afterIndex + i, queueItem);                     // ← in insertAll()
```

## Convenzioni da rispettare

- File **Java**, stile legacy del repo (niente coroutine, niente lambda moderne oltre a quelle già presenti). Pattern `Thread + Runnable interno + thread.join()` di questo file fa parte del debito documentato in `AGENTS.md` §8: **non riscriverlo**, aggiungi solo le guardie.
- Log: `Log.d(TAG, ...)` dove il file già logga; per le guardie usa un `Log.i`/`Log.w` con `TAG = "QueueRepository"` (già definito nel file).

## Passi

1. **Guardia su `getLastPlayedMediaIndex()`**: dopo `Queue lastMediaPlayed = getLastPlayedMediaThreadSafe.getQueueItem();` aggiungere `if (lastMediaPlayed == null) { return 0; }` prima di leggere `getTrackOrder()`.
2. **Guardia su `getLastPlayedMediaTimestamp()`**: stessa identica guardia, `return 0L;` prima di leggere `getPlayingChanged()`.
3. **Guardia su `insert()`**: prima di `mediaList.add(afterIndex, queueItem)` clampare:
   ```java
   int safeIndex = Math.min(Math.max(afterIndex, 0), mediaList.size());
   mediaList.add(safeIndex, queueItem);
   ```
   (se `mediaList` è vuota, `safeIndex` = 0: `add(0, …)` su lista vuota è valido).
4. **Guardia su `insertAll()`**: prima del loop `for (int i = 0; i < toAdd.size(); i++) { media.add(afterIndex + i, …) }` clampare analogamente in una variabile `safeIndex = Math.min(Math.max(afterIndex, 0), media.size())` e usare `media.add(safeIndex + i, queueItem)` nel loop.
5. Aggiungere un `Log.w(TAG, "queue desync: clamping afterIndex …")` nei due clamp quando `safeIndex != afterIndex`, così i casi reali diventano osservabili in logcat.

## Confini

- **In scope:** solo `app/src/main/java/com/cappielloantonio/tempo/repository/QueueRepository.java`.
- **Out of scope:** riscrittura del pattern Thread/join (debt documentato), `AppDatabase`, DAO, ogni altro repository, `MediaManager` (è il piano 002).
- Non cambiare le firme pubbliche dei metodi.

## Verifica

```bash
./gradlew :app:compileTempoDebugJavaWithJavac
```
- Atteso: `BUILD SUCCESSFUL`, exit code 0.
- Verifica manuale facoltativa su device/emulatore: avviare l'app senza coda → nessun crash; impostare la coda, aggiungere un brano "play next" → aggiunto al posto giusto.

## Criteri di done (macchine-verificabili)

- [ ] `getLastPlayedMediaIndex()` e `getLastPlayedMediaTimestamp()` contengono un `if (lastMediaPlayed == null) return` e non dereferenziano senza check.
- [ ] Nessuna `List.add(int, E)` con indice non clampato in `insert()` / `insertAll()`.
- [ ] `./gradlew :app:compileTempoDebugJavaWithJavac` esce 0.

## Manutenzione

- Chi rifattorizza `QueueRepository` verso executor/async (debt target) deve portarsi le guardie dietro; annotare nel PR che il clamp protegge dal desync player↔DB.
- `MediaManager.reset()` chiama `clearDatabase()`: ogni nuovo percorso che cancella la coda rende il punto 1 più probabile, non meno.
