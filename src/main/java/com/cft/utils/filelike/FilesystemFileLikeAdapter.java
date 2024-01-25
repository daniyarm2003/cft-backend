package com.cft.utils.filelike;

import java.io.*;

public class FilesystemFileLikeAdapter implements IFileLike {

    private final File file;

    public FilesystemFileLikeAdapter(File file) {
        this.file = file;
    }

    @Override
    public String getName() {
        return this.file.getName();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new FileInputStream(this.file);
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return new FileOutputStream(this.file);
    }
}
