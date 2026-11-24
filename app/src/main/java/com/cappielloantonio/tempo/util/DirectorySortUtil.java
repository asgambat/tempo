package com.cappielloantonio.tempo.util;

import com.cappielloantonio.tempo.subsonic.models.Child;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Ordinamento dei contenuti di una directory (sottocartelle e brani).
 * <p>
 * L'ordine scelto è una preferenza globale, così la stessa logica vale per la pagina della
 * directory e per la navigazione su Android Auto. Le sottocartelle restano sempre prima dei
 * brani, com'erano nell'ordine restituito dal server: cambia solo l'ordine dentro i due gruppi.
 */
public final class DirectorySortUtil {
    private DirectorySortUtil() {
    }

    /**
     * Restituisce una copia ordinata dell'elenco: prima le sottocartelle, poi i brani, ciascun
     * gruppo secondo {@code order}. Un elenco nullo diventa una lista vuota.
     */
    public static List<Child> sort(List<Child> children, String order) {
        List<Child> sorted = new ArrayList<>();
        List<Child> directories = new ArrayList<>();
        List<Child> songs = new ArrayList<>();

        if (children != null) {
            for (Child child : children) {
                if (child == null) {
                    continue;
                }

                if (child.isDir()) {
                    directories.add(child);
                } else {
                    songs.add(child);
                }
            }
        }

        Comparator<Child> comparator = getComparator(order);

        Collections.sort(directories, comparator);
        Collections.sort(songs, comparator);

        sorted.addAll(directories);
        sorted.addAll(songs);

        return sorted;
    }

    /**
     * L'indice alfabetico ha senso solo con l'ordine per nome crescente: con un altro
     * ordinamento una lettera porterebbe a righe che non iniziano con quella lettera.
     */
    public static boolean isAlphabetical(String order) {
        return order == null || Constants.DIRECTORY_ORDER_BY_NAME.equals(order);
    }

    private static Comparator<Child> getComparator(String order) {
        if (Constants.DIRECTORY_ORDER_BY_NAME_DESC.equals(order)) {
            return byName().reversed();
        }

        if (Constants.DIRECTORY_ORDER_BY_RECENTLY_ADDED.equals(order)) {
            return comparingNullsLast(Child::getCreated, true).thenComparing(byName());
        }

        if (Constants.DIRECTORY_ORDER_BY_LEAST_RECENTLY_ADDED.equals(order)) {
            return comparingNullsLast(Child::getCreated, false).thenComparing(byName());
        }

        if (Constants.DIRECTORY_ORDER_BY_MOST_PLAYED.equals(order)) {
            return comparingNullsLast(Child::getPlayCount, true).thenComparing(byName());
        }

        return byName();
    }

    private static <T extends Comparable<? super T>> Comparator<Child> comparingNullsLast(Function<Child, T> extractor, boolean descending) {
        Comparator<T> valueComparator = descending ? Comparator.reverseOrder() : Comparator.naturalOrder();

        return Comparator.comparing(extractor, Comparator.nullsLast(valueComparator));
    }

    private static Comparator<Child> byName() {
        Collator collator = Collator.getInstance();
        collator.setStrength(Collator.PRIMARY);

        return (first, second) -> collator.compare(getName(first), getName(second));
    }

    private static String getName(Child child) {
        return child.getTitle() != null ? child.getTitle() : "";
    }
}
