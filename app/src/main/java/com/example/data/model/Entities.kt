package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String = "",
    val durationMs: Long,
    val filePath: String,
    val fileName: String,
    val coverPath: String? = null,
    val isLiked: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val trackNumber: Int = 0,
    val discNumber: Int = 0
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val coverPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isSystemLiked: Boolean = false,
    val folderId: String? = null,
    val isPinned: Boolean = false
)

@Entity(tableName = "playlist_folders")
data class PlaylistFolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)

@Entity(tableName = "playlist_tracks")
data class PlaylistTrackEntity(
    @PrimaryKey(autoGenerate = true) val entryId: Long = 0,
    val playlistId: String,
    val trackId: String?,
    val orderIndex: Int,
    val isMissing: Boolean = false,
    val csvTitle: String,
    val csvArtist: String,
    val csvAlbum: String = "",
    val csvDurationMs: Long = 0,
    val csvTrackUri: String? = null,
    val resolvedFilePath: String? = null
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String
)

data class DuplicateItem(
    val entryId: Long,
    val trackId: String?,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val orderIndex: Int
)

data class DuplicateGroup(
    val groupKey: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val items: List<DuplicateItem>
)
