# Plan 004 — Connectivity: `NetworkCallback` al posto del broadcast morto

**Base commit:** `50365b8c` · **Categoria:** correttezza (feature UI non funzionante) · **Sforzo:** S · **Rischio fix:** basso

## Problema

`ui/activity/MainActivity.java:347-354`:

```java
private void connectivityStatusReceiverManager(boolean isActive) {
    if (isActive) {
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(connectivityStatusBroadcastReceiver, filter);
    } else {
        unregisterReceiver(connectivityStatusBroadcastReceiver);
    }
}
```

`ConnectivityManager.CONNECTIVITY_ACTION` è deprecato dall'API 28: con `targetSdk 35` il broadcast **non viene più consegnato** nemmeno ai receiver registrati a runtime. Conseguenze attuali:

- `broadcast/receiver/ConnectivityStatusBroadcastReceiver.java` è dead code;
- `offlineModeTextView` in `activity_main.xml` non può mai apparire/sparire — anche quando l'app è offline il banner resta nascosto.

## Stato attuale

- `ConnectivityStatusBroadcastReceiver.java` (intero, 30 righe): legge `EXTRA_NO_CONNECTIVITY` e imposta la visibility di `activity.bind.offlineModeTextView`.
- `MainActivity`: campo `ConnectivityStatusBroadcastReceiver connectivityStatusBroadcastReceiver;` (riga ~59), creato in `onCreate` (riga ~74) e gestito da `connectivityStatusReceiverManager(true/false)` — cercare la chiamata `false` per capire dove viene deregistrato (dovrebbe essere `onDestroy()`).
- `util/NetworkUtil.java` esiste già e fa la check "point-in-time" corretta con API moderne (`getActiveNetwork()` + `NetworkCapabilities`): è il riferimento di stile da seguire.

## Convenzioni

- Java per MainActivity/receiver. Nessun permission nuovo: `NetworkCallback` requires `ACCESS_NETWORK_STATE`, già presente per l'uso corrente di `ConnectivityManager`.

## Passi

1. In `ui/activity/MainActivity.java`:
   - Aggiungere il campo `ConnectivityManager.NetworkCallback networkCallback;`
   - Rimpiazzare `connectivityStatusReceiverManager` con versioni equivalenti: `registerConnectivityCallback()` / `unregisterConnectivityCallback()` che chiamano rispettivamente:
     ```java
     NetworkRequest request = new NetworkRequest.Builder()
             .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
             .build();
     networkCallback = new ConnectivityManager.NetworkCallback() {
         @Override public void onAvailable(@NonNull Network network) { setOfflineBanner(false); }
         @Override public void onLost(@NonNull Network network) { runOnUiThread(() -> setOfflineBanner(Preferences.isInUseServerAddressLocal() || NetworkUtil.isOffline())); }
     };
     getConnectivityManager().registerNetworkCallback(request, networkCallback);
     ```
     e `unregisterNetworkCallback(networkCallback)`.
     Nota: NetworkCallback arriva su un thread handler di sistema; per toccare le view usare `runOnUiThread(...)`.
     Suggerimento logica banner: mostrare il banner quando `NetworkUtil.isOffline()` restituisce true; `onAvailable` → nascondere, `onLost` → valutare `NetworkUtil.isOffline()` (perché `onLost` scatta anche quando passa da wifi→cellulare).
2. Aggiungere il privato `private void setOfflineBanner(boolean visible)` che usa `bind != null` (il binding di MainActivity: `bind.offlineModeTextView.setVisibility(visible ? View.VISIBLE : View.GONE)`), seguendo la convenzione dei null-check del binding.
3. Aggiornare i punti dove `connectivityStatusReceiverManager(true/false)` era chiamato (`onCreate`: `true`; e dove è `false` — probabilmente `onDestroy`) con le nuove funzioni.
4. **Cancellare** `broadcast/receiver/ConnectivityStatusBroadcastReceiver.java` e il campo/import correlati in MainActivity (classe morta confermata). Se il file della classe viene cancellato, aggiornare chiunque lo referenzi (grep: solo MainActivity).
5. `AGENTS.md`: la mappa dei package al §4 cita `broadcast/receiver/` con "receiver (es. boot)". Se dopo la cancellazione la directory resta con altri receiver, niente da fare; se resta vuota, eliminare anche la menzione nel doc nello stesso commit.

## Confini

- **In scope:** `MainActivity.java`, cancellazione di `ConnectivityStatusBroadcastReceiver.java`, eventuale riga di `AGENTS.md` §4.
- **Out of scope:** rete del player (`DownloadUtil`, wake network di `MediaService`), failover server locale/remoto di `pingServer` (piano 006 per gli observer, ma la logica di failover non si tocca), `NetworkUtil.isOffline()` (già corretto).

## Verifica

```bash
./gradlew :app:compileTempoDebugJavaWithJavac
```
- Atteso: `BUILD SUCCESSFUL`.
- Manuale su device/emulatore: avviare l'app → attivare la modalità aereo → il banner "offline" appare; disattivare la modalità aereo → il banner sparisce.

## Criteri di done

- [ ] Nessun riferimento a `CONNECTIVITY_ACTION` nel progetto (`grep -r CONNECTIVITY_ACTION app/src` → 0 risultati).
- [ ] `ConnectivityStatusBroadcastReceiver.java` eliminato e nessun import rotto.
- [ ] Banner visibilità funzionante (test manuale sopra).
- [ ] Compilazione OK.

## Manutenzione

- `registerNetworkCallback` senza `FLAG` di ricezione: attenzione quando targetSdk salirà (per ora i callback di rete sono system, esente dalle flag `RECEIVER_EXPORTED` che riguardano i broadcast).
- Se in futuro serve reattività in `MediaService` (pause player quando offline trasparente), ispirarsi a questa callback invece di tornare ai broadcast.
