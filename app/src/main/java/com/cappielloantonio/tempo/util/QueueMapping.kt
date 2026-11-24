package com.cappielloantonio.tempo.util

import android.os.Bundle
import androidx.media3.common.MediaItem
import com.cappielloantonio.tempo.subsonic.models.Child
import java.util.Date

/**
 * Converte la coda impostata sul player in [Child], per la coda persistita su Room.
 *
 * Android Auto imposta i brani direttamente sul player (`onSetMediaItems`) senza passare da
 * `MediaManager`: senza questa conversione la coda salvata resterebbe quella precedente. Gli
 * extras degli item esposti ai controller contengono tutti i campi di [Child]
 * (v. `MappingUtil.buildMusicExtras`, stesse chiavi `Constants.MEDIA_EXTRA_*`); si convertono solo
 * i brani musicali, perché podcast e radio non hanno una coda persistita.
 */
object QueueMapping {
    @JvmStatic
    fun fromMediaItems(items: List<MediaItem>?): List<Child> {
        val children = ArrayList<Child>(items?.size ?: 0)

        items?.forEach { item ->
            val extras = item.mediaMetadata.extras ?: return@forEach
            if (extras.getString(Constants.MEDIA_EXTRA_TYPE) != Constants.MEDIA_TYPE_MUSIC) return@forEach

            val id = extras.getString(Constants.MEDIA_EXTRA_ID) ?: return@forEach

            children.add(childOf(id, extras))
        }

        return children
    }

    private fun childOf(id: String, extras: Bundle) = Child(id).apply {
        parentId = extras.getString(Constants.MEDIA_EXTRA_PARENT_ID)
        isDir = extras.getBoolean(Constants.MEDIA_EXTRA_IS_DIR, false)
        title = extras.getString(Constants.MEDIA_EXTRA_TITLE)
        album = extras.getString(Constants.MEDIA_EXTRA_ALBUM)
        artist = extras.getString(Constants.MEDIA_EXTRA_ARTIST)
        track = extras.getInt(Constants.MEDIA_EXTRA_TRACK).takeIf { it > 0 }
        year = extras.getInt(Constants.MEDIA_EXTRA_YEAR).takeIf { it > 0 }
        genre = extras.getString(Constants.MEDIA_EXTRA_GENRE)
        coverArtId = extras.getString(Constants.MEDIA_EXTRA_COVER_ART_ID)
        size = extras.getLong(Constants.MEDIA_EXTRA_SIZE).takeIf { it > 0 }
        contentType = extras.getString(Constants.MEDIA_EXTRA_CONTENT_TYPE)
        suffix = extras.getString(Constants.MEDIA_EXTRA_SUFFIX)
        transcodedContentType = extras.getString(Constants.MEDIA_EXTRA_TRANSCODED_CONTENT_TYPE)
        transcodedSuffix = extras.getString(Constants.MEDIA_EXTRA_TRANSCODED_SUFFIX)
        duration = extras.getInt(Constants.MEDIA_EXTRA_DURATION).takeIf { it > 0 }
        bitrate = extras.getInt(Constants.MEDIA_EXTRA_BITRATE).takeIf { it > 0 }
        path = extras.getString(Constants.MEDIA_EXTRA_PATH)
        isVideo = extras.getBoolean(Constants.MEDIA_EXTRA_IS_VIDEO, false)
        userRating = extras.getInt(Constants.MEDIA_EXTRA_USER_RATING).takeIf { it > 0 }
        averageRating = extras.getDouble(Constants.MEDIA_EXTRA_AVERAGE_RATING).takeIf { it > 0 }
        playCount = extras.getLong(Constants.MEDIA_EXTRA_PLAY_COUNT).takeIf { it > 0 }
        discNumber = extras.getInt(Constants.MEDIA_EXTRA_DISC_NUMBER).takeIf { it > 0 }
        created = extras.getLong(Constants.MEDIA_EXTRA_CREATED).takeIf { it > 0 }?.let { Date(it) }
        starred = extras.getLong(Constants.MEDIA_EXTRA_STARRED).takeIf { it > 0 }?.let { Date(it) }
        albumId = extras.getString(Constants.MEDIA_EXTRA_ALBUM_ID)
        artistId = extras.getString(Constants.MEDIA_EXTRA_ARTIST_ID)
        type = Constants.MEDIA_TYPE_MUSIC
        bookmarkPosition = extras.getLong(Constants.MEDIA_EXTRA_BOOKMARK_POSITION).takeIf { it > 0 }
        originalWidth = extras.getInt(Constants.MEDIA_EXTRA_ORIGINAL_WIDTH).takeIf { it > 0 }
        originalHeight = extras.getInt(Constants.MEDIA_EXTRA_ORIGINAL_HEIGHT).takeIf { it > 0 }
    }
}
