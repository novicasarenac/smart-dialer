package com.smartdialer.directoryFactories;

import android.os.Environment;

import java.io.File;

public final class BaseAlbumDirectoryFactory extends AlbumStorageDirectoryFactory {

    private static final String CAMERA_DIRECTORY = "/dcim/";

    @Override
    public File getAlbumStorageDirectory(String albumName) {
        return new File(Environment.getExternalStorageDirectory() + CAMERA_DIRECTORY + albumName);
    }
}
