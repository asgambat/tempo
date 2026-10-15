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
    private boolean songListRequested = false;

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

    public LiveData<List<Child>> getSongList(LifecycleOwner owner) {
        if (!songListRequested) {
            songListRequested = true;

            if (title != null) {
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
        }

        return songList;
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
