package com.example.android.notepad;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.test.ProviderTestCase2;
import android.test.mock.MockContentResolver;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;

public class NotePadProviderTest extends ProviderTestCase2<NotePadProvider> {

    private static final Uri INVALID_URI =
        Uri.withAppendedPath(NotePad.Notes.CONTENT_URI, "invalid");

    // Contains a reference to the mocked content resolver for the provider under test.
    private MockContentResolver mMockResolver;

    // Contains an SQLite database, used as test data
    private SQLiteDatabase mDb;

    // Contains the test data, as an array of NoteInfo instances.
    private final NoteInfo[] TEST_NOTES = {
        new NoteInfo("Note0", "This is note 0"),
        new NoteInfo("Note1", "This is note 1"),
        new NoteInfo("Note2", "This is note 2"),
        new NoteInfo("Note3", "This is note 3"),
        new NoteInfo("Note4", "This is note 4"),
        new NoteInfo("Note5", "This is note 5"),
        new NoteInfo("Note6", "This is note 6"),
        new NoteInfo("Note7", "This is note 7"),
        new NoteInfo("Note8", "This is note 8"),
        new NoteInfo("Note9", "This is note 9") };

    // Number of milliseconds in one day (milliseconds * seconds * minutes * hours)
    private static final long ONE_DAY_MILLIS = 1000 * 60 * 60 * 24;

    // Number of milliseconds in one week
    private static final long ONE_WEEK_MILLIS = ONE_DAY_MILLIS * 7;

    // Creates a calendar object equal to January 1, 2010 at 12 midnight
    private static final GregorianCalendar TEST_CALENDAR =
        new GregorianCalendar(2010, Calendar.JANUARY, 1, 0, 0, 0);

    // Stores a timestamp value, set to an arbitrary starting point
    private final static long START_DATE = TEST_CALENDAR.getTimeInMillis();

    // Sets a MIME type filter, used to test provider methods that return more than one MIME type
    // for a particular note. The filter will retrieve any MIME types supported for the content URI.
    private final static String MIME_TYPES_ALL = "*/*";

    private final static String MIME_TYPES_NONE = "qwer/qwer";

    private final static String MIME_TYPE_TEXT = "text/plain";

    public NotePadProviderTest() {
        super(NotePadProvider.class, NotePad.AUTHORITY);
    }

