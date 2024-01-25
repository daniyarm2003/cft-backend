package com.cft.components;

import com.cft.utils.filelike.IFileLike;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;

public interface ICFTMultipartFileManager {
    InputStream getFileInputStream(String name) throws IOException;
    IFileLike saveMultipartFile(MultipartFile inputFile, String name) throws IOException;

    boolean deleteFile(String name);
}
