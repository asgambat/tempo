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
import androidx.annotation.OptIn;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.FragmentAlbumCatalogueBinding;
import com.cappielloantonio.tempo.helper.recyclerview.GridItemDecoration;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.subsonic.models.AlbumID3;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.adapter.AlbumCatalogueAdapter;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.viewmodel.AlbumCatalogueViewModel;

import java.util.ArrayList;
import java.util.List;

@OptIn(markerClass = UnstableApi.class)
public class AlbumCatalogueFragment extends Fragment implements ClickCallback {
    private static final String TAG = "AlbumCatalogueFragment";

    private FragmentAlbumCatalogueBinding bind;
    private MainActivity activity;
    private AlbumCatalogueViewModel albumCatalogueViewModel;

    private AlbumCatalogueAdapter albumAdapter;

    private String currentSort = Constants.ALBUM_ORDER_BY_NAME;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        initData();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        albumCatalogueViewModel.stopLoading();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentAlbumCatalogueBinding.inflate(inflater, container, false);
        View view = bind.getRoot();

        initAppBar();
        initAlbumCatalogueView();
        initProgressLoader();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void initData() {
        albumCatalogueViewModel = new ViewModelProvider(requireActivity()).get(AlbumCatalogueViewModel.class);
        albumCatalogueViewModel.loadAlbums();
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
            if ((bind.albumInfoSector.getHeight() + verticalOffset) < (2 * ViewCompat.getMinimumHeight(bind.toolbar))) {
                bind.toolbar.setTitle(R.string.album_catalogue_title);
            } else {
                bind.toolbar.setTitle(R.string.empty_string);
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initAlbumCatalogueView() {
        bind.albumCatalogueRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        bind.albumCatalogueRecyclerView.addItemDecoration(new GridItemDecoration(2, 20, false));
        bind.albumCatalogueRecyclerView.setHasFixedSize(true);

        albumAdapter = new AlbumCatalogueAdapter(this, true);
        albumAdapter.setStateRestorationPolicy(RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY);
        albumAdapter.setOnItemsChangedListener(this::updateAlphabetIndex);
        bind.albumCatalogueRecyclerView.setAdapter(albumAdapter);

        bind.albumCatalogueAlphabetIndexBar.setOnPositionSelectedListener(this::scrollToPosition);
        bind.albumCatalogueAlphabetIndexBar.watchScroll(bind.albumCatalogueRecyclerView);

        albumCatalogueViewModel.getAlbumList().observe(getViewLifecycleOwner(), albums -> albumAdapter.setItems(albums));

        bind.albumCatalogueRecyclerView.setOnTouchListener((v, event) -> {
            hideKeyboard(v);
            return false;
        });

        bind.albumListSortImageView.setOnClickListener(view -> showPopupMenu(view, R.menu.sort_album_popup_menu));
    }

    private void initProgressLoader() {
        albumCatalogueViewModel.getLoadingStatus().observe(getViewLifecycleOwner(), isLoading -> {
            if (isLoading) {
                bind.albumListSortImageView.setEnabled(false);
                bind.albumListProgressLoader.setVisibility(View.VISIBLE);
            } else {
                bind.albumListSortImageView.setEnabled(true);
                bind.albumListProgressLoader.setVisibility(View.GONE);
            }
        });
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
                albumAdapter.getFilter().filter(newText);
                return false;
            }
        });

        searchView.setPadding(-32, 0, 0, 0);
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void showPopupMenu(View view, int menuResource) {
        PopupMenu popup = new PopupMenu(requireContext(), view);
        popup.getMenuInflater().inflate(menuResource, popup.getMenu());

        popup.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getItemId() == R.id.menu_album_sort_name) {
                sortBy(Constants.ALBUM_ORDER_BY_NAME);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_album_sort_artist) {
                sortBy(Constants.ALBUM_ORDER_BY_ARTIST);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_album_sort_year) {
                sortBy(Constants.ALBUM_ORDER_BY_YEAR);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_album_sort_random) {
                sortBy(Constants.ALBUM_ORDER_BY_RANDOM);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_album_sort_recently_added) {
                sortBy(Constants.ALBUM_ORDER_BY_RECENTLY_ADDED);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_album_sort_recently_played) {
                sortBy(Constants.ALBUM_ORDER_BY_RECENTLY_PLAYED);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_album_sort_most_played) {
                sortBy(Constants.ALBUM_ORDER_BY_MOST_PLAYED);
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
        albumAdapter.sort(order);
        updateAlphabetIndex();
    }

    private void updateAlphabetIndex() {
        if (bind == null) return;

        if (!Constants.ALBUM_ORDER_BY_NAME.equals(currentSort)) {
            bind.albumCatalogueAlphabetIndexBar.setTitles(null);
            return;
        }

        List<String> titles = new ArrayList<>(albumAdapter.getItemCount());

        for (int i = 0; i < albumAdapter.getItemCount(); i++) {
            AlbumID3 album = albumAdapter.getItem(i);
            titles.add(album != null ? album.getName() : null);
        }

        bind.albumCatalogueAlphabetIndexBar.setTitles(titles);
    }

    private void scrollToPosition(int position) {
        if (bind == null) return;

        RecyclerView.LayoutManager layoutManager = bind.albumCatalogueRecyclerView.getLayoutManager();

        if (layoutManager instanceof LinearLayoutManager) {
            ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(position, 0);
        }
    }

    @Override
    public void onAlbumClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.albumPageFragment, bundle);
        hideKeyboard(requireView());
    }

    @Override
    public void onAlbumLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.albumBottomSheetDialog, bundle);
    }
}