    @Override
    protected void setUp() throws Exception {
        // Calls the base class implementation of this method.
        super.setUp();

        // Gets the resolver for this test.
        mMockResolver = getMockContentResolver();
        mDb = getProvider().getOpenHelperForTest().getWritableDatabase();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    private void insertData() {
        // Creates an instance of the ContentValues map type expected by database insertions
        ContentValues values = new ContentValues();

        // Sets up test data
        for (int index = 0; index < TEST_NOTES.length; index++) {

            // Set the creation and modification date for the note
            TEST_NOTES[index].setCreationDate(START_DATE + (index * ONE_DAY_MILLIS));
            TEST_NOTES[index].setModificationDate(START_DATE + (index * ONE_WEEK_MILLIS));

            // Adds a record to the database.
            mDb.insertOrThrow(
                NotePad.Notes.TABLE_NAME,             // the table name for the insert
                NotePad.Notes.COLUMN_NAME_TITLE,      // column set to null if empty values map
                TEST_NOTES[index].getContentValues()  // the values map to insert
            );
        }
    }

    public void testUriAndGetType() {
        // Tests the MIME type for the notes table URI.
        String mimeType = mMockResolver.getType(NotePad.Notes.CONTENT_URI);
        assertEquals(NotePad.Notes.CONTENT_TYPE, mimeType);

        // Tests the MIME type for the live folder URI.
        mimeType = mMockResolver.getType(NotePad.Notes.LIVE_FOLDER_URI);
        assertEquals(NotePad.Notes.CONTENT_TYPE, mimeType);

        // Creates a URI with a pattern for note ids. The id doesn't have to exist.
        Uri noteIdUri = ContentUris.withAppendedId(NotePad.Notes.CONTENT_ID_URI_BASE, 1);

        // Gets the note ID URI MIME type.
        mimeType = mMockResolver.getType(noteIdUri);
        assertEquals(NotePad.Notes.CONTENT_ITEM_TYPE, mimeType);

        // Tests an invalid URI. This should throw an IllegalArgumentException.
        mimeType = mMockResolver.getType(INVALID_URI);
    }

    public void testGetStreamTypes() {

        // Tests the notes table URI. This should return null, since the content provider does
        // not provide a stream MIME type for multiple notes.
        assertNull(mMockResolver.getStreamTypes(NotePad.Notes.CONTENT_URI, MIME_TYPES_ALL));

        // Tests the live folders URI. This should return null, since the content provider does not
        // provide a stream MIME type for multiple notes.
        assertNull(mMockResolver.getStreamTypes(NotePad.Notes.LIVE_FOLDER_URI, MIME_TYPES_ALL));
        
        Uri testUri = Uri.withAppendedPath(NotePad.Notes.CONTENT_ID_URI_BASE, "1");

        // Gets the MIME types for the URI, with the filter that selects all MIME types.
        String mimeType[] = mMockResolver.getStreamTypes(testUri, MIME_TYPES_ALL);

        // Tests that the result is not null and is equal to the expected value. Also tests that
        // only one MIME type is returned.
        assertNotNull(mimeType);
        assertEquals(mimeType[0],"text/plain");
        assertEquals(mimeType.length,1);

        /*
         * Tests with the same URI but with a filter that should not return any URIs.
         */
        mimeType = mMockResolver.getStreamTypes(testUri, MIME_TYPES_NONE);
        assertNull(mimeType);

   
        mimeType = mMockResolver.getStreamTypes(NotePad.Notes.CONTENT_URI, MIME_TYPES_ALL);
        assertNull(mimeType);

    }

    public void testOpenTypedAssetFile() throws FileNotFoundException, IOException {

        // A URI to contain a note ID content URI.
        Uri testNoteIdUri;

        // A handle for the file descriptor returned by openTypedAssetFile().
        AssetFileDescriptor testAssetDescriptor;

        // Inserts data into the provider, so that the note ID URI will be recognized.
        insertData();

        // Constructs a URI with a note ID of 1. This matches the note ID URI pattern that
        // openTypedAssetFile can handle.
        testNoteIdUri = ContentUris.withAppendedId(NotePad.Notes.CONTENT_ID_URI_BASE, 1);

        // Opens the pipe. The opts argument is for passing options from a caller to the provider,
        // but the NotePadProvider does not use it.
        testAssetDescriptor = mMockResolver.openTypedAssetFileDescriptor(
                testNoteIdUri,         // the URI for a single note. The pipe points to this
                                       // note's data
                MIME_TYPE_TEXT,        // a MIME type of "text/plain"
                null                   // the "opts" argument
        );

        // Gets the parcel file handle from the asset file handle.
        ParcelFileDescriptor testParcelDescriptor = testAssetDescriptor.getParcelFileDescriptor();

        // Gets the file handle from the asset file handle.
        FileDescriptor testDescriptor = testAssetDescriptor.getFileDescriptor();

        // Tests that the asset file handle is not null.
        assertNotNull(testAssetDescriptor);

        // Tests that the parcel file handle is not null.
        assertNotNull(testParcelDescriptor);

        // Tests that the file handle is not null.
        assertNotNull(testDescriptor);

        // Tests that the file handle is valid.
        assertTrue(testDescriptor.valid());

        // Closes the file handles.
        testParcelDescriptor.close();
        testAssetDescriptor.close();

        /*
         * Changes the URI to a notes URI for multiple notes, and re-test. This should fail, since
         * the provider does not support this type of URI. A FileNotFound exception is expected,
         * so call fail() if it does *not* occur.
         */
        try {
            testAssetDescriptor = mMockResolver.openTypedAssetFileDescriptor(
                    NotePad.Notes.CONTENT_URI,
                    MIME_TYPE_TEXT,
                    null
            );
            fail();
        } catch (FileNotFoundException e) {
            // continue
        }

        try {
            testAssetDescriptor = mMockResolver.openTypedAssetFileDescriptor(
                    testNoteIdUri,
                    MIME_TYPES_NONE,
                    null
            );
            fail();
        } catch (FileNotFoundException e) {
            // continue
        }

    }

        for (int index = 0; index < inputData.length; index++) {
            try {
                inputData[index] = bIn.readLine();
            } catch (IOException e) {

                e.printStackTrace();
                fail();
            }
        }

        // Asserts that the first record in the provider (written from TEST_NOTES[0]) has the same
        // note title as the first line retrieved from the pipe.
        assertEquals(TEST_NOTES[0].title, inputData[0]);

        // Asserts that the first record in the provider (written from TEST_NOTES[0]) has the same
        // note contents as the third line retrieved from the pipe.
        assertEquals(TEST_NOTES[0].note, inputData[2]);
    }

