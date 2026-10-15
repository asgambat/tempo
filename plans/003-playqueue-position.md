# Plan 003 — `savePlayQueue` con posizione reale del player

**Base commit:** `50365b8c` · **Categoria:** correttezza (parità con altri client Subsonic) · **Sforzo:** S · **Rischio fix:** basso

## Problema

`app/src/main/java/com/cappielloantonio/tempo/viewmodel/PlayerBottomSheetViewModel.java:224-234`:

```java
public boolean savePlayQueue() {
    Child media = getLiveMedia().getValue();
    List<Child> queue = queueRepository.getMedia();
    List<String> ids = queue.stream().map(Child::getId).collect(Collectors.toList());

    if (media != null) {
        queueRepository.savePlayQueue(ids, media.getId(), 0);   // ← position sempre 0
        return true;
    }
    return false;
}
```

L'endpoint Subsonic `savePlayQueue` accetta `position` (ms): inviando sempre `0`, il ripristino server-side della coda (usato da altri client Subsonic per continuare l'ascolto dal punto esatto) perde sempre l'offset. Il chiamante è `PlayerCoverFragment.java:140` (azione utente "salva coda").

## Stato dei file coinvolti

- `viewmodel/PlayerBottomSheetViewModel.java` — ha già `liveMedia` (`MutableLiveData<Child>`, righe ~53) perché è l'unico posto dove il bottom sheet conosce il brano in riproduzione. Non contiene **nessun riferimento al player/ExoPlayer**: non ha la posizione di playback.
- `ui/fragment/PlayerCoverFragment.java` — fragment del bottom sheet, dove vive il binding del player. Contiene già i riferimenti a `playerBottomSheetViewModel` e al `MediaBrowserListenableFuture` (cercare `mediaBrowserListenableFuture` nel file).

## Convenzioni

- File Java, ViewModel `AndroidViewModel` senza player: **non** introdurre riferimenti diretti a ExoPlayer nei ViewModel (violazione architetturale: il player passa per `MediaManager`, `AGENTS.md` §4).
- Per leggere la posizione dal browser si usa il pattern esistente di `MediaManager.getCurrentIndex(future, callback)` (`interfaces/MediaIndexCallback`).

## Passi

1. In `service/MediaManager.java` aggiungere, accanto a `getCurrentIndex()`:
   ```java
   public static void getCurrentPosition(ListenableFuture<MediaBrowser> future, MediaIndexCallback callback)
   ```
   che in `onRecovery` passa `future.get().getCurrentPosition()` (long, ms). Riusare lo stesso `try/catch (ExecutionException | InterruptedException)` con `e.printStackTrace()` del metodo gemello.
2. In `interfaces/MediaIndexCallback.java`: l'interfaccia esistente porta `int onRecovery(int index)`. Per non rompere i chiamanti esistenti, aggiungere un metodo `default` nuovo:
   ```java
   default void onPositionRecovery(long positionMs) {}
   ```
   (le interfacce del package usano metodi default vuoti + `@Keep`, v. `AGENTS.md` §5.7).
3. In `PlayerBottomSheetViewModel.java`: cambiare `savePlayQueue()` in:
   ```java
   public void savePlayQueue(long positionMs)
   ```
   che chiama `queueRepository.savePlayQueue(ids, media.getId(), positionMs)`. Il valore di ritorno boolean: cercare come è usato in `PlayerCoverFragment.java:140` (`if (playerBottomSheetViewModel.savePlayQueue()) { ... }`) e conservare la logica del ramo `if` dentro la chiamata, passando la posizione.
4. In `PlayerCoverFragment.java`: al posto della chiamata diretta, usare:
   ```java
   MediaManager.getCurrentPosition(mediaBrowserListenableFuture, new MediaIndexCallback() {
       @Override
       public void onPositionRecovery(long positionMs) {
           // logica attuale di success + playerBottomSheetViewModel.savePlayQueue(positionMs)
       }
   });
   ```
   (se il future del browser non è già un campo di questo fragment, seguirne il pattern di fatto in altri punti del fragment: viene ricevuto da `MainActivity`/`MediaBrowser` connect in `onStart`.)
5. Se il ramo "success" mostrava un toast/feedback, mantenerlo dopo la chiamata async.

## Confini

- **In scope:** `service/MediaManager.java`, `interfaces/MediaIndexCallback.java`, `viewmodel/PlayerBottomSheetViewModel.java`, `ui/fragment/PlayerCoverFragment.java`.
- **Out of scope:** `repository/QueueRepository.savePlayQueue` (firma già corretta: `(List<String>, String, long)`), `BookmarksService`, ripristino della coda server-side (altro flusso, non toccarlo), piano 002.

## Verifica

```bash
./gradlew :app:compileTempoDebugJavaWithJavac
```
- Atteso: `BUILD SUCCESSFUL`.
- Manuale: riprodurre un brano, avanzare a metà, salvare la coda (azioni del bottom sheet), poi da un altro client (o chiamando `getPlayQueue` dal server, es. `curl ".../rest/getPlayQueue?..."`) verificare che `position` sia l'offset in ms e non 0, e che `current` sia il brano giusto.

## Criteri di done

- [ ] `savePlayQueue` non passa letteralmente `0` come posizione.
- [ ] La posizione inviata proviene da `getCurrentPosition` del browser (non da un campo cached non aggiornato).
- [ ] `MediaIndexCallback.onRecovery` esistenti funzionano invariato (metodo nuovo `default`).
- [ ] Compilazione OK.

## Manutenzione

- Il piano 006 (observer/retry) tocca `PlayerBottomSheetViewModel`/`PlayerCoverFragment`: eseguirlo **dopo** questo per non duplicare conflitti.
- Estrapolazione: se in futuro il salvataggio avviene anche automaticamente (es. onPause), riusare lo stesso `MediaManager.getCurrentPosition`.
