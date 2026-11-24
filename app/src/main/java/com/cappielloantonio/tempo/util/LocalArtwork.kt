package com.cappielloantonio.tempo.util

import android.content.ContentResolver
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import com.cappielloantonio.tempo.BuildConfig
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Copertine disponibili senza rete.
 *
 * L'artwork della sessione è già su disco (cache `artwork_cache`), ma è indicizzato per URL
 * pubblico: senza un server configurato quell'URL non è costruibile. La copertina si espone quindi
 * con un content URI che porta solo l'id Subsonic, servito in sola lettura da
 * `provider/ArtworkProvider`: risolvibile sia dal nostro processo sia dall'host di Android Auto,
 * che carica da sé l'`artworkUri` delle liste. Dentro l'app il CachingBitmapLoader legge il file
 * direttamente dalla cache (v. `decodeLocalArtwork`), senza passare dal provider.
 *
 * La ricerca della copia su disco avviene quando serve davvero (decodifica lato sessione,
 * `openFile` lato host), mai mentre si costruisce una lista: se la copertina non è in cache
 * l'artwork resta mancante, come per un download mai coperto.
 */
@UnstableApi
object LocalArtwork {
    private const val AUTHORITY_SUFFIX = ".artwork"
    private const val PARAM_ID = "id"

    // `applicationId` del flavor: la stessa authority dichiarata nel manifest (${applicationId}).
    private val AUTHORITY = BuildConfig.APPLICATION_ID + AUTHORITY_SUFFIX

    // id Subsonic -> file già risolto (cache veloce per le richieste ripetute).
    private val resolvedFiles = ConcurrentHashMap<String, File>()

    // Indice completo della cache per id: si scandiscono le chiavi una volta, non a ogni brano.
    @Volatile
    private var index: Map<String, File> = emptyMap()

    @Volatile
    private var indexedKeyCount = -1

    /**
     * URI della copertina locale per [coverArtId]. Non contiene il path del file: l'URI finisce nei
     * `MediaMetadata` inviati ai controller esterni (Android Auto) e non deve esporre la struttura
     * delle directory private dell'app.
     */
    @JvmStatic
    fun uriFor(coverArtId: String): Uri =
        Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(AUTHORITY)
            .appendPath(coverArtId)
            .build()

    /** Vero per gli URI costruiti da [uriFor]. */
    @JvmStatic
    fun isLocalArtwork(uri: Uri): Boolean =
        uri.scheme == ContentResolver.SCHEME_CONTENT && uri.authority == AUTHORITY

    @JvmStatic
    fun fileFor(uri: Uri): File? =
        findCachedArtwork(uri.lastPathSegment)

    /**
     * Copertina già scaricata per [coverArtId], oppure null se la cache non la contiene (o è
     * stata sfrattata).
     *
     * L'id si legge dal parametro `id` della chiave (l'URL `getCoverArt` che ha riempito la
     * cache), non dalla coda della stringa: aggiungere o spostare parametri nell'URL non rompe la
     * corrispondenza. L'indice della cache viene costruito alla prima richiesta e poi riusato.
     *
     * La scansione può durare quanto tutta la cache: non c'è un lock condiviso intorno, così non
     * blocca le decodifiche concorrenti, e i chiamanti sono sull'executor artwork oppure sul
     * thread binder di `ArtworkProvider` (mai sulla UI). Lo stato condiviso è pubblicato da campi
     * `volatile` e [resolvedFiles] è una ConcurrentHashMap; due ricostruzioni concorrenti al massimo
     * ripetono il lavoro, senza risultati parziali.
     */
    @JvmStatic
    fun findCachedArtwork(coverArtId: String?): File? {
        if (coverArtId.isNullOrEmpty()) return null

        resolvedFiles[coverArtId]?.let { file ->
            if (file.isFile) return file

            resolvedFiles.remove(coverArtId)
        }

        val cache = DownloadUtil.getArtworkCache()
        val keys = cache.keys

        if (indexedKeyCount != keys.size) rebuildIndex(cache)

        val file = index[coverArtId] ?: return null

        if (!file.isFile) {
            // I file possono essere sfrattati senza che cambi il numero di chiavi: un secondo
            // indice dice se la copertina esiste ancora.
            rebuildIndex(cache)

            return index[coverArtId]?.takeIf { it.isFile }
        }

        resolvedFiles[coverArtId] = file

        return file
    }

    private fun rebuildIndex(cache: Cache) {
        val keys = cache.keys
        val files = HashMap<String, File>(keys.size)

        for (key in keys) {
            val coverArtId = Uri.parse(key).getQueryParameter(PARAM_ID) ?: continue

            val span = cache.getCachedSpans(key)
                ?.firstOrNull { it.isCached && it.length > 0 }
                ?: continue

            val file = span.file ?: continue

            files[coverArtId] = file
        }

        index = files
        indexedKeyCount = keys.size
    }
}
