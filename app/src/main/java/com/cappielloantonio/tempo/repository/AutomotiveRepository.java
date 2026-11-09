package com.cappielloantonio.tempo.repository;


import android.net.Uri;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.LibraryResult;
import androidx.media3.session.MediaSession;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.database.AppDatabase;
import com.cappielloantonio.tempo.database.dao.ChronologyDao;
import com.cappielloantonio.tempo.database.dao.DownloadDao;
import com.cappielloantonio.tempo.database.dao.QueueDao;
import com.cappielloantonio.tempo.database.dao.SessionMediaItemDao;
import com.cappielloantonio.tempo.model.Chronology;
import com.cappielloantonio.tempo.model.Download;
import com.cappielloantonio.tempo.model.Queue;
import com.cappielloantonio.tempo.model.SessionMediaItem;
import com.cappielloantonio.tempo.service.DownloaderManager;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;
import com.cappielloantonio.tempo.subsonic.models.AlbumID3;
import com.cappielloantonio.tempo.subsonic.models.Artist;
import com.cappielloantonio.tempo.subsonic.models.ArtistID3;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.Directory;
import com.cappielloantonio.tempo.subsonic.models.Index;
import com.cappielloantonio.tempo.subsonic.models.InternetRadioStation;
import com.cappielloantonio.tempo.subsonic.models.MusicFolder;
import com.cappielloantonio.tempo.subsonic.models.Playlist;
import com.cappielloantonio.tempo.subsonic.models.PodcastEpisode;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.MappingUtil;
import com.cappielloantonio.tempo.util.MusicUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AutomotiveRepository {
    private static final String TAG = "AutomotiveRepository";

    private static final long METADATA_TTL_MS = 24 * 60 * 60 * 1000L;
    private static final int SESSION_METADATA_CHUNK = 500;

    private final SessionMediaItemDao sessionMediaItemDao = AppDatabase.getInstance().sessionMediaItemDao();
    private final ChronologyDao chronologyDao = AppDatabase.getInstance().chronologyDao();
    private final DownloadDao downloadDao = AppDatabase.getInstance().downloadDao();
    private final QueueDao queueDao = AppDatabase.getInstance().queueDao();

    private static <T> void completeError(SettableFuture<LibraryResult<T>> future, int errorCode) {
        future.set(LibraryResult.ofError(errorCode));
    }

    private static <T> void completeFailure(SettableFuture<LibraryResult<T>> future, Throwable throwable) {
        Log.e(TAG, "Request failed", throwable);
        completeError(future, LibraryResult.RESULT_ERROR_IO);
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getAlbums(String prefix, String type, int size) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getAlbumList2(type, size, 0, null, null)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse() != null && response.body().getSubsonicResponse().getAlbumList2() != null && response.body().getSubsonicResponse().getAlbumList2().getAlbums() != null) {
                            List<AlbumID3> albums = response.body().getSubsonicResponse().getAlbumList2().getAlbums();

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (AlbumID3 album : albums) {
                                Uri artworkUri = MappingUtil.getPublicArtworkUri(album.getCoverArtId());

                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(album.getName())
                                        .setAlbumTitle(album.getName())
                                        .setArtist(album.getArtist())
                                        .setGenre(album.getGenre())
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                                        .setArtworkUri(artworkUri)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(prefix + album.getId())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri("")
                                        .build();

                                mediaItems.add(mediaItem);
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            completeError(listenableFuture, LibraryResult.RESULT_ERROR_BAD_VALUE);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        completeFailure(listenableFuture, t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getStarredSongs() {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getStarred2()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse() != null && response.body().getSubsonicResponse().getStarred2() != null && response.body().getSubsonicResponse().getStarred2().getSongs() != null) {
                            List<Child> songs = response.body().getSubsonicResponse().getStarred2().getSongs();

                            setChildrenMetadata(songs);

                            List<MediaItem> mediaItems = MappingUtil.mapMediaItems(songs, true);

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            completeError(listenableFuture, LibraryResult.RESULT_ERROR_BAD_VALUE);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        completeFailure(listenableFuture, t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getRandomSongs(int count) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getRandomSongs(100, null, null)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse() != null && response.body().getSubsonicResponse().getRandomSongs() != null && response.body().getSubsonicResponse().getRandomSongs().getSongs() != null) {
                            List<Child> songs = response.body().getSubsonicResponse().getRandomSongs().getSongs();

                            setChildrenMetadata(songs);

                            List<MediaItem> mediaItems = MappingUtil.mapMediaItems(songs, true);

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            completeError(listenableFuture, LibraryResult.RESULT_ERROR_BAD_VALUE);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        completeFailure(listenableFuture, t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getRecentlyPlayedSongs(String server, int count) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        if (server == null || server.isEmpty()) {
            completeError(listenableFuture, LibraryResult.RESULT_ERROR_BAD_VALUE);
            return listenableFuture;
        }

        final LiveData<List<Chronology>> lastPlayed = chronologyDao.getLastPlayed(server, count);

        lastPlayed.observeForever(new Observer<List<Chronology>>() {
            @Override
            public void onChanged(List<Chronology> chronology) {
                lastPlayed.removeObserver(this);

                if (chronology != null && !chronology.isEmpty()) {
                    List<Child> songs = new ArrayList<>(chronology);

                    setChildrenMetadata(songs);

                    List<MediaItem> mediaItems = MappingUtil.mapMediaItems(songs, true);

                    LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                    listenableFuture.set(libraryResult);
                } else {
                    completeError(listenableFuture, LibraryResult.RESULT_ERROR_BAD_VALUE);
                }
            }
        });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getStarredAlbums(String prefix) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getStarred2()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse() != null && response.body().getSubsonicResponse().getStarred2() != null && response.body().getSubsonicResponse().getStarred2().getAlbums() != null) {
                            List<AlbumID3> albums = response.body().getSubsonicResponse().getStarred2().getAlbums();

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (AlbumID3 album : albums) {
                                Uri artworkUri = MappingUtil.getPublicArtworkUri(album.getCoverArtId());

                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(album.getName())
                                        .setArtist(album.getArtist())
                                        .setGenre(album.getGenre())
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                                        .setArtworkUri(artworkUri)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(prefix + album.getId())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri("")
                                        .build();

                                mediaItems.add(mediaItem);
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            completeError(listenableFuture, LibraryResult.RESULT_ERROR_BAD_VALUE);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        completeFailure(listenableFuture, t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getStarredArtists(String prefix) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getStarred2()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse() != null && response.body().getSubsonicResponse().getStarred2() != null && response.body().getSubsonicResponse().getStarred2().getArtists() != null) {
                            List<ArtistID3> artists = response.body().getSubsonicResponse().getStarred2().getArtists();

                            Collections.shuffle(artists);

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (ArtistID3 artist : artists) {
                                Uri artworkUri = MappingUtil.getPublicArtworkUri(artist.getCoverArtId());

                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(artist.getName())
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
                                        .setArtworkUri(artworkUri)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(prefix + artist.getId())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri("")
                                        .build();

                                mediaItems.add(mediaItem);
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            completeError(listenableFuture, LibraryResult.RESULT_ERROR_BAD_VALUE);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        completeFailure(listenableFuture, t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getMusicFolders(String prefix) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getMusicFolders()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse() != null && response.body().getSubsonicResponse().getMusicFolders() != null && response.body().getSubsonicResponse().getMusicFolders().getMusicFolders() != null) {
                            List<MusicFolder> musicFolders = response.body().getSubsonicResponse().getMusicFolders().getMusicFolders();

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (MusicFolder musicFolder : musicFolders) {
                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(musicFolder.getName())
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(prefix + musicFolder.getId())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri("")
                                        .build();

                                mediaItems.add(mediaItem);
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            completeError(listenableFuture, LibraryResult.RESULT_ERROR_BAD_VALUE);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        completeFailure(listenableFuture, t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getIndexes(String artistPrefix, String directoryPrefix, String id) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getIndexes(id, null)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse() != null && response.body().getSubsonicResponse().getIndexes() != null) {
                            List<MediaItem> mediaItems = new ArrayList<>();

                            if (response.body().getSubsonicResponse().getIndexes().getIndices() != null) {
                                List<Index> indices = response.body().getSubsonicResponse().getIndexes().getIndices();

                                for (Index index : indices) {
                                    if (index.getArtists() != null) {
                                        for (Artist artist : index.getArtists()) {
                                            MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                                    .setTitle(artist.getName())
                                                    .setIsBrowsable(true)
                                                    .setIsPlayable(false)
                                                    .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
                                                    .build();

                                            MediaItem mediaItem = new MediaItem.Builder()
                                                    .setMediaId(artistPrefix + artist.getId())
                                                    .setMediaMetadata(mediaMetadata)
                                                    .setUri("")
                                                    .build();

                                            mediaItems.add(mediaItem);
                                        }
                                    }
                                }
                            }

                            if (response.body().getSubsonicResponse().getIndexes().getChildren() != null) {
                                List<Child> children = response.body().getSubsonicResponse().getIndexes().getChildren();
                                List<Child> songs = new ArrayList<>();

                                for (Child child : children) {
                                    if (child.isDir()) {
                                        MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                                .setTitle(child.getTitle())
                                                .setIsBrowsable(true)
                                                .setIsPlayable(false)
                                                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                                                .setArtworkUri(MappingUtil.getPublicArtworkUri(child.getCoverArtId()))
                                                .build();

                                        mediaItems.add(new MediaItem.Builder()
                                                .setMediaId(directoryPrefix + child.getId())
                                                .setMediaMetadata(mediaMetadata)
                                                .setUri("")
                                                .build());
                                    } else {
                                        songs.add(child);
                                    }
                                }

                                // Come in getDirectories: i brani passano da MappingUtil, così hanno
                                // extras e mediaId reale (il lookup dei metadati usa l'id Subsonic)
                                mediaItems.addAll(MappingUtil.mapMediaItems(songs, true));
                                setChildrenMetadata(songs);
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            completeError(listenableFuture, LibraryResult.RESULT_ERROR_BAD_VALUE);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        completeFailure(listenableFuture, t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getDirectories(String prefix, String id) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getMusicDirectory(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse() != null && response.body().getSubsonicResponse().getDirectory() != null && response.body().getSubsonicResponse().getDirectory().getChildren() != null) {
                            Directory directory = response.body().getSubsonicResponse().getDirectory();

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (Child child : directory.getChildren()) {
                                Uri artworkUri = MappingUtil.getPublicArtworkUri(child.getCoverArtId());

                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(child.getTitle())
                                        .setIsBrowsable(child.isDir())
                                        .setIsPlayable(!child.isDir())
                                        .setMediaType(child.isDir() ? MediaMetadata.MEDIA_TYPE_FOLDER_MIXED : MediaMetadata.MEDIA_TYPE_MUSIC)
                                        .setArtworkUri(artworkUri)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(child.isDir() ? prefix + child.getId() : child.getId())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri(!child.isDir() ? MusicUtil.getStreamUri(child.getId()) : Uri.parse(""))
                                        .build();

                                mediaItems.add(mediaItem);
                            }

                            setChildrenMetadata(directory.getChildren().stream().filter(child -> !child.isDir()).collect(Collectors.toList()));

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            completeError(listenableFuture, LibraryResult.RESULT_ERROR_BAD_VALUE);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        completeFailure(listenableFuture, t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getPlaylists(String prefix) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .getPlaylists()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse() != null && response.body().getSubsonicResponse().getPlaylists() != null && response.body().getSubsonicResponse().getPlaylists().getPlaylists() != null) {
                            List<Playlist> playlists = response.body().getSubsonicResponse().getPlaylists().getPlaylists();

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (Playlist playlist : playlists) {
                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(playlist.getName())
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(prefix + playlist.getId())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri("")
                                        .build();

                                mediaItems.add(mediaItem);
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            completeError(listenableFuture, LibraryResult.RESULT_ERROR_BAD_VALUE);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        completeFailure(listenableFuture, t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getNewestPodcastEpisodes(int count) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getPodcastClient()
                .getNewestPodcasts(count)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse() != null && response.body().getSubsonicResponse().getNewestPodcasts() != null && response.body().getSubsonicResponse().getNewestPodcasts().getEpisodes() != null) {
                            List<PodcastEpisode> episodes = response.body().getSubsonicResponse().getNewestPodcasts().getEpisodes();

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (PodcastEpisode episode : episodes) {
                                Uri artworkUri = MappingUtil.getPublicArtworkUri(episode.getCoverArtId());

                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(episode.getTitle())
                                        .setIsBrowsable(false)
                                        .setIsPlayable(true)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                                        .setArtworkUri(artworkUri)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(episode.getId())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri(MusicUtil.getStreamUri(episode.getStreamId()))
                                        .build();

                                mediaItems.add(mediaItem);
                            }

                            setPodcastEpisodesMetadata(episodes);

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            completeError(listenableFuture, LibraryResult.RESULT_ERROR_BAD_VALUE);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        completeFailure(listenableFuture, t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getInternetRadioStations() {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getInternetRadioClient()
                .getInternetRadioStations()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse() != null && response.body().getSubsonicResponse().getInternetRadioStations() != null && response.body().getSubsonicResponse().getInternetRadioStations().getInternetRadioStations() != null) {

                            List<InternetRadioStation> radioStations = response.body().getSubsonicResponse().getInternetRadioStations().getInternetRadioStations();

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (InternetRadioStation radioStation : radioStations) {
                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(radioStation.getName())
                                        .setIsBrowsable(false)
                                        .setIsPlayable(true)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(radioStation.getId())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri(radioStation.getStreamUrl())
                                        .build();

                                mediaItems.add(mediaItem);
                            }

                            setInternetRadioStationsMetadata(radioStations);

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            completeError(listenableFuture, LibraryResult.RESULT_ERROR_BAD_VALUE);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        completeFailure(listenableFuture, t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getDownloads() {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    List<Download> downloads = downloadDao.getAllSync();

                    if (downloads == null || downloads.isEmpty()) {
                        listenableFuture.set(LibraryResult.ofItemList(ImmutableList.of(emptyDownloadsItem()), null));
                        return;
                    }

                    List<MediaItem> mediaItems = MappingUtil.mapDownloadedItems(downloads, true);

                    listenableFuture.set(LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null));
                } catch (Throwable t) {
                    completeFailure(listenableFuture, t);
                }
            }
        });

        thread.start();

        return listenableFuture;
    }

    public ListenableFuture<MediaSession.MediaItemsWithStartPosition> getPlaybackResumption() {
        final SettableFuture<MediaSession.MediaItemsWithStartPosition> listenableFuture = SettableFuture.create();

        // Senza server gli URI di streaming non sono costruibili: meglio nessuna ripresa che una coda rotta.
        if (!MappingUtil.isServerConfigured()) {
            listenableFuture.set(null);
            return listenableFuture;
        }

        Thread thread = new Thread(() -> {
            try {
                List<Queue> queue = queueDao.getAllSimple();

                if (queue == null || queue.isEmpty()) {
                    listenableFuture.set(null);
                    return;
                }

                List<Child> media = new ArrayList<>(queue);
                Queue lastPlayed = queueDao.getLastPlayed();

                int startIndex = 0;

                // last_play resta 0 finché l'utente non ha riprodotto nulla: in quel caso si parte dall'inizio
                if (lastPlayed != null && lastPlayed.getLastPlay() > 0) {
                    startIndex = Math.min(Math.max(lastPlayed.getTrackOrder(), 0), media.size() - 1);
                }

                List<Child> limited = MusicUtil.limitPlayableMedia(media, startIndex);

                if (limited.isEmpty()) {
                    listenableFuture.set(null);
                    return;
                }

                int limitedStartIndex = MusicUtil.getPlayableMediaPosition(media, startIndex);
                List<MediaItem> mediaItems = MappingUtil.mapMediaItems(limited, true);

                // Posizione nel brano 0: non è persistita localmente (in Queue ci sono solo
                // timestamp), quindi a freddo si riparte dall'inizio del brano corrente.
                listenableFuture.set(
                        new MediaSession.MediaItemsWithStartPosition(mediaItems, limitedStartIndex, 0)
                );
            } catch (Throwable t) {
                Log.e(TAG, "Unable to build the playback resumption queue", t);
                listenableFuture.set(null);
            }
        });

        thread.start();

        return listenableFuture;
    }

    private static MediaItem emptyDownloadsItem() {
        MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                .setTitle(App.getContext().getString(R.string.auto_no_downloads))
                .setIsBrowsable(false)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MIXED)
                .build();

        return new MediaItem.Builder()
                .setMediaId("[emptyDownloadsID]")
                .setMediaMetadata(mediaMetadata)
                .setUri("")
                .build();
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getAlbumTracks(String id) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getAlbum(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse() != null && response.body().getSubsonicResponse().getAlbum() != null && response.body().getSubsonicResponse().getAlbum().getSongs() != null) {
                            List<Child> tracks = response.body().getSubsonicResponse().getAlbum().getSongs();

                            setChildrenMetadata(tracks);

                            List<MediaItem> mediaItems = MappingUtil.mapMediaItems(tracks, true);

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            completeError(listenableFuture, LibraryResult.RESULT_ERROR_BAD_VALUE);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        completeFailure(listenableFuture, t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getArtistAlbum(String prefix, String id) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getArtist(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse() != null && response.body().getSubsonicResponse().getArtist() != null && response.body().getSubsonicResponse().getArtist().getAlbums() != null) {
                            List<AlbumID3> albums = response.body().getSubsonicResponse().getArtist().getAlbums();

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (AlbumID3 album : albums) {
                                Uri artworkUri = MappingUtil.getPublicArtworkUri(album.getCoverArtId());

                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(album.getName())
                                        .setAlbumTitle(album.getName())
                                        .setArtist(album.getArtist())
                                        .setGenre(album.getGenre())
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                                        .setArtworkUri(artworkUri)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(prefix + album.getId())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri("")
                                        .build();

                                mediaItems.add(mediaItem);
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            completeError(listenableFuture, LibraryResult.RESULT_ERROR_BAD_VALUE);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        completeFailure(listenableFuture, t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getPlaylistSongs(String id) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .getPlaylist(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse() != null && response.body().getSubsonicResponse().getPlaylist() != null && response.body().getSubsonicResponse().getPlaylist().getEntries() != null) {
                            List<Child> tracks = response.body().getSubsonicResponse().getPlaylist().getEntries();

                            setChildrenMetadata(tracks);

                            List<MediaItem> mediaItems = MappingUtil.mapMediaItems(tracks, true);

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            completeError(listenableFuture, LibraryResult.RESULT_ERROR_BAD_VALUE);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        completeFailure(listenableFuture, t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getMadeForYou(String id, int count) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getSimilarSongs2(id, count)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse() != null && response.body().getSubsonicResponse().getSimilarSongs2() != null && response.body().getSubsonicResponse().getSimilarSongs2().getSongs() != null) {
                            List<Child> tracks = response.body().getSubsonicResponse().getSimilarSongs2().getSongs();

                            setChildrenMetadata(tracks);

                            List<MediaItem> mediaItems = MappingUtil.mapMediaItems(tracks, true);

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            completeError(listenableFuture, LibraryResult.RESULT_ERROR_BAD_VALUE);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        completeFailure(listenableFuture, t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> search(String query, String albumPrefix, String artistPrefix) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getSearchingClient()
                .search3(query, 20, 20, 20)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse() != null && response.body().getSubsonicResponse().getSearchResult3() != null) {
                            List<MediaItem> mediaItems = new ArrayList<>();

                            if (response.body().getSubsonicResponse().getSearchResult3().getArtists() != null) {
                                for (ArtistID3 artist : response.body().getSubsonicResponse().getSearchResult3().getArtists()) {
                                    Uri artworkUri = MappingUtil.getPublicArtworkUri(artist.getCoverArtId());

                                    MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                            .setTitle(artist.getName())
                                            .setIsBrowsable(true)
                                            .setIsPlayable(false)
                                            .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
                                            .setArtworkUri(artworkUri)
                                            .build();

                                    MediaItem mediaItem = new MediaItem.Builder()
                                            .setMediaId(artistPrefix + artist.getId())
                                            .setMediaMetadata(mediaMetadata)
                                            .setUri("")
                                            .build();

                                    mediaItems.add(mediaItem);
                                }
                            }

                            if (response.body().getSubsonicResponse().getSearchResult3().getAlbums() != null) {
                                for (AlbumID3 album : response.body().getSubsonicResponse().getSearchResult3().getAlbums()) {
                                    Uri artworkUri = MappingUtil.getPublicArtworkUri(album.getCoverArtId());

                                    MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                            .setTitle(album.getName())
                                            .setAlbumTitle(album.getName())
                                            .setArtist(album.getArtist())
                                            .setGenre(album.getGenre())
                                            .setIsBrowsable(true)
                                            .setIsPlayable(false)
                                            .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                                            .setArtworkUri(artworkUri)
                                            .build();

                                    MediaItem mediaItem = new MediaItem.Builder()
                                            .setMediaId(albumPrefix + album.getId())
                                            .setMediaMetadata(mediaMetadata)
                                            .setUri("")
                                            .build();

                                    mediaItems.add(mediaItem);
                                }
                            }

                            if (response.body().getSubsonicResponse().getSearchResult3().getSongs() != null) {
                                List<Child> tracks = response.body().getSubsonicResponse().getSearchResult3().getSongs();
                                setChildrenMetadata(tracks);
                                mediaItems.addAll(MappingUtil.mapMediaItems(tracks, true));
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            completeError(listenableFuture, LibraryResult.RESULT_ERROR_BAD_VALUE);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        completeFailure(listenableFuture, t);
                    }
                });

        return listenableFuture;
    }

    @OptIn(markerClass = UnstableApi.class)
    public void setChildrenMetadata(List<Child> children) {
        long timestamp = System.currentTimeMillis();
        ArrayList<SessionMediaItem> sessionMediaItems = new ArrayList<>();

        for (Child child : children) {
            SessionMediaItem sessionMediaItem = new SessionMediaItem(child);
            sessionMediaItem.setTimestamp(timestamp);
            sessionMediaItems.add(sessionMediaItem);
        }

        InsertAllThreadSafe insertAll = new InsertAllThreadSafe(sessionMediaItemDao, sessionMediaItems);
        Thread thread = new Thread(insertAll);
        thread.start();
    }

    @OptIn(markerClass = UnstableApi.class)
    public void setPodcastEpisodesMetadata(List<PodcastEpisode> podcastEpisodes) {
        long timestamp = System.currentTimeMillis();
        ArrayList<SessionMediaItem> sessionMediaItems = new ArrayList<>();

        for (PodcastEpisode podcastEpisode : podcastEpisodes) {
            SessionMediaItem sessionMediaItem = new SessionMediaItem(podcastEpisode);
            sessionMediaItem.setTimestamp(timestamp);
            sessionMediaItems.add(sessionMediaItem);
        }

        InsertAllThreadSafe insertAll = new InsertAllThreadSafe(sessionMediaItemDao, sessionMediaItems);
        Thread thread = new Thread(insertAll);
        thread.start();
    }

    @OptIn(markerClass = UnstableApi.class)
    public void setInternetRadioStationsMetadata(List<InternetRadioStation> internetRadioStations) {
        long timestamp = System.currentTimeMillis();
        ArrayList<SessionMediaItem> sessionMediaItems = new ArrayList<>();

        for (InternetRadioStation internetRadioStation : internetRadioStations) {
            SessionMediaItem sessionMediaItem = new SessionMediaItem(internetRadioStation);
            sessionMediaItem.setTimestamp(timestamp);
            sessionMediaItems.add(sessionMediaItem);
        }

        InsertAllThreadSafe insertAll = new InsertAllThreadSafe(sessionMediaItemDao, sessionMediaItems);
        Thread thread = new Thread(insertAll);
        thread.start();
    }

    /**
     * Metadati di sessione per gli id richiesti da un controller esterno (Android Auto).
     * Una sola query su un thread dedicato: il chiamante (onAddMediaItems) gira sul main thread
     * della sessione e la vecchia versione apriva due thread join per item, ricostruendo l'intero
     * batch per ogni id. Se la lettura fallisce si torna una mappa vuota: il chiamante tiene gli
     * item originali.
     */
    public ListenableFuture<Map<String, MediaItem>> getSessionMediaItems(List<String> ids) {
        final SettableFuture<Map<String, MediaItem>> listenableFuture = SettableFuture.create();

        if (ids == null || ids.isEmpty()) {
            listenableFuture.set(Collections.emptyMap());
            return listenableFuture;
        }

        Thread thread = new Thread(() -> {
            try {
                Map<String, SessionMediaItem> latest = new HashMap<>();

                for (int from = 0; from < ids.size(); from += SESSION_METADATA_CHUNK) {
                    List<SessionMediaItem> sessionMediaItems = sessionMediaItemDao.getByIds(
                            ids.subList(from, Math.min(from + SESSION_METADATA_CHUNK, ids.size()))
                    );

                    for (SessionMediaItem sessionMediaItem : sessionMediaItems) {
                        String id = sessionMediaItem.getId();

                        if (id == null) continue;

                        SessionMediaItem current = latest.get(id);

                        // Lo stesso id può avere più righe (PK autoGenerate): vince la più recente
                        if (current == null || sessionMediaItem.getTimestamp() > current.getTimestamp()) {
                            latest.put(id, sessionMediaItem);
                        }
                    }
                }

                Map<String, MediaItem> mediaItems = new HashMap<>();

                for (SessionMediaItem sessionMediaItem : latest.values()) {
                    mediaItems.put(sessionMediaItem.getId(), sessionMediaItem.getMediaItem());
                }

                listenableFuture.set(mediaItems);
            } catch (Throwable t) {
                Log.e(TAG, "Unable to read the session metadata", t);
                listenableFuture.set(Collections.emptyMap());
            }
        });

        thread.start();

        return listenableFuture;
    }

    public void pruneMetadata() {
        PruneThreadSafe prune = new PruneThreadSafe(sessionMediaItemDao, System.currentTimeMillis() - METADATA_TTL_MS);
        Thread thread = new Thread(prune);
        thread.start();
    }

    private static class InsertAllThreadSafe implements Runnable {
        private final SessionMediaItemDao sessionMediaItemDao;
        private final List<SessionMediaItem> sessionMediaItems;

        public InsertAllThreadSafe(SessionMediaItemDao sessionMediaItemDao, List<SessionMediaItem> sessionMediaItems) {
            this.sessionMediaItemDao = sessionMediaItemDao;
            this.sessionMediaItems = sessionMediaItems;
        }

        @Override
        public void run() {
            sessionMediaItemDao.insertAll(sessionMediaItems);
        }
    }

    private static class PruneThreadSafe implements Runnable {
        private final SessionMediaItemDao sessionMediaItemDao;
        private final long threshold;

        public PruneThreadSafe(SessionMediaItemDao sessionMediaItemDao, long threshold) {
            this.sessionMediaItemDao = sessionMediaItemDao;
            this.threshold = threshold;
        }

        @Override
        public void run() {
            sessionMediaItemDao.deleteOlderThan(threshold);
        }
    }
}
