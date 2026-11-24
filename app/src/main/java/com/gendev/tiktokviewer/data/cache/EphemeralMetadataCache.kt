package com.gendev.tiktokviewer.data.cache

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import com.gendev.tiktokviewer.domain.model.FeedPage
import com.gendev.tiktokviewer.domain.model.PublicVideo
import java.io.File

/**
 * Ephemeral, RAM-bounded, TTL-expiring cache for public metadata only.
 * Never persists watch history or user searches to disk.
 */
class EphemeralMetadataCache(
    private val maxEntries: Int = 48,
    private val ttlMillis: Long = 5 * 60 * 1000L,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    private data class CacheEntry<T>(
        val value: T,
        val expiresAtMs: Long
    )

    private val feedCache = object : LinkedHashMap<String, CacheEntry<FeedPage>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry<FeedPage>>?): Boolean {
            return size > 8
        }
    }

    private val videoCache = object : LinkedHashMap<String, CacheEntry<PublicVideo>>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry<PublicVideo>>?): Boolean {
            return size > maxEntries
        }
    }

    private val searchCache = object : LinkedHashMap<String, CacheEntry<List<PublicVideo>>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry<List<PublicVideo>>>?): Boolean {
            return size > 12
        }
    }

    @Synchronized
    fun getFeed(key: String): FeedPage? {
        val entry = feedCache[key] ?: return null
        if (nowProvider() > entry.expiresAtMs) {
            feedCache.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun putFeed(key: String, page: FeedPage) {
        feedCache[key] = CacheEntry(page, nowProvider() + ttlMillis)
        page.videos.forEach { putVideo(it) }
    }

    @Synchronized
    fun getVideo(videoId: String): PublicVideo? {
        val entry = videoCache[videoId] ?: return null
        if (nowProvider() > entry.expiresAtMs) {
            videoCache.remove(videoId)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun putVideo(video: PublicVideo) {
        videoCache[video.id] = CacheEntry(video, nowProvider() + ttlMillis)
    }

    @Synchronized
    fun getSearch(queryKey: String): List<PublicVideo>? {
        val entry = searchCache[queryKey] ?: return null
        if (nowProvider() > entry.expiresAtMs) {
            searchCache.remove(queryKey)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun putSearch(queryKey: String, results: List<PublicVideo>) {
        searchCache[queryKey] = CacheEntry(results, nowProvider() + ttlMillis)
        results.forEach { putVideo(it) }
    }

    @Synchronized
    fun pruneExpired() {
        val now = nowProvider()
        feedCache.entries.removeAll { it.value.expiresAtMs < now }
        videoCache.entries.removeAll { it.value.expiresAtMs < now }
        searchCache.entries.removeAll { it.value.expiresAtMs < now }
    }

    @Synchronized
    fun entryCount(): Int = feedCache.size + videoCache.size + searchCache.size

    @Synchronized
    fun clearMemory() {
        feedCache.clear()
        videoCache.clear()
        searchCache.clear()
    }

    fun wipeAll(context: Context?) {
        clearMemory()
        if (context == null) return
        runCatching {
            deleteDirectoryContents(context.cacheDir)
            context.externalCacheDir?.let { deleteDirectoryContents(it) }
        }
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
        }
    }

    private fun deleteDirectoryContents(dir: File) {
        if (!dir.exists() || !dir.isDirectory) return
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                deleteDirectoryContents(child)
            }
            child.delete()
        }
    }
}
