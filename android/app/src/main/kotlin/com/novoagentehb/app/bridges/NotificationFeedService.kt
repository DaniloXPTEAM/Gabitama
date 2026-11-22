package com.novoagentehb.app.bridges

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.novoagentehb.app.data.local.AppDatabase
import com.novoagentehb.app.data.local.FeedEventEntity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotificationFeedService : NotificationListenerService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface FeedServiceEntryPoint {
        fun database(): AppDatabase
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mediaSessionListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private val trackedSpotifyCallbacks = mutableListOf<Pair<MediaController, MediaController.Callback>>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        _listenerConnected.value = true
        registerSpotifySessionWatcher()
        refreshSpotifyPlaybackState()
    }

    override fun onListenerDisconnected() {
        unregisterSpotifySessionWatcher()
        _listenerConnected.value = false
        _spotifyPlaying.value = false
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName == packageName) return
        refreshSpotifyPlaybackState()

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val appLabel = when (sbn.packageName) {
            "com.whatsapp", "com.whatsapp.w4b" -> "WhatsApp"
            "com.google.android.gm" -> "Gmail"
            "com.spotify.music" -> "Spotify"
            else -> sbn.packageName.substringAfterLast('.')
        }

        scope.launch {
            runCatching {
                val ep = EntryPointAccessors.fromApplication(applicationContext, FeedServiceEntryPoint::class.java)
                ep.database().feedEventDao().insert(
                    FeedEventEntity(
                        sourcePackage = sbn.packageName,
                        sourceLabel = appLabel,
                        title = title.take(120),
                        body = text.take(280),
                        kind = "notification"
                    )
                )
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshSpotifyPlaybackState()
    }

    private fun registerSpotifySessionWatcher() {
        unregisterSpotifySessionWatcher()
        runCatching {
            val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
            val component = ComponentName(this, NotificationFeedService::class.java)
            val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                attachCallbacksAndEvaluate(controllers.orEmpty())
            }
            mediaSessionListener = listener
            msm.addOnActiveSessionsChangedListener(listener, component)
            attachCallbacksAndEvaluate(msm.getActiveSessions(component))
        }
    }

    private fun unregisterSpotifySessionWatcher() {
        runCatching {
            val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            mediaSessionListener?.let { msm?.removeOnActiveSessionsChangedListener(it) }
        }
        mediaSessionListener = null
        clearSpotifyControllerCallbacks()
    }

    private fun clearSpotifyControllerCallbacks() {
        trackedSpotifyCallbacks.forEach { (controller, cb) ->
            runCatching { controller.unregisterCallback(cb) }
        }
        trackedSpotifyCallbacks.clear()
    }

    private fun attachCallbacksAndEvaluate(controllers: List<MediaController>) {
        clearSpotifyControllerCallbacks()
        controllers.filter { it.packageName == "com.spotify.music" }.forEach { controller ->
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    refreshSpotifyPlaybackState()
                }
            }
            runCatching {
                controller.registerCallback(cb)
                trackedSpotifyCallbacks.add(controller to cb)
            }
        }
        val playing = controllers.any {
            it.packageName == "com.spotify.music" &&
                it.playbackState?.state == PlaybackState.STATE_PLAYING
        }
        _spotifyPlaying.value = playing
    }

    private fun refreshSpotifyPlaybackState() {
        refreshActiveMediaSessions(this)
    }

    companion object {
        private val _listenerConnected = MutableStateFlow(false)
        val listenerConnected: StateFlow<Boolean> = _listenerConnected.asStateFlow()

        private val _spotifyPlaying = MutableStateFlow(false)
        val spotifyPlaying: StateFlow<Boolean> = _spotifyPlaying.asStateFlow()

        fun isEnabled(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        fun refreshActiveMediaSessions(context: Context) {
            if (!isEnabled(context)) {
                _spotifyPlaying.value = false
                return
            }
            runCatching {
                val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
                val component = ComponentName(context, NotificationFeedService::class.java)
                val controllers = msm.getActiveSessions(component)
                val playing = controllers.any {
                    it.packageName == "com.spotify.music" &&
                        it.playbackState?.state == PlaybackState.STATE_PLAYING
                }
                _spotifyPlaying.value = playing
            }.onFailure {
                _spotifyPlaying.value = false
            }
        }
    }
}
