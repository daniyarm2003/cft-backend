package com.cft.api;

import com.cft.components.CFTFileManager;
import com.cft.config.GoogleServiceConfig;
import com.cft.entities.CFTEvent;
import com.cft.entities.DeletedFighter;
import com.cft.entities.Fight;
import com.cft.entities.Fighter;
import com.cft.entities.ws.SimpleWSUpdate;
import com.cft.repos.CFTEventRepo;
import com.cft.repos.DeletedFighterRepo;
import com.cft.repos.FightRepo;
import com.cft.repos.FighterRepo;
import com.cft.utils.ws.WebSocketMessageHelper;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import lombok.NonNull;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.util.MimeType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URI;
import java.util.*;

@RestController
@CrossOrigin
public class FighterController {

    public static final long MAX_FIGHTER_IMAGE_FILE_SIZE = 5 * 1024 * 1024;

    @Autowired
    private FighterRepo fighterRepo;

    @Autowired
    private FightRepo fightRepo;

    @Autowired
    private CFTEventRepo eventRepo;

    @Autowired
    private DeletedFighterRepo deletedFighterRepo;

    @Autowired
    private SimpMessagingTemplate wsTemplate;

    @Autowired
    private CFTFileManager fileManager;

    @Autowired
    private Sheets sheetsService;

    @Autowired
    private Drive driveService;

    @GetMapping("/api/fighters")
    public List<Fighter> getFighters() {
        return this.fighterRepo.findAll(Sort.by(Sort.Direction.ASC, "position"));
    }

    @GetMapping("/api/fighters/{uuid}")
    public Fighter getFighter(@PathVariable UUID uuid) {
        return this.fighterRepo.findById(uuid).orElseThrow();
    }

    @PostMapping("/api/fighters")
    public Fighter addFighter(@RequestBody Fighter fighter) {
        Fighter newFighter = this.fighterRepo.save(fighter);
        WebSocketMessageHelper.sendFighterUpdate(this.wsTemplate, SimpleWSUpdate.UpdateOrigin.FIGHTERS,
                SimpleWSUpdate.UpdateType.POST, newFighter);

        return newFighter;
    }

    @PutMapping("/api/fighters/{uuid}")
    public Fighter updateFighter(@PathVariable UUID uuid, @RequestBody Fighter updated) {
        Fighter fighter = this.fighterRepo.findById(uuid).orElseThrow();

        fighter.setName(updated.getName());

        if(fighter.getPosition() != updated.getPosition()) {
            this.fighterRepo.findAll().stream()
                    .filter(otherFighter -> !otherFighter.equals(fighter)
                            && otherFighter.getPosition() == updated.getPosition())
                    .forEach(otherFighter -> {
                        otherFighter.setPosition(fighter.getPosition());

                        Fighter savedFighter = this.fighterRepo.save(otherFighter);
                        WebSocketMessageHelper.sendFighterUpdate(this.wsTemplate, SimpleWSUpdate.UpdateOrigin.FIGHTERS,
                                SimpleWSUpdate.UpdateType.PUT, savedFighter);
                    });

            fighter.setPosition(updated.getPosition());
        }

        fighter.setLocation(updated.getLocation());
        fighter.setTeam(updated.getTeam());

        fighter.setHeightInBlocks(updated.getHeightInBlocks());
        fighter.setLengthInBlocks(updated.getLengthInBlocks());

        Fighter savedFighter = this.fighterRepo.save(fighter);
        WebSocketMessageHelper.sendFighterUpdate(this.wsTemplate, SimpleWSUpdate.UpdateOrigin.FIGHTERS,
                SimpleWSUpdate.UpdateType.PUT, savedFighter);

        return savedFighter;
    }

