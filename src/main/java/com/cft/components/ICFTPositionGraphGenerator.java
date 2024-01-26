package com.cft.components;

import com.cft.entities.Fighter;
import lombok.NonNull;
import org.springframework.core.io.Resource;

import java.io.IOException;

public interface ICFTPositionGraphGenerator {
    Resource generatePositionGraphResource(@NonNull Fighter fighter) throws IOException;
    String getImageType();
}
