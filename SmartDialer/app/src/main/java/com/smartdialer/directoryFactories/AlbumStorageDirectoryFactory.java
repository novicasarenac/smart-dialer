package com.smartdialer.directoryFactories;

import java.io.File;

public abstract class AlbumStorageDirectoryFactory {
    public abstract File getAlbumStorageDirectory(String albumName);
}
