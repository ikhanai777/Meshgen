package com.meshgen.app.llm

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.StatFs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest

sealed interface ModelState {
    data object NotDownloaded : ModelState
    /** [waiting] = paused by the system (no network, etc.); the download resumes on its own. */
    data class Downloading(val bytes: Long, val total: Long, val waiting: String?) : ModelState
    data class Verifying(val fraction: Float) : ModelState
    data object Ready : ModelState
    data class Failed(val message: String) : ModelState
}

/**
 * Downloads, verifies and deletes models. Uses Android's DownloadManager, which keeps going in the background,
 * shows a notification and resumes on its own after the connection drops.
 */
class ModelManager(private val context: Context) {
    private val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val prefs = context.getSharedPreferences("models", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var poller: Job? = null

    private val _states = MutableStateFlow(ModelCatalog.ALL.associate { it.id to initialState(it) })
    val states: StateFlow<Map<String, ModelState>> = _states.asStateFlow()

    private val _active = MutableStateFlow(prefs.getString("active", null)?.let { ModelCatalog.byId(it) })
    /** The model the user chose to use (only meaningful when it is Ready). */
    val active: StateFlow<ModelSpec?> = _active.asStateFlow()

    val dir: File get() = (context.getExternalFilesDir("models") ?: File(context.filesDir, "models")).apply { mkdirs() }

    fun file(spec: ModelSpec) = File(dir, spec.fileName)
    private fun partFile(spec: ModelSpec) = File(dir, spec.fileName + ".part")

    init {
        if (ModelCatalog.ALL.any { prefs.contains("dl_${it.id}") }) startPolling()
    }

    private fun initialState(spec: ModelSpec): ModelState = when {
        file(spec).isFile && file(spec).length() == spec.sizeBytes -> ModelState.Ready
        prefs.contains("dl_${spec.id}") -> ModelState.Downloading(0, spec.sizeBytes, null)
        else -> ModelState.NotDownloaded
    }

    fun readyModel(): ModelSpec? {
        val states = _states.value
        return _active.value?.takeIf { states[it.id] == ModelState.Ready }
            ?: ModelCatalog.ALL.firstOrNull { states[it.id] == ModelState.Ready }
    }

    fun setActive(spec: ModelSpec) {
        prefs.edit().putString("active", spec.id).apply()
        _active.value = spec
    }

    fun freeSpaceBytes(): Long = StatFs(dir.path).availableBytes

    /** Starts a download. Returns an error message instead when it cannot start. */
    fun download(spec: ModelSpec): String? {
        val needed = spec.sizeBytes + (300L shl 20)
        if (freeSpaceBytes() < needed) {
            return "Not enough storage: ${gb(spec.sizeBytes + (300L shl 20))} needed, ${gb(freeSpaceBytes())} free."
        }
        partFile(spec).delete()
        val req = DownloadManager.Request(Uri.parse(spec.url))
            .setTitle("MeshGen: ${spec.name}")
            .setDescription("AI model (${gb(spec.sizeBytes)})")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, "models", spec.fileName + ".part")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        val id = dm.enqueue(req)
        prefs.edit().putLong("dl_${spec.id}", id).apply()
        set(spec, ModelState.Downloading(0, spec.sizeBytes, null))
        startPolling()
        return null
    }

    fun cancel(spec: ModelSpec) {
        val id = prefs.getLong("dl_${spec.id}", -1)
        if (id >= 0) dm.remove(id)
        prefs.edit().remove("dl_${spec.id}").apply()
        partFile(spec).delete()
        set(spec, ModelState.NotDownloaded)
    }

    fun delete(spec: ModelSpec) {
        file(spec).delete()
        partFile(spec).delete()
        context.cacheDir.resolve("llm").listFiles()?.filter { it.name.startsWith(spec.id) }?.forEach { it.delete() }
        set(spec, ModelState.NotDownloaded)
    }

    private fun set(spec: ModelSpec, s: ModelState) = _states.update { it + (spec.id to s) }

    private fun startPolling() {
        if (poller?.isActive == true) return
        poller = scope.launch {
            while (isActive) {
                var anyActive = false
                for (spec in ModelCatalog.ALL) {
                    val id = prefs.getLong("dl_${spec.id}", -1)
                    if (id < 0) continue
                    anyActive = true
                    poll(spec, id)
                }
                if (!anyActive) break
                delay(700)
            }
        }
    }

    private fun poll(spec: ModelSpec, id: Long) {
        dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (!c.moveToFirst()) {
                prefs.edit().remove("dl_${spec.id}").apply()
                set(spec, ModelState.Failed("The download was removed. Tap Download to start again."))
                return
            }
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val bytes = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).takeIf { it > 0 } ?: spec.sizeBytes
            val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    prefs.edit().remove("dl_${spec.id}").apply()
                    verify(spec)
                }
                DownloadManager.STATUS_FAILED -> {
                    prefs.edit().remove("dl_${spec.id}").apply()
                    dm.remove(id)
                    set(spec, ModelState.Failed("Download failed (${failReason(reason)}). Tap Download to try again."))
                }
                DownloadManager.STATUS_PAUSED -> set(spec, ModelState.Downloading(bytes, total, pauseReason(reason)))
                else -> set(spec, ModelState.Downloading(bytes, total, null))
            }
        }
    }

    private fun verify(spec: ModelSpec) {
        val part = partFile(spec)
        set(spec, ModelState.Verifying(0f))
        if (!part.isFile || part.length() != spec.sizeBytes) {
            part.delete()
            set(spec, ModelState.Failed("The downloaded file has the wrong size. Tap Download to try again."))
            return
        }
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(1 shl 20)
        var done = 0L
        part.inputStream().use { input ->
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
                done += n
                if (done % (64L shl 20) < n) set(spec, ModelState.Verifying(done.toFloat() / spec.sizeBytes))
            }
        }
        val hex = md.digest().joinToString("") { "%02x".format(it) }
        if (hex != spec.sha256) {
            part.delete()
            set(spec, ModelState.Failed("The download was damaged (checksum mismatch). Tap Download to try again."))
            return
        }
        file(spec).delete()
        part.renameTo(file(spec))
        if (_active.value == null || readyModel() == null) setActive(spec)
        set(spec, ModelState.Ready)
    }

    private fun pauseReason(r: Int) = when (r) {
        DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Waiting for internet"
        DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "Waiting for Wi-Fi"
        DownloadManager.PAUSED_WAITING_TO_RETRY -> "Connection lost, retrying"
        else -> "Paused by the system"
    }

    private fun failReason(r: Int) = when (r) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "not enough storage"
        DownloadManager.ERROR_CANNOT_RESUME -> "could not resume"
        DownloadManager.ERROR_HTTP_DATA_ERROR, DownloadManager.ERROR_TOO_MANY_REDIRECTS, DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "server error"
        in 400..599 -> "server error $r"
        else -> "error $r"
    }

    companion object {
        fun gb(bytes: Long) = String.format(java.util.Locale.US, "%.1f GB", bytes / 1e9)
    }
}
