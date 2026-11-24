package com.cappielloantonio.tempo.util;

import androidx.annotation.Nullable;

import java.text.Normalizer;
import java.util.List;

/**
 * Logica dell'indice alfabetico condivisa da {@code AlphabetIndexBar}: normalizzazione dei titoli,
 * lettere coperte dalla lista e posizione della prima voce con una certa iniziale.
 */
public final class AlphabetIndex {
    public static final int LETTER_COUNT = 26;
    public static final int NO_POSITION = -1;

    private AlphabetIndex() {
    }

    /**
     * Indice 0-25 dell'iniziale del titolo, senza accenti e saltando punteggiatura e spazi:
     * "Èlisa" finisce sotto la E, "[Live] Roma" sotto la L. Titoli che iniziano con alfabeti
     * diversi da quello latino restituiscono {@link #NO_POSITION} e non partecipano all'indice.
     */
    public static int initialIndex(@Nullable String title) {
        if (title == null) return NO_POSITION;

        String normalized = Normalizer.normalize(title.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        for (int i = 0; i < normalized.length(); i++) {
            char character = Character.toUpperCase(normalized.charAt(i));

            if (character >= 'A' && character <= 'Z') {
                return character - 'A';
            }

            if (Character.isLetterOrDigit(character)) {
                return NO_POSITION;
            }
        }

        return NO_POSITION;
    }

    /**
     * Per ogni lettera dell'alfabeto, se esiste almeno una voce con quell'iniziale.
     */
    public static boolean[] availableInitials(@Nullable List<String> titles) {
        boolean[] available = new boolean[LETTER_COUNT];

        if (titles == null) return available;

        for (String title : titles) {
            int initial = initialIndex(title);

            if (initial != NO_POSITION) {
                available[initial] = true;
            }
        }

        return available;
    }

    /**
     * Posizione della prima voce la cui iniziale è pari o successiva alla lettera richiesta: una
     * lettera senza voci porta alla prima iniziale seguente. Oltre l'ultima iniziale disponibile
     * si va in fondo alla lista.
     */
    public static int findPosition(@Nullable List<String> titles, char letter) {
        if (titles == null || titles.isEmpty()) return NO_POSITION;

        for (int i = 0; i < titles.size(); i++) {
            int initial = initialIndex(titles.get(i));

            if (initial != NO_POSITION && initial >= letter - 'A') {
                return i;
            }
        }

        return titles.size() - 1;
    }
}
