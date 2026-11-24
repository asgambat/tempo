package com.cappielloantonio.tempo.ui.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.PopupMenu;
import android.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.FragmentArtistCatalogueBinding;
import com.cappielloantonio.tempo.helper.recyclerview.GridItemDecoration;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.subsonic.models.ArtistID3;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.adapter.ArtistCatalogueAdapter;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.viewmodel.ArtistCatalogueViewModel;

import java.util.ArrayList;
import java.util.List;

@UnstableApi
public class ArtistCatalogueFragment extends Fragment implements ClickCallback {
    private static final String TAG = "ArtistCatalogueFragment";

    private FragmentArtistCatalogueBinding bind;
    private MainActivity activity;
    private ArtistCatalogueViewModel artistCatalogueViewModel;

    private ArtistCatalogueAdapter artistAdapter;

    private String currentSort = Constants.ARTIST_ORDER_BY_NAME;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        initData();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentArtistCatalogueBinding.inflate(inflater, container, false);
        View view = bind.getRoot();

        initAppBar();
        initArtistCatalogueView();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void initData() {
        artistCatalogueViewModel = new ViewModelProvider(requireActivity()).get(ArtistCatalogueViewModel.class);
        artistCatalogueViewModel.loadArtists();
    }

    private void initAppBar() {
        activity.setSupportActionBar(bind.toolbar);

        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        bind.toolbar.setNavigationOnClickListener(v -> {
            hideKeyboard(v);
            activity.navController.navigateUp();
        });


        bind.appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            if ((bind.artistInfoSector.getHeight() + verticalOffset) < (2 * ViewCompat.getMinimumHeight(bind.toolbar))) {
                bind.toolbar.setTitle(R.string.artist_catalogue_title);
            } else {
                bind.toolbar.setTitle(R.string.empty_string);
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initArtistCatalogueView() {
        bind.artistCatalogueRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        bind.artistCatalogueRecyclerView.addItemDecoration(new GridItemDecoration(2, 20, false));
        bind.artistCatalogueRecyclerView.setHasFixedSize(true);

        artistAdapter = new ArtistCatalogueAdapter(this);
        artistAdapter.setStateRestorationPolicy(RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY);
        artistAdapter.setOnItemsChangedListener(this::updateAlphabetIndex);
        bind.artistCatalogueRecyclerView.setAdapter(artistAdapter);

        bind.artistCatalogueAlphabetIndexBar.setOnPositionSelectedListener(this::scrollToPosition);
        bind.artistCatalogueAlphabetIndexBar.watchScroll(bind.artistCatalogueRecyclerView);

        artistCatalogueViewModel.getArtistList().observe(getViewLifecycleOwner(), artistList -> artistAdapter.setItems(artistList));

        bind.artistCatalogueRecyclerView.setOnTouchListener((v, event) -> {
            hideKeyboard(v);
            return false;
        });

        bind.artistListSortImageView.setOnClickListener(view -> showPopupMenu(view, R.menu.sort_artist_popup_menu));
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.toolbar_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);

        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setImeOptions(EditorInfo.IME_ACTION_DONE);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchView.clearFocus();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                artistAdapter.getFilter().filter(newText);
                return false;
            }
        });

        searchView.setPadding(-32, 0, 0, 0);
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void showPopupMenu(View view, int menuResource) {
        PopupMenu popup = new PopupMenu(requireContext(), view);
        popup.getMenuInflater().inflate(menuResource, popup.getMenu());

        popup.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getItemId() == R.id.menu_artist_sort_name) {
                sortBy(Constants.ARTIST_ORDER_BY_NAME);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_artist_sort_random) {
                sortBy(Constants.ARTIST_ORDER_BY_RANDOM);
                return true;
            }

            return false;
        });

        popup.show();
    }

    // L'indice A-Z ha senso solo con l'ordine alfabetico: con un altro ordinamento una lettera
    // porterebbe a righe che non iniziano con quella lettera.
    private void sortBy(String order) {
        currentSort = order;
        artistAdapter.sort(order);
        updateAlphabetIndex();
    }

    private void updateAlphabetIndex() {
        if (bind == null) return;

        if (!Constants.ARTIST_ORDER_BY_NAME.equals(currentSort)) {
            bind.artistCatalogueAlphabetIndexBar.setTitles(null);
            return;
        }

        List<String> titles = new ArrayList<>(artistAdapter.getItemCount());

        for (int i = 0; i < artistAdapter.getItemCount(); i++) {
            ArtistID3 artist = artistAdapter.getItem(i);
            titles.add(artist != null ? artist.getName() : null);
        }

        bind.artistCatalogueAlphabetIndexBar.setTitles(titles);
    }

    private void scrollToPosition(int position) {
        if (bind == null) return;

        RecyclerView.LayoutManager layoutManager = bind.artistCatalogueRecyclerView.getLayoutManager();

        if (layoutManager instanceof LinearLayoutManager) {
            ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(position, 0);
        }
    }

    @Override
    public void onArtistClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.artistPageFragment, bundle);
        hideKeyboard(requireView());
    }

    @Override
    public void onArtistLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.artistBottomSheetDialog, bundle);
    }
}