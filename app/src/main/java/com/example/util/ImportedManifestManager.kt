package com.example.util

import android.content.Context
import com.example.data.model.TrackEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ManifestEntry(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val filePath: String,
    val durationMs: Long,
    val importedAt: Long
)

object ImportedManifestManager {

    private const val MANIFEST_FILE_NAME = "imported_tracks_manifest.json"

    /**
     * Saves all currently imported songs to imported_tracks_manifest.json
     * storing their paths, titles, artists, albums, duration, and import timestamps.
     */
    fun saveManifest(context: Context, tracks: List<TrackEntity>) {
        try {
            val jsonObject = JSONObject()
            jsonObject.put("lastScanTime", System.currentTimeMillis())
            jsonObject.put("totalTracks", tracks.size)

            val jsonArray = JSONArray()
            val now = System.currentTimeMillis()
            for (t in tracks) {
                val item = JSONObject()
                item.put("id", t.id)
                item.put("title", t.title)
                item.put("artist", t.artist)
                item.put("album", t.album)
                item.put("filePath", t.filePath)
                item.put("durationMs", t.durationMs)
                item.put("importedAt", now)
                jsonArray.put(item)
            }
            jsonObject.put("tracks", jsonArray)

            val file = File(context.filesDir, MANIFEST_FILE_NAME)
            file.writeText(jsonObject.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Loads manifest of imported songs from imported_tracks_manifest.json
     */
    fun loadManifest(context: Context): List<ManifestEntry> {
        val result = mutableListOf<ManifestEntry>()
        try {
            val file = File(context.filesDir, MANIFEST_FILE_NAME)
            if (!file.exists()) return result
            val content = file.readText()
            if (content.isBlank()) return result
            val jsonObject = JSONObject(content)
            val array = jsonObject.optJSONArray("tracks") ?: return result
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                result.add(
                    ManifestEntry(
                        id = item.optString("id", ""),
                        title = item.optString("title", ""),
                        artist = item.optString("artist", ""),
                        album = item.optString("album", ""),
                        filePath = item.optString("filePath", ""),
                        durationMs = item.optLong("durationMs", 0L),
                        importedAt = item.optLong("importedAt", 0L)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }
}
