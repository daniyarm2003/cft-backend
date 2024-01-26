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
    private static final int GRAPH_HORIZONTAL_PADDING = 60, GRAPH_VERTICAL_PADDING = 50;

    private static final Color GRAPH_BORDER_COLOR = new Color(0, 0, 0);
    private static final Stroke GRAPH_BORDER_STROKE = new BasicStroke(5.0f);

    private static final Color GRAPH_LINE_COLOR = new Color(0, 128, 255);
    private static final Stroke GRAPH_LINE_STROKE = new BasicStroke(3.0f);

    private static final Stroke GRAPH_TICK_MARK_STROKE = new BasicStroke(1.5f);
    private static final int GRAPH_TICK_MARK_SIZE = 10;

    private static final int MAX_NUM_HORIZONTAL_TICK_MARKS = 5, MAX_NUM_VERTICAL_TICK_MARKS = 10;

    private static final String GRAPH_FILE_TYPE = "png";

    @Autowired
    private CFTEventSnapshotEntryRepo snapshotEntryRepo;

    private void drawLineGraph(@NonNull Graphics2D imageGraphics, List<CFTEventSnapshotEntry> snapshotEntries, int maxPosition) {

        // This entity has to exist if this function is being called

        imageGraphics.setColor(GRAPH_LINE_COLOR);
        imageGraphics.setStroke(GRAPH_LINE_STROKE);

        int graphDrawableWidth = GRAPH_WIDTH - 2 * GRAPH_HORIZONTAL_PADDING;
        int graphDrawableHeight = GRAPH_HEIGHT - 2 * GRAPH_VERTICAL_PADDING;

        for(int i = 0; i < snapshotEntries.size() - 1; i++) {
            float graphLineRelLeftX = (float)graphDrawableWidth * (float)i / (float)(snapshotEntries.size() - 1);
            float graphLineRelRightX = (float)graphDrawableWidth * (float)(i + 1) / (float)(snapshotEntries.size() - 1);

            float graphLineRelLeftY = (float)graphDrawableHeight * (float)snapshotEntries.get(i).getPosition() / (float)maxPosition;
            float graphLineRelRightY = (float)graphDrawableHeight * (float)snapshotEntries.get(i + 1).getPosition() / (float)maxPosition;

            imageGraphics.drawLine(GRAPH_HORIZONTAL_PADDING + (int)graphLineRelLeftX, GRAPH_VERTICAL_PADDING + (int)graphLineRelLeftY,
                    GRAPH_HORIZONTAL_PADDING + (int)graphLineRelRightX, GRAPH_VERTICAL_PADDING + (int)graphLineRelRightY);
        }
    }

    private void drawGraphBorder(@NonNull Graphics2D imageGraphics) {
        imageGraphics.setColor(GRAPH_BORDER_COLOR);
        imageGraphics.setStroke(GRAPH_BORDER_STROKE);

        imageGraphics.drawLine(GRAPH_HORIZONTAL_PADDING, GRAPH_VERTICAL_PADDING,
                GRAPH_HORIZONTAL_PADDING, GRAPH_HEIGHT - GRAPH_VERTICAL_PADDING);

        imageGraphics.drawLine(GRAPH_HORIZONTAL_PADDING, GRAPH_HEIGHT - GRAPH_VERTICAL_PADDING,
                GRAPH_WIDTH - GRAPH_HORIZONTAL_PADDING, GRAPH_HEIGHT - GRAPH_VERTICAL_PADDING);
    }

    private void drawHorizontalAxis(@NonNull Graphics2D imageGraphics, List<CFTEventSnapshotEntry> snapshotEntries) {
        imageGraphics.setColor(GRAPH_BORDER_COLOR);
        imageGraphics.setStroke(GRAPH_TICK_MARK_STROKE);

        int numTickMarks = Math.min(snapshotEntries.size(), MAX_NUM_HORIZONTAL_TICK_MARKS);

        for(int i = 0; i < numTickMarks; i++) {
            float lerpVal = (float)i / (float)(numTickMarks - 1);
            int snapshotIndex = (int)(lerpVal * (snapshotEntries.size() - 1));

            String eventName = snapshotEntries.get(snapshotIndex).getSnapshot().getEvent().getName();

            int tickMarkLeftLimit = GRAPH_HORIZONTAL_PADDING;
            int tickMarkRightLimit = GRAPH_WIDTH - GRAPH_HORIZONTAL_PADDING;

            int tickMarkX = (int)((float)tickMarkLeftLimit + lerpVal * (tickMarkRightLimit - tickMarkLeftLimit));

            imageGraphics.drawLine(tickMarkX, GRAPH_HEIGHT - GRAPH_VERTICAL_PADDING,
                    tickMarkX, GRAPH_HEIGHT - GRAPH_VERTICAL_PADDING + GRAPH_TICK_MARK_SIZE);

            FontMetrics fontMetrics = imageGraphics.getFontMetrics();

            int fontHeight = fontMetrics.getHeight();

            imageGraphics.drawString(eventName, tickMarkX, GRAPH_HEIGHT - GRAPH_VERTICAL_PADDING + GRAPH_TICK_MARK_SIZE + fontHeight);
        }
    }

    private void drawVerticalTickMarks(@NonNull Graphics2D imageGraphics, int maxPosition) {
        int positionIncreaseRate = maxPosition / MAX_NUM_VERTICAL_TICK_MARKS;

        // If more than the max number of ticks can be drawn, increment the fighter position increase rate
        int remainingPositions = maxPosition - positionIncreaseRate * MAX_NUM_VERTICAL_TICK_MARKS;
        if(remainingPositions >= positionIncreaseRate) {
            positionIncreaseRate++;
        }

        for(int position = 0; position <= maxPosition; position += positionIncreaseRate) {
            float lerpVal = (float)position / (float)maxPosition;

            int tickMarkTopLimit = GRAPH_VERTICAL_PADDING;
            int tickMarkBottomLimit = GRAPH_HEIGHT - GRAPH_VERTICAL_PADDING;

            int tickMarkY = (int)((float)tickMarkTopLimit + lerpVal * (tickMarkBottomLimit - tickMarkTopLimit));

            imageGraphics.drawLine(GRAPH_HORIZONTAL_PADDING - GRAPH_TICK_MARK_SIZE, tickMarkY,
                    GRAPH_HORIZONTAL_PADDING, tickMarkY);

            String positionText = position != 0 ? String.valueOf(position) : "C";
            FontMetrics fontMetrics = imageGraphics.getFontMetrics();

            int positionTextWidth = fontMetrics.stringWidth(positionText);

            imageGraphics.drawString(positionText, GRAPH_HORIZONTAL_PADDING - GRAPH_TICK_MARK_SIZE - positionTextWidth,
                    tickMarkY);
        }
    }

    private void drawGraph(@NonNull Graphics2D imageGraphics, List<CFTEventSnapshotEntry> snapshotEntries) {
        // Add one to make bottom position visible on graph (rather than covered by border)
        int maxPosition = this.snapshotEntryRepo.findFirstByOrderByPositionDesc().getPosition() + 1;

        this.drawLineGraph(imageGraphics, snapshotEntries, maxPosition);
        this.drawGraphBorder(imageGraphics);
        this.drawHorizontalAxis(imageGraphics, snapshotEntries);
        this.drawVerticalTickMarks(imageGraphics, maxPosition);
    }

    @Override
    public Resource generatePositionGraphResource(@NonNull Fighter fighter) throws IOException {
        List<CFTEventSnapshotEntry> snapshotEntries = this.snapshotEntryRepo.findByFighterOrderBySnapshot_SnapshotDateAsc(fighter);

        BufferedImage image = new BufferedImage(GRAPH_WIDTH, GRAPH_HEIGHT, BufferedImage.TYPE_INT_ARGB);

        Graphics2D imageGraphics = image.createGraphics();

        if(snapshotEntries.size() >= 2) {
            this.drawGraph(imageGraphics, snapshotEntries);
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
