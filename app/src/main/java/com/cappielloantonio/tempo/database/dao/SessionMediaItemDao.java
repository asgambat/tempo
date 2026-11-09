package com.cappielloantonio.tempo.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.cappielloantonio.tempo.model.Queue;
import com.cappielloantonio.tempo.model.SessionMediaItem;

import java.util.List;

@Dao
public interface SessionMediaItemDao {
    @Query("SELECT * FROM session_media_item WHERE id = :id ORDER BY timestamp DESC LIMIT 1")
    SessionMediaItem get(String id);

    @Query("SELECT * FROM session_media_item WHERE id IN (:ids)")
    List<SessionMediaItem> getByIds(List<String> ids);

    @Query("SELECT * FROM session_media_item WHERE timestamp = :timestamp")
    List<SessionMediaItem> get(long timestamp);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(SessionMediaItem sessionMediaItem);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAll(List<SessionMediaItem> sessionMediaItems);

    @Query("DELETE FROM session_media_item")
    void deleteAll();

    @Query("DELETE FROM session_media_item WHERE timestamp < :threshold")
    void deleteOlderThan(long threshold);
}