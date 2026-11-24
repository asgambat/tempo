package com.cappielloantonio.tempo.util

object Constants {
    const val SHARED_PREF_KEY = "play-shared-preferences"

    const val ITEM_POSITION = "ITEM_POSITION"

    const val TRACK_OBJECT = "TRACK_OBJECT"
    const val TRACKS_OBJECT = "TRACKS_OBJECT"
    const val ALBUM_OBJECT = "ALBUM_OBJECT"
    const val ARTIST_OBJECT = "ARTIST_OBJECT"
    const val GENRE_OBJECT = "GENRE_OBJECT"
    const val PLAYLIST_OBJECT = "PLAYLIST_OBJECT"
    const val PODCAST_OBJECT = "PODCAST_OBJECT"
    const val PODCAST_CHANNEL_OBJECT = "PODCAST_CHANNEL_OBJECT"
    const val INTERNET_RADIO_STATION_OBJECT = "INTERNET_RADIO_STATION_OBJECT"
    const val MUSIC_FOLDER_OBJECT = "MUSIC_FOLDER_OBJECT"
    const val MUSIC_DIRECTORY_OBJECT = "MUSIC_DIRECTORY_OBJECT"
    const val MUSIC_INDEX_OBJECT = "MUSIC_DIRECTORY_OBJECT"
    const val MUSIC_DIRECTORY_ID = "MUSIC_DIRECTORY_ID"

    const val ALBUM_RECENTLY_PLAYED = "ALBUM_RECENTLY_PLAYED"
    const val ALBUM_MOST_PLAYED = "ALBUM_MOST_PLAYED"
    const val ALBUM_RECENTLY_ADDED = "ALBUM_RECENTLY_ADDED"
    const val ALBUM_DOWNLOADED = "ALBUM_DOWNLOADED"
    const val ALBUM_STARRED = "ALBUM_STARRED"
    const val ALBUM_FROM_ARTIST = "ALBUM_FROM_ARTIST"
    const val ALBUM_NEW_RELEASES = "ALBUM_NEW_RELEASES"
    const val ALBUM_ORDER_BY_NAME = "ALBUM_ORDER_BY_NAME"
    const val ALBUM_ORDER_BY_ARTIST = "ALBUM_ORDER_BY_ARTIST"
    const val ALBUM_ORDER_BY_YEAR = "ALBUM_ORDER_BY_YEAR"
    const val ALBUM_ORDER_BY_RANDOM = "ALBUM_ORDER_BY_RANDOM"
    const val ALBUM_ORDER_BY_RECENTLY_ADDED = "ALBUM_ORDER_BY_RECENTLY_ADDED"
    const val ALBUM_ORDER_BY_RECENTLY_PLAYED = "ALBUM_ORDER_BY_RECENTLY_PLAYED"
    const val ALBUM_ORDER_BY_MOST_PLAYED = "ALBUM_ORDER_BY_MOST_PLAYED"
    const val ALBUM_ORDER_BY_MOST_RECENTLY_STARRED = "ALBUM_ORDER_BY_MOST_RECENTLY_STARRED"
    const val ALBUM_ORDER_BY_LEAST_RECENTLY_STARRED = "ALBUM_ORDER_BY_LEAST_RECENTLY_STARRED"

    const val ARTIST_DOWNLOADED = "ARTIST_DOWNLOADED"
    const val ARTIST_STARRED = "ARTIST_STARRED"
    const val ARTIST_ORDER_BY_NAME = "ARTIST_ORDER_BY_NAME"
    const val ARTIST_ORDER_BY_RANDOM = "ARTIST_ORDER_BY_RANDOM"
    const val ARTIST_ORDER_BY_MOST_RECENTLY_STARRED = "ARTIST_ORDER_BY_MOST_RECENTLY_STARRED"
    const val ARTIST_ORDER_BY_LEAST_RECENTLY_STARRED = "ARTIST_ORDER_BY_LEAST_RECENTLY_STARRED"

    const val GENRE_ORDER_BY_NAME = "GENRE_ORDER_BY_NAME"
    const val GENRE_ORDER_BY_RANDOM = "GENRE_ORDER_BY_RANDOM"

    const val PLAYLIST_ALL = "ALL"
    const val PLAYLIST_DOWNLOADED = "DOWNLOADED"
    const val PLAYLIST_ORDER_BY_NAME = "ORDER_BY_NAME"
    const val PLAYLIST_ORDER_BY_RANDOM = "ORDER_BY_RANDOM"