    private void deleteFighter(@NonNull Fighter fighter) {

        if(fighter.getImageFileName() != null && !this.fileManager.deleteFile(fighter.getImageFileName())) {
            System.err.printf("Unable to delete file \"%s\".%n", fighter.getImageFileName());
        }

        CFTEvent firstEvent, lastEvent;

        if(fighter.getFights().isEmpty()) {
            List<CFTEvent> events = this.eventRepo.findAll(Sort.by(Sort.Direction.ASC, "date"));
            firstEvent = lastEvent = events.isEmpty() ? null : events.get(events.size() - 1);
        }
        else {
            firstEvent = Collections.min(fighter.getFights().stream().map(Fight::getEvent).toList(),
                    Comparator.comparing(CFTEvent::getDate));

            lastEvent = Collections.max(fighter.getFights().stream().map(Fight::getEvent).toList(),
                    Comparator.comparing(CFTEvent::getDate));
        }

        if(firstEvent != null && lastEvent != null) {
            DeletedFighter deletedFighter = new DeletedFighter();

            deletedFighter.setFighterName(fighter.getName());
            deletedFighter.setDebutEvent(firstEvent);
            deletedFighter.setFinalEvent(lastEvent);

            this.deletedFighterRepo.save(deletedFighter);
        }

        Set<Fight> toDelete = new HashSet<>();

        fighter.getFights().forEach(fight -> {
            fight.getFighters().removeIf(fightFighter -> fightFighter.equals(fighter));

            if(fighter.equals(fight.getWinner()))
                fight.setWinner(null);

            if(fight.getFighters().isEmpty()) {
                toDelete.add(this.fightRepo.save(fight));
                return;
            }

            Fight savedFight = this.fightRepo.save(fight);

            WebSocketMessageHelper.sendFightUpdate(this.wsTemplate, SimpleWSUpdate.UpdateOrigin.FIGHTERS,
                    SimpleWSUpdate.UpdateType.PUT, savedFight);
        });

        fighter.getFights().clear();

        fighter.getSnapshotEntries().forEach(entry -> {
            entry.setFighter(null);
        });

        this.fighterRepo.save(fighter);

        toDelete.forEach(fight -> {
            this.fightRepo.delete(fight);

            WebSocketMessageHelper.sendFightUpdate(this.wsTemplate, SimpleWSUpdate.UpdateOrigin.FIGHTERS,
                    SimpleWSUpdate.UpdateType.DELETE, fight);
        });

        this.fighterRepo.delete(fighter);

        this.fighterRepo.findAll().stream().filter(otherFighter -> otherFighter.getPosition() > fighter.getPosition())
                .forEach(otherFighter -> {
                    otherFighter.setPosition(otherFighter.getPosition() - 1);

                    Fighter savedFighter = this.fighterRepo.save(otherFighter);
                    WebSocketMessageHelper.sendFighterUpdate(this.wsTemplate, SimpleWSUpdate.UpdateOrigin.FIGHTERS,
                            SimpleWSUpdate.UpdateType.PUT, savedFighter);
                });

        WebSocketMessageHelper.sendFighterUpdate(this.wsTemplate, SimpleWSUpdate.UpdateOrigin.FIGHTERS,
                SimpleWSUpdate.UpdateType.DELETE, fighter);
    }

    @DeleteMapping("/api/fighters")
    public void deleteAllFighters() {
        this.fighterRepo.findAll().forEach(this::deleteFighter);
    }

    @DeleteMapping("/api/fighters/{uuid}")
    public void deleteFighter(@PathVariable UUID uuid) {
        Fighter fighter = this.fighterRepo.findById(uuid).orElseThrow();
        this.deleteFighter(fighter);
    }

    @GetMapping("/api/fighters/{uuid}/fights")
    public Set<Fight> getFights(@PathVariable UUID uuid) {
        Fighter fighter = this.fighterRepo.findById(uuid).orElseThrow();
        return fighter.getFights();
    }

    @GetMapping("/api/fighters/champion")
    public Fighter getChampion() {
        List<Fighter> fighters = this.getFighters();
        return fighters.isEmpty() ? null : fighters.get(0);
    }

