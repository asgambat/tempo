package com.cappielloantonio.tempo.viewmodel;

import android.app.Application;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cappielloantonio.tempo.repository.ArtistRepository;
import com.cappielloantonio.tempo.repository.SongRepository;
import com.cappielloantonio.tempo.subsonic.models.AlbumID3;
import com.cappielloantonio.tempo.subsonic.models.ArtistID3;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.Genre;
import com.cappielloantonio.tempo.util.Constants;

import java.util.ArrayList;
import java.util.List;

public class SongListPageViewModel extends AndroidViewModel {
    private final SongRepository songRepository;
    private final ArtistRepository artistRepository;

    public String title;
    public String toolbarTitle;
    public Genre genre;
    public ArtistID3 artist;
    public AlbumID3 album;

    private final MutableLiveData<List<Child>> songList = new MutableLiveData<>(new ArrayList<>());

    private String requestedListKey;

    public ArrayList<String> filters = new ArrayList<>();
    public ArrayList<String> filterNames = new ArrayList<>();

    public int year = 0;
    public int maxNumberByYear = 500;
    public int maxNumberByGenre = 100;

    public SongListPageViewModel(@NonNull Application application) {
        super(application);

        songRepository = new SongRepository();
        artistRepository = new ArtistRepository();
    }

    public LiveData<List<Child>> getSongList() {
        return songList;
    }

    /**
     * Fetches the list for the current {@link #title}. It is called once for every view creation,
     * so a failed request is retried the next time the screen is opened instead of leaving the
     * list empty for the whole ViewModel lifetime. The content is cleared only when a different
     * list is requested: re-creating the view of the list already loaded (rotation, back and
     * forth) keeps it on screen instead of flashing empty and downloading it again.
     */
    public void requestSongList(LifecycleOwner owner) {
        String listKey = getListKey();

        if (listKey == null) {
            requestedListKey = null;
            songList.setValue(new ArrayList<>());
            return;
        }

        List<Child> currentSongs = songList.getValue();
        boolean alreadyLoaded = listKey.equals(requestedListKey) && currentSongs != null && !currentSongs.isEmpty();

        if (alreadyLoaded) {
            return;
        }

        if (!listKey.equals(requestedListKey)) {
            songList.setValue(new ArrayList<>());
        }

        requestedListKey = listKey;

        switch (title) {
            case Constants.MEDIA_BY_GENRE:
                if (genre != null) songRepository.getSongsByGenre(genre.getGenre(), 0).observe(owner, songs -> {
                    if (songs != null) songList.setValue(songs);
                });
                break;
            case Constants.MEDIA_BY_ARTIST:
                if (artist != null) artistRepository.getTopSongs(artist.getName(), 50).observe(owner, songs -> {
                    if (songs != null) songList.setValue(songs);
                });
                break;
            case Constants.MEDIA_BY_GENRES:
                songRepository.getSongsByGenres(filters).observe(owner, songs -> {
                    if (songs != null) songList.setValue(songs);
                });
                break;
            case Constants.MEDIA_BY_YEAR:
                songRepository.getRandomSample(maxNumberByYear, year, year + 10).observe(owner, songs -> {
                    if (songs != null) songList.setValue(songs);
                });
                break;
            case Constants.MEDIA_STARRED:
                songRepository.getStarredSongs(false, -1).observe(owner, songs -> {
                    if (songs != null) songList.setValue(songs);
                });
                break;
        }
    }

    private String getListKey() {
        if (title == null) {
            return null;
        }

        switch (title) {
            case Constants.MEDIA_BY_GENRE:
                return genre != null ? title + "|" + genre.getGenre() : null;
            case Constants.MEDIA_BY_ARTIST:
                return artist != null ? title + "|" + artist.getName() : null;
            case Constants.MEDIA_BY_GENRES:
                return title + "|" + TextUtils.join(",", filters);
            case Constants.MEDIA_BY_YEAR:
                return title + "|" + year;
            case Constants.MEDIA_STARRED:
                return title;
            default:
                return null;
        }
    }

    public void getSongsByPage(LifecycleOwner owner) {
        if (title == null) {
            return;
        }

        switch (title) {
            case Constants.MEDIA_BY_GENRE:
                if (genre == null) {
                    break;
                }

                int songCount = songList.getValue() != null ? songList.getValue().size() : 0;

                if (songCount > 0 && songCount % maxNumberByGenre != 0) return;

                int page = songCount / maxNumberByGenre;
                songRepository.getSongsByGenre(genre.getGenre(), page).observe(owner, children -> {
                    if (children != null && !children.isEmpty()) {
                        List<Child> currentMedia = songList.getValue();
                        currentMedia.addAll(children);
                        songList.setValue(currentMedia);
                    }
                });
                break;
            case Constants.MEDIA_BY_ARTIST:
            case Constants.MEDIA_BY_GENRES:
            case Constants.MEDIA_BY_YEAR:
            case Constants.MEDIA_STARRED:
                break;
        }
    }

    public String getFiltersTitle() {
        return TextUtils.join(", ", filterNames);
    }
}
