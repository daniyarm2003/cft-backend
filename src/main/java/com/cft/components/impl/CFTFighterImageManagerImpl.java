package com.cft.components.impl;

import com.cft.components.ICFTFighterImageManager;
import com.cft.components.ICFTMultipartFileManager;
import com.cft.entities.Fighter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.UUID;

@Service
public class CFTFighterImageManagerImpl implements ICFTFighterImageManager {
    @Autowired
    private ICFTMultipartFileManager fileManager;

    @Override
    public FileInputStream getFighterImageFile(Fighter fighter) throws IOException {
        return this.fileManager.getFileInputStream(fighter.getImageFileName());
    }

    @Override
    public File writeFighterImageFile(MultipartFile inputFile, Fighter fighter) throws IOException {
        String fileName = fighter.getName() + UUID.randomUUID() + inputFile.getOriginalFilename();
        return this.fileManager.saveMultipartFile(inputFile, fileName);
    }

    @Override
    public boolean deleteFighterImageFile(Fighter fighter) {
        return this.fileManager.deleteFile(fighter.getImageFileName());
    }
}
