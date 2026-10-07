# Convenzioni di progetto — Tempo

> Come si scrive codice in questo repo. Le regole dure sono riassunte in [`AGENTS.MD`](../AGENTS.MD); questa pagina aggiunge esempi e motivazioni.

## 1. Principio guida

Il progetto è eterogeneo per storia (Java legacy + Kotlin recente). **La coerenza col file/pacchetto che si modifica vale più di qualsiasi convenzione astratta**: prima di aggiungere codice, guarda i 2–3 file vicini.

## 2. Java vs Kotlin

| Dominio | Linguaggio |
|---|---|
| UI (Fragment, Dialog, Adapter, Activity) | Java |
| ViewModel | Java |
| Repository | Java |
| DAO + `AppDatabase` | Java |
| Retrofit `XService`/`XClient` | Java |
| Modelli Subsonic (`subsonic/models/`) e locali (`model/`) | **Kotlin** (data class, Parcelable) |
| `Constants.kt`, `Preferences.kt`, `RetrofitClient.kt` | Kotlin |
| Servizi di flavor (`MediaService.kt`, `MediaBrowserTree.kt`, `MediaLibrarySessionCallback.kt`) | Kotlin |
| Utility recenti (`StreamingCacheDataSource.kt`, `DateConverters.kt`, `NestedScrollableHost.kt`) | Kotlin |

- Nei `.java`: solo sintassi **Java 8** (`jvmTarget 1.8`); `stream()` ammesso (minSdk 24 con desugaring AGP).
- Nei `.kt`: `object` per singleton (`Preferences`, `Constants`), `@JvmStatic` per l'interoperabilità con Java.

## 3. Naming delle classi

| Layer | Pattern | Esempio |
|---|---|---|
| Fragment pagina | `XFragment` | `SongListPageFragment` |
| Fragment pager | `XPagerFragment` | `LibraryPagerFragment` |
| Bottom sheet | `XBottomSheetDialog` | `SongBottomSheetDialog` |
| Dialog | `XDialog` | `PlaylistEditorDialog` |
| Adapter | `XAdapter` (Horiz./Vertical/Grid nel nome) | `SongHorizontalAdapter` |
| ViewModel | `XViewModel` | `SongListPageViewModel` |
| Repository | `XRepository` | `SongRepository` |
| Retrofit service/client | `XService` / `XClient` | `AlbumSongListService` / `AlbumSongListClient` |
| Entità Room / modello | singolare, nome dominio | `Queue`, `Child`, `AlbumID3` |
| Callback | `XCallback` | `ClickCallback`, `DialogClickCallback` |

## 4. Pattern Fragment (Java)

Struttura canonica (es. `SongListPageFragment`):

```java
@UnstableApi  // se tocca API Media3
public class XFragment extends Fragment implements ClickCallback {
    private static final String TAG = "XFragment";

    private FragmentXBinding bind;             // SEMPRE chiamato "bind"
    private MainActivity activity;             // cast diretto di getActivity()
    private XViewModel xViewModel;

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    @Override
    public View onCreateView(...) {
        activity = (MainActivity) getActivity();
        bind = FragmentXBinding.inflate(inflater, container, false);
        xViewModel = new ViewModelProvider(requireActivity()).get(XViewModel.class);

        init();            // legge requireArguments() e popola il ViewModel
        initAppBar();      // toolbar + back navigation
        initButtons();     // click listeners
        initListView();    // RecyclerView + adapter + observe

        return bind.getRoot();
    }

    @Override public void onStart() { super.onStart(); initializeMediaBrowser(); }
    @Override public void onStop()  { releaseMediaBrowser(); super.onStop(); }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;       // obbligatorio
    }
}
```

Regole:
- Callback asincrone → `if (bind != null)`.
- Navigazione: `activity.navController.navigateUp()` / `Navigation.findNavController(...)`.
- Il fragment implementa `ClickCallback` e riceve `Bundle` dagli adapter.
- Tastiera: `hideKeyboard(view)` helper locale (InputMethodManager).

## 5. Pattern ViewModel (Java)

```java
public class XViewModel extends AndroidViewModel {
    private final XRepository xRepository;      // istanziato nel costruttore
    public String title;                        // stato di pagina pubblico (popolato dal Fragment)

    public XViewModel(@NonNull Application application) {
        super(application);
        xRepository = new XRepository();
    }

    public LiveData<List<Child>> getData() { return xRepository.getX(); }
}
```

## 6. Pattern Repository (Java)

Vedi `ARCHITECTURE.md §4`. Regole:
- Un repository per dominio; metodi che ritornano `MutableLiveData` per le letture; `void` per le scritture remote "fire and forget".
- Null-check a cascata sulla risposta (`response.isSuccessful()`, `body()`, `getSubsonicResponse().getX()`, campo interno).
- Nel codice nuovo: `Log.e(TAG, ...)` in `onFailure`.
- Letture Room: metodi `LiveData` del DAO passati direttamente; scritture: `Thread` + `Runnable` (pattern legacy di `QueueRepository` — non estenderlo, usa thread espliciti e chiari).

## 7. Pattern Adapter (Java)

