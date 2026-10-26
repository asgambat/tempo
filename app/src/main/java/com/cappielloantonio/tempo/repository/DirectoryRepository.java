package com.cappielloantonio.tempo.repository;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.Directory;
import com.cappielloantonio.tempo.subsonic.models.Indexes;
import com.cappielloantonio.tempo.subsonic.models.MusicFolder;
import com.cappielloantonio.tempo.util.Constants;

import java.text.Collator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DirectoryRepository {
    private static final String TAG = "DirectoryRepository";

    public interface SongsCallback {
        void onSongs(List<Child> songs);
    }

    /**
     * Handle of a folder collection: cancelling it stops the pending chain, so a fragment that is
     * gone does not keep issuing directory requests.
     */
    public static class SongsRequest {
        private volatile boolean cancelled = false;

        public void cancel() {
            cancelled = true;
        }

        public boolean isCancelled() {
            return cancelled;
        }
    }

    public MutableLiveData<List<MusicFolder>> getMusicFolders() {
        MutableLiveData<List<MusicFolder>> liveMusicFolders = new MutableLiveData<>();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getMusicFolders()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse() != null && response.body().getSubsonicResponse().getMusicFolders() != null) {
                            liveMusicFolders.setValue(response.body().getSubsonicResponse().getMusicFolders().getMusicFolders() != null ? response.body().getSubsonicResponse().getMusicFolders().getMusicFolders() : Collections.emptyList());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "Request failed", t);
                    }
                });

        return liveMusicFolders;
    }

    public MutableLiveData<Indexes> getIndexes(String musicFolderId, Long ifModifiedSince) {
        MutableLiveData<Indexes> liveIndexes = new MutableLiveData<>();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getIndexes(musicFolderId, ifModifiedSince)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse() != null && response.body().getSubsonicResponse().getIndexes() != null) {
                            liveIndexes.setValue(response.body().getSubsonicResponse().getIndexes());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "Request failed", t);
                    }
                });

        return liveIndexes;
    }

    /**
     * Collects the songs of the given directory followed by the songs of its subfolders, sorted
     * alphabetically by folder name and explored depth-first up to {@code maxDepth} levels. The
     * result is capped at {@link Constants#PLAYABLE_MEDIA_LIMIT} songs and at
     * {@link Constants#DIRECTORY_SUBFOLDERS_MAX_REQUESTS} directory requests. Unreachable
     * subfolders are skipped and a cancelled request stops without calling the callback.
     */
    public SongsRequest getSongsIncludingSubfolders(Directory directory, int maxDepth, SongsCallback callback) {
        SongsRequest request = new SongsRequest();
        List<Child> songs = new ArrayList<>();
        Deque<DirectoryNode> pendingDirectories = new ArrayDeque<>();

        if (directory != null) {
            addSongs(directory.getChildren(), songs);
            pushDirectories(pendingDirectories, getSubDirectories(directory.getChildren()), 1);
        }

        collectSubfolderSongs(request, pendingDirectories, maxDepth, songs, 0, callback);

        return request;
    }

    private void collectSubfolderSongs(SongsRequest request, Deque<DirectoryNode> pendingDirectories, int maxDepth, List<Child> songs, int requests, SongsCallback callback) {
        if (request.isCancelled()) {
            return;
        }

        if (pendingDirectories.isEmpty() || requests >= Constants.DIRECTORY_SUBFOLDERS_MAX_REQUESTS || songs.size() >= Constants.PLAYABLE_MEDIA_LIMIT) {
            if (songs.size() >= Constants.PLAYABLE_MEDIA_LIMIT) {
                Log.w(TAG, "Subfolder collection truncated to " + Constants.PLAYABLE_MEDIA_LIMIT + " songs");
            }

            callback.onSongs(songs);
            return;
        }

        DirectoryNode node = pendingDirectories.pop();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getMusicDirectory(node.directory.getId())
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        Directory directory = null;

                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse() != null) {
                            directory = response.body().getSubsonicResponse().getDirectory();
                        }

                        List<Child> children = directory != null ? directory.getChildren() : null;

                        addSongs(children, songs);

                        if (node.depth < maxDepth) {
                            pushDirectories(pendingDirectories, getSubDirectories(children), node.depth + 1);
                        }

                        collectSubfolderSongs(request, pendingDirectories, maxDepth, songs, requests + 1, callback);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "Unable to load the songs of the directory " + node.directory.getId(), t);
                        collectSubfolderSongs(request, pendingDirectories, maxDepth, songs, requests + 1, callback);
                    }
                });
    }

    private void pushDirectories(Deque<DirectoryNode> pendingDirectories, List<Child> directories, int depth) {
        for (int i = directories.size() - 1; i >= 0; i--) {
            pendingDirectories.push(new DirectoryNode(directories.get(i), depth));
        }
    }

    private List<Child> getSubDirectories(List<Child> children) {
        List<Child> subDirectories = new ArrayList<>();

        if (children == null) {
            return subDirectories;
        }

        for (Child child : children) {
            if (child.isDir()) {
                subDirectories.add(child);
            }
        }

        Collator collator = Collator.getInstance();
        collator.setStrength(Collator.PRIMARY);

        Collections.sort(subDirectories, (first, second) -> collator.compare(getDirectoryName(first), getDirectoryName(second)));

        return subDirectories;
    }

    private static String getDirectoryName(Child directory) {
        return directory.getTitle() != null ? directory.getTitle() : "";
    }

    private void addSongs(List<Child> children, List<Child> songs) {
        if (children == null) {
            return;
        }

        for (Child child : children) {
            if (songs.size() >= Constants.PLAYABLE_MEDIA_LIMIT) {
                return;
            }

            if (!child.isDir()) {
                songs.add(child);
            }
        }
    }

    private static class DirectoryNode {
        private final Child directory;
        private final int depth;

        DirectoryNode(Child directory, int depth) {
            this.directory = directory;
            this.depth = depth;
        }
    }

    public MutableLiveData<Directory> getMusicDirectory(String id) {
        MutableLiveData<Directory> liveMusicDirectory = new MutableLiveData<>();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getMusicDirectory(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse() != null && response.body().getSubsonicResponse().getDirectory() != null) {
                            liveMusicDirectory.setValue(response.body().getSubsonicResponse().getDirectory());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        t.printStackTrace();
                    }
                });

        return liveMusicDirectory;
    }
}
