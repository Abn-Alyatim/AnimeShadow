package com.animeshadow.app.data

import com.animeshadow.app.core.Clock
import com.animeshadow.app.data.local.AnimeEntity
import com.animeshadow.app.data.local.NotificationDao
import com.animeshadow.app.data.local.NotificationEntity

/** History behind the drawer bell: every new-episode / reminder alert the app posts is also logged here. */
class NotificationRepository(private val dao: NotificationDao, private val clock: Clock) {
    fun observeAll() = dao.observeAll()
    fun observeUnreadCount() = dao.observeUnreadCount()

    suspend fun log(anime: AnimeEntity, episode: Int, kind: String): Long = log(anime.id, anime.displayTitle, anime.gridPoster, episode, kind)

    suspend fun log(animeId: Int, title: String, image: String?, episode: Int, kind: String): Long {
        val id = dao.insert(NotificationEntity(animeId = animeId, episode = episode, title = title, image = image, kind = kind, createdAt = clock.now()))
        dao.trim()
        return id
    }

    suspend fun markRead(id: Long) = dao.markRead(id)
    suspend fun markAllRead() = dao.markAllRead()
    suspend fun clear() = dao.clear()

    companion object {
        const val KIND_NEW_EPISODE = "new_episode"
        const val KIND_REMINDER = "reminder"
    }
}
