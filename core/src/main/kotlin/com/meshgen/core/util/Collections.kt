package com.meshgen.core.util

/** Growable int list without boxing. */
class IntList(initialCapacity: Int = 16) {
    var data = IntArray(maxOf(initialCapacity, 4))
        private set
    var size = 0
        private set

    fun add(v: Int) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = v
    }

    operator fun get(i: Int): Int = data[i]
    operator fun set(i: Int, v: Int) { data[i] = v }
    fun clear() { size = 0 }
    fun removeAt(i: Int) { data[i] = data[--size] } // unordered
    fun toIntArray(): IntArray = data.copyOf(size)
}

/** Open-addressing Long → Int hash map (keys must not be [EMPTY]). */
class LongIntMap(expected: Int = 16) {
    private var keys: LongArray
    private var values: IntArray
    private var mask: Int
    var size = 0
        private set

    init {
        var cap = 16
        while (cap < expected * 2) cap = cap shl 1
        keys = LongArray(cap) { EMPTY }
        values = IntArray(cap)
        mask = cap - 1
    }

    private fun slot(key: Long): Int {
        var h = key * -0x61c8864680b583ebL
        h = h xor (h ushr 29)
        return h.toInt() and mask
    }

    fun get(key: Long, default: Int = -1): Int {
        var i = slot(key)
        while (true) {
            val k = keys[i]
            if (k == EMPTY) return default
            if (k == key) return values[i]
            i = (i + 1) and mask
        }
    }

    /** Returns existing value for [key], or stores [value] and returns -1. */
    fun putIfAbsent(key: Long, value: Int): Int {
        var i = slot(key)
        while (true) {
            val k = keys[i]
            if (k == EMPTY) {
                keys[i] = key
                values[i] = value
                if (++size * 2 > keys.size) grow()
                return -1
            }
            if (k == key) return values[i]
            i = (i + 1) and mask
        }
    }

    fun put(key: Long, value: Int) {
        var i = slot(key)
        while (true) {
            val k = keys[i]
            if (k == EMPTY) {
                keys[i] = key
                values[i] = value
                if (++size * 2 > keys.size) grow()
                return
            }
            if (k == key) { values[i] = value; return }
            i = (i + 1) and mask
        }
    }

    private fun grow() {
        val oldK = keys
        val oldV = values
        keys = LongArray(oldK.size * 2) { EMPTY }
        values = IntArray(oldK.size * 2)
        mask = keys.size - 1
        size = 0
        for (i in oldK.indices) if (oldK[i] != EMPTY) put(oldK[i], oldV[i])
    }

    companion object {
        const val EMPTY = Long.MIN_VALUE
    }
}

/** Key for an undirected edge between vertices a and b. */
fun edgeKey(a: Int, b: Int): Long =
    if (a < b) (a.toLong() shl 32) or b.toLong() else (b.toLong() shl 32) or a.toLong()
