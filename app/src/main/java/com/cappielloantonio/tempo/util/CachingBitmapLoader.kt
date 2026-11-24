package com.cappielloantonio.tempo.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import java.io.IOException
import java.util.concurrent.Callable

/**
 * [BitmapLoader] con cache in memoria dei bitmap decodificati e prefetch dell'artwork successivo.
 *
 * La sessione chiede l'artwork dopo ogni cambio brano e il loader usato prima di questo
 * ([androidx.media3.datasource.DataSourceBitmapLoader]) non ha cache: brani dello stesso album
 * (stesso URI) riscaricavano e ridecodificavano la stessa copertina a ogni transizione, ed era il
 * ritardo visibile in auto e nella notifica. Le richieste concorrenti per lo stesso URI
 * condividono un solo download, così il prefetch non duplica il lavoro della sessione.
 *
 * Serve anche le copertine locali (content URI di [LocalArtwork]): stessa cache in memoria e
 * stessa deduplica, così un'offline cover non viene ridecodificata a ogni cambio brano.
 */
@UnstableApi
class CachingBitmapLoader(
    private val delegate: BitmapLoader,
    private val localArtworkExecutor: ListeningExecutorService
) : BitmapLoader {

    // Il default di LruCache conta le voci: qui la dimensione è in KB di bitmap decodificato.
    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    // URI -> richiesta in corso: sessione e prefetch chiedono lo stesso artwork quasi insieme.
    private val inFlight = mutableMapOf<String, ListenableFuture<Bitmap>>()

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        delegate.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val key = uri.toString()

        cache.get(key)?.let { return Futures.immediateFuture(it) }

        synchronized(inFlight) {
            inFlight[key]?.let { return it }

            val future = Futures.transform(
                if (LocalArtwork.isLocalArtwork(uri)) {
                    decodeLocalArtwork(uri)
                } else {
                    delegate.loadBitmap(uri)
                },
                { bitmap: Bitmap ->
                    cache.put(key, bitmap)
                    bitmap
                },
                MoreExecutors.directExecutor()
            )

            inFlight[key] = future
            future.addListener(
                { synchronized(inFlight) { inFlight.remove(key) } },
                MoreExecutors.directExecutor()
            )

            return future
        }
    }

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap> {
        metadata.artworkData?.let { return delegate.decodeBitmap(it) }

        // Senza bitmap inline la sessione carica dall'URI: è il caso degli item esposti
        // all'auto, che quindi passa dalla cache condivisa.
        val uri = metadata.artworkUri
                ?: return Futures.immediateFailedFuture<Bitmap>(
                        IllegalArgumentException("No artwork")
                )

        return loadBitmap(uri)
    }

    /**
     * Copertine locali (content URI di [LocalArtwork]): file della cache su disco, letti e
     * decodificati sull'executor della sessione. Il risultato entra nella stessa cache in memoria
     * degli artwork di rete e le richieste concorrenti condividono la decodifica.
     */
    private fun decodeLocalArtwork(uri: Uri): ListenableFuture<Bitmap> =
        // La lookup del file, che costruisce o aggiorna l'indice della cache su disco, gira
        // sull'executor della sessione e non sul thread chiamante (sessione o UI).
        // La cache artwork può sfrattare il file: il fallimento è gestito dalla sessione come
        // artwork mancante, non deve interrompere il playback.
        localArtworkExecutor.submit(
            Callable {
                val file = LocalArtwork.fileFor(uri)
                    ?: throw IllegalArgumentException("No local artwork")

                BitmapFactory.decodeFile(file.absolutePath)
                    ?: throw IOException("Unable to decode the cached artwork")
            }
        )

    override fun supportsMimeType(mimeType: String): Boolean =
        delegate.supportsMimeType(mimeType)

    /**
     * Scalda la cache per l'artwork del brano corrente e, al termine del download, per quello del
     * brano successivo. Il prefetch del successivo parte solo dopo, perché l'executor del loader è
     * a thread singolo: accodarlo subito ritarderebbe proprio la copertina che l'utente sta per
     * vedere.
     */
    fun prefetch(mediaItem: MediaItem?, nextMediaItem: MediaItem?) {
        val currentArtwork = artworkUri(mediaItem) ?: return

        if (artworkUri(nextMediaItem) == currentArtwork) {
            loadBitmap(currentArtwork)
            return
        }

        loadBitmap(currentArtwork).addListener(
            { artworkUri(nextMediaItem)?.let { loadBitmap(it) } },
            MoreExecutors.directExecutor()
        )
    }

    // Artwork di rete e copertine locali: entrambi passano da questa cache. Gli altri schemi
    // (es. file://) li gestisce direttamente il delegate.
    private fun artworkUri(mediaItem: MediaItem?): Uri? =
        mediaItem?.mediaMetadata?.artworkUri?.takeIf {
            it.scheme == "http" || it.scheme == "https" || LocalArtwork.isLocalArtwork(it)
        }

    private companion object {
        // 8 MB: circa 8 copertine a 500 px decodificate, senza togliere RAM al player.
        const val MAX_CACHE_KB = 8 * 1024
    }
}
