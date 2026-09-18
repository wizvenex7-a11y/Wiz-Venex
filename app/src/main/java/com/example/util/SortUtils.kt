package com.example.util

import com.example.data.model.PlaylistEntity
import com.example.data.model.PlaylistTrackEntity
import com.example.data.model.TrackEntity

enum class SongSortOrder(val displayName: String) {
    CUSTOM("Custom / Default"),
    TITLE_A_Z("Title (A–Z)"),
    TITLE_Z_A("Title (Z–A)"),
    ARTIST_A_Z("Artist (A–Z)"),
    RECENTLY_ADDED("Recently Added"),
    RECENTLY_PLAYED("Recently Played"),
    MOST_PLAYED("Most Played"),
    DURATION_ASC("Duration (Shortest first)"),
    DURATION_DESC("Duration (Longest first)");

    val label: String get() = displayName
}

enum class PlaylistSortOrder(val displayName: String) {
    NAME_A_Z("Name (A–Z)"),
    NAME_Z_A("Name (Z–A)"),
    RECENTLY_UPDATED("Recently Updated"),
    RECENTLY_CREATED("Recently Created"),
    TRACK_COUNT_DESC("Most Tracks"),
    TRACK_COUNT_ASC("Fewest Tracks");

    val label: String get() = displayName
}

object SortUtils {

    fun sortTracks(tracks: List<TrackEntity>, sortOrder: SongSortOrder): List<TrackEntity> {
        return when (sortOrder) {
            SongSortOrder.CUSTOM -> tracks
            SongSortOrder.TITLE_A_Z -> tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            SongSortOrder.TITLE_Z_A -> tracks.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
            SongSortOrder.ARTIST_A_Z -> tracks.sortedWith(
                compareBy<TrackEntity, String>(String.CASE_INSENSITIVE_ORDER) { it.artist }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            )
            SongSortOrder.RECENTLY_ADDED -> tracks.sortedByDescending { it.addedAt }
            SongSortOrder.RECENTLY_PLAYED -> tracks.sortedByDescending { it.lastPlayedAt ?: 0L }
            SongSortOrder.MOST_PLAYED -> tracks.sortedByDescending { it.playCount }
            SongSortOrder.DURATION_ASC -> tracks.sortedBy { it.durationMs }
            SongSortOrder.DURATION_DESC -> tracks.sortedByDescending { it.durationMs }
        }
    }

    fun sortPlaylistEntries(
        entries: List<Pair<PlaylistTrackEntity, TrackEntity?>>,
        sortOrder: SongSortOrder
    ): List<Pair<PlaylistTrackEntity, TrackEntity?>> {
        return when (sortOrder) {
            SongSortOrder.CUSTOM -> entries.sortedBy { it.first.orderIndex }
            SongSortOrder.TITLE_A_Z -> entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.second?.title ?: it.first.csvTitle })
            SongSortOrder.TITLE_Z_A -> entries.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.second?.title ?: it.first.csvTitle })
            SongSortOrder.ARTIST_A_Z -> entries.sortedWith(
                compareBy<Pair<PlaylistTrackEntity, TrackEntity?>, String>(String.CASE_INSENSITIVE_ORDER) { it.second?.artist ?: it.first.csvArtist }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.second?.title ?: it.first.csvTitle }
            )
            SongSortOrder.RECENTLY_ADDED -> entries.sortedByDescending { it.second?.addedAt ?: 0L }
            SongSortOrder.RECENTLY_PLAYED -> entries.sortedByDescending { it.second?.lastPlayedAt ?: 0L }
            SongSortOrder.MOST_PLAYED -> entries.sortedByDescending { it.second?.playCount ?: 0 }
            SongSortOrder.DURATION_ASC -> entries.sortedBy { it.second?.durationMs ?: it.first.csvDurationMs }
            SongSortOrder.DURATION_DESC -> entries.sortedByDescending { it.second?.durationMs ?: it.first.csvDurationMs }
        }
    }

    fun sortPlaylists(
        playlists: List<PlaylistEntity>,
        sortOrder: PlaylistSortOrder,
        countsMap: Map<String, Int> = emptyMap()
    ): List<PlaylistEntity> {
        return when (sortOrder) {
            PlaylistSortOrder.NAME_A_Z -> playlists.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            PlaylistSortOrder.NAME_Z_A -> playlists.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
            PlaylistSortOrder.RECENTLY_UPDATED -> playlists.sortedByDescending { it.updatedAt }
            PlaylistSortOrder.RECENTLY_CREATED -> playlists.sortedByDescending { it.createdAt }
            PlaylistSortOrder.TRACK_COUNT_DESC -> playlists.sortedByDescending { countsMap[it.id] ?: 0 }
            PlaylistSortOrder.TRACK_COUNT_ASC -> playlists.sortedBy { countsMap[it.id] ?: 0 }
        }
    }
}
