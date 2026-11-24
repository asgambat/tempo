package com.cappielloantonio.tempo.model

import androidx.annotation.Keep
import androidx.media3.common.MediaItem
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cappielloantonio.tempo.subsonic.models.Child
import com.cappielloantonio.tempo.util.Constants
import com.cappielloantonio.tempo.util.Preferences
import kotlinx.parcelize.Parcelize
import java.util.*

@Keep
@Parcelize
@Entity(tableName = "chronology")
class Chronology(@PrimaryKey override val id: String) : Child(id) {
    @ColumnInfo(name = "timestamp")
    var timestamp: Long = System.currentTimeMillis()

    @ColumnInfo(name = "server")
    var server: String? = null

    constructor(mediaItem: MediaItem) : this(mediaItem.mediaMetadata.extras!!.getString(Constants.MEDIA_EXTRA_ID)!!) {
        parentId = mediaItem.mediaMetadata.extras!!.getString(Constants.MEDIA_EXTRA_PARENT_ID)
        isDir = mediaItem.mediaMetadata.extras!!.getBoolean(Constants.MEDIA_EXTRA_IS_DIR)
        title = mediaItem.mediaMetadata.extras!!.getString(Constants.MEDIA_EXTRA_TITLE)
        album = mediaItem.mediaMetadata.extras!!.getString(Constants.MEDIA_EXTRA_ALBUM)
        artist = mediaItem.mediaMetadata.extras!!.getString(Constants.MEDIA_EXTRA_ARTIST)
        track = mediaItem.mediaMetadata.extras!!.getInt(Constants.MEDIA_EXTRA_TRACK)
        year = mediaItem.mediaMetadata.extras!!.getInt(Constants.MEDIA_EXTRA_YEAR)
        genre = mediaItem.mediaMetadata.extras!!.getString(Constants.MEDIA_EXTRA_GENRE)
        coverArtId = mediaItem.mediaMetadata.extras!!.getString(Constants.MEDIA_EXTRA_COVER_ART_ID)
        size = mediaItem.mediaMetadata.extras!!.getLong(Constants.MEDIA_EXTRA_SIZE)
        contentType = mediaItem.mediaMetadata.extras!!.getString(Constants.MEDIA_EXTRA_CONTENT_TYPE)
        suffix = mediaItem.mediaMetadata.extras!!.getString(Constants.MEDIA_EXTRA_SUFFIX)
        transcodedContentType = mediaItem.mediaMetadata.extras!!.getString(Constants.MEDIA_EXTRA_TRANSCODED_CONTENT_TYPE)
        transcodedSuffix = mediaItem.mediaMetadata.extras!!.getString(Constants.MEDIA_EXTRA_TRANSCODED_SUFFIX)
        duration = mediaItem.mediaMetadata.extras!!.getInt(Constants.MEDIA_EXTRA_DURATION)
        bitrate = mediaItem.mediaMetadata.extras!!.getInt(Constants.MEDIA_EXTRA_BITRATE)
        path = mediaItem.mediaMetadata.extras!!.getString(Constants.MEDIA_EXTRA_PATH)
        isVideo = mediaItem.mediaMetadata.extras!!.getBoolean(Constants.MEDIA_EXTRA_IS_VIDEO)
        userRating = mediaItem.mediaMetadata.extras!!.getInt(Constants.MEDIA_EXTRA_USER_RATING)
        averageRating = mediaItem.mediaMetadata.extras!!.getDouble(Constants.MEDIA_EXTRA_AVERAGE_RATING)
        playCount = mediaItem.mediaMetadata.extras!!.getLong(Constants.MEDIA_EXTRA_PLAY_COUNT)
        discNumber = mediaItem.mediaMetadata.extras!!.getInt(Constants.MEDIA_EXTRA_DISC_NUMBER)
        created = Date(mediaItem.mediaMetadata.extras!!.getLong(Constants.MEDIA_EXTRA_CREATED))
        starred = Date(mediaItem.mediaMetadata.extras!!.getLong(Constants.MEDIA_EXTRA_STARRED))
        albumId = mediaItem.mediaMetadata.extras!!.getString(Constants.MEDIA_EXTRA_ALBUM_ID)
        artistId = mediaItem.mediaMetadata.extras!!.getString(Constants.MEDIA_EXTRA_ARTIST_ID)
        type = mediaItem.mediaMetadata.extras!!.getString(Constants.MEDIA_EXTRA_TYPE)
        bookmarkPosition = mediaItem.mediaMetadata.extras!!.getLong(Constants.MEDIA_EXTRA_BOOKMARK_POSITION)
        originalWidth = mediaItem.mediaMetadata.extras!!.getInt(Constants.MEDIA_EXTRA_ORIGINAL_WIDTH)
        originalHeight = mediaItem.mediaMetadata.extras!!.getInt(Constants.MEDIA_EXTRA_ORIGINAL_HEIGHT)
        server = Preferences.getServerId()
        timestamp = Date().time
    }
}