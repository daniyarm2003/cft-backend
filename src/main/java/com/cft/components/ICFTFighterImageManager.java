package com.cft.components;

import com.cft.entities.Fighter;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public interface ICFTFighterImageManager {
    FileInputStream getFighterImageFile(Fighter fighter) throws IOException;

    File writeFighterImageFile(MultipartFile inputFile, Fighter fighter) throws IOException;

    boolean deleteFighterImageFile(Fighter fighter);
}
