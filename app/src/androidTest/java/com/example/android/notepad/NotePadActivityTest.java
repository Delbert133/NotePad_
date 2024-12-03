package com.example.android.notepad;

import android.test.ActivityInstrumentationTestCase2;
import com.example.android.notepad.NotesList;

public class NotePadActivityTest extends ActivityInstrumentationTestCase2<NotesList> {

    public NotePadActivityTest() {
        super(NotesList.class);
    }

    public void testActivityTestCaseSetUpProperly() {
        assertNotNull("activity should be launched successfully", getActivity());
    }
}
