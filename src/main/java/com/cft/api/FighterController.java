package com.cft.api;

import com.cft.components.ICFTFighterImageManager;
import com.cft.config.GoogleServiceConfig;
import com.cft.entities.*;
import com.cft.entities.ws.SimpleWSUpdate;
import com.cft.repos.*;
import com.cft.utils.ws.WebSocketMessageHelper;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import lombok.NonNull;
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
    private CFTEventSnapshotEntryRepo snapshotEntryRepo;

    @Autowired
    private DeletedFighterRepo deletedFighterRepo;

    @Autowired
    private SimpMessagingTemplate wsTemplate;

    @Autowired
    private ICFTFighterImageManager fighterImageManager;

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

        if(fighter.getImageFileName() != null && !this.fighterImageManager.deleteFighterImageFile(fighter)) {
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

    @GetMapping("/api/fighters/{uuid}/image")
    public ResponseEntity<?> getFighterImage(@PathVariable UUID uuid) {
        Optional<Fighter> fighterQuery = this.fighterRepo.findById(uuid);

        if(fighterQuery.isEmpty())
            return ResponseEntity.notFound().build();

        Fighter fighter = fighterQuery.get();

        if(fighter.getImageFileName() == null)
            return ResponseEntity.notFound().build();

        try {
            FileInputStream imageInputStream = this.fighterImageManager.getFighterImageFile(fighter);

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

        java.io.File outputFile;
        try {
            outputFile = this.fighterImageManager.writeFighterImageFile(imageFile, fighter);
        }
        catch(IOException ex) {
            ex.printStackTrace();
            return ResponseEntity.internalServerError().body("Unable to upload file.");
        }

        fighter.setImageFileName(outputFile.getName());
        this.fighterRepo.save(fighter);

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

        if(!this.fighterImageManager.deleteFighterImageFile(fighter))
            return ResponseEntity.internalServerError().body("Unable to delete image.");

        fighter.setImageFileName(null);
        this.fighterRepo.save(fighter);

        WebSocketMessageHelper.sendFighterUpdate(this.wsTemplate, SimpleWSUpdate.UpdateOrigin.FIGHTERS,
                SimpleWSUpdate.UpdateType.PUT, fighter);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/fighters/{uuid}/snapshots")
    public ResponseEntity<?> getFighterSnapshotEntries(@PathVariable UUID uuid) {
        Optional<Fighter> fighterQuery = this.fighterRepo.findById(uuid);

        if(fighterQuery.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Fighter fighter = fighterQuery.get();

        List<CFTEventSnapshotEntry> snapshotEntries =
                this.snapshotEntryRepo.findByFighterOrderBySnapshot_SnapshotDateAsc(fighter);

        return ResponseEntity.ok(snapshotEntries);
    }
}
