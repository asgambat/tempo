package com.cappielloantonio.tempo.glide;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.signature.ObjectKey;
import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.util.MusicUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.google.android.material.elevation.SurfaceColors;


public class CustomGlideRequest {
    public static final int CORNER_RADIUS = Preferences.isCornerRoundingEnabled() ? Preferences.getRoundedCornerSize() : 1;

    public static final DiskCacheStrategy DEFAULT_DISK_CACHE_STRATEGY = DiskCacheStrategy.ALL;

    public enum ResourceType {
        Unknown,
        Album,
        Artist,
        Folder,
        Directory,
        Playlist,
        Podcast,
        Radio,
        Song,
    }

    public static RequestOptions createRequestOptions(Context context, String item, ResourceType type) {
        return new RequestOptions()
                .placeholder(new ColorDrawable(SurfaceColors.SURFACE_5.getColor(context)))
                .fallback(getPlaceholder(context, type))
                .error(getPlaceholder(context, type))
                .diskCacheStrategy(DEFAULT_DISK_CACHE_STRATEGY)
                .signature(new ObjectKey(item != null ? item : 0))
                .transform(new CenterCrop(), new RoundedCorners(CustomGlideRequest.CORNER_RADIUS));
    }

    @Nullable
    private static Drawable getPlaceholder(Context context, ResourceType type) {
        switch (type) {
            case Album:
                return AppCompatResources.getDrawable(context, R.drawable.ic_placeholder_album);
            case Artist:
                return AppCompatResources.getDrawable(context, R.drawable.ic_placeholder_artist);
            case Folder:
                return AppCompatResources.getDrawable(context, R.drawable.ic_placeholder_folder);
            case Directory:
                return AppCompatResources.getDrawable(context, R.drawable.ic_placeholder_directory);
            case Playlist:
                return AppCompatResources.getDrawable(context, R.drawable.ic_placeholder_playlist);
            case Podcast:
                return AppCompatResources.getDrawable(context, R.drawable.ic_placeholder_podcast);
            case Radio:
                return AppCompatResources.getDrawable(context, R.drawable.ic_placeholder_radio);
            case Song:
                return AppCompatResources.getDrawable(context, R.drawable.ic_placeholder_song);
            default:
            case Unknown:
                return new ColorDrawable(SurfaceColors.SURFACE_5.getColor(context));
        }
    }

    public static String createUrl(String item, int size) {
        return MusicUtil.getCoverArtUrl(item, size, true);
    }

    public static class Builder {
        private final RequestManager requestManager;
        private Object item;

        private Builder(Context context, String item, ResourceType type) {
            this.requestManager = Glide.with(context);

            if (item != null && !Preferences.isDataSavingMode()) {
                this.item = createUrl(item, Preferences.getImageSize());
            }

            requestManager.applyDefaultRequestOptions(createRequestOptions(context, item, type));
        }

        public static Builder from(Context context, String item, ResourceType type) {
            return new Builder(context, item, type);
        }

        public RequestBuilder<Drawable> build() {
            return requestManager
                    .load(item)
                    .transition(DrawableTransitionOptions.withCrossFade());
        }
    }
}
