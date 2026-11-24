package com.cappielloantonio.tempo.util;

import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.glide.CustomGlideRequest;
import com.cappielloantonio.tempo.model.Download;
import com.cappielloantonio.tempo.repository.DownloadRepository;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.InternetRadioStation;
import com.cappielloantonio.tempo.subsonic.models.PodcastEpisode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@OptIn(markerClass = UnstableApi.class)
public class MappingUtil {
    public static List<MediaItem> mapMediaItems(List<Child> items) {
        return mapMediaItems(items, false);
    }

    /**
     * @param publicArtwork true per gli item esposti a controller esterni (Android Auto):
     *                      l'artwork URI non contiene credenziali Subsonic.
     */
    public static List<MediaItem> mapMediaItems(List<Child> items, boolean publicArtwork) {
        ArrayList<MediaItem> mediaItems = new ArrayList<>();

        if (items == null) {
            return mediaItems;
        }

        for (int i = 0; i < items.size(); i++) {
            mediaItems.add(mapMediaItem(items.get(i), publicArtwork));
        }

        return mediaItems;
    }

    public static MediaItem mapMediaItem(Child media) {
        return mapMediaItem(media, false);
    }

    public static MediaItem mapMediaItem(Child media, boolean publicArtwork) {
        Uri uri = getUri(media);
        Uri artworkUri = getArtworkUri(media.getCoverArtId(), media.getAlbumId(), publicArtwork);

        Bundle bundle = buildMusicExtras(media, uri, !publicArtwork);

        return new MediaItem.Builder()
                .setMediaId(media.getId())
                .setMediaMetadata(
                        new MediaMetadata.Builder()
                                .setTitle(media.getTitle())
                                .setTrackNumber(media.getTrack() != null ? media.getTrack() : 0)
                                .setDiscNumber(media.getDiscNumber() != null ? media.getDiscNumber() : 0)
                                .setReleaseYear(media.getYear() != null ? media.getYear() : 0)
                                .setAlbumTitle(media.getAlbum())
                                .setArtist(media.getArtist())
                                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                .setArtworkUri(artworkUri)
                                .setExtras(bundle)
                                .setIsBrowsable(false)
                                .setIsPlayable(true)
                                .build()
                )
                .setRequestMetadata(
                        new MediaItem.RequestMetadata.Builder()
                                .setMediaUri(uri)
                                .setExtras(bundle)
                                .build()
                )
                .setMimeType(MimeTypes.BASE_TYPE_AUDIO)
                .setUri(uri)
                .build();
    }

    public static List<MediaItem> mapDownloads(List<Child> items) {
        ArrayList<MediaItem> downloads = new ArrayList<>();

        if (items == null) {
            return downloads;
        }

        for (int i = 0; i < items.size(); i++) {
            downloads.add(mapDownload(items.get(i)));
        }

        return downloads;
    }

    public static MediaItem mapDownload(Child media) {
        return new MediaItem.Builder()
                .setMediaId(media.getId())
                .setMediaMetadata(
                        new MediaMetadata.Builder()
                                .setTitle(media.getTitle())
                                .setTrackNumber(media.getTrack() != null ? media.getTrack() : 0)
                                .setDiscNumber(media.getDiscNumber() != null ? media.getDiscNumber() : 0)
                                .setReleaseYear(media.getYear() != null ? media.getYear() : 0)
                                .setAlbumTitle(media.getAlbum())
                                .setArtist(media.getArtist())
                                .setIsBrowsable(false)
                                .setIsPlayable(true)
                                .build()
                )
                .setRequestMetadata(
                        new MediaItem.RequestMetadata.Builder()
                                .setMediaUri(Preferences.preferTranscodedDownload() ? MusicUtil.getTranscodedDownloadUri(media.getId()) : MusicUtil.getDownloadUri(media.getId()))
                                .build()
                )
                .setMimeType(MimeTypes.BASE_TYPE_AUDIO)
                .setUri(Preferences.preferTranscodedDownload() ? MusicUtil.getTranscodedDownloadUri(media.getId()) : MusicUtil.getDownloadUri(media.getId()))
                .build();
    }

