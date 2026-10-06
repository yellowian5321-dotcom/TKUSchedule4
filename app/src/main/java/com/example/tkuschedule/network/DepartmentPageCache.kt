package com.example.tkuschedule.network

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** 同一頁只載入一次；取消或失敗時不留下假的空結果，之後可重試。 */
internal class DepartmentPageCache<T : Any> {
    private val values = ConcurrentHashMap<String, T>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun get(key: String, load: suspend () -> T): T {
        values[key]?.let { return it }
        val lock = locks.getOrPut(key) { Mutex() }
        return lock.withLock {
            values[key] ?: load().also { values[key] = it }
        }
    }
}