package com.cft.components;

import com.cft.entities.Fighter;
import com.cft.repos.FighterRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.Optional;
import java.util.UUID;

@Service
@PropertySource("classpath:cft.properties")
public class CFTFileManager {
    private static final String FOLDER_NAME_PROPERTY = "cft.files.folder-name";

    private final File dir;

    @Autowired
    private FighterRepo fighterRepo;

    public CFTFileManager(Environment env) {
        String dirName = env.getProperty(FOLDER_NAME_PROPERTY);

        if(dirName == null)
            throw new IllegalArgumentException("Property %s is missing".formatted(FOLDER_NAME_PROPERTY));

        this.dir = new File(dirName);

        if(this.dir.exists() && !this.dir.isDirectory()) {
            throw new IllegalStateException("File %s must be a directory".formatted(this.dir.getAbsolutePath()));
        }

        else if(!this.dir.exists() && !this.dir.mkdir()) {
            throw new RuntimeException("Unable to create directory %s".formatted(this.dir.getAbsolutePath()));
        }
    }

    public File writeMultipartFile(MultipartFile file, String... fileNamePrefixes) throws IOException {
        File outputFile = new File(this.dir, String.join("", fileNamePrefixes) + file.getOriginalFilename());

        try (FileOutputStream outputFileStream = new FileOutputStream(outputFile);
             InputStream inputFileStream = file.getInputStream()) {

            inputFileStream.transferTo(outputFileStream);
        }

        return outputFile;
    }

    public boolean deleteFile(String fileName) {
        File fileToDelete = new File(this.dir, fileName);
        return fileToDelete.delete();
    }

    public Optional<FileInputStream> getFighterImageFile(Fighter fighter) throws FileNotFoundException {
        File imageFile = new File(this.dir, fighter.getImageFileName());

        if(!imageFile.exists()) {
            return Optional.empty();
        }

        return Optional.of(new FileInputStream(imageFile));
    }

    public boolean writeFighterImageFile(MultipartFile file, Fighter fighter) {
        File newImageFile;

        try {
            newImageFile = this.writeMultipartFile(file, fighter.getName(), UUID.randomUUID().toString());
        }
        catch(IOException ex) {
            ex.printStackTrace();
            return false;
        }

        String newFileName = newImageFile.getName();

        if(fighter.getImageFileName() != null && !this.deleteFile(fighter.getImageFileName())) {
            this.deleteFile(newFileName);
            return false;
        }

        fighter.setImageFileName(newFileName);
        this.fighterRepo.save(fighter);

        return true;
    }
}
