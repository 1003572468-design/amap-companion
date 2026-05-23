package com.autonavi.companion;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.text.TextUtils;

import java.io.File;
import java.io.FileNotFoundException;

public class ApkFileProvider extends ContentProvider {
    static final String AUTHORITY = "com.autonavi.companion.apkfileprovider";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = fileForUri(uri);
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        try {
            File file = fileForUri(uri);
            MatrixCursor cursor = new MatrixCursor(new String[]{
                    OpenableColumns.DISPLAY_NAME,
                    OpenableColumns.SIZE
            });
            cursor.addRow(new Object[]{file.getName(), file.length()});
            return cursor;
        } catch (FileNotFoundException ignored) {
            return null;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        return 0;
    }

    private File fileForUri(Uri uri) throws FileNotFoundException {
        if (getContext() == null || uri == null || !AUTHORITY.equals(uri.getAuthority())) {
            throw new FileNotFoundException("Invalid APK uri");
        }
        String name = uri.getLastPathSegment();
        if (TextUtils.isEmpty(name) || name.contains("/") || name.contains("\\")) {
            throw new FileNotFoundException("Invalid APK name");
        }
        File file = new File(getContext().getCacheDir(), name);
        if (!file.isFile()) {
            throw new FileNotFoundException(file.getAbsolutePath());
        }
        return file;
    }
}
