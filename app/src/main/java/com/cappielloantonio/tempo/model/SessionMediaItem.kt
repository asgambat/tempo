package com.cappielloantonio.tempo.model

import android.net.Uri
import android.os.Bundle
import androidx.annotation.Keep
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.RequestMetadata
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cappielloantonio.tempo.subsonic.models.Child
import com.cappielloantonio.tempo.subsonic.models.InternetRadioStation
import com.cappielloantonio.tempo.subsonic.models.PodcastEpisode
import com.cappielloantonio.tempo.util.Constants
import com.cappielloantonio.tempo.util.MappingUtil
import com.cappielloantonio.tempo.util.MusicUtil
import java.util.Date

@UnstableApi
@Keep
@Entity(tableName = "session_media_item")
class SessionMediaItem() {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "index")
    var index: Int = 0

    @ColumnInfo(name = "id")
    var id: String? = null

    @ColumnInfo(name = "parent_id")
    var parentId: String? = null

    @ColumnInfo(name = "is_dir")
    var isDir: Boolean = false

    @ColumnInfo
    var title: String? = null

    @ColumnInfo
    var album: String? = null

    @ColumnInfo
    var artist: String? = null

    @ColumnInfo
    var track: Int? = null

    @ColumnInfo
    var year: Int? = null

    @ColumnInfo
    var genre: String? = null

    @ColumnInfo(name = "cover_art_id")
    var coverArtId: String? = null

    @ColumnInfo
    var size: Long? = null

    @ColumnInfo(name = "content_type")
    var contentType: String? = null

    @ColumnInfo
    var suffix: String? = null

    @ColumnInfo("transcoding_content_type")
    var transcodedContentType: String? = null

    @ColumnInfo(name = "transcoded_suffix")
    var transcodedSuffix: String? = null

    @ColumnInfo
    var duration: Int? = null

    @ColumnInfo("bitrate")
    var bitrate: Int? = null

    @ColumnInfo
    var path: String? = null

    @ColumnInfo(name = "is_video")
    var isVideo: Boolean = false

    @ColumnInfo(name = "user_rating")
    var userRating: Int? = null

    @ColumnInfo(name = "average_rating")
    var averageRating: Double? = null

    @ColumnInfo(name = "play_count")
    var playCount: Long? = null

    @ColumnInfo(name = "disc_number")
    var discNumber: Int? = null

    @ColumnInfo
    var created: Date? = null

    @ColumnInfo
    var starred: Date? = null

    @ColumnInfo(name = "album_id")
    var albumId: String? = null

    @ColumnInfo(name = "artist_id")
    var artistId: String? = null

    @ColumnInfo
    var type: String? = null

    @ColumnInfo(name = "bookmark_position")
    var bookmarkPosition: Long? = null

    @ColumnInfo(name = "original_width")
    var originalWidth: Int? = null

    @ColumnInfo(name = "original_height")
    var originalHeight: Int? = null

    @ColumnInfo(name = "stream_id")
    var streamId: String? = null

    @ColumnInfo(name = "stream_url")
    var streamUrl: String? = null

    @ColumnInfo(name = "timestamp")
    var timestamp: Long? = null

    constructor(child: Child) : this() {
        id = child.id
        parentId = child.parentId
        isDir = child.isDir
        title = child.title
        album = child.album
        artist = child.artist
        track = child.track
        year = child.year
        genre = child.genre
        coverArtId = child.coverArtId
        size = child.size
        contentType = child.contentType
        suffix = child.suffix
        transcodedContentType = child.transcodedContentType
        transcodedSuffix = child.transcodedSuffix
        duration = child.duration
        bitrate = child.bitrate
        path = child.path
        isVideo = child.isVideo
        userRating = child.userRating
        averageRating = child.averageRating
        playCount = child.playCount
        discNumber = child.discNumber
        created = child.created
        starred = child.starred
        albumId = child.albumId
        artistId = child.artistId
        type = Constants.MEDIA_TYPE_MUSIC
        bookmarkPosition = child.bookmarkPosition
        originalWidth = child.originalWidth
        originalHeight = child.originalHeight
    }

    constructor(podcastEpisode: PodcastEpisode) : this() {
        id = podcastEpisode.id
        parentId = podcastEpisode.parentId
        isDir = podcastEpisode.isDir
        title = podcastEpisode.title
        album = podcastEpisode.album
        artist = podcastEpisode.artist
        year = podcastEpisode.year
        genre = podcastEpisode.genre
        coverArtId = podcastEpisode.coverArtId
        size = podcastEpisode.size
        contentType = podcastEpisode.contentType
        suffix = podcastEpisode.suffix
        duration = podcastEpisode.duration
        bitrate = podcastEpisode.bitrate
        path = podcastEpisode.path
        isVideo = podcastEpisode.isVideo
        created = podcastEpisode.created
        artistId = podcastEpisode.artistId
        streamId = podcastEpisode.streamId
        type = Constants.MEDIA_TYPE_PODCAST
    }

    constructor(internetRadioStation: InternetRadioStation) : this() {
        id = internetRadioStation.id
        title = internetRadioStation.name
        streamUrl = internetRadioStation.streamUrl
        type = Constants.MEDIA_TYPE_RADIO
    }

    fun getMediaItem(): MediaItem {
        val uri: Uri = getStreamUri()
        // Metadati usati solo per Android Auto: l'artwork URI non deve contenere credenziali.
        // Senza coverArtId si usa albumId; se manca anche quello niente URI: un "id=null" sarebbe
        // uguale per tutti i brani e l'host terrebbe la copertina dell'album precedente.
        val artworkUri = MappingUtil.getArtworkId(coverArtId, albumId)?.let {
            MusicUtil.getCoverArtUri(it, Constants.SESSION_ARTWORK_SIZE)
        }

        val bundle = Bundle()
        bundle.putString(Constants.MEDIA_EXTRA_ID, id)
        bundle.putString(Constants.MEDIA_EXTRA_PARENT_ID, parentId)
        bundle.putBoolean(Constants.MEDIA_EXTRA_IS_DIR, isDir)
        bundle.putString(Constants.MEDIA_EXTRA_TITLE, title)
        bundle.putString(Constants.MEDIA_EXTRA_ALBUM, album)
        bundle.putString(Constants.MEDIA_EXTRA_ARTIST, artist)
        bundle.putInt(Constants.MEDIA_EXTRA_TRACK, track ?: 0)
        bundle.putInt(Constants.MEDIA_EXTRA_YEAR, year ?: 0)
        bundle.putString(Constants.MEDIA_EXTRA_GENRE, genre)
        bundle.putString(Constants.MEDIA_EXTRA_COVER_ART_ID, coverArtId)
        bundle.putLong(Constants.MEDIA_EXTRA_SIZE, size ?: 0)
        bundle.putString(Constants.MEDIA_EXTRA_CONTENT_TYPE, contentType)
        bundle.putString(Constants.MEDIA_EXTRA_SUFFIX, suffix)
        bundle.putString(Constants.MEDIA_EXTRA_TRANSCODED_CONTENT_TYPE, transcodedContentType)
        bundle.putString(Constants.MEDIA_EXTRA_TRANSCODED_SUFFIX, transcodedSuffix)
        bundle.putInt(Constants.MEDIA_EXTRA_DURATION, duration ?: 0)
        bundle.putInt(Constants.MEDIA_EXTRA_BITRATE, bitrate ?: 0)
        bundle.putString(Constants.MEDIA_EXTRA_PATH, path)
        bundle.putBoolean(Constants.MEDIA_EXTRA_IS_VIDEO, isVideo)
        bundle.putInt(Constants.MEDIA_EXTRA_USER_RATING, userRating ?: 0)
        bundle.putDouble(Constants.MEDIA_EXTRA_AVERAGE_RATING, averageRating ?: .0)
        bundle.putLong(Constants.MEDIA_EXTRA_PLAY_COUNT, playCount ?: 0)
        bundle.putInt(Constants.MEDIA_EXTRA_DISC_NUMBER, discNumber ?: 0)
        bundle.putLong(Constants.MEDIA_EXTRA_CREATED, created?.time ?: 0)
        bundle.putLong(Constants.MEDIA_EXTRA_STARRED, starred?.time ?: 0)
        bundle.putString(Constants.MEDIA_EXTRA_ALBUM_ID, albumId)
        bundle.putString(Constants.MEDIA_EXTRA_ARTIST_ID, artistId)
        bundle.putString(Constants.MEDIA_EXTRA_TYPE, Constants.MEDIA_TYPE_MUSIC)
        bundle.putLong(Constants.MEDIA_EXTRA_BOOKMARK_POSITION, bookmarkPosition ?: 0)
        bundle.putInt(Constants.MEDIA_EXTRA_ORIGINAL_WIDTH, originalWidth ?: 0)
        bundle.putInt(Constants.MEDIA_EXTRA_ORIGINAL_HEIGHT, originalHeight ?: 0)
        // Nessun extras "uri": il bundle è esposto ad Android Auto e l'URI contiene credenziali

        return MediaItem.Builder()
            .setMediaId(id!!)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setTrackNumber(track ?: 0)
                    .setDiscNumber(discNumber ?: 0)
                    .setReleaseYear(year ?: 0)
                    .setAlbumTitle(album)
                    .setArtist(artist)
                    .setArtworkUri(artworkUri)
                    .setExtras(bundle)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .setRequestMetadata(
                RequestMetadata.Builder()
                    .setMediaUri(uri)
                    .setExtras(bundle)
                    .build()
            )
            .setMimeType(MimeTypes.BASE_TYPE_AUDIO)
            .setUri(uri)
            .build()
    }

    private fun getStreamUri(): Uri {
        return when (type) {
            Constants.MEDIA_TYPE_MUSIC -> {
                MusicUtil.getStreamUri(id)
            }

            Constants.MEDIA_TYPE_PODCAST -> {
                MusicUtil.getStreamUri(streamId)
            }

            Constants.MEDIA_TYPE_RADIO -> {
                Uri.parse(streamUrl)
            }

            else -> {
                MusicUtil.getStreamUri(id)
            }
        }
    }
}