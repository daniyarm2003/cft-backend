package com.cft.components.impl;

import com.cft.components.ICFTMultipartFileManager;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;

@Service
@PropertySource("classpath:cft.properties")
public class CFTSimpleMultipartFileManager implements ICFTMultipartFileManager {
    private static final String FOLDER_NAME_PROPERTY = "cft.files.folder-name";

    private final File directory;

    public CFTSimpleMultipartFileManager(Environment env) {
        String dirPath = env.getProperty(FOLDER_NAME_PROPERTY);

        if(dirPath == null) {
            throw new IllegalArgumentException("ERROR: Environment variable %s not set"
                    .formatted(FOLDER_NAME_PROPERTY));
        }

        this.directory = new File(dirPath);

        if(!this.directory.isDirectory()) {
            throw new IllegalStateException("ERROR: %s must be a directory".formatted(this.directory.getPath()));
        }

        else if(!this.directory.exists() && !this.directory.mkdir()) {
            throw new RuntimeException("ERROR: Unable to create directory %s".formatted(this.directory.getPath()));
        }
    }

    @Override
    public FileInputStream getFileInputStream(String name) throws IOException {
        File inputFile = new File(this.directory, name);

        if(!inputFile.exists()) {
            throw new FileNotFoundException("File %s does not exist".formatted(name));
        }

        return new FileInputStream(inputFile);
    }

    @Override
    public File saveMultipartFile(MultipartFile inputFile, String name) throws IOException {
        File outputFile = new File(this.directory, name);

        try(FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
                InputStream multipartFileInputStream = inputFile.getInputStream()) {

            multipartFileInputStream.transferTo(fileOutputStream);
        }

        return outputFile;
    }

    @Override
    public boolean deleteFile(String name) {
        File fileToDelete = new File(this.directory, name);
        return fileToDelete.delete();
    }
}
