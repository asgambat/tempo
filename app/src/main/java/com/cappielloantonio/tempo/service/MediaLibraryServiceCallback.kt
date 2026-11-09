package com.cappielloantonio.tempo.service

import android.content.Context
import android.os.Bundle
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

    private var notificationController: MediaSession.ControllerInfo? = null

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
            if (session.isMediaNotificationController(controller)) {
                notificationController = controller
            }

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
            notificationController?.let { controllerInfo ->
                session.setCustomLayout(
                    controllerInfo,
                    ImmutableList.of(customLayoutCommandButtons[1])
                )
            }

            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        } else if (CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_OFF == customCommand.customAction) {
            session.player.shuffleModeEnabled = false
            notificationController?.let { controllerInfo ->
                session.setCustomLayout(
                    controllerInfo,
                    ImmutableList.of(customLayoutCommandButtons[0])
                )
            }

            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
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
        session.notifySearchResultChanged(browser, query, 60, params)
        return Futures.immediateFuture(LibraryResult.ofVoid())
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
        private const val CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_ON =
            "com.cappielloantonio.tempo.SHUFFLE_ON"
        private const val CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_OFF =
            "com.cappielloantonio.tempo.SHUFFLE_OFF"
    }
}