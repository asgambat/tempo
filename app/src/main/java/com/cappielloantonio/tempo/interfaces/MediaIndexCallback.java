package com.cappielloantonio.tempo.interfaces;

import androidx.annotation.Keep;

@Keep
public interface MediaIndexCallback {
    default void onRecovery(int index) {}

    // Position recovery in milliseconds (for savePlayQueue position param)
    default void onPositionRecovery(long positionMs) {}
}
