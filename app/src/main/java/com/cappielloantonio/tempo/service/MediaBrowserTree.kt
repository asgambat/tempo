package com.cappielloantonio.tempo.service

import android.net.Uri
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

    // Root
    private const val ROOT_ID = "[rootID]"

    // Shown when no server has been configured yet
    private const val NOT_CONFIGURED_ID = "[notConfiguredID]"

    private const val TAG = "MediaBrowserTree"

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
        imageUri: Uri? = null
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
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
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

    fun getPlaybackResumption(): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
        automotiveRepository.getPlaybackResumption()

    fun getChildren(
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
            FOLDER_ID -> automotiveRepository.getMusicFolders(id)
            PLAYLIST_ID -> automotiveRepository.getPlaylists(id)
            PODCAST_ID -> automotiveRepository.getNewestPodcastEpisodes(100)
            RADIO_ID -> automotiveRepository.internetRadioStations

            else -> {
                if (id.startsWith(MOST_PLAYED_ID)) {
                    return automotiveRepository.getAlbumTracks(
                        id.removePrefix(
                            MOST_PLAYED_ID
                        )
                    )
                }

                if (id.startsWith(LAST_PLAYED_ID)) {
                    return automotiveRepository.getAlbumTracks(
                        id.removePrefix(
                            LAST_PLAYED_ID
                        )
                    )
                }

                if (id.startsWith(RECENTLY_ADDED_ID)) {
                    return automotiveRepository.getAlbumTracks(
                        id.removePrefix(
                            RECENTLY_ADDED_ID
                        )
                    )
                }

                if (id.startsWith(STARRED_ALBUMS_ID)) {
                    return automotiveRepository.getAlbumTracks(
                        id.removePrefix(
                            STARRED_ALBUMS_ID
                        )
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
                    return browseFolder(
                        id.removePrefix(
                            FOLDER_ID
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
                    return automotiveRepository.getDirectories(
                        DIRECTORY_ID,
                        id.removePrefix(
                            DIRECTORY_ID
                        )
                    )
                }

                if (id.startsWith(PLAYLIST_ID)) {
                    return automotiveRepository.getPlaylistSongs(
                        id.removePrefix(
                            PLAYLIST_ID
                        )
                    )
                }

                if (id.startsWith(ALBUM_ID)) {
                    return automotiveRepository.getAlbumTracks(
                        id.removePrefix(
                            ALBUM_ID
                        )
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

    fun search(query: String): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        if (!isConfigured()) {
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), null))
        }

        return automotiveRepository.search(
            query,
            ALBUM_ID,
            ARTIST_ID
        )
    }
}
