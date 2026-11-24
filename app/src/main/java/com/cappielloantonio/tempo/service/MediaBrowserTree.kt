package com.cappielloantonio.tempo.service

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaSession
import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.repository.AutomotiveRepository
import com.cappielloantonio.tempo.util.Preferences
import com.cappielloantonio.tempo.util.Preferences.getServerId
import com.google.common.base.Function
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture

object MediaBrowserTree {

    private lateinit var automotiveRepository: AutomotiveRepository

    private var treeNodes: MutableMap<String, MediaItemNode> = mutableMapOf()

    private var isInitialized = false

    // Ultime raccolte servite all'host (directory, album, playlist, download, cartella musicale).
    // Android Auto, al tap su un singolo brano, chiede la riproduzione del solo item toccato:
    // la lista da cui proviene viene memorizzata qui per accodare il resto della raccolta,
    // nello stesso ordine mostrato a video (e senza una nuova richiesta al server).
    private const val BROWSED_COLLECTIONS_LIMIT = 12

    private val browsedCollections = LinkedHashMap<String, ImmutableList<MediaItem>>()

    // La ricerca dell'host arriva in due chiamate (onSearch per il conteggio, poi
    // onGetSearchResult per la pagina): la query si esegue una volta sola e l'esito resta qui,
    // altrimenti ogni ricerca produceva due richieste identiche a search3.
    private const val SEARCH_CACHE_LIMIT = 4

    private val searchCache = LinkedHashMap<String, ImmutableList<MediaItem>>()

    // Server con cui sono state popolate le due cache qui sopra: dopo un cambio server o un
    // logout gli item memorizzati portano mediaId ed extras di un altro catalogo.
    private var cachedServerId: String? = null

    // Root
    private const val ROOT_ID = "[rootID]"

    // Shown when no server has been configured yet
    private const val NOT_CONFIGURED_ID = "[notConfiguredID]"

    private const val TAG = "MediaBrowserTree"

    // Content style hint (stessi valori di androidx.media.utils.MediaConstants): nel ramo Cartelle
    // si forza la lista, così l'host di Android Auto non passa alla griglia, che non ha l'indice A-Z
    // nella barra di scorrimento.
    private const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
    private const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
    private const val CONTENT_STYLE_LIST_ITEM = 1

    // First level
    private const val HOME_ID = "[homeID]"
    private const val LIBRARY_ID = "[libraryID]"
    private const val DOWNLOADS_ID = "[downloadsID]"

    // Second level HOME_ID
    private const val MOST_PLAYED_ID = "[mostPlayedID]"
    private const val LAST_PLAYED_ID = "[lastPlayedID]"
    private const val RECENTLY_ADDED_ID = "[recentlyAddedID]"
    private const val RECENT_SONGS_ID = "[recentSongsID]"
    private const val STARRED_TRACKS_ID = "[starredTracksID]"
    private const val STARRED_ALBUMS_ID = "[starredAlbumsID]"
    private const val STARRED_ARTISTS_ID = "[starredArtistsID]"
    private const val RANDOM_ID = "[randomID]"

    // Second level LIBRARY_ID
    private const val FOLDER_ID = "[folderID]"
    private const val INDEX_ID = "[indexID]"
    private const val DIRECTORY_ID = "[directoryID]"
    private const val PLAYLIST_ID = "[playlistID]"

    // Podcast e radio (dentro LIBRARY_ID)
    private const val PODCAST_ID = "[podcastID]"
    private const val RADIO_ID = "[radioID]"

    private const val ALBUM_ID = "[albumID]"
    private const val ARTIST_ID = "[artistID]"

    // Azioni in testa a ogni cartella: accodano i brani della cartella e delle sottocartelle
    private const val PLAY_ALL_ID = "[playAllID]"
    private const val SHUFFLE_ALL_ID = "[shuffleAllID]"

    private class MediaItemNode(val item: MediaItem) {
        private val children: MutableList<MediaItem> = ArrayList()

