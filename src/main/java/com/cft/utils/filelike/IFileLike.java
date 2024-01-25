package com.cft.utils.filelike;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface IFileLike {
    String getName();

    InputStream getInputStream() throws IOException;
    OutputStream getOutputStream() throws IOException;
}
