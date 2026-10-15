# Plan 005 — Logging senza credenziali (OkHttp level + redaction)

**Base commit:** `50365b8c` · **Categoria:** sicurezza / privacy · **Sforzo:** S · **Rischio fix:** basso

## Problema

Due client OkHttp nel progetto loggano a livello `BODY` **sempre** (anche in release):

- `app/src/main/java/com/cappielloantonio/tempo/subsonic/RetrofitClient.kt:50-52`:
  ```kotlin
  private fun getHttpLoggingInterceptor(): HttpLoggingInterceptor {
      val loggingInterceptor = HttpLoggingInterceptor()
      loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
      return loggingInterceptor
  }
  ```
- `app/src/main/java/com/cappielloantonio/tempo/github/GithubRetrofitClient.kt:25-27`: identico.

Credenziali nei query params: da `subsonic/Subsonic.java:146-156`, ogni richiesta porta `u`, e in casi diversi `p=salt:token` (low security), oppure `t=<md5>` + `s=<salt>`. Il token `t` + salt `s` **sono credenziali funzionali** (chi li conosce può ricostruire l'accesso, finché la password non cambia). A livello `BODY` la **URL intera** (query compresa) finisce nel logcat di ogni device, incluso in build release. AGENTS.md §8 lo segnala come debt "da task dedicato": questo è quel task.

## Convenzioni

- Kotlin per i due file client (restano Kotlin).
- Nessuna dipendenza nuova; `HttpLoggingInterceptor` è già lì.

## Passi

1. In `subsonic/RetrofitClient.kt`:
   - Cambiare il level in base al flag debug. Verifica semplice già disponibile senza dipendenze:
     ```kotlin
     val debuggable = App.getContext().applicationInfo.flags and
         android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
     loggingInterceptor.setLevel(if (debuggable) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE)
     ```
     ¨Nota: `Level.BASIC` logga la riga di richiesta **con l'URL completa, query compresa**, quindi `BASIC` non riduce l'esposizione delle credenziali: la redaction dell'URL è fragile e fragile da mantenere, per questo il piano non la implementa e sceglie NONE in release (passo 2).
2. **Nessun interceptor di redaction custom**: rimuovere `p`/`t`/`s` dall'URL della *richiesta reale* romperebbe l'autenticazione Subsonic, e intercettare il solo output del logger richiederebbe copiare `HttpLoggingInterceptor`: fuori scope. Scelta: release `NONE`, debug `BODY` come oggi (vice versa il developer vede già tutto il logcat locale).
3. In `github/GithubRetrofitClient.kt`: stessa transizione (release → `NONE`, debug → `BASIC` senza body; non ci sono credenziali qui, si può permettere `BASIC` in debug).
4. Nessun cambio di doc necessario oltre ad `AGENTS.md` §8 (in manutenzione): aggiornare la nota sull'interceptor nello stesso commit del piano.

## Confini

- **In scope:** `subsonic/RetrofitClient.kt`, `github/GithubRetrofitClient.kt`.
- **Out of scope:** l'aggiunta del param `p` in `Subsonic.getParams()` (convenzione Subsonic: serve per password obfuscated/low security); il login flow; ProGuard rules.

## Verifica

```bash
./gradlew :app:compileTempoDebugJavaWithJavac
```
- Atteso: `BUILD SUCCESSFUL`.
- Manuale debug: lanciare l'app e controllare che in logcat continui ad apparire l'output di rete (BUILD_DEBUG behavior invariato).
- Manuale release: nel flusso di build/verifica già usato dal maintainer (CI), confermare che il level diventi NONE — con questa change il codice dipende da `FLAG_DEBUGGABLE`, quindi basta leggere il codice; la verifica runtime release è opzionale ma raccomandata.

## Criteri di done

- [ ] `RetrofitClient.kt` e `GithubRetrofitClient.kt`: il level non è più la costante `Level.BODY` incondizionata; release == NONE.
- [ ] Nessuna dipendenza aggiunta.
- [ ] Compilazione OK.

## Manutenzione

- Se un domani si vuole logging in release per diagnosticare bug di rete, evitarlo: i query params dell'auth renderebbero il log non condivisibile. Considerare in quel caso un interceptor che redige esplicitamente i payload (es. libreria `chucker` — nuovo task, nuova dipendenza, da valutare separatamente).
- AGENTS.md §8 ("interceptor a BODY sempre attivo… eventuale fix come task dedicato") va aggiornato nello stesso commit di questo piano.
