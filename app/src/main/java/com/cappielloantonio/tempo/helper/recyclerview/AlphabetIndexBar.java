package com.cappielloantonio.tempo.helper.recyclerview;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.GridLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.util.AlphabetIndex;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

import java.util.Collections;
import java.util.List;

/**
 * Indice alfabetico da affiancare a una lista di cartelle: una pastiglia "A-Z" sul bordo sinistro
 * della lista che, al tocco, apre una tastiera di lettere e comunica la posizione della prima voce
 * con quell'iniziale. Le lettere senza corrispondenze restano disattivate, così non si salta a
 * vuoto; se la lettera scelta non ha voci si ripiega sulla prima iniziale successiva.
 */
public class AlphabetIndexBar extends AppCompatTextView {
    private static final int MIN_ITEMS = 2;
    private static final long LETTER_LABEL_TIMEOUT_MS = 900;
    private static final float DISABLED_ALPHA = 0.38f;

    public interface OnPositionSelectedListener {
        void onPositionSelected(int position);
    }

    private List<String> titles = Collections.emptyList();
    @Nullable
    private OnPositionSelectedListener listener;
    @Nullable
    private BottomSheetDialog letterPickerDialog;
    @Nullable
    private View watchedScrollable;
    @Nullable
    private RecyclerView watchedRecyclerView;
    private int lastShownPosition = AlphabetIndex.NO_POSITION;

    private final Runnable restoreLabelRunnable = this::restoreLabel;

