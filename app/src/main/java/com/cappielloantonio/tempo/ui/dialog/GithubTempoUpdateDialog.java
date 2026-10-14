package com.cappielloantonio.tempo.ui.dialog;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.DialogGithubTempoUpdateBinding;
import com.cappielloantonio.tempo.github.models.LatestRelease;
import com.cappielloantonio.tempo.util.Preferences;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class GithubTempoUpdateDialog extends DialogFragment {
    private static final String ARG_HTML_URL = "html_url";

    public GithubTempoUpdateDialog() {
    }

    public GithubTempoUpdateDialog(LatestRelease latestRelease) {
        Bundle args = new Bundle();
        args.putString(ARG_HTML_URL, latestRelease != null ? latestRelease.getHtmlUrl() : null);
        setArguments(args);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        DialogGithubTempoUpdateBinding bind = DialogGithubTempoUpdateBinding.inflate(getLayoutInflater());

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity())
                .setView(bind.getRoot())
                .setTitle(R.string.github_update_dialog_title)
                .setPositiveButton(R.string.github_update_dialog_positive_button, (dialog, id) -> { })
                .setNegativeButton(R.string.github_update_dialog_negative_button, (dialog, id) -> { })
                .setNeutralButton(R.string.github_update_dialog_neutral_button, (dialog, id) -> { });

        return builder.create();
    }

    @Override
    public void onStart() {
        super.onStart();

        setButtonAction();
    }

    private void setButtonAction() {
        AlertDialog alertDialog = (AlertDialog) getDialog();

        if (alertDialog == null) {
            return;
        }

        String htmlUrl = requireArguments().getString(ARG_HTML_URL);

        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (htmlUrl != null) {
                openLink(htmlUrl);
            }
            alertDialog.dismiss();
        });

        alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
            Preferences.setTempoUpdateReminder();
            alertDialog.dismiss();
        });

        alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            openLink(getString(R.string.support_url));
            alertDialog.dismiss();
        });
    }

    private void openLink(String link) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