    public static List<MediaItem> mapDownloadedItems(List<Download> items) {
        return mapDownloadedItems(items, false);
    }

    public static List<MediaItem> mapDownloadedItems(List<Download> items, boolean publicArtwork) {
        ArrayList<MediaItem> mediaItems = new ArrayList<>();

        if (items == null) {
            return mediaItems;
        }

        for (Download download : items) {
            mediaItems.add(mapDownloadedItem(download, publicArtwork));
        }

        return mediaItems;
    }

    public static MediaItem mapDownloadedItem(Download download) {
        return mapDownloadedItem(download, false);
    }

    public static MediaItem mapDownloadedItem(Download download, boolean publicArtwork) {
        Uri uri = getStoredDownloadUri(download);
        Uri artworkUri = publicArtwork ? getDownloadedPublicArtworkUri(download.getCoverArtId()) : getDownloadArtworkUri(download.getCoverArtId());

        // Gli extras servono a MediaService per scrobble e chronology anche in riproduzione offline
        Bundle extras = buildMusicExtras(download, uri, !publicArtwork);

        return new MediaItem.Builder()
                .setMediaId(download.getId())
                .setMediaMetadata(
                        new MediaMetadata.Builder()
                                .setTitle(download.getTitle())
                                .setTrackNumber(download.getTrack() != null ? download.getTrack() : 0)
                                .setDiscNumber(download.getDiscNumber() != null ? download.getDiscNumber() : 0)
                                .setReleaseYear(download.getYear() != null ? download.getYear() : 0)
                                .setAlbumTitle(download.getAlbum())
                                .setArtist(download.getArtist())
                                .setArtworkUri(artworkUri)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                .setExtras(extras)
                                .setIsBrowsable(false)
                                .setIsPlayable(true)
                                .build()
                )
                .setRequestMetadata(
                        new MediaItem.RequestMetadata.Builder()
                                .setMediaUri(uri)
                                .setExtras(extras)
                                .build()
                )
                .setMimeType(MimeTypes.BASE_TYPE_AUDIO)
                .setUri(uri)
                .build();
    }

