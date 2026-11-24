package com.cappielloantonio.tempo.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cappielloantonio.tempo.util.LocalArtwork;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

/**
 * Serve in sola lettura le copertine già presenti nella cache artwork, per id Subsonic:
 * {@code content://<applicationId>.artwork/<coverArtId>}.
 * <p>
 * È esposto perché Android Auto (processo separato) recupera le miniature delle liste
 * dall'`artworkUri` degli item: lo schema interno dell'app non sarebbe risolvibile da lì, mentre
 * un content URI sì. Il provider non consente enumerazione né scrittura: un id sconosciuto o non
 * più in cache risponde {@link FileNotFoundException}, e l'host mostra il segnaposto come per
 * qualsiasi artwork mancante. L'id è un segmento singolo e viene risolto solo contro l'indice
 * della cache, mai usato come path.
 */
public class ArtworkProvider extends ContentProvider {
    private static final String TAG = "ArtworkProvider";
    private static final String READ_ONLY_MODE = "r";
    private static final int HEADER_SIZE = 12;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        if (!READ_ONLY_MODE.equals(mode)) {
            throw new FileNotFoundException("Read-only provider: " + mode);
        }

        File file = artworkFile(uri);

        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        try {
            return mimeTypeOf(artworkFile(uri));
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        throw new UnsupportedOperationException("Read-only provider");
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only provider");
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only provider");
    }

    private static File artworkFile(Uri uri) throws FileNotFoundException {
        List<String> segments = uri.getPathSegments();

        if (segments.size() != 1) {
            throw new FileNotFoundException("Invalid artwork uri: " + uri);
        }

        File file = LocalArtwork.findCachedArtwork(segments.get(0));

        if (file == null || !file.isFile()) {
            throw new FileNotFoundException("Artwork not cached: " + uri);
        }

        return file;
    }

    /**
     * I file della cache non hanno estensione: il tipo si ricava dai primi byte, così l'host non
     * deve indovinare. Con un formato non riconosciuto resta {@code image/*}.
     */
    private static String mimeTypeOf(File file) {
        byte[] header = new byte[HEADER_SIZE];

        try (FileInputStream input = new FileInputStream(file)) {
            int read = input.read(header);

            if (read >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
                return "image/jpeg";
            }

            if (read >= 8 && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
                return "image/png";
            }

            if (read >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
                return "image/webp";
            }

            if (read >= 4 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8') {
                return "image/gif";
            }
        } catch (IOException e) {
            Log.w(TAG, "Unable to read the artwork header", e);
        }

        return "image/*";
    }
}