```java
@UnstableApi
public class XAdapter extends RecyclerView.Adapter<XAdapter.ViewHolder> implements Filterable {
    private final ClickCallback click;              // callback iniettata dal costruttore
    private List<Child> itemsFull;                  // lista completa per il filtro
    private List<Child> items;                      // lista visibile (filtrata/ordinata)

    public void setItems(List<Child> items) { ... filtering.filter(currentFilter); notifyDataSetChanged(); }
    public void sort(String order) { /* switch su Constants.X_ORDER_BY_* */ notifyDataSetChanged(); }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemXBinding item;                          // binding dell'item, chiamato "item"
        // click → Bundle con Constants.* → click.onXClick(bundle)
    }
}
```

- Ricerca: `Filter` interno + `getFilter()` (usato dai menu di ricerca).
- Ordinamenti: switch sulle costanti `*_ORDER_BY_*` di `Constants.kt`.
- Cover: sempre `CustomGlideRequest.Builder.from(ctx, coverArtId, ResourceType.X)`.

## 8. Costanti, Bundle, navigazione

- **Tutte** le chiavi Bundle e i "tipi lista" in `util/Constants.kt` (`TRACK_OBJECT`, `MEDIA_BY_GENRE`, `HOME_SECTOR_*`, `DOWNLOAD_*`, …). Nessuna stringa magica.
- Navigazione unica in `res/navigation/nav_graph.xml`; i bottom sheet sono destination con id dedicati (es. `songBottomSheetDialog`).
- Il passaggio di oggetti avviene con `Parcelable` (`putParcelable(Constants.ALBUM_OBJECT, album)`) o liste `putParcelableArrayList(Constants.TRACKS_OBJECT, ...)`.

## 9. Preferenze

```kotlin
object Preferences {
    private const val MY_PREF = "my_pref"          // chiave private const

    @JvmStatic fun isMyPrefEnabled(): Boolean = App.getInstance().preferences.getBoolean(MY_PREF, false)
    @JvmStatic fun setMyPrefEnabled(value: Boolean) { ... }
}
```

- Un solo punto di accesso; nessun `SharedPreferences` diretto fuori da `App`/`Preferences`.
- Impostazioni visibili → `res/xml/global_preferences.xml` (+ `SettingsFragment`/`SettingViewModel`).

## 10. Room

- Entità in `model/*.kt` con annotazioni `@Entity`, campi nullable; DAO Java con `@Dao`.
- Migrazioni: bump `version` + `@AutoMigration`; committare lo schema esportato in `app/schemas/com.cappielloantonio.tempo.database.AppDatabase/<n>.json`.
- Non chiamare DAO sync sul main thread.

## 11. Modelli Gson (`subsonic/models/*.kt`)

```kotlin
data class Child(
    val id: String? = null,
    val title: String? = null,
    ...
) : Parcelable   // quando serve navigare (kotlin-parcelize)
```

- Campi **nullable con default** (i server Subsonic omettono campi a piacere).
- Nessun `@SerializedName` custom se non necessario; nomi allineati al JSON Subsonic.

## 12. Risorse

| Tipo | Convenzione | Esempi |
|---|---|---|
| Layout pagina | `fragment_*`, `activity_*` | `fragment_song_list_page.xml` |
| Layout item | `item_*` | `item_horizontal_album.xml` |
| Dialog | `dialog_*`, `bottom_sheet_*_dialog` | `dialog_rating.xml` |
| Menu | `*_menu`, `*_popup_menu` | `toolbar_menu.xml`, `sort_song_popup_menu.xml` |
| Icone | `ic_*` | `ic_star.xml` |
| Stringhe | `snake_case`, prefisso dominio | `song_list_page_starred` |
| ID view | suffisso tipo | `pageTitleLabel`, `songCoverImageView`, `searchResultSongTitleTextView` |
| Nav/ID | camelCase | `songBottomSheetDialog` |

Colori/dimens/tipi in `values/colors.xml`, `values/dimens.xml`, `values/typography.xml`.

## 13. i18n

- `values/strings.xml` = fonte inglese (una stringa nuova deve esserci sempre).
- Traduzioni: `values-it`, `-de`, `-fr`, `-ko`, `-pt`, `-ru`, `-zh` — aggiornare `values-it` per i cambiamenti propri.
- `xml/locale_config.xml` dichiara le lingue; non aggiungere lingue senza file tradotti completi.
- Changelog per release F-Droid: `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.

## 14. Commit

- Prefissi osservati: `feat:`, `fix:`, `gradle:` (build/dipendenze). Subject inglese breve e imperativo.
- Un tema per commit; dependency bump separati dallo sviluppo feature.

## 15. Minificazione/ProGuard

- Release: `minifyEnabled true` + `shrinkResources true`.
- Regole custom in `app/proguard-rules.pro`: `keep` per `retrofit2.**`, `TypeToken`, eccezioni, `SourceFile/LineNumberTable`.
- Gson + riflessione: nuovi modelli serializzati via Gson non devono essere rinominati in release senza verificare (i data class Kotlin con campi standard passano; attenzione a interfacce/annotazioni rimosse dal minifier).