    private static Bundle buildMusicExtras(Child media, Uri uri, boolean includeUri) {
        Bundle bundle = new Bundle();
        bundle.putString(Constants.MEDIA_EXTRA_ID, media.getId());
        bundle.putString(Constants.MEDIA_EXTRA_PARENT_ID, media.getParentId());
        bundle.putBoolean(Constants.MEDIA_EXTRA_IS_DIR, media.isDir());
        bundle.putString(Constants.MEDIA_EXTRA_TITLE, media.getTitle());
        bundle.putString(Constants.MEDIA_EXTRA_ALBUM, media.getAlbum());
        bundle.putString(Constants.MEDIA_EXTRA_ARTIST, media.getArtist());
        bundle.putInt(Constants.MEDIA_EXTRA_TRACK, media.getTrack() != null ? media.getTrack() : 0);
        bundle.putInt(Constants.MEDIA_EXTRA_YEAR, media.getYear() != null ? media.getYear() : 0);
        bundle.putString(Constants.MEDIA_EXTRA_GENRE, media.getGenre());
        bundle.putString(Constants.MEDIA_EXTRA_COVER_ART_ID, media.getCoverArtId());
        bundle.putLong(Constants.MEDIA_EXTRA_SIZE, media.getSize() != null ? media.getSize() : 0);
        bundle.putString(Constants.MEDIA_EXTRA_CONTENT_TYPE, media.getContentType());
        bundle.putString(Constants.MEDIA_EXTRA_SUFFIX, media.getSuffix());
        bundle.putString(Constants.MEDIA_EXTRA_TRANSCODED_CONTENT_TYPE, media.getTranscodedContentType());
        bundle.putString(Constants.MEDIA_EXTRA_TRANSCODED_SUFFIX, media.getTranscodedSuffix());
        bundle.putInt(Constants.MEDIA_EXTRA_DURATION, media.getDuration() != null ? media.getDuration() : 0);
        bundle.putInt(Constants.MEDIA_EXTRA_BITRATE, media.getBitrate() != null ? media.getBitrate() : 0);
        bundle.putString(Constants.MEDIA_EXTRA_PATH, media.getPath());
        bundle.putBoolean(Constants.MEDIA_EXTRA_IS_VIDEO, media.isVideo());
        bundle.putInt(Constants.MEDIA_EXTRA_USER_RATING, media.getUserRating() != null ? media.getUserRating() : 0);
        bundle.putDouble(Constants.MEDIA_EXTRA_AVERAGE_RATING, media.getAverageRating() != null ? media.getAverageRating() : 0);
        bundle.putLong(Constants.MEDIA_EXTRA_PLAY_COUNT, media.getPlayCount() != null ? media.getPlayCount() : 0);
        bundle.putInt(Constants.MEDIA_EXTRA_DISC_NUMBER, media.getDiscNumber() != null ? media.getDiscNumber() : 0);
        bundle.putLong(Constants.MEDIA_EXTRA_CREATED, media.getCreated() != null ? media.getCreated().getTime() : 0);
        bundle.putLong(Constants.MEDIA_EXTRA_STARRED, media.getStarred() != null ? media.getStarred().getTime() : 0);
        bundle.putString(Constants.MEDIA_EXTRA_ALBUM_ID, media.getAlbumId());
        bundle.putString(Constants.MEDIA_EXTRA_ARTIST_ID, media.getArtistId());
        bundle.putString(Constants.MEDIA_EXTRA_TYPE, Constants.MEDIA_TYPE_MUSIC);
        bundle.putLong(Constants.MEDIA_EXTRA_BOOKMARK_POSITION, media.getBookmarkPosition() != null ? media.getBookmarkPosition() : 0);
        bundle.putInt(Constants.MEDIA_EXTRA_ORIGINAL_WIDTH, media.getOriginalWidth() != null ? media.getOriginalWidth() : 0);
        bundle.putInt(Constants.MEDIA_EXTRA_ORIGINAL_HEIGHT, media.getOriginalHeight() != null ? media.getOriginalHeight() : 0);

        // L'URI di streaming contiene le credenziali Subsonic e gli extras sono trasmessi ai
        // controller: niente chiave "uri" negli item esposti ad Android Auto (publicArtwork).
        if (includeUri) bundle.putString(Constants.MEDIA_EXTRA_URI, uri.toString());

        return bundle;
    }

    private static Uri getStoredDownloadUri(Download download) {
        String storedUri = download.getDownloadUri();

        if (storedUri != null && !storedUri.isEmpty()) {
            return Uri.parse(storedUri);
        }

        // Senza server non c'è un client Subsonic da interrogare: l'item resta non riproducibile
        return isServerConfigured() ? MusicUtil.getDownloadUri(download.getId()) : Uri.parse("");
    }

    /**
     * Id da usare in getCoverArt per un brano. Alcuni server non espongono "coverArt" sui singoli
     * brani ma solo sull'album: in quel caso si usa albumId, così ogni album ha il suo artwork.
     * Restituisce null quando non c'è nessun id: un URI costruito con "id=null" sarebbe identico
     * per tutti i brani e l'host (Android Auto) continuerebbe a mostrare la copertina precedente.
     */
    public static String getArtworkId(String coverArtId, String albumId) {
        if (coverArtId != null && !coverArtId.isEmpty()) return coverArtId;
        if (albumId != null && !albumId.isEmpty()) return albumId;

        return null;
    }

    private static Uri getArtworkUri(String coverArtId, String albumId, boolean publicArtwork) {
        if (publicArtwork) return getPublicArtworkUri(coverArtId, albumId);

        String artworkId = getArtworkId(coverArtId, albumId);

        if (artworkId == null) return null;

        return Uri.parse(CustomGlideRequest.createUrl(artworkId, Preferences.getImageSize()));
    }

