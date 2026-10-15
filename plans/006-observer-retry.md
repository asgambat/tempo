# Plan 006 — Observer accumulation in MainActivity + retry-on-failure nelle LiveData

**Base commit:** `50365b8c` · **Categoria:** robustezza · **Sforzo:** M · **Rischio fix:** basso
**Ordine:** eseguire **dopo** il piano 003 (condividono il flusso bottom-sheet).

## Problema

### 6a — Osservatori che si accumulano in `MainActivity`
`ui/activity/MainActivity.java:361-387`:

```java
private void pingServer() {
    if (Preferences.getToken() == null) return;

    if (Preferences.isInUseServerAddressLocal()) {
        mainViewModel.ping().observe(this, subsonicResponse -> {
            if (subsonicResponse == null) {
                Preferences.setServerSwitchableTimer();
                Preferences.switchInUseServerAddress();
                App.refreshSubsonicClient();
                pingServer();                    // ← ricorsione
            } else { ... }
        });
    } else {
        if (Preferences.isServerSwitchable()) {
            ... pingServer();
        } else {
            mainViewModel.ping().observe(this, subsonicResponse -> { ... });
        }
    }
}
```

- `mainViewModel.ping()` (in `viewmodel/MainViewModel.java`) crea una **nuova** LiveData a ogni chiamata, quindi ogni giro di ricorsione registra un nuovo observer sull'Activity; gli observer relativi alle LiveData scartate restano registrati fino a `onDestroy`. Leak limitato, ma a ogni ping fallito la catena si allunga e la logica diventa difficile da ragionare.
- Stesso schema in `getOpenSubsonicExtensions()` (`MainActivity.java:392-399`) e `checkTempoUpdate()`: chiamate singole, più accettabile, ma uniformarle con lo stesso helper rende il codice prevedibile.

### 6b — `onFailure` vuoto + impossibilità di retry
- Quasi tutti i repository hanno `onFailure` vuoto (stato di fatto documentato in `AGENTS.md` §8, che richiede di loggare nel codice nuovo).
- Caso aggravato dal diff in corso: `viewmodel/SongListPageViewModel.getSongList(LifecycleOwner)` ora usa il flag `songListRequested` (impostato a `true` alla prima richiesta). Se la richiesta di rete fallisce **prima** che la view venga ricreata (rotazione o back-navigation), al rientro **nessuna nuova richiesta parte**: la lista resta vuota senza possibilità di retry per tutta la vita del ViewModel.

## Passi

### 6a — pingServer con observe-once
1. In `MainActivity.java` aggiungere un piccolo helper observe-once:
   ```java
   private <T> void observeOnce(LiveData<T> liveData, Observer<T> onResult) {
       liveData.observe(this, new Observer<T>() {
           @Override
           public void onChanged(T value) {
               liveData.removeObserver(this);
               onResult.onChanged(value);
           }
       });
   }
   ```
   Nota: `androidx.lifecycle` non ha `observeOnce`: rimuovere manualmente l'observer alla prima callback come sopra è il pattern corretto.
2. Riscrivere `pingServer()` per usare `observeOnce` ed estrarre la logica di fallimento in un metodo privato `handlePingFailure()`, mantenendo **fedelmente** le due branch attuali (non cambiare la semantica del cooldown `isServerSwitchable` né dei timer di 15 s — regola `AGENTS.md` §8):
   - local → spegni timer, switch, refresh client, un solo nuovo ping (niente rimbalzi oltre il primo);
   - remote → switch solo se cooldown scaduto; se la rete è irraggiungibile mostrare `ServerUnreachableDialog` Come oggi.
3. Sostituire `.observe(this, ...)` con `observeOnce(..., ...)` anche in `pingServer()` (ramo remoto), `getOpenSubsonicExtensions()` e `checkTempoUpdate()`: sono letture single-shot, l'observer-once è la semantica giusta.
4. Verificare con grep che resti un'unica definizione di `pingServer` e nessuna chiamata ricorsiva oltre a quella voluta.

### 6b — log negli onFailure + retry in SongListPageViewModel
1. **Sweep meccanico dei log:** in ogni file `app/src/main/java/com/cappielloantonio/tempo/repository/*.java`, in ogni callback `onFailure` vuoto, aggiungere `Log.e(TAG, "request failed", t);` (il campo `TAG` esiste già in ogni repository; importare `android.util.Log` dove manca). Committare separatamente per una review leggibile.
2. **Retry in `SongListPageViewModel`:**
   - rimuovere il flag `songListRequested`;
   - rinominare `getSongList(LifecycleOwner owner)` in `requestSongList(LifecycleOwner owner)` che esegue il `switch (title)` attuale **a ogni chiamata** (i repository creano LiveData nuove per chiamata, quindi nessun doppiente inaspettato: la LiveData `songList` del ViewModel resta l'unica osservata dai fragment);
   - aggiornare `SongListPageFragment` (i due punti `getSongList(getViewLifecycleOwner())`, righe ~174 e ~194) per chiamare `requestSongList(...)`: a ogni `onViewCreated` la richiesta riparte e in caso di fallimento precedente la lista si ricarica.
3. Grep di sicurezza: `getSongList` è usato solo da `SongListPageFragment`; aggiornare ogni chiamante trovato.

## Confini

- **In scope:** `MainActivity.java`, `viewmodel/SongListPageViewModel.java`, `ui/fragment/SongListPageFragment.java`, `onFailure` dei repository di `app/src/main/java/com/cappielloantonio/tempo/repository/*.java` (solo aggiunta di log).
- **Out of scope:** `ServerUnreachableDialog`, `isServerSwitchable` e i timer (non rimuovere né accelerare), wrapping dei repository con `Result<…>` (candidato direzionale, altro piano), `viewmodel/MainViewModel.java` (lasciare che `ping()` produca LiveData nuove: è compatibile con l'helper observe-once).

## Verifica

```bash
./gradlew :app:compileTempoDebugJavaWithJavac
```
- Atteso: `BUILD SUCCESSFUL`.
- Manuale: attivare la modalità aereo → avviare l'app → aprire una schermata "brani per genere" → in logcat compaiono i log `E/<RepositoryName>` per le richieste fallite; disattivare la modalità aereo → uscire e rientrare nella schermata → la lista si carica (retry funziona). Verificare anche che il failover local→remote continui a funzionare come prima (ping server in modalità offline → switch sull'indirizzo alternativo).

## Criteri di done

- [ ] `pingServer()` non accumula observer: tutte le letture single-shot passano da `observeOnce`.
- [ ] Ogni `onFailure` vuoto nei repository logga con `Log.e(TAG, ..., t)`.
- [ ] `SongListPageViewModel` non ha più il blocco `songListRequested`: nuova richiesta a ogni creazione della view.
- [ ] Compilazione OK.

## Manutenzione

- Lo sweep dei log tocca tutti i repository: commit separato (`feat: log retrofit failures`).
- Quando si introdurrà un wrapper `Result<…>` nei repository, i log introdotti qui sono i punti da migrare.
