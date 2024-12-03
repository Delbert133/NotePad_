package com.example.android.notepad;

import com.example.android.notepad.NotePad;

import android.app.Activity;
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.os.Bundle;
import android.provider.LiveFolders;

public class NotesLiveFolder extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        final String action = intent.getAction();

        if (LiveFolders.ACTION_CREATE_LIVE_FOLDER.equals(action)) {

            // Creates a new Intent.
            final Intent liveFolderIntent = new Intent();

            /*
             * The following statements put data into the outgoing Intent. Please see
             * {@link android.provider.LiveFolders for a detailed description of these
             * data values. From this data, HOME sets up a live folder.
             */
            // Sets the URI pattern for the content provider backing the folder.
            liveFolderIntent.setData(NotePad.Notes.LIVE_FOLDER_URI);

            // Adds the display name of the live folder as an Extra string.
            String foldername = getString(R.string.live_folder_name);
            liveFolderIntent.putExtra(LiveFolders.EXTRA_LIVE_FOLDER_NAME, foldername);

            // Adds the display icon of the live folder as an Extra resource.
            ShortcutIconResource foldericon =
                Intent.ShortcutIconResource.fromContext(this, R.drawable.live_folder_notes);
            liveFolderIntent.putExtra(LiveFolders.EXTRA_LIVE_FOLDER_ICON, foldericon);

            // Add the display mode of the live folder as an integer. The specified
            // mode causes the live folder to display as a list.
            liveFolderIntent.putExtra(
                    LiveFolders.EXTRA_LIVE_FOLDER_DISPLAY_MODE,
                    LiveFolders.DISPLAY_MODE_LIST);

            Intent returnIntent
                    = new Intent(Intent.ACTION_EDIT, NotePad.Notes.CONTENT_ID_URI_PATTERN);
            liveFolderIntent.putExtra(LiveFolders.EXTRA_LIVE_FOLDER_BASE_INTENT, returnIntent);

            /* Creates an ActivityResult object to propagate back to HOME. Set its result indicator
             * to OK, and sets the returned Intent to the live folder Intent that was just
             * constructed.
             */
            setResult(RESULT_OK, liveFolderIntent);

        } else {

            // If the original action was not ACTION_CREATE_LIVE_FOLDER, creates an
            // ActivityResult with the indicator set to CANCELED, but do not return an Intent
            setResult(RESULT_CANCELED);
        }

        // Closes the Activity. The ActivityObject is propagated back to the caller.
        finish();
    }
}