    /*
     * Tests the provider's public API for querying data in the table, using the URI for
     * a dataset of records.
     */
    public void testQueriesOnNotesUri() {
        // Defines a projection of column names to return for a query
        final String[] TEST_PROJECTION = {
            NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_NOTE,
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE
        };

        // Defines a selection column for the query. When the selection columns are passed
        // to the query, the selection arguments replace the placeholders.
        final String TITLE_SELECTION = NotePad.Notes.COLUMN_NAME_TITLE + " = " + "?";

        // Defines the selection columns for a query.
        final String SELECTION_COLUMNS =
            TITLE_SELECTION + " OR " + TITLE_SELECTION + " OR " + TITLE_SELECTION;

         // Defines the arguments for the selection columns.
        final String[] SELECTION_ARGS = { "Note0", "Note1", "Note5" };

         // Defines a query sort order
        final String SORT_ORDER = NotePad.Notes.COLUMN_NAME_TITLE + " ASC";

        // Query subtest 1.
        // If there are no records in the table, the returned cursor from a query should be empty.
        Cursor cursor = mMockResolver.query(
            NotePad.Notes.CONTENT_URI,  // the URI for the main data table
            null,                       // no projection, get all columns
            null,                       // no selection criteria, get all records
            null,                       // no selection arguments
            null                        // use default sort order
        );

         // Asserts that the returned cursor contains no records
        assertEquals(0, cursor.getCount());

         // Query subtest 2.
         // If the table contains records, the returned cursor from a query should contain records.

        // Inserts the test data into the provider's underlying data source
        insertData();

        // Gets all the columns for all the rows in the table
        cursor = mMockResolver.query(
            NotePad.Notes.CONTENT_URI,  // the URI for the main data table
            null,                       // no projection, get all columns
            null,                       // no selection criteria, get all records
            null,                       // no selection arguments
            null                        // use default sort order
        );

        // Asserts that the returned cursor contains the same number of rows as the size of the
        // test data array.
        assertEquals(TEST_NOTES.length, cursor.getCount());

        // Query subtest 3.
        // A query that uses a projection should return a cursor with the same number of columns
        // as the projection, with the same names, in the same order.
        Cursor projectionCursor = mMockResolver.query(
              NotePad.Notes.CONTENT_URI,  // the URI for the main data table
              TEST_PROJECTION,            // get the title, note, and mod date columns
              null,                       // no selection columns, get all the records
              null,                       // no selection criteria
              null                        // use default the sort order
        );

        // Asserts that the number of columns in the cursor is the same as in the projection
        assertEquals(TEST_PROJECTION.length, projectionCursor.getColumnCount());

        // Asserts that the names of the columns in the cursor and in the projection are the same.
        // This also verifies that the names are in the same order.
        assertEquals(TEST_PROJECTION[0], projectionCursor.getColumnName(0));
        assertEquals(TEST_PROJECTION[1], projectionCursor.getColumnName(1));
        assertEquals(TEST_PROJECTION[2], projectionCursor.getColumnName(2));

        // Query subtest 4
        // A query that uses selection criteria should return only those rows that match the
        // criteria. Use a projection so that it's easy to get the data in a particular column.
        projectionCursor = mMockResolver.query(
            NotePad.Notes.CONTENT_URI, // the URI for the main data table
            TEST_PROJECTION,           // get the title, note, and mod date columns
            SELECTION_COLUMNS,         // select on the title column
            SELECTION_ARGS,            // select titles "Note0", "Note1", or "Note5"
            SORT_ORDER                 // sort ascending on the title column
        );

        // Asserts that the cursor has the same number of rows as the number of selection arguments
        assertEquals(SELECTION_ARGS.length, projectionCursor.getCount());

        int index = 0;

        while (projectionCursor.moveToNext()) {

            // Asserts that the selection argument at the current index matches the value of
            // the title column (column 0) in the current record of the cursor
            assertEquals(SELECTION_ARGS[index], projectionCursor.getString(0));

            index++;
        }

        // Asserts that the index pointer is now the same as the number of selection arguments, so
        // that the number of arguments tested is exactly the same as the number of rows returned.
        assertEquals(SELECTION_ARGS.length, index);

    }

