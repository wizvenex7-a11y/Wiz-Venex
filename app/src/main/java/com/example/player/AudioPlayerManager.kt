package com.example.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import com.example.data.db.AppDatabase
import com.example.data.model.TrackEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

class AudioPlayerManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val database = AppDatabase.getInstance(context)

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _currentTrack = MutableStateFlow<TrackEntity?>(null)
    val currentTrack: StateFlow<TrackEntity?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _queue = MutableStateFlow<List<TrackEntity>>(emptyList())
    val queue: StateFlow<List<TrackEntity>> = _queue.asStateFlow()

    private val _queueIndex = MutableStateFlow(0)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    private var originalQueue = listOf<TrackEntity>()

    init {
        initMediaPlayer()
    }

    private fun initMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setWakeMode(context, android.os.PowerManager.PARTIAL_WAKE_LOCK)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setOnCompletionListener {
                handleTrackCompletion()
            }
            setOnErrorListener { _, what, extra ->
                _isPlaying.value = false
                false
            }
        }
    }

    fun playTrack(track: TrackEntity, newQueue: List<TrackEntity>? = null) {
        val targetQueue = newQueue ?: if (_queue.value.contains(track)) _queue.value else listOf(track)
        originalQueue = targetQueue

        val activeList = if (_isShuffle.value) {
            val shuffled = targetQueue.toMutableList()
            shuffled.remove(track)
            shuffled.shuffle()
            listOf(track) + shuffled
        } else {
            targetQueue
        }

        _queue.value = activeList
        val index = activeList.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        _queueIndex.value = index

        loadAndPlay(track)
    }

    private fun loadAndPlay(track: TrackEntity) {
        try {
            mediaPlayer?.reset() ?: initMediaPlayer()
            val mp = mediaPlayer ?: return

            if (track.filePath.startsWith("content://")) {
                mp.setDataSource(context, Uri.parse(track.filePath))
            } else {
                val f = File(track.filePath)
                if (f.exists()) {
                    mp.setDataSource(f.absolutePath)
                } else {
                    mp.setDataSource(track.filePath)
                }
            }

            mp.prepare()
            mp.start()
            _isPlaying.value = true
            _currentTrack.value = track
            _durationMs.value = if (mp.duration > 0) mp.duration.toLong() else track.durationMs
            _currentPositionMs.value = 0L

            try {
                MediaPlaybackService.start(context)
            } catch (e: Exception) {
                // background start restriction fallback
            }

            // Record history and play count in Room DB
            scope.launch(Dispatchers.IO) {
                database.trackDao().recordPlayback(track.id, System.currentTimeMillis())
            }

            startProgressTracker()
        } catch (e: Exception) {
            e.printStackTrace()
            _isPlaying.value = false
        }
    }

    fun togglePlayPause() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            _isPlaying.value = false
            stopProgressTracker()
        } else {
            if (_currentTrack.value == null && _queue.value.isNotEmpty()) {
                playTrack(_queue.value.first())
            } else {
                mp.start()
                _isPlaying.value = true
                startProgressTracker()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.seekTo(positionMs.toInt())
        _currentPositionMs.value = positionMs
    }

    fun skipNext() {
        val q = _queue.value
        if (q.isEmpty()) return

        val nextIndex = _queueIndex.value + 1
        if (nextIndex < q.size) {
            _queueIndex.value = nextIndex
            loadAndPlay(q[nextIndex])
        } else if (_repeatMode.value == RepeatMode.ALL) {
            _queueIndex.value = 0
            loadAndPlay(q[0])
        }
    }

    fun skipPrevious() {
        val mp = mediaPlayer
        if (mp != null && mp.currentPosition > 3000) {
            // If > 3 seconds, restart current track
            seekTo(0)
            return
        }

        val q = _queue.value
        if (q.isEmpty()) return

        val prevIndex = _queueIndex.value - 1
        if (prevIndex >= 0) {
            _queueIndex.value = prevIndex
            loadAndPlay(q[prevIndex])
        } else {
            seekTo(0)
        }
    }

    fun toggleShuffle() {
        val newShuffle = !_isShuffle.value
        _isShuffle.value = newShuffle
        val current = _currentTrack.value

        if (newShuffle) {
            val remaining = originalQueue.toMutableList()
            if (current != null) remaining.remove(current)
            remaining.shuffle()
            val newQ = if (current != null) listOf(current) + remaining else remaining
            _queue.value = newQ
            _queueIndex.value = 0
        } else {
            _queue.value = originalQueue
            _queueIndex.value = current?.let { c -> originalQueue.indexOfFirst { it.id == c.id } } ?: 0
        }
    }

    fun toggleRepeat() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    fun addToQueue(track: TrackEntity) {
        val currentQ = _queue.value.toMutableList()
        currentQ.add(track)
        _queue.value = currentQ
        if (_currentTrack.value == null) {
            playTrack(track)
        }
    }

    fun addToQueueBatch(tracks: List<TrackEntity>) {
        if (tracks.isEmpty()) return
        val currentQ = _queue.value.toMutableList()
        currentQ.addAll(tracks)
        _queue.value = currentQ
        if (_currentTrack.value == null) {
            playTrack(tracks.first())
        }
    }

    fun playNext(track: TrackEntity) {
        val currentQ = _queue.value.toMutableList()
        val insertIdx = (_queueIndex.value + 1).coerceAtMost(currentQ.size)
        currentQ.add(insertIdx, track)
        _queue.value = currentQ
        if (_currentTrack.value == null) {
            playTrack(track)
        }
    }

    fun playNextBatch(tracks: List<TrackEntity>) {
        if (tracks.isEmpty()) return
        val currentQ = _queue.value.toMutableList()
        val insertIdx = (_queueIndex.value + 1).coerceAtMost(currentQ.size)
        currentQ.addAll(insertIdx, tracks)
        _queue.value = currentQ
        if (_currentTrack.value == null) {
            playTrack(tracks.first())
        }
    }

    fun removeFromQueue(index: Int) {
        val currentQ = _queue.value.toMutableList()
        if (index in currentQ.indices) {
            currentQ.removeAt(index)
            _queue.value = currentQ
            if (index < _queueIndex.value) {
                _queueIndex.value = (_queueIndex.value - 1).coerceAtLeast(0)
            }
        }
    }

    fun toggleLikeCurrentTrack() {
        val track = _currentTrack.value ?: return
        val newLiked = !track.isLiked
        val updated = track.copy(isLiked = newLiked)
        _currentTrack.value = updated
        scope.launch(Dispatchers.IO) {
            database.trackDao().setLiked(track.id, newLiked)
        }
    }

    private fun handleTrackCompletion() {
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                seekTo(0)
                mediaPlayer?.start()
                _isPlaying.value = true
                startProgressTracker()
            }
            RepeatMode.ALL -> {
                skipNext()
            }
            RepeatMode.OFF -> {
                if (_queueIndex.value + 1 < _queue.value.size) {
                    skipNext()
                } else {
                    _isPlaying.value = false
                    stopProgressTracker()
                    seekTo(0)
                }
            }
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let { mp ->
                    if (_isPlaying.value && mp.isPlaying) {
                        _currentPositionMs.value = mp.currentPosition.toLong()
                    }
                }
                delay(300)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stopProgressTracker()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    companion object {
        @Volatile
        private var INSTANCE: AudioPlayerManager? = null

        fun getInstance(context: Context): AudioPlayerManager {
            return INSTANCE ?: synchronized(this) {
                val instance = AudioPlayerManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