        fun addChild(childID: String) {
            this.children.add(treeNodes[childID]!!.item)
        }

        fun getChildren(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val listenableFuture = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            val libraryResult = LibraryResult.ofItemList(children, null)

            listenableFuture.set(libraryResult)

            return listenableFuture
        }
    }

    private fun buildMediaItem(
        title: String,
        mediaId: String,
        isPlayable: Boolean,
        isBrowsable: Boolean,
        mediaType: @MediaMetadata.MediaType Int,
        subtitleConfigurations: List<SubtitleConfiguration> = mutableListOf(),
        album: String? = null,
        artist: String? = null,
        genre: String? = null,
        sourceUri: Uri? = null,
        imageUri: Uri? = null,
        extras: Bundle? = null
    ): MediaItem {
        val metadata =
            MediaMetadata.Builder()
                .setAlbumTitle(album)
                .setTitle(title)
                .setArtist(artist)
                .setGenre(genre)
                .setIsBrowsable(isBrowsable)
                .setIsPlayable(isPlayable)
                .setArtworkUri(imageUri)
                .setMediaType(mediaType)
                .setExtras(extras)
                .build()

        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setSubtitleConfigurations(subtitleConfigurations)
            .setMediaMetadata(metadata)
            .setUri(sourceUri)
            .build()
    }