    @PostMapping("/api/fighters/snapshots")
    public ResponseEntity<?> addSnapshot() throws IOException {
        List<Fighter> fighters = this.fighterRepo.findAll();

        if(this.driveService == null || this.sheetsService == null)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error has occurred with Google service initialization");

        List<CFTEvent> events = this.eventRepo.findAll(Sort.by(Sort.Direction.ASC, "date"));
        CFTEvent curEvent = events.isEmpty() ? null : events.get(events.size() - 1);

        File newSheetFile = this.driveService.files().create(new File()
                .setName("%s Snapshot".formatted(curEvent == null ? "No Event" : curEvent.getName()))
                .setMimeType(GoogleServiceConfig.GOOGLE_SHEETS_MIME_TYPE)
                .setParents(Collections.singletonList(GoogleServiceConfig.SNAPSHOT_DRIVE_FOLDER_ID))).execute();

        Spreadsheet newSheet = this.sheetsService.spreadsheets().get(newSheetFile.getId()).execute();

        List<ValueRange> ranges = new ArrayList<>();

        ranges.add(new ValueRange().setRange("A1").setValues(Collections.singletonList(
                List.of("Position", "Name", "Wins", "Draws", "Losses", "No Contests", "Position Change")
        )));

        ranges.addAll(fighters.stream().map(fighter -> new ValueRange().setRange("A%d".formatted(2 + fighter.getPosition()))
                .setValues(Collections.singletonList(
                List.of(fighter.getPosition() == 0 ? "C" : String.valueOf(fighter.getPosition()), fighter.getName(),
                        fighter.getStats().wins(), fighter.getStats().draws(), fighter.getStats().losses(),
                        fighter.getStats().noContests(), fighter.getPositionChangeText())
        ))).toList());

        BatchUpdateValuesRequest updateRequest = new BatchUpdateValuesRequest()
                .setValueInputOption(GoogleServiceConfig.GOOGLE_SHEETS_INPUT_OPTION)
                .setData(ranges);

        this.sheetsService.spreadsheets().values()
                .batchUpdate(newSheet.getSpreadsheetId(), updateRequest).execute();

        return ResponseEntity.created(URI.create(newSheet.getSpreadsheetUrl())).build();
    }

    @GetMapping("/api/fighters/{uuid}/image")
    public ResponseEntity<?> getFighterImage(@PathVariable UUID uuid) {
        Optional<Fighter> fighterQuery = this.fighterRepo.findById(uuid);

        if(fighterQuery.isEmpty())
            return ResponseEntity.notFound().build();

        Fighter fighter = fighterQuery.get();

        if(fighter.getImageFileName() == null)
            return ResponseEntity.notFound().build();

        try {
            Optional<FileInputStream> imageInputStreamReq = this.fileManager.getFighterImageFile(fighter);

            if(imageInputStreamReq.isEmpty())
                return ResponseEntity.notFound().build();

            FileInputStream imageInputStream = imageInputStreamReq.get();

            int extensionLocation = fighter.getImageFileName().lastIndexOf('.');
            String extension = extensionLocation == -1 || extensionLocation >= fighter.getImageFileName().length() - 1
                    ? "image" : fighter.getImageFileName().substring(extensionLocation + 1);

            return ResponseEntity.ok().contentType(MediaType.asMediaType(new MimeType("image", extension)))
                    .body(new InputStreamResource(imageInputStream));
        }
        catch(Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.internalServerError().body("Unable to read file.");
        }
    }

    @PutMapping("/api/fighters/{uuid}/image")
    public ResponseEntity<?> setFighterImage(@PathVariable UUID uuid, @RequestParam("file") MultipartFile imageFile) {
        Optional<Fighter> fighterQuery = this.fighterRepo.findById(uuid);

        if(fighterQuery.isEmpty())
            return ResponseEntity.notFound().build();

        Fighter fighter = fighterQuery.get();

        if(imageFile.getSize() > MAX_FIGHTER_IMAGE_FILE_SIZE)
            return ResponseEntity.badRequest().body("The uploaded file is too large.");

        if(!this.fileManager.writeFighterImageFile(imageFile, fighter)) {
            return ResponseEntity.internalServerError().body("Unable to upload file.");
        }

        WebSocketMessageHelper.sendFighterUpdate(this.wsTemplate, SimpleWSUpdate.UpdateOrigin.FIGHTERS,
                SimpleWSUpdate.UpdateType.PUT, fighter);

        return ResponseEntity.ok(fighter);
    }

    @DeleteMapping("/api/fighters/{uuid}/image")
    public ResponseEntity<?> deleteFighterImage(@PathVariable UUID uuid) {
        Optional<Fighter> fighterQuery = this.fighterRepo.findById(uuid);

        if(fighterQuery.isEmpty())
            return ResponseEntity.notFound().build();

        Fighter fighter = fighterQuery.get();

        if(fighter.getImageFileName() == null)
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body("This fighter does not have an image.");

        if(!this.fileManager.deleteFile(fighter.getImageFileName()))
            return ResponseEntity.internalServerError().body("Unable to delete image.");

        fighter.setImageFileName(null);
        this.fighterRepo.save(fighter);

        WebSocketMessageHelper.sendFighterUpdate(this.wsTemplate, SimpleWSUpdate.UpdateOrigin.FIGHTERS,
                SimpleWSUpdate.UpdateType.PUT, fighter);

        return ResponseEntity.noContent().build();
    }
}
