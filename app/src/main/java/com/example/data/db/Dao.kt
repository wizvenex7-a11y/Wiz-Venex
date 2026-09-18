package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AppSettingEntity
import com.example.data.model.PlaylistEntity
import com.example.data.model.PlaylistTrackEntity
import com.example.data.model.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY title ASC")
    fun getAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY title ASC")
    suspend fun getAllTracksSnapshot(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun getTracksByIds(ids: List<String>): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE isLiked = 1 ORDER BY addedAt DESC")
    fun getLikedTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT 50")
    fun getRecentlyPlayed(): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity)

    @Update
    suspend fun updateTrack(track: TrackEntity)

    @Query("UPDATE tracks SET isLiked = :isLiked WHERE id = :id")
    suspend fun setLiked(id: String, isLiked: Boolean)

    @Query("UPDATE tracks SET isLiked = :isLiked WHERE id IN (:ids)")
    suspend fun batchSetLiked(ids: List<String>, isLiked: Boolean)

    @Query("UPDATE tracks SET playCount = playCount + 1, lastPlayedAt = :timestamp WHERE id = :id")
    suspend fun recordPlayback(id: String, timestamp: Long)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteTrackById(id: String)

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteTracksByIds(ids: List<String>)

    @Query("DELETE FROM tracks")
    suspend fun clearAllTracks()

    @Query("SELECT * FROM tracks WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%'")
    fun searchTracks(query: String): Flow<List<TrackEntity>>
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY isPinned DESC, isSystemLiked DESC, updatedAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY isPinned DESC, isSystemLiked DESC, updatedAt DESC")
    suspend fun getAllPlaylistsSnapshot(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE folderId = :folderId ORDER BY isPinned DESC, updatedAt DESC")
    fun getPlaylistsInFolder(folderId: String): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE folderId IS NULL ORDER BY isPinned DESC, isSystemLiked DESC, updatedAt DESC")
    fun getRootPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun getPlaylistById(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE name = :name LIMIT 1")
    suspend fun getPlaylistByName(name: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE name = :name AND folderId = :folderId LIMIT 1")
    suspend fun getPlaylistByNameInFolder(name: String, folderId: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE name = :name AND folderId IS NULL LIMIT 1")
    suspend fun getRootPlaylistByName(name: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET folderId = :folderId WHERE id = :playlistId")
    suspend fun setPlaylistFolder(playlistId: String, folderId: String?)

    @Query("UPDATE playlists SET isPinned = :isPinned WHERE id = :playlistId")
    suspend fun setPlaylistPinned(playlistId: String, isPinned: Boolean)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: String)

    @Query("DELETE FROM playlists")
    suspend fun clearAllPlaylists()

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    fun getPlaylistEntries(playlistId: String): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    suspend fun getPlaylistEntriesSnapshot(playlistId: String): List<PlaylistTrackEntity>

    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :playlistId AND trackId IS NOT NULL")
    suspend fun getPlaylistTrackIds(playlistId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistEntries(entries: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deletePlaylistEntries(playlistId: String)

    @Query("DELETE FROM playlist_tracks WHERE entryId = :entryId")
    suspend fun deletePlaylistEntryById(entryId: Long)

    @Query("DELETE FROM playlist_tracks WHERE entryId IN (:entryIds)")
    suspend fun batchDeletePlaylistEntries(entryIds: List<Long>)

    @Update
    suspend fun updatePlaylistEntry(entry: PlaylistTrackEntity)

    @Query("SELECT * FROM playlist_tracks WHERE isMissing = 1")
    fun getAllMissingEntries(): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_tracks")
    fun getAllPlaylistTracks(): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE isMissing = 1")
    suspend fun getAllMissingEntriesSnapshot(): List<PlaylistTrackEntity>

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND isMissing = 1")
    suspend fun ignoreMissingTracksInPlaylist(playlistId: String)

    @Query("UPDATE playlist_tracks SET isMissing = 1 WHERE trackId IN (:trackIds)")
    suspend fun markTracksAsMissing(trackIds: List<String>)
}

@Dao
interface PlaylistFolderDao {
    @Query("SELECT * FROM playlist_folders ORDER BY isPinned DESC, createdAt DESC")
    fun getAllFolders(): Flow<List<com.example.data.model.PlaylistFolderEntity>>

    @Query("SELECT * FROM playlist_folders ORDER BY isPinned DESC, createdAt DESC")
    suspend fun getAllFoldersSnapshot(): List<com.example.data.model.PlaylistFolderEntity>

    @Query("SELECT * FROM playlist_folders WHERE id = :id LIMIT 1")
    suspend fun getFolderById(id: String): com.example.data.model.PlaylistFolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: com.example.data.model.PlaylistFolderEntity)

    @Update
    suspend fun updateFolder(folder: com.example.data.model.PlaylistFolderEntity)

    @Query("UPDATE playlist_folders SET isPinned = :isPinned WHERE id = :folderId")
    suspend fun setFolderPinned(folderId: String, isPinned: Boolean)

    @Query("DELETE FROM playlist_folders WHERE id = :id")
    suspend fun deleteFolderById(id: String)

    @Query("UPDATE playlists SET folderId = NULL WHERE folderId = :folderId")
    suspend fun detachPlaylistsFromFolder(folderId: String)
}

@Dao
interface AppSettingDao {
    @Query("SELECT * FROM app_settings")
    suspend fun getAllSettings(): List<AppSettingEntity>

    @Query("SELECT value FROM app_settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: AppSettingEntity)
}