    public static Uri getPublicArtworkUri(String coverArtId) {
        return getPublicArtworkUri(coverArtId, null);
    }

    /**
     * Artwork URI senza credenziali Subsonic, per i MediaItem esposti ad Android Auto.
     * Il BitmapLoader della sessione aggiunge l'autenticazione al momento del caricamento.
     */
    public static Uri getPublicArtworkUri(String coverArtId, String albumId) {
        String artworkId = getArtworkId(coverArtId, albumId);

        if (artworkId == null || !isServerConfigured()) {
            return null;
        }

        return MusicUtil.getCoverArtUri(artworkId, Constants.SESSION_ARTWORK_SIZE);
    }

    private static Uri getDownloadArtworkUri(String coverArtId) {
        if (coverArtId == null || coverArtId.isEmpty() || !isServerConfigured()) {
            return null;
        }

        return Uri.parse(CustomGlideRequest.createUrl(coverArtId, Preferences.getImageSize()));
    }

    /**
     * Artwork dei download per i controller esterni: l'URL pubblico richiede un server
     * configurato, quindi senza server si usa l'URI locale (id Subsonic, opaco: non espone il path
     * dei file dell'app). La copia su disco non viene cercata qui: la risolve il
     * CachingBitmapLoader della sessione al momento della decodifica, fuori dal thread che
     * costruisce la lista dei download. Se la copertina non è in cache l'artwork resta mancante.
     */
    @Nullable
    private static Uri getDownloadedPublicArtworkUri(String coverArtId) {
        Uri publicArtworkUri = getPublicArtworkUri(coverArtId);

        if (publicArtworkUri != null) return publicArtworkUri;

        if (coverArtId == null || coverArtId.isEmpty()) return null;

        return LocalArtwork.uriFor(coverArtId);
    }

    public static boolean isServerConfigured() {
        String serverId = Preferences.getServerId();
        String server = Preferences.getServer();

        return serverId != null && !serverId.isEmpty() && server != null && !server.isEmpty();
    }

    public static MediaItem mapInternetRadioStation(InternetRadioStation internetRadioStation) {
        Uri uri = Uri.parse(internetRadioStation.getStreamUrl());

        Bundle bundle = new Bundle();
        bundle.putString(Constants.MEDIA_EXTRA_ID, internetRadioStation.getId());
        bundle.putString(Constants.MEDIA_EXTRA_TITLE, internetRadioStation.getName());
        bundle.putString(Constants.MEDIA_EXTRA_ARTIST, uri.toString());
        bundle.putString(Constants.MEDIA_EXTRA_URI, uri.toString());
        bundle.putString(Constants.MEDIA_EXTRA_TYPE, Constants.MEDIA_TYPE_RADIO);

        return new MediaItem.Builder()
                .setMediaId(internetRadioStation.getId())
                .setMediaMetadata(
                        new MediaMetadata.Builder()
                                .setTitle(internetRadioStation.getName())
                                .setArtist(internetRadioStation.getStreamUrl())
                                .setExtras(bundle)
                                .setIsBrowsable(false)
                                .setIsPlayable(true)
                                .build()
                )
                .setRequestMetadata(
                        new MediaItem.RequestMetadata.Builder()
                                .setMediaUri(uri)
                                .setExtras(bundle)
                                .build()
                )
                // .setMimeType(MimeTypes.BASE_TYPE_AUDIO)
                .setUri(uri)
                .build();
    }

