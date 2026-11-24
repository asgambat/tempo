package com.cappielloantonio.tempo.repository;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.database.AppDatabase;
import com.cappielloantonio.tempo.database.dao.QueueDao;
import com.cappielloantonio.tempo.model.Queue;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.PlayQueue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class QueueRepository {
    private static final String TAG = "QueueRepository";

    /**
     * Queue mutations are serialized on a single background thread: the caller (usually the UI
     * thread, through {@code MediaManager}) never waits for the delete-all/insert-all round trip
     * and concurrent enqueues cannot interleave with each other. Reads that still return a value
     * synchronously wait on the same thread, so they always observe the pending writes in order.
     */
    private static final ExecutorService QUEUE_EXECUTOR = Executors.newSingleThreadExecutor();

    private final QueueDao queueDao = AppDatabase.getInstance().queueDao();

    public LiveData<List<Queue>> getLiveQueue() {
        return queueDao.getAll();
    }

    public List<Child> getMedia() {
        List<Queue> media = awaitOnQueueThread(queueDao::getAllSimple);

        if (media == null) {
            return new ArrayList<>();
        }

        List<Child> children = new ArrayList<>();

        for (Queue queueItem : media) {
            children.add(queueItem);
        }

        return children;
    }

    public MutableLiveData<PlayQueue> getPlayQueue() {
        MutableLiveData<PlayQueue> playQueue = new MutableLiveData<>();

        App.getSubsonicClientInstance(false)
                .getBookmarksClient()
                .getPlayQueue()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse() != null && response.body().getSubsonicResponse().getPlayQueue() != null) {
                            playQueue.setValue(response.body().getSubsonicResponse().getPlayQueue());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        playQueue.setValue(null);
                    }
                });

        return playQueue;
    }

    public void savePlayQueue(List<String> ids, String current, long position) {
        App.getSubsonicClientInstance(false)
                .getBookmarksClient()
                .savePlayQueue(ids, current, position)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {

                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "Request failed", t);
                    }
                });
    }

    /**
     * Inserts a single item and reorders the whole queue on a background thread: the call is
     * fire-and-forget, so the queue is not necessarily persisted when it returns. The synchronous
     * reads of this repository wait for it, the Room LiveData of the queue does not.
     */
    public void insert(Child media, boolean reset, int afterIndex) {
        if (media == null) return;

        QUEUE_EXECUTOR.execute(() -> insertInternal(media, reset, afterIndex));
    }

    /**
     * Inserts a list of items and reorders the whole queue on a background thread: same
     * fire-and-forget contract as {@link #insert(Child, boolean, int)}.
     */
    public void insertAll(List<Child> toAdd, boolean reset, int afterIndex) {
        if (toAdd == null) return;

        QUEUE_EXECUTOR.execute(() -> insertAllInternal(toAdd, reset, afterIndex));
    }

    public void delete(int position) {
        QUEUE_EXECUTOR.execute(() -> queueDao.delete(position));
    }

    public void deleteAll() {
        QUEUE_EXECUTOR.execute(queueDao::deleteAll);
    }

    /**
     * Sostituisce l'intera coda con {@code media} (fire-and-forget, come le altre mutazioni).
     * Serve ai controller che impostano la coda direttamente sul player (Android Auto): gli stati
     * di riproduzione già salvati per gli stessi brani vengono conservati, così la ripresa resta
     * valida dopo il rimpiazzo.
     */
    public void replaceAll(List<Child> media) {
        if (media == null) return;

        QUEUE_EXECUTOR.execute(() -> {
            Map<String, Queue> previous = new HashMap<>();
            List<Queue> existing = queueDao.getAllSimple();

            if (existing != null) {
                for (Queue queueItem : existing) {
                    previous.put(queueItem.getId(), queueItem);
                }
            }

            List<Queue> queue = new ArrayList<>();

            for (int i = 0; i < media.size(); i++) {
                Queue item = new Queue(media.get(i));
                Queue old = previous.get(item.getId());

                if (old != null) {
                    item.setLastPlay(old.getLastPlay());
                    item.setPlayingChanged(old.getPlayingChanged());
                    item.setStreamId(old.getStreamId());
                }

                item.setTrackOrder(i);
                queue.add(item);
            }

            replaceQueue(queue);
        });
    }

    public int count() {
        Integer count = awaitOnQueueThread(queueDao::count);
        return count != null ? count : 0;
    }

    public void setLastPlayedTimestamp(String id) {
        if (id == null) return;

        QUEUE_EXECUTOR.execute(() -> queueDao.setLastPlay(id, System.currentTimeMillis()));
    }

    public void setPlayingPausedTimestamp(String id, long ms) {
        if (id == null) return;

        QUEUE_EXECUTOR.execute(() -> queueDao.setPlayingChanged(id, ms));
    }

    public int getLastPlayedMediaIndex() {
        Queue lastMediaPlayed = awaitOnQueueThread(queueDao::getLastPlayed);

        if (lastMediaPlayed == null) {
            return 0;
        }

        return lastMediaPlayed.getTrackOrder();
    }

    public long getLastPlayedMediaTimestamp() {
        Queue lastMediaPlayed = awaitOnQueueThread(queueDao::getLastPlayed);

        if (lastMediaPlayed == null) {
            return 0;
        }

        return lastMediaPlayed.getPlayingChanged();
    }

    private void insertInternal(Child media, boolean reset, int afterIndex) {
        List<Queue> mediaList = reset ? new ArrayList<>() : getMediaForUpdate();

        int safeIndex = Math.min(Math.max(afterIndex, 0), mediaList.size());

        if (safeIndex != afterIndex) {
            Log.w(TAG, "queue desync: clamping afterIndex " + afterIndex + " to " + safeIndex);
        }

        mediaList.add(safeIndex, new Queue(media));

        writeQueue(mediaList);
    }

    private void insertAllInternal(List<Child> toAdd, boolean reset, int afterIndex) {
        List<Queue> media = reset ? new ArrayList<>() : getMediaForUpdate();

        int safeIndex = Math.min(Math.max(afterIndex, 0), media.size());

        if (safeIndex != afterIndex) {
            Log.w(TAG, "queue desync: clamping afterIndex " + afterIndex + " to " + safeIndex);
        }

        for (int i = 0; i < toAdd.size(); i++) {
            media.add(safeIndex + i, new Queue(toAdd.get(i)));
        }

        writeQueue(media);
    }

    private List<Queue> getMediaForUpdate() {
        List<Queue> media = queueDao.getAllSimple();
        return media != null ? new ArrayList<>(media) : new ArrayList<>();
    }

    private void writeQueue(List<Queue> media) {
        for (int i = 0; i < media.size(); i++) {
            media.get(i).setTrackOrder(i);
        }

        replaceQueue(media);
    }

    /**
     * Riscrive la coda in una sola transazione: la LiveData della coda non vede mai lo stato
     * intermedio vuoto tra la delete-all e la insert-all, e un errore in scrittura non lascia la
     * coda cancellata.
     */
    private void replaceQueue(List<Queue> media) {
        AppDatabase.getInstance().runInTransaction(() -> {
            queueDao.deleteAll();
            queueDao.insertAll(media);
        });
    }

    private static <T> T awaitOnQueueThread(Callable<T> task) {
        try {
            return QUEUE_EXECUTOR.submit(task).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "Interrupted while reading the queue", e);
            return null;
        } catch (ExecutionException e) {
            Log.e(TAG, "Unable to read the queue", e.getCause() != null ? e.getCause() : e);
            return null;
        }
    }
}
