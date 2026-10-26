# Plan 007 — "Riproduci con le sottocartelle" nella schermata cartella

**Base commit:** `dbf72428` · **Categoria:** feature · **Sforzo:** M · **Rischio:** basso
**Stato:** DONE (2026-10-25, `compileTempoDebugJavaWithJavac` exit 0; notquitemy/play in verifica)

## Problema

`DirectoryFragment` riproduceva solo i brani contenuti direttamente nella cartella aperta: per
ascoltare una discografia organizzata in sottocartelle (es. `Artista/Album/`) serviva aprire e
avviare ogni cartella. In più il click su un brano metteva in coda anche le voci `isDir`
presenti nello stesso elenco.

## Soluzione

- `DirectoryRepository.getSongsIncludingSubfolders(...)`: raccoglie i brani della cartella e, a
  seguire, quelli delle sottocartelle in ordine alfabetico (Collator, depth-first) fino a
  `Constants.DIRECTORY_SUBFOLDERS_MAX_DEPTH` (3). Tetti: `PLAYABLE_MEDIA_LIMIT` (100 brani,
  troncamento loggato) e `DIRECTORY_SUBFOLDERS_MAX_REQUESTS` (100 richieste). Le sottocartelle
  irraggiungibili vengono saltate.
- Il metodo restituisce un `SongsRequest`: `cancel()` interrompe la catena senza invocare la
  callback, e il fragment lo cancella in `onDestroyView()` e quando carica una nuova cartella.
- `DirectoryFragment`: pulsante tonale play nell'app bar, visibile solo se la cartella contiene
  sottocartelle, disabilitato durante la raccolta; se non c'è nulla da riprodurre mostra un
  Toast (`directory_page_no_playable_songs`).
- `MusicDirectoryAdapter`: click su un brano → coda composta dai soli brani della cartella
  (indice ricalcolato sul sottoinsieme); guardie `RecyclerView.NO_POSITION` su click/long-click.

## Verifica

```bash
./gradlew :app:compileTempoDebugJavaWithJavac
```
- Atteso: `BUILD SUCCESSFUL`.
- Manuale: cartella con brani e sottocartelle → il pulsante compare; premuto, la coda contiene i
  brani della cartella e delle sottocartelle. Cartella senza brani raggiungibili → Toast e coda
  invariata. Back durante la raccolta → nessuna accodamento a view distrutta.

## Criteri di done

- [x] Raccolta ricorsiva con limiti espliciti e ordinamento deterministico.
- [x] Richiesta cancellabile e collegata al ciclo di vita della view.
- [x] Feedback esplicito quando non c'è nulla da riprodurre.
- [ ] Compilazione dei tre flavor (tempo OK; notquitemy/play da confermare).