    const val PODCAST_FILTER_BY_DOWNLOAD = "PODCAST_FILTER_BY_DOWNLOAD"
    const val PODCAST_FILTER_BY_ALL = "PODCAST_FILTER_BY_ALL"

    const val DIRECTORY_ORDER_BY_NAME = "DIRECTORY_ORDER_BY_NAME"
    const val DIRECTORY_ORDER_BY_NAME_DESC = "DIRECTORY_ORDER_BY_NAME_DESC"
    const val DIRECTORY_ORDER_BY_RECENTLY_ADDED = "DIRECTORY_ORDER_BY_RECENTLY_ADDED"
    const val DIRECTORY_ORDER_BY_LEAST_RECENTLY_ADDED = "DIRECTORY_ORDER_BY_LEAST_RECENTLY_ADDED"
    const val DIRECTORY_ORDER_BY_MOST_PLAYED = "DIRECTORY_ORDER_BY_MOST_PLAYED"

    const val MEDIA_TYPE_MUSIC = "music"
    const val MEDIA_TYPE_PODCAST = "podcast"
    const val MEDIA_TYPE_AUDIOBOOK = "audiobook"
    const val MEDIA_TYPE_VIDEO = "video"
    const val MEDIA_TYPE_RADIO = "radio"

    const val MEDIA_PLAYBACK_SPEED_080 = 0.8f
    const val MEDIA_PLAYBACK_SPEED_100 = 1.0f
    const val MEDIA_PLAYBACK_SPEED_125 = 1.25f
    const val MEDIA_PLAYBACK_SPEED_150 = 1.50f
    const val MEDIA_PLAYBACK_SPEED_175 = 1.75f
    const val MEDIA_PLAYBACK_SPEED_200 = 2.0f

    const val MEDIA_RECENTLY_PLAYED = "MEDIA_RECENTLY_PLAYED"
    const val MEDIA_MOST_PLAYED = "MEDIA_MOST_PLAYED"
    const val MEDIA_RECENTLY_ADDED = "MEDIA_RECENTLY_ADDED"
    const val MEDIA_BY_GENRE = "MEDIA_BY_GENRE"
    const val MEDIA_BY_GENRES = "MEDIA_BY_GENRES"
    const val MEDIA_BY_ARTIST = "MEDIA_BY_ARTIST"
    const val MEDIA_BY_YEAR = "MEDIA_BY_YEAR"
    const val MEDIA_STARRED = "MEDIA_STARRED"
    const val MEDIA_DOWNLOADED = "MEDIA_DOWNLOADED"
    const val MEDIA_FROM_ALBUM = "MEDIA_FROM_ALBUM"
    const val MEDIA_MIX = "MEDIA_MIX"
    const val MEDIA_CHRONOLOGY = "MEDIA_CHRONOLOGY"
    const val MEDIA_BEST_OF = "MEDIA_BEST_OF"
    const val MEDIA_BY_TITLE = "MEDIA_BY_TITLE"
    const val MEDIA_MOST_RECENTLY_STARRED = "MEDIA_MOST_RECENTLY_STARRED"
    const val MEDIA_LEAST_RECENTLY_STARRED = "MEDIA_LEAST_RECENTLY_STARRED"

    const val DOWNLOAD_URI = "rest/download"

    const val DOWNLOAD_TYPE_TRACK = "download_type_track"
    const val DOWNLOAD_TYPE_ALBUM = "download_type_album"
    const val DOWNLOAD_TYPE_ARTIST = "download_type_artist"
    const val DOWNLOAD_TYPE_GENRE = "download_type_genre"
    const val DOWNLOAD_TYPE_YEAR = "download_type_year"

    const val DOWNLOAD_GROUP = "download_group"
    const val DOWNLOAD_GROUP_TITLE = "download_group_title"
    const val DOWNLOAD_GROUP_SUBTITLE = "download_group_subtitle"

    const val SHARE_OBJECT = "share_object"

    const val PLAYABLE_MEDIA_LIMIT = 100
    const val PRE_PLAYABLE_MEDIA = 15
    const val DIRECTORY_SUBFOLDERS_MAX_DEPTH = 3
    const val DIRECTORY_SUBFOLDERS_MAX_REQUESTS = 100

    // Risoluzione dell'artwork inviato alla sessione (Android Auto e notifica): una misura
    // dedicata evita di scaricare l'originale del server a ogni cambio brano.
    const val SESSION_ARTWORK_SIZE = 500

