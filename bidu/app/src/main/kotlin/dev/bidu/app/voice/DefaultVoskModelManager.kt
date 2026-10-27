package dev.bidu.app.voice

import android.content.Context
import android.util.Log
import dev.bidu.app.R
import dev.bidu.app.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.ZipFile

/**
 * Downloads the Vosk model only after the user consented (BiduSettings.voskModelConsent), streams
 * it to the cache directory, checks every archive entry against zip-slip and size limits, unpacks
 * into a staging folder next to the final location, verifies the model layout and then renames
 * it into `filesDir/vosk/<MODEL_NAME>` in one step. Partial files are always deleted.
 */
class DefaultVoskModelManager(
    context: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) : VoskModelManager {

    private val appContext = context.applicationContext
    private val rootDir = File(appContext.filesDir, "vosk")
    private val modelDir = File(rootDir, VoskModelManager.MODEL_NAME)

    private val _state = MutableStateFlow(diskState())
    override val state: StateFlow<ModelState> = _state.asStateFlow()

    private val lock = Any()
    private var installJob: Job? = null
    @Volatile private var activeConnection: HttpURLConnection? = null

    override suspend fun install() {
        val job = synchronized(lock) {
            val running = installJob
            if (running != null && running.isActive) {
                running
            } else {
                if (isValidModel(modelDir)) {
                    _state.value = ModelState.Installed(modelDir, VoskModelManager.MODEL_NAME)
                    return
                }
                if (!settings.current().voskModelConsent) {
                    _state.value = ModelState.Failed(appContext.getString(R.string.voice_model_consent_required))
                    return
                }
                _state.value = ModelState.Downloading(0L, null)
                scope.launch(Dispatchers.IO) { runInstall() }.also { installJob = it }
            }
        }
        // The download belongs to the app scope: leaving the screen that started it does not cancel it.
        job.join()
    }

    override fun cancelInstall() {
        val job = synchronized(lock) { installJob }
        job?.cancel()
        // Unblocks a read that is waiting on the network.
        activeConnection?.disconnect()
    }

    override suspend fun remove() {
        val job = synchronized(lock) { installJob }
        if (job != null) {
            activeConnection?.disconnect()
            job.cancelAndJoin()
        }
        withContext(Dispatchers.IO) {
            if (modelDir.exists() && !modelDir.deleteRecursively()) Log.w(TAG, "model directory not fully removed")
        }
        _state.value = diskState()
    }

    override fun installedDirectory(): File? = modelDir.takeIf { isValidModel(it) }

    private suspend fun runInstall() {
        val workDir = File(appContext.cacheDir, "vosk-download")
        val archive = File(workDir, "${VoskModelManager.MODEL_NAME}.zip.part")
        val staging = File(rootDir, ".staging")
        try {
            workDir.mkdirs()
            rootDir.mkdirs()
            if (rootDir.usableSpace < REQUIRED_FREE_BYTES) {
                throw InstallException(appContext.getString(R.string.voice_model_no_space))
            }
            download(archive)
            _state.value = ModelState.Extracting
            staging.deleteRecursively()
            if (!staging.mkdirs()) throw InstallException(appContext.getString(R.string.voice_model_install_failed))
            unzip(archive, staging)
            val root = locateModelRoot(staging) ?: throw InstallException(appContext.getString(R.string.voice_model_invalid))
            promote(root)
            _state.value = ModelState.Installed(modelDir, VoskModelManager.MODEL_NAME)
            Log.i(TAG, "speech model installed")
        } catch (e: Exception) {
            val cancelled = e is CancellationException || !currentCoroutineContext().isActive
            _state.value = if (cancelled) diskState() else ModelState.Failed(describe(e))
            if (cancelled) {
                Log.i(TAG, "model download cancelled")
                throw (e as? CancellationException ?: CancellationException("install cancelled"))
            }
            Log.w(TAG, "model install failed: ${e.javaClass.simpleName}")
        } finally {
            activeConnection = null
            archive.delete()
            staging.deleteRecursively()
            workDir.delete()
        }
    }

    private suspend fun download(target: File) {
        val connection = URI.create(VoskModelManager.MODEL_URL).toURL().openConnection() as HttpURLConnection
        activeConnection = connection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept-Encoding", "identity")
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw InstallException(appContext.getString(R.string.voice_model_http_error, code))
            }
            // HttpURLConnection never follows a redirect across protocols; stay on HTTPS regardless.
            if (connection.url.protocol != "https") throw InstallException(appContext.getString(R.string.voice_model_network_error))
            val total = connection.contentLengthLong.takeIf { it > 0 }
            if (total != null && total > MAX_ARCHIVE_BYTES) throw InstallException(appContext.getString(R.string.voice_model_too_large))
            _state.value = ModelState.Downloading(0L, total)
            var received = 0L
            var reported = 0L
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        received += read
                        if (received > MAX_ARCHIVE_BYTES) throw InstallException(appContext.getString(R.string.voice_model_too_large))
                        if (received - reported >= PROGRESS_STEP_BYTES) {
                            reported = received
                            _state.value = ModelState.Downloading(received, total)
                        }
                    }
                    output.fd.sync()
                }
            }
            if (total != null && received != total) throw InstallException(appContext.getString(R.string.voice_model_incomplete))
            if (received < MIN_ARCHIVE_BYTES) throw InstallException(appContext.getString(R.string.voice_model_invalid))
            _state.value = ModelState.Downloading(received, total)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun unzip(archive: File, destination: File) {
        val root = destination.canonicalFile
        val rootPrefix = root.path + File.separator
        var unpacked = 0L
        ZipFile(archive).use { zip ->
            val entries = zip.entries().toList()
            if (entries.isEmpty() || entries.size > MAX_ENTRIES) throw InstallException(appContext.getString(R.string.voice_model_invalid))
            for (entry in entries) {
                currentCoroutineContext().ensureActive()
                val name = entry.name
                if (name.isEmpty() || name.contains('\u0000') || name.startsWith("/") || name.contains('\\')) {
                    throw InstallException(appContext.getString(R.string.voice_model_invalid))
                }
                val target = File(root, name).canonicalFile
                if (target != root && !target.path.startsWith(rootPrefix)) {
                    // Zip slip: the entry would land outside the staging folder.
                    throw InstallException(appContext.getString(R.string.voice_model_invalid))
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                    continue
                }
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            unpacked += read
                            if (unpacked > MAX_UNPACKED_BYTES) throw InstallException(appContext.getString(R.string.voice_model_too_large))
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
        }
    }

    /** The archive normally wraps the model in one top-level folder; accept either layout. */
    private fun locateModelRoot(staging: File): File? {
        if (isValidModel(staging)) return staging
        val children = staging.listFiles()?.filter { it.isDirectory }.orEmpty()
        return children.singleOrNull { isValidModel(it) }
    }

    private fun promote(source: File) {
        val old = File(rootDir, ".old")
        old.deleteRecursively()
        val hadPrevious = modelDir.exists()
        if (hadPrevious && !modelDir.renameTo(old)) throw InstallException(appContext.getString(R.string.voice_model_install_failed))
        // Same filesystem, so this rename is atomic: the model is either fully there or not at all.
        if (!source.renameTo(modelDir)) {
            if (hadPrevious) old.renameTo(modelDir)
            throw InstallException(appContext.getString(R.string.voice_model_install_failed))
        }
        old.deleteRecursively()
        if (!isValidModel(modelDir)) throw InstallException(appContext.getString(R.string.voice_model_invalid))
    }

    private fun diskState(): ModelState =
        if (isValidModel(modelDir)) ModelState.Installed(modelDir, VoskModelManager.MODEL_NAME) else ModelState.NotInstalled

    private fun describe(e: Exception): String = when (e) {
        is InstallException -> e.message ?: appContext.getString(R.string.voice_model_install_failed)
        is IOException -> appContext.getString(R.string.voice_model_network_error)
        else -> appContext.getString(R.string.voice_model_install_failed)
    }

    private class InstallException(message: String) : Exception(message)

    private companion object {
        const val TAG = "Bidu/Model"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val BUFFER_BYTES = 64 * 1024
        const val PROGRESS_STEP_BYTES = 512L * 1024
        const val MIN_ARCHIVE_BYTES = 5L * 1024 * 1024
        const val MAX_ARCHIVE_BYTES = 200L * 1024 * 1024
        const val MAX_UNPACKED_BYTES = 400L * 1024 * 1024
        const val REQUIRED_FREE_BYTES = 3 * VoskModelManager.APPROX_SIZE_BYTES + 64L * 1024 * 1024
        const val MAX_ENTRIES = 2_000

        /** What Vosk needs to load a model, and what grammar mode needs (HCLr+Gr) or a static graph (HCLG). */
        fun isValidModel(dir: File): Boolean {
            if (!dir.isDirectory) return false
            val am = File(dir, "am/final.mdl").isFile
            val conf = File(dir, "conf/mfcc.conf").isFile
            val graph = File(dir, "graph")
            val lookahead = File(graph, "HCLr.fst").isFile && File(graph, "Gr.fst").isFile
            val static = File(graph, "HCLG.fst").isFile
            return am && conf && (lookahead || static)
        }
    }
}