    fun initialize(automotiveRepository: AutomotiveRepository) {
        this.automotiveRepository = automotiveRepository

        if (isInitialized) return

        isInitialized = true

        // Root level

        treeNodes[ROOT_ID] =
            MediaItemNode(
                buildMediaItem(
                    title = App.getContext().getString(R.string.app_name),
                    mediaId = ROOT_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

        // First level

        treeNodes[HOME_ID] =
            MediaItemNode(
                buildMediaItem(
                    title = App.getContext().getString(R.string.auto_home),
                    mediaId = HOME_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

        treeNodes[LIBRARY_ID] =
            MediaItemNode(
                buildMediaItem(
                    title = App.getContext().getString(R.string.auto_library),
                    mediaId = LIBRARY_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

        treeNodes[DOWNLOADS_ID] =
            MediaItemNode(
                buildMediaItem(
                    title = App.getContext().getString(R.string.auto_downloads),
                    mediaId = DOWNLOADS_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

        treeNodes[ROOT_ID]!!.addChild(HOME_ID)
        treeNodes[ROOT_ID]!!.addChild(LIBRARY_ID)
        treeNodes[ROOT_ID]!!.addChild(DOWNLOADS_ID)

        // Second level HOME_ID

        treeNodes[MOST_PLAYED_ID] =
            MediaItemNode(
                buildMediaItem(
                    title = App.getContext().getString(R.string.auto_most_played),
                    mediaId = MOST_PLAYED_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                )
            )

        treeNodes[LAST_PLAYED_ID] =
            MediaItemNode(
                buildMediaItem(
                    title = App.getContext().getString(R.string.auto_last_played),
                    mediaId = LAST_PLAYED_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                )
            )

        treeNodes[RECENTLY_ADDED_ID] =
            MediaItemNode(
                buildMediaItem(
                    title = App.getContext().getString(R.string.auto_recently_added),
                    mediaId = RECENTLY_ADDED_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                )
            )

        treeNodes[RECENT_SONGS_ID] =
                MediaItemNode(
                        buildMediaItem(
                                title = App.getContext().getString(R.string.auto_recent_songs),
                                mediaId = RECENT_SONGS_ID,
                                isPlayable = false,
                                isBrowsable = true,
                                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                        )
                )

        treeNodes[STARRED_TRACKS_ID] =
            MediaItemNode(
                buildMediaItem(
                    title = App.getContext().getString(R.string.auto_starred_tracks),
                    mediaId = STARRED_TRACKS_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

        treeNodes[STARRED_ALBUMS_ID] =
            MediaItemNode(
                buildMediaItem(
                    title = App.getContext().getString(R.string.auto_starred_albums),
                    mediaId = STARRED_ALBUMS_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                )
            )

        treeNodes[STARRED_ARTISTS_ID] =
            MediaItemNode(
                buildMediaItem(
                    title = App.getContext().getString(R.string.auto_starred_artists),
                    mediaId = STARRED_ARTISTS_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS
                )
            )

        treeNodes[RANDOM_ID] =
                MediaItemNode(
                        buildMediaItem(
                                title = App.getContext().getString(R.string.auto_random),
                                mediaId = RANDOM_ID,
                                isPlayable = false,
                                isBrowsable = true,
                                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                        )
                )

        treeNodes[HOME_ID]!!.addChild(MOST_PLAYED_ID)
        treeNodes[HOME_ID]!!.addChild(LAST_PLAYED_ID)
        treeNodes[HOME_ID]!!.addChild(RECENTLY_ADDED_ID)
        treeNodes[HOME_ID]!!.addChild(RECENT_SONGS_ID)
        treeNodes[HOME_ID]!!.addChild(STARRED_TRACKS_ID)
        treeNodes[HOME_ID]!!.addChild(STARRED_ALBUMS_ID)
        treeNodes[HOME_ID]!!.addChild(STARRED_ARTISTS_ID)
        treeNodes[HOME_ID]!!.addChild(RANDOM_ID)

        // Second level LIBRARY_ID

        treeNodes[FOLDER_ID] =
            MediaItemNode(
                buildMediaItem(
                    title = App.getContext().getString(R.string.auto_folders),
                    mediaId = FOLDER_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                    extras = folderListStyle()
                )
            )

        treeNodes[PLAYLIST_ID] =
            MediaItemNode(
                buildMediaItem(
                    title = App.getContext().getString(R.string.auto_playlists),
                    mediaId = PLAYLIST_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                )
            )

        treeNodes[LIBRARY_ID]!!.addChild(FOLDER_ID)
        treeNodes[LIBRARY_ID]!!.addChild(PLAYLIST_ID)

        // Podcast e radio (dentro LIBRARY_ID)

        treeNodes[PODCAST_ID] =
            MediaItemNode(
                buildMediaItem(
                    title = App.getContext().getString(R.string.auto_podcasts),
                    mediaId = PODCAST_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS
                )
            )

        treeNodes[RADIO_ID] =
            MediaItemNode(
                buildMediaItem(
                    title = App.getContext().getString(R.string.auto_radio_stations),
                    mediaId = RADIO_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS
                )
            )

        treeNodes[LIBRARY_ID]!!.addChild(PODCAST_ID)
        treeNodes[LIBRARY_ID]!!.addChild(RADIO_ID)
    }

    private fun isConfigured(): Boolean =
        !Preferences.getServerId().isNullOrEmpty() && !Preferences.getServer().isNullOrEmpty()

    private fun notConfiguredItem(): MediaItem =
        buildMediaItem(
            title = App.getContext().getString(R.string.auto_not_configured),
            mediaId = NOT_CONFIGURED_ID,
            isPlayable = false,
            isBrowsable = false,
            mediaType = MediaMetadata.MEDIA_TYPE_MIXED
        )

    fun getRootItem(): MediaItem {
        return treeNodes[ROOT_ID]!!.item
    }

    private fun collectionActions(collectionId: String): List<MediaItem> =
        listOf(
            buildMediaItem(
                title = App.getContext().getString(R.string.auto_play_all),
                mediaId = PLAY_ALL_ID + collectionId,
                isPlayable = true,
                isBrowsable = false,
                mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST
            ),
            buildMediaItem(
                title = App.getContext().getString(R.string.auto_shuffle_all),
                mediaId = SHUFFLE_ALL_ID + collectionId,
                isPlayable = true,
                isBrowsable = false,
                mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST
            )
        )

    // Le due azioni stanno in testa alla raccolta (cartella, album, playlist), così l'host le
    // mostra come prime righe riproducibili. Senza brani non avrebbero nulla da accodare.
    private fun withActions(
        result: LibraryResult<ImmutableList<MediaItem>>,
        collectionId: String
    ): LibraryResult<ImmutableList<MediaItem>> {
        val items = result.value

        if (result.resultCode != LibraryResult.RESULT_SUCCESS || items.isNullOrEmpty()) return result

        return LibraryResult.ofItemList(
            ImmutableList.copyOf(collectionActions(collectionId) + items),
            result.params
        )
    }

    private fun collectionListing(
        collectionId: String,
        listing: ListenableFuture<LibraryResult<ImmutableList<MediaItem>>>
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        Futures.transform(
            listing,
            { result -> withActions(result, collectionId) },
            MoreExecutors.directExecutor()
        )

    fun isCollectionAction(mediaId: String): Boolean =
        mediaId.startsWith(PLAY_ALL_ID) || mediaId.startsWith(SHUFFLE_ALL_ID)

    // L'azione non è un brano: si espande nell'intera raccolta. L'id dell'azione porta il tipo
    // (album, playlist, altrimenti cartella), così la raccolta giusta viene richiesta qui.
    fun expandCollectionAction(item: MediaItem): ListenableFuture<List<MediaItem>> {
        val shuffle = item.mediaId.startsWith(SHUFFLE_ALL_ID)
        val collectionId = item.mediaId.removePrefix(if (shuffle) SHUFFLE_ALL_ID else PLAY_ALL_ID)

        if (collectionId.startsWith(ALBUM_ID)) {
            return shuffled(
                automotiveRepository.getAlbumTracks(collectionId.removePrefix(ALBUM_ID)),
                shuffle
            )
        }

        if (collectionId.startsWith(PLAYLIST_ID)) {
            return shuffled(
                automotiveRepository.getPlaylistSongs(collectionId.removePrefix(PLAYLIST_ID)),
                shuffle
            )
        }

        return automotiveRepository.getFolderSongs(collectionId, shuffle)
    }

    private fun shuffled(
        songs: ListenableFuture<LibraryResult<ImmutableList<MediaItem>>>,
        shuffle: Boolean
    ): ListenableFuture<List<MediaItem>> =
        Futures.transform(
            songs,
            { result ->
                val items = mutableListOf<MediaItem>()

                if (result.resultCode == LibraryResult.RESULT_SUCCESS) {
                    result.value?.let { items.addAll(it) }
                }

                if (shuffle) items.shuffle()

                items.toList()
            },
            MoreExecutors.directExecutor()
        )

    fun getPlaybackResumption(): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
        automotiveRepository.getPlaybackResumption()

    private fun folderListStyle(): Bundle =
        Bundle().apply {
            putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_LIST_ITEM)
            putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST_ITEM)
        }

    // Con una sola music folder il livello intermedio non aggiunge nulla: si mostra subito il suo
    // contenuto, così in auto la lista è quella che l'utente si aspetta (e abbastanza lunga da
    // avere l'indice A-Z che l'host disegna nella barra di scorrimento).
    private fun foldersListing(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        Futures.transformAsync(
            automotiveRepository.getMusicFolders(FOLDER_ID),
            { result ->
                val onlyFolder = result.value?.singleOrNull()

                if (result.resultCode == LibraryResult.RESULT_SUCCESS && onlyFolder != null) {
                    // È la stessa raccolta che si apre dal livello intermedio: senza
                    // collectionListing "Riproduci tutto"/"Shuffle" sparivano proprio nel caso
                    // della music folder unica.
                    val folderId = onlyFolder.mediaId.removePrefix(FOLDER_ID)

                    // Senza prefisso, come gli altri rami: l'id finisce nelle azioni di raccolta
                    // e expandCollectionAction lo passa a getFolderSongs così com'è.
                    collectionListing(folderId, browseFolder(folderId))
                } else {
                    Futures.immediateFuture(result)
                }
            },
            MoreExecutors.directExecutor()
        )

    fun getChildren(
        id: String
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        Futures.transform(
            childrenOf(id),
            { result -> rememberCollection(id, result) },
            MoreExecutors.directExecutor()
        )

    private fun childrenOf(
        id: String
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        // I download sono locali: restano raggiungibili anche senza server configurato
        if (id == DOWNLOADS_ID) {
            return automotiveRepository.getDownloads()
        }

        if (!isConfigured()) {
            if (id == ROOT_ID) {
                return treeNodes[ROOT_ID]!!.getChildren()
            }

            Log.w(TAG, "No server configured, returning the setup hint item")
            return Futures.immediateFuture(
                LibraryResult.ofItemList(ImmutableList.of(notConfiguredItem()), null)
            )
        }

        return when (id) {
            ROOT_ID -> treeNodes[ROOT_ID]?.getChildren()!!
            HOME_ID -> treeNodes[HOME_ID]?.getChildren()!!
            LIBRARY_ID -> treeNodes[LIBRARY_ID]?.getChildren()!!

            MOST_PLAYED_ID -> automotiveRepository.getAlbums(id, "frequent", 100)
            LAST_PLAYED_ID -> automotiveRepository.getAlbums(id, "recent", 100)
            RECENTLY_ADDED_ID -> automotiveRepository.getAlbums(id, "newest", 100)
            RECENT_SONGS_ID -> automotiveRepository.getRecentlyPlayedSongs(getServerId(),100)
            STARRED_TRACKS_ID -> automotiveRepository.starredSongs
            STARRED_ALBUMS_ID -> automotiveRepository.getStarredAlbums(id)
            STARRED_ARTISTS_ID -> automotiveRepository.getStarredArtists(id)
            RANDOM_ID -> automotiveRepository.getRandomSongs(100)
            FOLDER_ID -> foldersListing()
            PLAYLIST_ID -> automotiveRepository.getPlaylists(id)
            PODCAST_ID -> automotiveRepository.getNewestPodcastEpisodes(100)
            RADIO_ID -> automotiveRepository.internetRadioStations

            else -> {
                if (id.startsWith(MOST_PLAYED_ID)) {
                    val albumId = id.removePrefix(MOST_PLAYED_ID)

                    return collectionListing(
                        ALBUM_ID + albumId,
                        automotiveRepository.getAlbumTracks(albumId)
                    )
                }

                if (id.startsWith(LAST_PLAYED_ID)) {
                    val albumId = id.removePrefix(LAST_PLAYED_ID)

                    return collectionListing(
                        ALBUM_ID + albumId,
                        automotiveRepository.getAlbumTracks(albumId)
                    )
                }

                if (id.startsWith(RECENTLY_ADDED_ID)) {
                    val albumId = id.removePrefix(RECENTLY_ADDED_ID)

                    return collectionListing(
                        ALBUM_ID + albumId,
                        automotiveRepository.getAlbumTracks(albumId)
                    )
                }

                if (id.startsWith(STARRED_ALBUMS_ID)) {
                    val albumId = id.removePrefix(STARRED_ALBUMS_ID)

                    return collectionListing(
                        ALBUM_ID + albumId,
                        automotiveRepository.getAlbumTracks(albumId)
                    )
                }

                if (id.startsWith(STARRED_ARTISTS_ID)) {
                    return automotiveRepository.getArtistAlbum(
                        STARRED_ALBUMS_ID,
                        id.removePrefix(
                            STARRED_ARTISTS_ID
                        )
                    )
                }

                if (id.startsWith(FOLDER_ID)) {
                    return collectionListing(
                        id.removePrefix(FOLDER_ID),
                        browseFolder(
                            id.removePrefix(
                                FOLDER_ID
                            )
                        )
                    )
                }

                if (id.startsWith(INDEX_ID)) {
                    return browseIndexEntry(
                        id.removePrefix(
                            INDEX_ID
                        )
                    )
                }

                if (id.startsWith(DIRECTORY_ID)) {
                    return collectionListing(
                        id.removePrefix(DIRECTORY_ID),
                        automotiveRepository.getDirectories(
                            DIRECTORY_ID,
                            id.removePrefix(
                                DIRECTORY_ID
                            )
                        )
                    )
                }

                if (id.startsWith(PLAYLIST_ID)) {
                    val playlistId = id.removePrefix(PLAYLIST_ID)

                    return collectionListing(
                        PLAYLIST_ID + playlistId,
                        automotiveRepository.getPlaylistSongs(playlistId)
                    )
                }

                if (id.startsWith(ALBUM_ID)) {
                    val albumId = id.removePrefix(ALBUM_ID)

                    return collectionListing(
                        ALBUM_ID + albumId,
                        automotiveRepository.getAlbumTracks(albumId)
                    )
                }

                if (id.startsWith(ARTIST_ID)) {
                    return automotiveRepository.getArtistAlbum(
                        ALBUM_ID,
                        id.removePrefix(
                            ARTIST_ID
                        )
                    )
                }

                return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            }
        }
    }

    // Il percorso delle cartelle cambia con il server: alcuni espongono un indice ID3
    // (getIndexes, poi getArtist), altri solo directory (getMusicDirectory). Se la prima strada
    // restituisce una lista vuota o un errore si prova la seconda, così una cartella non mostra
    // "nessun elemento" quando i dati ci sono. Sul telefono la stessa voce dell'indice apre una
    // directory (IndexFragment -> DirectoryFragment).
    private fun browseFolder(
        id: String
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        Futures.transformAsync(
            automotiveRepository.getIndexes(INDEX_ID, DIRECTORY_ID, id),
            { result ->
                val items = result.value

                if (result.resultCode == LibraryResult.RESULT_SUCCESS && !items.isNullOrEmpty()) {
                    Futures.immediateFuture(result)
                } else {
                    Log.d(TAG, "No artists index for music folder $id, reading it as a directory")
                    automotiveRepository.getDirectories(DIRECTORY_ID, id)
                }
            },
            MoreExecutors.directExecutor()
        )

    private fun browseIndexEntry(
        id: String
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        Futures.transformAsync(
            automotiveRepository.getArtistAlbum(ALBUM_ID, id),
            { result ->
                val items = result.value

                if (result.resultCode == LibraryResult.RESULT_SUCCESS && !items.isNullOrEmpty()) {
                    Futures.immediateFuture(result)
                } else {
                    Log.d(TAG, "No albums for index entry $id, reading it as a directory")
                    automotiveRepository.getDirectories(DIRECTORY_ID, id)
                }
            },
            MoreExecutors.directExecutor()
        )

    // https://github.com/androidx/media/issues/156
    // onAddMediaItems deve restituire un item per ogni item richiesto, nello stesso ordine:
    // niente deduplica per mediaId (un brano può comparire due volte in coda o in playlist) e
    // niente coda residua della sessione (subList(index, size)), che duplicava i brani.
    fun getItems(mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> {
        val ids = mediaItems.map { it.mediaId }.distinct()

        val mapItems = Function<Map<String, MediaItem>, List<MediaItem>> { metadata ->
            mediaItems.map { item ->
                if (item.localConfiguration?.uri != null) {
                    item
                } else {
                    val enrichedItem = metadata[item.mediaId]

                    if (enrichedItem == null) {
                        Log.w(TAG, "No session metadata for ${item.mediaId}, keeping the original item")
                        item
                    } else {
                        enrichedItem
                    }
                }
            }
        }

        return Futures.transform(
            automotiveRepository.getSessionMediaItems(ids),
            mapItems,
            MoreExecutors.directExecutor()
        )
    }

    // Un id di raccolta contiene brani che l'utente si aspetta in coda dopo quello toccato.
    // Restano fuori gli elenchi "instabili" (ricerca, random, recenti): lì il tap resta singolo.
    // Gli album aperti dalle liste di Home usano l'id della lista come prefisso (es.
    // MOST_PLAYED_ID + albumId), ma il loro dettaglio è una raccolta come le altre.
    private fun isCollectionId(id: String): Boolean =
        id == DOWNLOADS_ID ||
            id.startsWith(FOLDER_ID) ||
            id.startsWith(DIRECTORY_ID) ||
            id.startsWith(ALBUM_ID) ||
            id.startsWith(PLAYLIST_ID) ||
            id.startsWith(MOST_PLAYED_ID) ||
            id.startsWith(LAST_PLAYED_ID) ||
            id.startsWith(RECENTLY_ADDED_ID) ||
            id.startsWith(STARRED_ALBUMS_ID)

    private fun invalidateCachesIfServerChanged() {
        val serverId = getServerId()

        if (serverId == cachedServerId) return

        synchronized(browsedCollections) { browsedCollections.clear() }
        synchronized(searchCache) { searchCache.clear() }

        cachedServerId = serverId
    }

    private fun rememberCollection(
        id: String,
        result: LibraryResult<ImmutableList<MediaItem>>
    ): LibraryResult<ImmutableList<MediaItem>> {
        val items = result.value

        if (result.resultCode != LibraryResult.RESULT_SUCCESS || items == null || !isCollectionId(id)) {
            return result
        }

        if (items.any { it.mediaMetadata.isPlayable == true }) {
            synchronized(browsedCollections) {
                browsedCollections.remove(id)
                browsedCollections[id] = items

                if (browsedCollections.size > BROWSED_COLLECTIONS_LIMIT) {
                    browsedCollections.remove(browsedCollections.keys.first())
                }
            }
        }

        return result
    }

    /**
     * Lista da accodare quando l'host chiede la riproduzione di un singolo brano: i brani
     * riproducibili della raccolta da cui proviene e la posizione del brano toccato. Null se la
     * raccolta non è nota (es. ricerca) o non contiene l'item: in quel caso resta il singolo brano.
     */
    fun expandPlayableItem(item: MediaItem): Pair<List<MediaItem>, Int>? {
        if (item.mediaMetadata.mediaType != MediaMetadata.MEDIA_TYPE_MUSIC) return null

        invalidateCachesIfServerChanged()

        // La raccolta appena sfogliata è l'ultima inserita: si parte da lì.
        val collections = synchronized(browsedCollections) {
            browsedCollections.values.toList().asReversed()
        }

        for (items in collections) {
            val playable = items.filter {
                it.mediaMetadata.isPlayable == true && !isCollectionAction(it.mediaId)
            }

            val index = playable.indexOfFirst { it.mediaId == item.mediaId }

            if (index >= 0) return playable to index
        }

        return null
    }

    fun search(query: String): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        if (!isConfigured()) {
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), null))
        }

        invalidateCachesIfServerChanged()

        synchronized(searchCache) {
            searchCache.remove(query)?.let { cached ->
                searchCache[query] = cached

                return Futures.immediateFuture(LibraryResult.ofItemList(cached, null))
            }
        }

        return Futures.transform(
            automotiveRepository.search(
                query,
                ALBUM_ID,
                ARTIST_ID
            ),
            { result -> rememberSearch(query, result) },
            MoreExecutors.directExecutor()
        )
    }

    private fun rememberSearch(
        query: String,
        result: LibraryResult<ImmutableList<MediaItem>>
    ): LibraryResult<ImmutableList<MediaItem>> {
        val items = result.value

        if (result.resultCode != LibraryResult.RESULT_SUCCESS || items == null) return result

        synchronized(searchCache) {
            searchCache.remove(query)
            searchCache[query] = items

            if (searchCache.size > SEARCH_CACHE_LIMIT) {
                searchCache.remove(searchCache.keys.first())
            }
        }

        return result
    }
}