    // Chiavi degli extras dei MediaItem (Bundle): le scrivono MappingUtil.buildMusicExtras e
    // SessionMediaItem, le leggono QueueMapping, Chronology, MediaManager e la UI del player.
    // Un unico posto evita che un rename lasci in silenzio metà del codice su chiavi diverse.
    const val MEDIA_EXTRA_ID = "id"
    const val MEDIA_EXTRA_PARENT_ID = "parentId"
    const val MEDIA_EXTRA_IS_DIR = "isDir"
    const val MEDIA_EXTRA_TITLE = "title"
    const val MEDIA_EXTRA_ALBUM = "album"
    const val MEDIA_EXTRA_ARTIST = "artist"
    const val MEDIA_EXTRA_TRACK = "track"
    const val MEDIA_EXTRA_YEAR = "year"
    const val MEDIA_EXTRA_GENRE = "genre"
    const val MEDIA_EXTRA_COVER_ART_ID = "coverArtId"
    const val MEDIA_EXTRA_SIZE = "size"
    const val MEDIA_EXTRA_CONTENT_TYPE = "contentType"
    const val MEDIA_EXTRA_SUFFIX = "suffix"
    const val MEDIA_EXTRA_TRANSCODED_CONTENT_TYPE = "transcodedContentType"
    const val MEDIA_EXTRA_TRANSCODED_SUFFIX = "transcodedSuffix"
    const val MEDIA_EXTRA_DURATION = "duration"
    const val MEDIA_EXTRA_BITRATE = "bitrate"
    const val MEDIA_EXTRA_PATH = "path"
    const val MEDIA_EXTRA_IS_VIDEO = "isVideo"
    const val MEDIA_EXTRA_USER_RATING = "userRating"
    const val MEDIA_EXTRA_AVERAGE_RATING = "averageRating"
    const val MEDIA_EXTRA_PLAY_COUNT = "playCount"
    const val MEDIA_EXTRA_DISC_NUMBER = "discNumber"
    const val MEDIA_EXTRA_CREATED = "created"
    const val MEDIA_EXTRA_STARRED = "starred"
    const val MEDIA_EXTRA_ALBUM_ID = "albumId"
    const val MEDIA_EXTRA_ARTIST_ID = "artistId"
    const val MEDIA_EXTRA_TYPE = "type"
    const val MEDIA_EXTRA_BOOKMARK_POSITION = "bookmarkPosition"
    const val MEDIA_EXTRA_ORIGINAL_WIDTH = "originalWidth"
    const val MEDIA_EXTRA_ORIGINAL_HEIGHT = "originalHeight"
    const val MEDIA_EXTRA_URI = "uri"
    const val MEDIA_EXTRA_DESCRIPTION = "description"

    const val HOME_SECTOR_FAVORITE_FOLDER = "HOME_SECTOR_FAVORITE_FOLDER"
    const val HOME_SECTOR_DISCOVERY = "HOME_SECTOR_DISCOVERY"
    const val HOME_SECTOR_MADE_FOR_YOU = "HOME_SECTOR_MADE_FOR_YOU"
    const val HOME_SECTOR_BEST_OF = "HOME_SECTOR_BEST_OF"
    const val HOME_SECTOR_RADIO_STATION = "HOME_SECTOR_RADIO_STATION"
    const val HOME_SECTOR_TOP_SONGS = "HOME_SECTOR_TOP_SONGS"
    const val HOME_SECTOR_STARRED_TRACKS = "HOME_SECTOR_STARRED_TRACKS"
    const val HOME_SECTOR_STARRED_ALBUMS = "HOME_SECTOR_STARRED_ALBUMS"
    const val HOME_SECTOR_STARRED_ARTISTS = "HOME_SECTOR_STARRED_ARTISTS"
    const val HOME_SECTOR_NEW_RELEASES = "HOME_SECTOR_NEW_RELEASES"
    const val HOME_SECTOR_FLASHBACK = "HOME_SECTOR_FLASHBACK"
    const val HOME_SECTOR_MOST_PLAYED = "HOME_SECTOR_MOST_PLAYED"
    const val HOME_SECTOR_LAST_PLAYED = "HOME_SECTOR_LAST_PLAYED"
    const val HOME_SECTOR_RECENTLY_ADDED = "HOME_SECTOR_RECENTLY_ADDED"
    const val HOME_SECTOR_PINNED_PLAYLISTS = "HOME_SECTOR_PINNED_PLAYLISTS"
    const val HOME_SECTOR_SHARED = "HOME_SECTOR_SHARED"
}