package com.cappielloantonio.tempo.repository;

import androidx.annotation.NonNull;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.database.AppDatabase;
import com.cappielloantonio.tempo.database.dao.FavoriteDao;
import com.cappielloantonio.tempo.interfaces.StarCallback;
import com.cappielloantonio.tempo.model.Favorite;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FavoriteRepository {
    private final FavoriteDao favoriteDao = AppDatabase.getInstance().favoriteDao();

    public void star(String id, String albumId, String artistId, StarCallback starCallback) {
        App.getSubsonicClientInstance(false)
                .getMediaAnnotationClient()
                .star(id, albumId, artistId)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()) {
                            starCallback.onSuccess();
                        } else {
                            starCallback.onError();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        starCallback.onError();
                    }
                });
    }

    public void unstar(String id, String albumId, String artistId, StarCallback starCallback) {
        App.getSubsonicClientInstance(false)
                .getMediaAnnotationClient()
                .unstar(id, albumId, artistId)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()) {
                            starCallback.onSuccess();
                        } else {
                            starCallback.onError();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        starCallback.onError();
                    }
                });
    }

    public List<Favorite> getFavorites() {
        List<Favorite> favorites = new ArrayList<>();

        GetAllThreadSafe getAllThreadSafe = new GetAllThreadSafe(favoriteDao);
        Thread thread = new Thread(getAllThreadSafe);
        thread.start();

        try {
            thread.join();
            favorites = getAllThreadSafe.getFavorites();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return favorites;
    }

    public List<String> getFavoriteFolderIds() {
        List<String> folderIds = new ArrayList<>();

        for (Favorite favorite : getFavorites()) {
            if (favorite.getFolderId() != null && !folderIds.contains(favorite.getFolderId())) {
                folderIds.add(favorite.getFolderId());
            }
        }

        return folderIds;
    }

    public boolean isFolderFavorite(String folderId) {
        if (folderId == null) return false;

        for (Favorite favorite : getFavorites()) {
            if (folderId.equals(favorite.getFolderId())) {
                return true;
            }
        }

        return false;
    }

    public void setFolderFavorite(String folderId, boolean favorite) {
        if (folderId == null) return;

        if (favorite) {
            InsertThreadSafe insert = new InsertThreadSafe(favoriteDao, new Favorite(System.currentTimeMillis(), null, null, null, true, folderId));
            Thread thread = new Thread(insert);
            thread.start();
        } else {
            for (Favorite favoriteItem : getFavorites()) {
                if (folderId.equals(favoriteItem.getFolderId())) {
                    delete(favoriteItem);
                }
            }
        }
    }

    private static class GetAllThreadSafe implements Runnable {
        private final FavoriteDao favoriteDao;
        private List<Favorite> favorites = new ArrayList<>();

        public GetAllThreadSafe(FavoriteDao favoriteDao) {
            this.favoriteDao = favoriteDao;
        }

        @Override
        public void run() {
            favorites = favoriteDao.getAll();
        }

        public List<Favorite> getFavorites() {
            return favorites;
        }
    }

    public void starLater(String id, String albumId, String artistId, boolean toStar) {
        InsertThreadSafe insert = new InsertThreadSafe(favoriteDao, new Favorite(System.currentTimeMillis(), id, albumId, artistId, toStar, null));
        Thread thread = new Thread(insert);
        thread.start();
    }

    private static class InsertThreadSafe implements Runnable {
        private final FavoriteDao favoriteDao;
        private final Favorite favorite;

        public InsertThreadSafe(FavoriteDao favoriteDao, Favorite favorite) {
            this.favoriteDao = favoriteDao;
            this.favorite = favorite;
        }

        @Override
        public void run() {
            favoriteDao.insert(favorite);
        }
    }

    public void delete(Favorite favorite) {
        DeleteThreadSafe delete = new DeleteThreadSafe(favoriteDao, favorite);
        Thread thread = new Thread(delete);
        thread.start();
    }

    private static class DeleteThreadSafe implements Runnable {
        private final FavoriteDao favoriteDao;
        private final Favorite favorite;

        public DeleteThreadSafe(FavoriteDao favoriteDao, Favorite favorite) {
            this.favoriteDao = favoriteDao;
            this.favorite = favorite;
        }

        @Override
        public void run() {
            favoriteDao.delete(favorite);
        }
    }
}
