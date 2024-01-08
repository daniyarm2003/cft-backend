package com.cft.services.impl;

import com.cft.config.GoogleServiceConfig;
import com.cft.entities.CFTEvent;
import com.cft.entities.CFTEventSnapshot;
import com.cft.entities.CFTEventSnapshotEntry;
import com.cft.entities.Fighter;
import com.cft.repos.CFTEventRepo;
import com.cft.repos.CFTEventShapshotRepo;
import com.cft.repos.FighterRepo;
import com.cft.services.ISnapshotService;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Service("mainSnapshotService")
@PropertySource("classpath:cft.properties")
public class SnapshotServiceImpl implements ISnapshotService {

    private static final String SNAPSHOT_DRIVE_FOLDER_ID_PROPERTY = "cft.google.snapshot-folder-id";
    private static final String SNAPSHOT_ROW_INPUT_OPTION = "RAW";

    @Autowired
    private FighterRepo fighterRepo;

    @Autowired
    private CFTEventShapshotRepo snapshotRepo;

    @Autowired
    private CFTEventRepo eventRepo;

    @Autowired
    private Drive driveService;

    @Autowired
    private Sheets sheetsService;

    @Autowired
    private Environment env;

    private CFTEventSnapshotEntry getSnapshotEntryFromFighter(@NonNull Fighter fighter) {
        CFTEventSnapshotEntry entry = new CFTEventSnapshotEntry();

        entry.setFighter(fighter);
        entry.setFighterName(fighter.getName());

        entry.setWins(fighter.getStats().wins());
        entry.setLosses(fighter.getStats().losses());
        entry.setDraws(fighter.getStats().draws());
        entry.setNoContests(fighter.getStats().noContests());
        entry.setNewFighter(fighter.isNewFighter());

        entry.setPosition(fighter.getPosition());
        entry.setPositionChange(fighter.getPositionChange());

        return entry;
    }

    @Override
    public CFTEventSnapshot takeEventSnapshot(@NonNull CFTEvent event) {
        List<Fighter> fighters = this.fighterRepo.findAll();

        CFTEventSnapshot snapshot = new CFTEventSnapshot();
        snapshot.setSnapshotDate(Date.from(Instant.now()));

        CFTEventSnapshot savedSnapshot = this.snapshotRepo.save(snapshot);

        List<CFTEventSnapshotEntry> entries = fighters.stream().map(this::getSnapshotEntryFromFighter).toList();

        savedSnapshot.getSnapshotEntries().addAll(entries);

        CFTEventSnapshot finalSavedSnapshot = savedSnapshot;
        entries.forEach(entry -> entry.setSnapshot(finalSavedSnapshot));

        savedSnapshot.setEvent(event);

        savedSnapshot = this.snapshotRepo.save(savedSnapshot);
        event.setSnapshot(savedSnapshot);

        this.eventRepo.save(event);

        return savedSnapshot;
    }

    @Override
    public CFTEventSnapshot uploadSnapshotToGoogleSheets(@NonNull CFTEventSnapshot snapshot) throws IOException {
        if(this.driveService == null || this.sheetsService == null) {
            throw new IllegalStateException("An error has occurred with Google service initialization");
        }

        String snapshotFolderId = this.env.getProperty(SNAPSHOT_DRIVE_FOLDER_ID_PROPERTY);

        if(snapshotFolderId == null) {
            throw new IllegalStateException("Unable to find drive folder ID in environment");
        }

        CFTEvent event = snapshot.getEvent();
        List<CFTEventSnapshotEntry> entries = snapshot.getSnapshotEntries();

        File newSheetFile = this.driveService.files().create(new File()
                .setName("%s Snapshot".formatted(event.getName()))
                .setMimeType(GoogleServiceConfig.GOOGLE_SHEETS_MIME_TYPE)
                .setParents(Collections.singletonList(snapshotFolderId))).execute();

        Spreadsheet spreadsheet = this.sheetsService.spreadsheets().get(newSheetFile.getId()).execute();
        List<ValueRange> valueRanges = new ArrayList<>();

        List<Object> columnHeaders = List.of("Position", "Name", "Wins", "Draws", "Losses", "No Contests", "Position Change");

        valueRanges.add(new ValueRange().setRange("A1")
                .setValues(Collections.singletonList(columnHeaders)));

        for(CFTEventSnapshotEntry entry : entries) {
            String positionStr = entry.getPosition() != 0 ? String.valueOf(entry.getPosition()) : "C";
            String positionChangeStr;

            if(entry.isNewFighter()) {
                positionChangeStr = "NEW";
            }
            else if(entry.getPositionChange() == 0) {
                positionChangeStr = "-";
            }
            else {
                positionChangeStr = entry.getPositionChange() < 0 ?
                        String.valueOf(entry.getPositionChange()) : "+%d".formatted(entry.getPositionChange());
            }

            List<Object> snapshotRow = List.of(positionStr, entry.getFighterName(), entry.getWins(), entry.getDraws(),
                    entry.getLosses(), entry.getNoContests(), positionChangeStr);

            valueRanges.add(new ValueRange().setRange("A%d".formatted(entry.getPosition() + 2))
                    .setValues(Collections.singletonList(snapshotRow)));
        }

        BatchUpdateValuesRequest updateRequest = new BatchUpdateValuesRequest()
                .setValueInputOption(SNAPSHOT_ROW_INPUT_OPTION).setData(valueRanges);

        this.sheetsService.spreadsheets().values().batchUpdate(spreadsheet.getSpreadsheetId(), updateRequest).execute();

        snapshot.setGoogleSheetURL(spreadsheet.getSpreadsheetUrl());
        snapshot = this.snapshotRepo.save(snapshot);

        return snapshot;
    }
}