    /*
     * Tests queries against the provider, using the note id URI. This URI encodes a single
     * record ID. The provider should only return 0 or 1 record.
     */
    public void testQueriesOnNoteIdUri() {
      // Defines the selection column for a query. The "?" is replaced by entries in the
      // selection argument array
      final String SELECTION_COLUMNS = NotePad.Notes.COLUMN_NAME_TITLE + " = " + "?";

      // Defines the argument for the selection column.
      final String[] SELECTION_ARGS = { "Note1" };

      // A sort order for the query.
      final String SORT_ORDER = NotePad.Notes.COLUMN_NAME_TITLE + " ASC";

      // Creates a projection includes the note id column, so that note id can be retrieved.
      final String[] NOTE_ID_PROJECTION = {
           NotePad.Notes._ID,                 // The Notes class extends BaseColumns,
                                              // which includes _ID as the column name for the
                                              // record's id in the data model
           NotePad.Notes.COLUMN_NAME_TITLE};  // The note's title

      Uri noteIdUri = ContentUris.withAppendedId(NotePad.Notes.CONTENT_ID_URI_BASE, 1);

      // Queries the table with the notes ID URI. This should return an empty cursor.
      Cursor cursor = mMockResolver.query(
          noteIdUri, // URI pointing to a single record
          null,      // no projection, get all the columns for each record
          null,      // no selection criteria, get all the records in the table
          null,      // no need for selection arguments
          null       // default sort, by ascending title
      );

      // Asserts that the cursor is null.
      assertEquals(0,cursor.getCount());

      insertData();

      // Queries the table using the URI for the full table.
      cursor = mMockResolver.query(
          NotePad.Notes.CONTENT_URI, // the base URI for the table
          NOTE_ID_PROJECTION,        // returns the ID and title columns of rows
          SELECTION_COLUMNS,         // select based on the title column
          SELECTION_ARGS,            // select title of "Note1"
          SORT_ORDER                 // sort order returned is by title, ascending
      );

      // Asserts that the cursor contains only one row.
      assertEquals(1, cursor.getCount());

      // Moves to the cursor's first row, and asserts that this did not fail.
      assertTrue(cursor.moveToFirst());

      // Saves the record's note ID.
      int inputNoteId = cursor.getInt(0);

      // Builds a URI based on the provider's content ID URI base and the saved note ID.
      noteIdUri = ContentUris.withAppendedId(NotePad.Notes.CONTENT_ID_URI_BASE, inputNoteId);

      // Queries the table using the content ID URI, which returns a single record with the
      // specified note ID, matching the selection criteria provided.
      cursor = mMockResolver.query(noteIdUri, // the URI for a single note
          NOTE_ID_PROJECTION,                 // same projection, get ID and title columns
          SELECTION_COLUMNS,                  // same selection, based on title column
          SELECTION_ARGS,                     // same selection arguments, title = "Note1"
          SORT_ORDER                          // same sort order returned, by title, ascending
      );

      // Asserts that the cursor contains only one row.
      assertEquals(1, cursor.getCount());

      // Moves to the cursor's first row, and asserts that this did not fail.
      assertTrue(cursor.moveToFirst());

      // Asserts that the note ID passed to the provider is the same as the note ID returned.
      assertEquals(inputNoteId, cursor.getInt(0));
    }

