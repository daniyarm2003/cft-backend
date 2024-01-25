package com.cft.components;

import com.cft.entities.Fighter;
import com.cft.utils.filelike.IFileLike;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;

public interface ICFTFighterImageManager {
    InputStream getFighterImageFile(Fighter fighter) throws IOException;

    IFileLike writeFighterImageFile(MultipartFile inputFile, Fighter fighter) throws IOException;

    boolean deleteFighterImageFile(Fighter fighter);
}