    public static MediaItem mapMediaItem(PodcastEpisode podcastEpisode) {
        Uri uri = getUri(podcastEpisode);
        Uri artworkUri = Uri.parse(CustomGlideRequest.createUrl(podcastEpisode.getCoverArtId(), Preferences.getImageSize()));

        Bundle bundle = new Bundle();
        bundle.putString(Constants.MEDIA_EXTRA_ID, podcastEpisode.getId());
        bundle.putString(Constants.MEDIA_EXTRA_PARENT_ID, podcastEpisode.getParentId());
        bundle.putBoolean(Constants.MEDIA_EXTRA_IS_DIR, podcastEpisode.isDir());
        bundle.putString(Constants.MEDIA_EXTRA_TITLE, podcastEpisode.getTitle());
        bundle.putString(Constants.MEDIA_EXTRA_ALBUM, podcastEpisode.getAlbum());
        bundle.putString(Constants.MEDIA_EXTRA_ARTIST, podcastEpisode.getArtist());
        bundle.putInt(Constants.MEDIA_EXTRA_YEAR, podcastEpisode.getYear() != null ? podcastEpisode.getYear() : 0);
        bundle.putString(Constants.MEDIA_EXTRA_COVER_ART_ID, podcastEpisode.getCoverArtId());
        bundle.putLong(Constants.MEDIA_EXTRA_SIZE, podcastEpisode.getSize() != null ? podcastEpisode.getSize() : 0);
        bundle.putString(Constants.MEDIA_EXTRA_CONTENT_TYPE, podcastEpisode.getContentType());
        bundle.putString(Constants.MEDIA_EXTRA_SUFFIX, podcastEpisode.getSuffix());
        bundle.putInt(Constants.MEDIA_EXTRA_DURATION, podcastEpisode.getDuration() != null ? podcastEpisode.getDuration() : 0);
        bundle.putInt(Constants.MEDIA_EXTRA_BITRATE, podcastEpisode.getBitrate() != null ? podcastEpisode.getBitrate() : 0);
        bundle.putBoolean(Constants.MEDIA_EXTRA_IS_VIDEO, podcastEpisode.isVideo());
        bundle.putLong(Constants.MEDIA_EXTRA_CREATED, podcastEpisode.getCreated() != null ? podcastEpisode.getCreated().getTime() : 0);
        bundle.putString(Constants.MEDIA_EXTRA_ARTIST_ID, podcastEpisode.getArtistId());
        bundle.putString(Constants.MEDIA_EXTRA_DESCRIPTION, podcastEpisode.getDescription());
        bundle.putString(Constants.MEDIA_EXTRA_TYPE, Constants.MEDIA_TYPE_PODCAST);
        bundle.putString(Constants.MEDIA_EXTRA_URI, uri.toString());

        MediaItem item = new MediaItem.Builder()
                .setMediaId(podcastEpisode.getId())
                .setMediaMetadata(
                        new MediaMetadata.Builder()
                                .setTitle(podcastEpisode.getTitle())
                                .setReleaseYear(podcastEpisode.getYear() != null ? podcastEpisode.getYear() : 0)
                                .setAlbumTitle(podcastEpisode.getAlbum())
                                .setArtist(podcastEpisode.getArtist())
                                .setArtworkUri(artworkUri)
                                .setExtras(bundle)
                                .setIsBrowsable(false)
                                .setIsPlayable(true)
                                .build()
                )
                .setRequestMetadata(
                        new MediaItem.RequestMetadata.Builder()
                                .setMediaUri(uri)
                                .setExtras(bundle)
                                .build()
                )
                .setMimeType(MimeTypes.BASE_TYPE_AUDIO)
                .setUri(uri)
                .build();

        return item;
    }

    private static Uri getUri(Child media) {
        return DownloadUtil.getDownloadTracker(App.getContext()).isDownloaded(media.getId())
                ? getDownloadUri(media.getId())
                : MusicUtil.getStreamUri(media.getId());
    }

    private static Uri getUri(PodcastEpisode podcastEpisode) {
        return DownloadUtil.getDownloadTracker(App.getContext()).isDownloaded(podcastEpisode.getStreamId())
                ? getDownloadUri(podcastEpisode.getStreamId())
                : MusicUtil.getStreamUri(podcastEpisode.getStreamId());
    }

    private static Uri getDownloadUri(String id) {
        Download download = new DownloadRepository().getDownload(id);
        return download != null && !download.getDownloadUri().isEmpty() ? Uri.parse(download.getDownloadUri()) : MusicUtil.getDownloadUri(id);
    }
}