    /*
     *  Tests inserts into the data model.
     */
    public void testInserts() {
        // Creates a new note instance with ID of 30.
        NoteInfo note = new NoteInfo(
            "Note30", // the note's title
            "Test inserting a note" // the note's content
        );

        // Sets the note's creation and modification times
        note.setCreationDate(START_DATE + (10 * ONE_DAY_MILLIS));
        note.setModificationDate(START_DATE + (2 * ONE_WEEK_MILLIS));

        // Insert subtest 1.
        // Inserts a row using the new note instance.
        // No assertion will be done. The insert() method either works or throws an Exception
        Uri rowUri = mMockResolver.insert(
            NotePad.Notes.CONTENT_URI,  // the main table URI
            note.getContentValues()     // the map of values to insert as a new record
        );

        // Parses the returned URI to get the note ID of the new note. The ID is used in subtest 2.
        long noteId = ContentUris.parseId(rowUri);

        // Does a full query on the table. Since insertData() hasn't yet been called, the
        // table should only contain the record just inserted.
        Cursor cursor = mMockResolver.query(
            NotePad.Notes.CONTENT_URI, // the main table URI
            null,                      // no projection, return all the columns
            null,                      // no selection criteria, return all the rows in the model
            null,                      // no selection arguments
            null                       // default sort order
        );

        // Asserts that there should be only 1 record.
        assertEquals(1, cursor.getCount());

        // Moves to the first (and only) record in the cursor and asserts that this worked.
        assertTrue(cursor.moveToFirst());

        // Since no projection was used, get the column indexes of the returned columns
        int titleIndex = cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
        int noteIndex = cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
        int crdateIndex = cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_CREATE_DATE);
        int moddateIndex = cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE);

        // Tests each column in the returned cursor against the data that was inserted, comparing
        // the field in the NoteInfo object to the data at the column index in the cursor.
        assertEquals(note.title, cursor.getString(titleIndex));
        assertEquals(note.note, cursor.getString(noteIndex));
        assertEquals(note.createDate, cursor.getLong(crdateIndex));
        assertEquals(note.modDate, cursor.getLong(moddateIndex));

        // Insert subtest 2.
        // Tests that we can't insert a record whose id value already exists.

        // Defines a ContentValues object so that the test can add a note ID to it.
        ContentValues values = note.getContentValues();

        // Adds the note ID retrieved in subtest 1 to the ContentValues object.
        values.put(NotePad.Notes._ID, (int) noteId);

        // Tries to insert this record into the table. This should fail and drop into the
        // catch block. If it succeeds, issue a failure message.
        try {
            rowUri = mMockResolver.insert(NotePad.Notes.CONTENT_URI, values);
            fail("Expected insert failure for existing record but insert succeeded.");
        } catch (Exception e) {
          // succeeded, so do nothing.
        }
    }

    /*
     * Tests deletions from the data model.
     */
    public void testDeletes() {
        // Subtest 1.
        // Tries to delete a record from a data model that is empty.

        // Sets the selection column to "title"
        final String SELECTION_COLUMNS = NotePad.Notes.COLUMN_NAME_TITLE + " = " + "?";

        // Sets the selection argument "Note0"
        final String[] SELECTION_ARGS = { "Note0" };

        // Tries to delete rows matching the selection criteria from the data model.
        int rowsDeleted = mMockResolver.delete(
            NotePad.Notes.CONTENT_URI, // the base URI of the table
            SELECTION_COLUMNS,         // select based on the title column
            SELECTION_ARGS             // select title = "Note0"
        );

        // Assert that the deletion did not work. The number of deleted rows should be zero.
        assertEquals(0, rowsDeleted);

        // Subtest 2.
        // Tries to delete an existing record. Repeats the previous subtest, but inserts data first.

        // Inserts data into the model.
        insertData();

        // Uses the same parameters to try to delete the row with title "Note0"
        rowsDeleted = mMockResolver.delete(
            NotePad.Notes.CONTENT_URI, // the base URI of the table
            SELECTION_COLUMNS,         // same selection column, "title"
            SELECTION_ARGS             // same selection arguments, title = "Note0"
        );

        // The number of deleted rows should be 1.
        assertEquals(1, rowsDeleted);

        // Tests that the record no longer exists. Tries to get it from the table, and
        // asserts that nothing was returned.

        // Queries the table with the same selection column and argument used to delete the row.
        Cursor cursor = mMockResolver.query(
            NotePad.Notes.CONTENT_URI, // the base URI of the table
            null,                      // no projection, return all columns
            SELECTION_COLUMNS,         // select based on the title column
            SELECTION_ARGS,            // select title = "Note0"
            null                       // use the default sort order
        );

        // Asserts that the cursor is empty since the record had already been deleted.
        assertEquals(0, cursor.getCount());
    }

    /*
     * Tests updates to the data model.
     */
    public void testUpdates() {
        // Selection column for identifying a record in the data model.
        final String SELECTION_COLUMNS = NotePad.Notes.COLUMN_NAME_TITLE + " = " + "?";

        // Selection argument for the selection column.
        final String[] selectionArgs = { "Note1" };

        // Defines a map of column names and values
        ContentValues values = new ContentValues();

        // Subtest 1.
        // Tries to update a record in an empty table.

        // Sets up the update by putting the "note" column and a value into the values map.
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, "Testing an update with this string");

        // Tries to update the table
        int rowsUpdated = mMockResolver.update(
            NotePad.Notes.CONTENT_URI,  // the URI of the data table
            values,                     // a map of the updates to do (column title and value)
            SELECTION_COLUMNS,           // select based on the title column
            selectionArgs               // select "title = Note1"
        );

        // Asserts that no rows were updated.
        assertEquals(0, rowsUpdated);

        // Subtest 2.
        // Builds the table, and then tries the update again using the same arguments.

        // Inserts data into the model.
        insertData();

        //  Does the update again, using the same arguments as in subtest 1.
        rowsUpdated = mMockResolver.update(
            NotePad.Notes.CONTENT_URI,   // The URI of the data table
            values,                      // the same map of updates
            SELECTION_COLUMNS,            // same selection, based on the title column
            selectionArgs                // same selection argument, to select "title = Note1"
        );

        // Asserts that only one row was updated. The selection criteria evaluated to
        // "title = Note1", and the test data should only contain one row that matches that.
        assertEquals(1, rowsUpdated);

    }

    // A utility for converting note data to a ContentValues map.
    private static class NoteInfo {
        String title;
        String note;
        long createDate;
        long modDate;

        public NoteInfo(String t, String n) {
            title = t;
            note = n;
            createDate = 0;
            modDate = 0;
        }

        // Sets the creation date for a test note
        public void setCreationDate(long c) {
            createDate = c;
        }

        // Sets the modification date for a test note
        public void setModificationDate(long m) {
            modDate = m;
        }

        /*
         * Returns a ContentValues instance (a map) for this NoteInfo instance. This is useful for
         * inserting a NoteInfo into a database.
         */
        public ContentValues getContentValues() {
            // Gets a new ContentValues object
            ContentValues v = new ContentValues();

            // Adds map entries for the user-controlled fields in the map
            v.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
            v.put(NotePad.Notes.COLUMN_NAME_NOTE, note);
            v.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, createDate);
            v.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, modDate);
            return v;

        }
    }
}
