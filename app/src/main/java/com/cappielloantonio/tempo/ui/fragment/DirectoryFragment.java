package com.cappielloantonio.tempo.ui.fragment;

import android.content.ComponentName;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.FragmentDirectoryBinding;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.interfaces.DialogClickCallback;
import com.cappielloantonio.tempo.model.Download;
import com.cappielloantonio.tempo.repository.DirectoryRepository;
import com.cappielloantonio.tempo.service.MediaManager;
import com.cappielloantonio.tempo.service.MediaService;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.Directory;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.adapter.MusicDirectoryAdapter;
import com.cappielloantonio.tempo.ui.dialog.DownloadDirectoryDialog;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.DirectorySortUtil;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.MappingUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.viewmodel.DirectoryViewModel;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.stream.Collectors;

@UnstableApi
public class DirectoryFragment extends Fragment implements ClickCallback {
    private static final String TAG = "DirectoryFragment";

    private FragmentDirectoryBinding bind;
    private MainActivity activity;
    private DirectoryViewModel directoryViewModel;

    private MusicDirectoryAdapter musicDirectoryAdapter;

    private Directory directory;

    private DirectoryRepository.SongsRequest subfoldersRequest;

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    private MenuItem menuItem;

    private String currentSort = Constants.DIRECTORY_ORDER_BY_NAME;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.directory_page_menu, menu);

        menuItem = menu.getItem(0);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentDirectoryBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        directoryViewModel = new ViewModelProvider(requireActivity()).get(DirectoryViewModel.class);
        currentSort = Preferences.getDirectorySortOrder();

        initAppBar();
        initSubfoldersPlayButton();
        initSortButton();
        initDirectoryListView();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        initializeMediaBrowser();
    }

    @Override
    public void onStop() {
        releaseMediaBrowser();
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();

        // L'ordinamento può essere cambiato altrove (impostazioni, un'altra directory): si
        // riallinea la lista già aperta invece di aspettare la prossima navigazione.
        if (bind == null || directory == null) return;

        String preferredSort = Preferences.getDirectorySortOrder();

        if (preferredSort.equals(currentSort)) return;

        currentSort = preferredSort;

        List<Child> children = DirectorySortUtil.sort(directory.getChildren(), currentSort);

        musicDirectoryAdapter.setItems(children);
        updateAlphabetIndex(children);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelSubfoldersRequest();
        bind = null;
        directory = null;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_download_directory) {
            DownloadDirectoryDialog dialog = new DownloadDirectoryDialog(new DialogClickCallback() {
                @Override
                public void onPositiveClick() {
                    directoryViewModel.loadMusicDirectory(getArguments().getString(Constants.MUSIC_DIRECTORY_ID)).observe(getViewLifecycleOwner(), directory -> {
                        if (isVisible() && getActivity() != null && directory != null && directory.getChildren() != null) {
                            List<Child> songs = directory.getChildren().stream().filter(child -> !child.isDir()).collect(Collectors.toList());
                            DownloadUtil.getDownloadTracker(requireContext()).download(
                                    MappingUtil.mapDownloads(songs),
                                    songs.stream().map(Download::new).collect(Collectors.toList())
                            );
                        }
                    });
                }
            });

            dialog.show(activity.getSupportFragmentManager(), null);

            return true;
        }

        return false;
    }

    private void initAppBar() {
        activity.setSupportActionBar(bind.toolbar);

        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        if (bind != null) {
            bind.toolbar.setNavigationOnClickListener(v -> activity.navController.navigateUp());
            bind.directoryBackImageView.setOnClickListener(v -> activity.navController.navigateUp());

            bind.appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
                if ((bind.directoryInfoSector.getHeight() + verticalOffset) < (2 * ViewCompat.getMinimumHeight(bind.toolbar))) {
                    bind.toolbar.setTitle(directory != null ? directory.getName() : getString(R.string.empty_string));
                } else {
                    bind.toolbar.setTitle(R.string.empty_string);
                }
            });
        }
    }

    private void initDirectoryListView() {
        bind.directoryRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.directoryRecyclerView.setHasFixedSize(true);

        musicDirectoryAdapter = new MusicDirectoryAdapter(this);
        bind.directoryRecyclerView.setAdapter(musicDirectoryAdapter);

        bind.directoryAlphabetIndexBar.setOnPositionSelectedListener(this::scrollToPosition);
        bind.directoryAlphabetIndexBar.watchScroll(bind.directoryRecyclerView);
        directoryViewModel.loadMusicDirectory(getArguments().getString(Constants.MUSIC_DIRECTORY_ID)).observe(getViewLifecycleOwner(), directory -> {
            if (directory == null) {
                return;
            }

            cancelSubfoldersRequest();

            this.directory = directory;
            bind.directoryTitleLabel.setText(directory.getName());
            bind.directorySubfoldersPlayButton.setVisibility(hasSubDirectories(directory) ? View.VISIBLE : View.GONE);
            bind.directorySubfoldersPlayButton.setEnabled(true);

            List<Child> children = DirectorySortUtil.sort(directory.getChildren(), currentSort);

            musicDirectoryAdapter.setItems(children);
            updateAlphabetIndex(children);

            if (menuItem != null) {
                menuItem.setVisible(
                        directory.getChildren() != null && directory.getChildren()
                                .stream()
                                .filter(child -> !child.isDir())
                                .findFirst()
                                .orElse(null) != null
                );
            }
        });
    }

    private void scrollToPosition(int position) {
        if (bind == null) return;

        RecyclerView.LayoutManager layoutManager = bind.directoryRecyclerView.getLayoutManager();

        if (layoutManager instanceof LinearLayoutManager) {
            ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(position, 0);
        }
    }

    private void initSortButton() {
        bind.directorySortButton.setOnClickListener(view -> {
            PopupMenu popup = new PopupMenu(requireContext(), view);
            popup.getMenuInflater().inflate(R.menu.sort_directory_popup_menu, popup.getMenu());

            MenuItem checkedItem = popup.getMenu().findItem(menuItemIdForSort(currentSort));

            if (checkedItem != null) checkedItem.setChecked(true);

            popup.setOnMenuItemClickListener(menuItem -> {
                if (menuItem.getItemId() == R.id.menu_directory_sort_name) {
                    sortBy(Constants.DIRECTORY_ORDER_BY_NAME);
                    return true;
                } else if (menuItem.getItemId() == R.id.menu_directory_sort_name_desc) {
                    sortBy(Constants.DIRECTORY_ORDER_BY_NAME_DESC);
                    return true;
                } else if (menuItem.getItemId() == R.id.menu_directory_sort_recently_added) {
                    sortBy(Constants.DIRECTORY_ORDER_BY_RECENTLY_ADDED);
                    return true;
                } else if (menuItem.getItemId() == R.id.menu_directory_sort_least_recently_added) {
                    sortBy(Constants.DIRECTORY_ORDER_BY_LEAST_RECENTLY_ADDED);
                    return true;
                } else if (menuItem.getItemId() == R.id.menu_directory_sort_most_played) {
                    sortBy(Constants.DIRECTORY_ORDER_BY_MOST_PLAYED);
                    return true;
                }

                return false;
            });

            popup.show();
        });
    }

    private int menuItemIdForSort(String order) {
        switch (order) {
            case Constants.DIRECTORY_ORDER_BY_NAME_DESC:
                return R.id.menu_directory_sort_name_desc;
            case Constants.DIRECTORY_ORDER_BY_RECENTLY_ADDED:
                return R.id.menu_directory_sort_recently_added;
            case Constants.DIRECTORY_ORDER_BY_LEAST_RECENTLY_ADDED:
                return R.id.menu_directory_sort_least_recently_added;
            case Constants.DIRECTORY_ORDER_BY_MOST_PLAYED:
                return R.id.menu_directory_sort_most_played;
            default:
                return R.id.menu_directory_sort_name;
        }
    }

    private void sortBy(String order) {
        currentSort = order;
        Preferences.setDirectorySortOrder(order);

        if (bind == null || directory == null) return;

        List<Child> children = DirectorySortUtil.sort(directory.getChildren(), order);

        musicDirectoryAdapter.setItems(children);
        updateAlphabetIndex(children);
    }

    private void updateAlphabetIndex(List<Child> children) {
        if (bind == null) return;

        if (!DirectorySortUtil.isAlphabetical(currentSort)) {
            bind.directoryAlphabetIndexBar.setTitles(null);
            return;
        }

        bind.directoryAlphabetIndexBar.setTitles(
                children != null
                        ? children.stream().map(Child::getTitle).collect(Collectors.toList())
                        : null
        );
    }

    private void initSubfoldersPlayButton() {
        bind.directorySubfoldersPlayButton.setOnClickListener(v -> playDirectoryIncludingSubfolders());
    }

    private void playDirectoryIncludingSubfolders() {
        if (bind == null || directory == null) return;

        bind.directorySubfoldersPlayButton.setEnabled(false);

        cancelSubfoldersRequest();

        subfoldersRequest = directoryViewModel.getSongsIncludingSubfolders(directory, Constants.DIRECTORY_SUBFOLDERS_MAX_DEPTH, songs -> {
            subfoldersRequest = null;

            if (bind == null) return;

            bind.directorySubfoldersPlayButton.setEnabled(true);

            if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                return;
            }

            if (songs == null || songs.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.directory_page_no_playable_songs), Toast.LENGTH_SHORT).show();
                return;
            }

            MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
        });
    }

    private void cancelSubfoldersRequest() {
        if (subfoldersRequest != null) {
            subfoldersRequest.cancel();
            subfoldersRequest = null;
        }
    }

    private boolean hasSubDirectories(Directory directory) {
        return directory.getChildren() != null && directory.getChildren().stream().anyMatch(Child::isDir);
    }

    private void initializeMediaBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseMediaBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

    @Override
    public void onMediaClick(Bundle bundle) {
        MediaManager.startQueue(mediaBrowserListenableFuture, bundle.getParcelableArrayList(Constants.TRACKS_OBJECT), bundle.getInt(Constants.ITEM_POSITION));
    }

    @Override
    public void onMediaLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.songBottomSheetDialog, bundle);
    }

    @Override
    public void onMusicDirectoryClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.directoryFragment, bundle);
    }
}