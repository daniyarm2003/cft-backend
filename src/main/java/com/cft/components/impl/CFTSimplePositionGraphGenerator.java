package com.cft.components.impl;

import com.cft.components.ICFTPositionGraphGenerator;
import com.cft.entities.CFTEventSnapshotEntry;
import com.cft.entities.Fighter;
import com.cft.repos.CFTEventSnapshotEntryRepo;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.util.List;

@Service
public class CFTSimplePositionGraphGenerator implements ICFTPositionGraphGenerator {

    private static final int GRAPH_WIDTH = 800, GRAPH_HEIGHT = 600;
    private static final int GRAPH_HORIZONTAL_PADDING = 40, GRAPH_VERTICAL_PADDING = 20;

    private static final Color GRAPH_BORDER_COLOR = new Color(0, 0, 0);
    private static final Stroke GRAPH_BORDER_STROKE = new BasicStroke(5.0f);

    private static final Color GRAPH_LINE_COLOR = new Color(0, 128, 255);
    private static final Stroke GRAPH_LINE_STROKE = new BasicStroke(3.0f);

    private static final String GRAPH_FILE_TYPE = "png";

    @Autowired
    private CFTEventSnapshotEntryRepo snapshotEntryRepo;

    private void drawGraph(@NonNull Graphics2D imageGraphics, @NonNull Fighter fighter, List<CFTEventSnapshotEntry> snapshotEntries) {

        // This entity has to exist if this function is being called
        int maxPosition = this.snapshotEntryRepo.findFirstByOrderByPositionDesc().getPosition();

        // Draw line graph
        imageGraphics.setColor(GRAPH_LINE_COLOR);
        imageGraphics.setStroke(GRAPH_LINE_STROKE);

        int graphDrawableWidth = GRAPH_WIDTH - 2 * GRAPH_HORIZONTAL_PADDING;
        int graphDrawableHeight = GRAPH_HEIGHT - 2 * GRAPH_VERTICAL_PADDING;

        for(int i = 0; i < snapshotEntries.size() - 1; i++) {
            float graphLineRelLeftX = (float)graphDrawableWidth * (float)i / (float)snapshotEntries.size();
            float graphLineRelRightX = (float)graphDrawableWidth * (float)(i + 1) / (float)snapshotEntries.size();

            float graphLineRelLeftY = (float)graphDrawableHeight * (float)snapshotEntries.get(i).getPosition() / (float)maxPosition;
            float graphLineRelRightY = (float)graphDrawableHeight * (float)snapshotEntries.get(i + 1).getPosition() / (float)maxPosition;

            imageGraphics.drawLine(GRAPH_HORIZONTAL_PADDING + (int)graphLineRelLeftX, GRAPH_VERTICAL_PADDING + (int)graphLineRelLeftY,
                    GRAPH_HORIZONTAL_PADDING + (int)graphLineRelRightX, GRAPH_VERTICAL_PADDING + (int)graphLineRelRightY);
        }

        // Draw graph border
        imageGraphics.setColor(GRAPH_BORDER_COLOR);
        imageGraphics.setStroke(GRAPH_BORDER_STROKE);

        imageGraphics.drawLine(GRAPH_HORIZONTAL_PADDING, GRAPH_VERTICAL_PADDING,
                GRAPH_HORIZONTAL_PADDING, GRAPH_HEIGHT - GRAPH_VERTICAL_PADDING);

        imageGraphics.drawLine(GRAPH_HORIZONTAL_PADDING, GRAPH_HEIGHT - GRAPH_VERTICAL_PADDING,
                GRAPH_WIDTH - GRAPH_HORIZONTAL_PADDING, GRAPH_HEIGHT - GRAPH_VERTICAL_PADDING);
    }

    @Override
    public Resource generatePositionGraphResource(@NonNull Fighter fighter) throws IOException {
        List<CFTEventSnapshotEntry> snapshotEntries = this.snapshotEntryRepo.findByFighterOrderBySnapshot_SnapshotDateAsc(fighter);

        BufferedImage image = new BufferedImage(GRAPH_WIDTH, GRAPH_HEIGHT, BufferedImage.TYPE_INT_ARGB);

        Graphics2D imageGraphics = image.createGraphics();

        if(snapshotEntries.size() >= 2) {
            this.drawGraph(imageGraphics, fighter, snapshotEntries);
        }

        ByteArrayInputStream graphImageInStream;

        try(ByteArrayOutputStream graphImageOutStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, GRAPH_FILE_TYPE, graphImageOutStream);
            graphImageInStream = new ByteArrayInputStream(graphImageOutStream.toByteArray());
        }

        return new InputStreamResource(graphImageInStream);
    }

    @Override
    public String getImageType() {
        return GRAPH_FILE_TYPE;
    }
}
