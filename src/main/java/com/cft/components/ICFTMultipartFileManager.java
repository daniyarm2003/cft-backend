package com.cft.components;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Optional;

public interface ICFTMultipartFileManager {
    FileInputStream getFileInputStream(String name) throws IOException;
    File saveMultipartFile(MultipartFile inputFile, String name) throws IOException;

    boolean deleteFile(String name);
}