    private final RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            if (dy != 0 && watchedScrollable != null) {
                showLetterAt(firstVisiblePosition(watchedScrollable, recyclerView));
            }
        }
    };

    private final ViewTreeObserver.OnScrollChangedListener scrollableListener = () -> {
        if (watchedScrollable == null || watchedRecyclerView == null) return;

        int position = firstVisiblePosition(watchedScrollable, watchedRecyclerView);

        // Il ViewTreeObserver notifica anche gli scorrimenti orizzontali di altre liste della
        // schermata: si aggiorna l'etichetta solo quando cambia la prima voce visibile.
        if (position == lastShownPosition) return;

        lastShownPosition = position;
        showLetterAt(position);
    };

    public AlphabetIndexBar(Context context) {
        this(context, null);
    }

    public AlphabetIndexBar(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AlphabetIndexBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setText(R.string.alphabet_index_label);
        setContentDescription(getContext().getString(R.string.alphabet_index_content_description));
        setGravity(Gravity.CENTER);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        setTypeface(getTypeface(), Typeface.BOLD);
        setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant));
        setBackground(AppCompatResources.getDrawable(getContext(), R.drawable.alphabet_index_background));
        setElevation(dp(3));
        setClickable(true);
        setFocusable(true);
        setVisibility(GONE);

        setOnClickListener(view -> showLetterPicker());
    }

    /**
     * Titoli delle righe nell'ordine dell'adapter: servono a sapere quali lettere hanno voci e
     * dove salta l'indice. Con meno di due voci non c'è nulla da indicizzare e la pastiglia sparisce.
     */
    public void setTitles(@Nullable List<String> titles) {
        this.titles = titles != null ? titles : Collections.emptyList();
        setVisibility(this.titles.size() >= MIN_ITEMS ? VISIBLE : GONE);
    }

    public void setOnPositionSelectedListener(@Nullable OnPositionSelectedListener listener) {
        this.listener = listener;
    }

    /**
     * Mentre si scorre la lista la pastiglia mostra l'iniziale della prima riga visibile, così si
     * capisce in che punto dell'alfabeto si è; dopo qualche istante torna l'etichetta "A-Z".
     */
    public void watchScroll(RecyclerView recyclerView) {
        watchScroll(recyclerView, recyclerView);
    }

    /**
     * Come {@link #watchScroll(RecyclerView)}, ma per le liste espanse in un contenitore esterno:
     * {@code scrollable} è la view che scorre davvero (es. la NestedScrollView della Libreria),
     * {@code recyclerView} la lista dei titoli.
     */
    public void watchScroll(@NonNull View scrollable, @NonNull RecyclerView recyclerView) {
        detachScrollWatcher();

        watchedScrollable = scrollable;
        watchedRecyclerView = recyclerView;

        recyclerView.addOnScrollListener(scrollListener);

        if (scrollable != recyclerView) {
            // ViewTreeObserver.notifica in aggiunta ai listener già presenti: sul contenitore non
            // si installa un View.OnScrollChangeListener, che cancellerebbe quello di altri.
            scrollable.getViewTreeObserver().addOnScrollChangedListener(scrollableListener);
        }
    }

    /**
     * Sgancia il watcher precedente: la barra può essere riusata con un'altra lista, o la view
     * ricreata, senza sommare listener che tengono in vita la lista vecchia.
     */
    private void detachScrollWatcher() {
        if (watchedRecyclerView != null) {
            watchedRecyclerView.removeOnScrollListener(scrollListener);
            watchedRecyclerView = null;
        }

        if (watchedScrollable != null) {
            ViewTreeObserver observer = watchedScrollable.getViewTreeObserver();

            if (observer.isAlive()) {
                observer.removeOnScrollChangedListener(scrollableListener);
            }

            watchedScrollable = null;
        }

        lastShownPosition = AlphabetIndex.NO_POSITION;
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(restoreLabelRunnable);
        restoreLabel();
        detachScrollWatcher();

        // La tastiera è una finestra separata: se si esce dalla schermata resta aperta.
        if (letterPickerDialog != null) {
            letterPickerDialog.dismiss();
            letterPickerDialog = null;
        }

        super.onDetachedFromWindow();
    }

    private void showLetterAt(int position) {
        if (position < 0 || position >= titles.size()) return;

        int initial = AlphabetIndex.initialIndex(titles.get(position));

        if (initial == AlphabetIndex.NO_POSITION) {
            restoreLabel();
            return;
        }

        setText(String.valueOf((char) ('A' + initial)));

        removeCallbacks(restoreLabelRunnable);
        postDelayed(restoreLabelRunnable, LETTER_LABEL_TIMEOUT_MS);
    }

    private void restoreLabel() {
        setText(R.string.alphabet_index_label);
    }

    private int firstVisiblePosition(View scrollable, RecyclerView recyclerView) {
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();

        if (!(layoutManager instanceof LinearLayoutManager)) return AlphabetIndex.NO_POSITION;

        int viewportTop = Math.max(
                bottomOf(scrollable, scrollable.getPaddingTop()),
                bottomOf(recyclerView, recyclerView.getPaddingTop())
        );

        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);

            if (bottomOf(child, child.getHeight()) > viewportTop) {
                return layoutManager.getPosition(child);
            }
        }

        return ((LinearLayoutManager) layoutManager).findFirstVisibleItemPosition();
    }

    private int bottomOf(View view, int height) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);

        return location[1] + height;
    }

    private void showLetterPicker() {
        Context context = getContext();
        if (context == null || titles.isEmpty()) return;

        if (letterPickerDialog != null) {
            letterPickerDialog.dismiss();
            letterPickerDialog = null;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(context);
        letterPickerDialog = dialog;
        dialog.setOnDismissListener(dismissed -> {
            if (letterPickerDialog == dismissed) letterPickerDialog = null;
        });

        LayoutInflater inflater = LayoutInflater.from(context);
        View content = inflater.inflate(R.layout.bottom_sheet_alphabet_index_dialog, null, false);
        GridLayout keyboard = content.findViewById(R.id.alphabet_index_keyboard);

        boolean[] available = AlphabetIndex.availableInitials(titles);

        for (int i = 0; i < AlphabetIndex.LETTER_COUNT; i++) {
            final char letter = (char) ('A' + i);
            final boolean isAvailable = available[i];

            MaterialButton key = (MaterialButton) inflater.inflate(R.layout.alphabet_index_key, keyboard, false);
            key.setText(String.valueOf(letter));
            key.setEnabled(isAvailable);
            key.setAlpha(isAvailable ? 1f : DISABLED_ALPHA);

            if (isAvailable) {
                key.setOnClickListener(view -> {
                    dialog.dismiss();
                    dispatchLetter(letter);
                });
            }

            keyboard.addView(key);
        }

        dialog.setContentView(content);
        dialog.show();
    }

    private void dispatchLetter(char letter) {
        if (listener == null) return;

        int position = AlphabetIndex.findPosition(titles, letter);

        if (position != AlphabetIndex.NO_POSITION) {
            listener.onPositionSelected(position);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
