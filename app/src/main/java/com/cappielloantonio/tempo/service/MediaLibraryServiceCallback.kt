package com.cappielloantonio.tempo.service

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.repository.AutomotiveRepository
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.AsyncFunction
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.atomic.AtomicBoolean

open class MediaLibraryServiceCallback(
    context: Context,
    automotiveRepository: AutomotiveRepository
) :
    MediaLibraryService.MediaLibrarySession.Callback {

    init {
        MediaBrowserTree.initialize(automotiveRepository)
    }

    private val customLayoutCommandButtons: List<CommandButton> = listOf(
        CommandButton.Builder()
            .setDisplayName(context.getString(R.string.exo_controls_shuffle_on_description))
            .setSessionCommand(
                SessionCommand(
                    CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_ON, Bundle.EMPTY
                )
            ).setIconResId(R.drawable.exo_icon_shuffle_off).build(),

        CommandButton.Builder()
            .setDisplayName(context.getString(R.string.exo_controls_shuffle_off_description))
            .setSessionCommand(
                SessionCommand(
                    CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_OFF, Bundle.EMPTY
                )
            ).setIconResId(R.drawable.exo_icon_shuffle_on).build()
    )

    @OptIn(UnstableApi::class)
    val mediaNotificationSessionCommands =
        MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
            .also { builder ->
                customLayoutCommandButtons.forEach { commandButton ->
                    commandButton.sessionCommand?.let { builder.add(it) }
                }
            }.build()

    @OptIn(UnstableApi::class)
    override fun onConnect(
        session: MediaSession, controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        if (session.isMediaNotificationController(controller) || session.isAutomotiveController(
                controller
            ) || session.isAutoCompanionController(controller)
        ) {
            val customLayout =
                customLayoutCommandButtons[if (session.player.shuffleModeEnabled) 1 else 0]

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(mediaNotificationSessionCommands)
                .setCustomLayout(ImmutableList.of(customLayout)).build()
        }

        return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
    }

    @OptIn(UnstableApi::class)
    override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
        if (controller.controllerVersion == 0) return

        // Il comando shuffle è disponibile solo per questi controller (v. onConnect): impostare il
        // layout anche sul MediaController del telefono spedirebbe un comando che non conosce.
        if (!session.isMediaNotificationController(controller) &&
            !session.isAutomotiveController(controller) &&
            !session.isAutoCompanionController(controller)
        ) return

        val customLayout = customLayoutCommandButtons[if (session.player.shuffleModeEnabled) 1 else 0]

        session.setCustomLayout(controller, ImmutableList.of(customLayout))
    }

    @OptIn(UnstableApi::class)
    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        if (CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_ON == customCommand.customAction) {
            session.player.shuffleModeEnabled = true
            updateShuffleLayout(session, true)

            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        } else if (CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_OFF == customCommand.customAction) {
            session.player.shuffleModeEnabled = false
            updateShuffleLayout(session, false)

            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
    }

    /**
     * Il comando shuffle arriva anche dal controller automotive, che tiene il layout ricevuto al
     * connect: aggiornare il solo controller della notifica lasciava l'icona sbagliata in auto.
     */
    @OptIn(UnstableApi::class)
    private fun updateShuffleLayout(session: MediaSession, shuffleEnabled: Boolean) {
        val customLayout =
            ImmutableList.of(customLayoutCommandButtons[if (shuffleEnabled) 1 else 0])

        for (controller in session.connectedControllers) {
            if (session.isMediaNotificationController(controller) ||
                session.isAutomotiveController(controller) ||
                session.isAutoCompanionController(controller)
            ) {
                session.setCustomLayout(controller, customLayout)
            }
        }
    }

    override fun onGetLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        return Futures.immediateFuture(LibraryResult.ofItem(MediaBrowserTree.getRootItem(), params))
    }

    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return Futures.transform(
            MediaBrowserTree.getChildren(parentId),
            { result -> pageOf(result, page, pageSize) },
            MoreExecutors.directExecutor()
        )
    }

    override fun onPlaybackResumption(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        return MediaBrowserTree.getPlaybackResumption()
    }

    // "Riproduci tutto" e "Shuffle" sono un solo item in libreria ma valgono l'intera raccolta
    // (cartella, album o playlist): qui si espandono nella playlist, come fa la demo Media3.
    @OptIn(UnstableApi::class) // MediaSession.MediaItemsWithStartPosition
    override fun onSetMediaItems(
        mediaSession: MediaSession,
        browser: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        // Un singolo item non espanso (un risultato di ricerca, un brano random) resta la coda
        // del player ma non sostituisce quella salvata: in telefono non deve sparire la coda
        // precedente per un brano toccato in auto.
        val expanded = AtomicBoolean(false)

        return syncStoredQueue(
            mediaSession,
            browser,
            resolveMediaItems(
                mediaSession,
                browser,
                mediaItems,
                startIndex,
                startPositionMs,
                expanded
            ),
            expanded
        )
    }

    private fun resolveMediaItems(
        mediaSession: MediaSession,
        browser: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
        expanded: AtomicBoolean
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        // L'espansione vale solo per i controller dell'auto: il telefono manda qui anche la
        // riproduzione di un singolo brano (SongBottomSheetDialog) e deve restare singola.
        if (mediaItems.size == 1 && isCarController(mediaSession, browser)) {
            val item = mediaItems.first()

            if (MediaBrowserTree.isCollectionAction(item.mediaId)) {
                return Futures.transform(
                    MediaBrowserTree.expandCollectionAction(item),
                    { items ->
                        if (items.isEmpty()) {
                            // Raccolta vuota o richiesta fallita (rete assente, cartella senza
                            // brani): meglio lasciare la coda com'è che azzerarla.
                            Log.w(TAG, "Empty expansion for ${item.mediaId}, keeping the queue")
                            currentQueue(mediaSession)
                        } else {
                            expanded.set(true)

                            MediaSession.MediaItemsWithStartPosition(items, 0, 0L)
                        }
                    },
                    MoreExecutors.directExecutor()
                )
            }

            // Il tap su un brano dentro una raccolta (cartella, album, playlist, download) deve
            // accodare anche gli altri brani: l'host qui chiede solo l'item toccato, quindi si
            // riparte dalla lista che gli è stata servita, con la posizione del brano toccato.
            val collection = MediaBrowserTree.expandPlayableItem(item)

            if (collection != null) {
                expanded.set(true)

                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(
                        collection.first,
                        collection.second,
                        startPositionMs
                    )
                )
            }
        }

        return super.onSetMediaItems(mediaSession, browser, mediaItems, startIndex, startPositionMs)
    }

    /**
     * L'host dell'auto imposta la coda direttamente sul player: la si salva, così il telefono
     * mostra e riprende la stessa coda invece di quella precedente.
     */
    private fun syncStoredQueue(
        mediaSession: MediaSession,
        browser: MediaSession.ControllerInfo,
        future: ListenableFuture<MediaSession.MediaItemsWithStartPosition>,
        expanded: AtomicBoolean
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        if (!isCarController(mediaSession, browser)) return future

        return Futures.transform(
            future,
            { result ->
                // Più brani sono comunque una coda nuova; un brano solo se viene dall'espansione
                // di una raccolta (album con un solo pezzo).
                if (expanded.get() || result.mediaItems.size > 1) {
                    MediaManager.syncQueue(result.mediaItems)
                } else {
                    Log.d(TAG, "Single item not expanded, keeping the stored queue")
                }

                result
            },
            MoreExecutors.directExecutor()
        )
    }

    /**
     * Coda in riproduzione, con la posizione corrente: serve a non fermare la musica quando
     * un'azione di raccolta non riesce a espandersi (la sessione riceve la stessa coda).
     */
    @OptIn(UnstableApi::class)
    private fun currentQueue(session: MediaSession): MediaSession.MediaItemsWithStartPosition {
        val player = session.player
        val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }

        return MediaSession.MediaItemsWithStartPosition(
            items,
            if (items.isEmpty()) 0 else player.currentMediaItemIndex.coerceIn(0, items.lastIndex),
            player.currentPosition
        )
    }

    private fun isCarController(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo
    ): Boolean =
        mediaSession.isAutomotiveController(controller) || mediaSession.isAutoCompanionController(
            controller
        )

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        val resolve = object : AsyncFunction<List<MediaItem>, List<MediaItem>> {
            override fun apply(items: List<MediaItem>): ListenableFuture<List<MediaItem>> =
                addMediaItems(mediaSession, controller, items)
        }

        return Futures.transformAsync(
            MediaBrowserTree.getItems(mediaItems),
            resolve,
            MoreExecutors.directExecutor()
        )
    }

    private fun addMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        return super.onAddMediaItems(mediaSession, controller, mediaItems)
    }

    override fun onSearch(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        // notifySearchResultChanged vuole il numero di risultati, che Media3 non conosce: la query
        // va eseguita qui. L'host la ripete poi in onGetSearchResult per la pagina (pattern Media3:
        // due richieste per ricerca). Prima si annunciavano 60 risultati fissi, anche quando la
        // ricerca ne trovava meno.
        return Futures.transform(
            MediaBrowserTree.search(query),
            { result ->
                val items = result.value

                if (result.resultCode == LibraryResult.RESULT_SUCCESS && items != null) {
                    session.notifySearchResultChanged(browser, query, items.size, params)
                    LibraryResult.ofVoid()
                } else {
                    // Nessun risultato annunciato e errore propagato: l'host mostra un messaggio
                    // invece di restare in caricamento.
                    session.notifySearchResultChanged(browser, query, 0, params)
                    LibraryResult.ofError(
                        if (result.resultCode == LibraryResult.RESULT_SUCCESS) {
                            LibraryResult.RESULT_ERROR_BAD_VALUE
                        } else {
                            result.resultCode
                        }
                    )
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    override fun onGetSearchResult(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return Futures.transform(
            MediaBrowserTree.search(query),
            { result -> pageOf(result, page, pageSize) },
            MoreExecutors.directExecutor()
        )
    }

    private fun pageOf(
        result: LibraryResult<ImmutableList<MediaItem>>,
        page: Int,
        pageSize: Int
    ): LibraryResult<ImmutableList<MediaItem>> {
        if (result.resultCode != LibraryResult.RESULT_SUCCESS) return result

        val items = result.value
            ?: return LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)

        if (pageSize <= 0) return result

        // Long: con pageSize == Int.MAX_VALUE (default Media3) page * pageSize andrebbe in overflow
        val from = page.coerceAtLeast(0).toLong() * pageSize
        if (from >= items.size) {
            return LibraryResult.ofItemList(ImmutableList.of(), result.params)
        }

        val to = minOf(from + pageSize, items.size.toLong())

        return LibraryResult.ofItemList(
            ImmutableList.copyOf(items.subList(from.toInt(), to.toInt())),
            result.params
        )
    }

    companion object {
        private const val TAG = "MediaLibraryServiceCallback"

        private const val CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_ON =
            "com.cappielloantonio.tempo.SHUFFLE_ON"
        private const val CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_OFF =
            "com.cappielloantonio.tempo.SHUFFLE_OFF"
    }
}