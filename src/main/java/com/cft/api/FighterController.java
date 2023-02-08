package com.cft.api;

import com.cft.config.WebSocketConfig;
import com.cft.entities.CFTEvent;
import com.cft.entities.DeletedFighter;
import com.cft.entities.Fight;
import com.cft.entities.Fighter;
import com.cft.entities.ws.SimpleWSUpdate;
import com.cft.google.GoogleServiceConsts;
import com.cft.google.GoogleServices;
import com.cft.repos.CFTEventRepo;
import com.cft.repos.DeletedFighterRepo;
import com.cft.repos.FightRepo;
import com.cft.repos.FighterRepo;
import com.google.api.services.drive.model.File;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.net.URI;
import java.util.*;

@RestController
@CrossOrigin
public class FighterController {

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

    private void sendFighterUpdate(SimpleWSUpdate.UpdateType updateType, Fighter fighter) {
        this.wsTemplate.convertAndSend(WebSocketConfig.FIGHTER_ENDPOINT, new SimpleWSUpdate<>(updateType, fighter));
    }

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
        this.sendFighterUpdate(SimpleWSUpdate.UpdateType.POST, newFighter);

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
                        this.sendFighterUpdate(SimpleWSUpdate.UpdateType.PUT, savedFighter);
                    });

            fighter.setPosition(updated.getPosition());
        }

        Fighter savedFighter = this.fighterRepo.save(fighter);
        this.sendFighterUpdate(SimpleWSUpdate.UpdateType.PUT, savedFighter);

        return savedFighter;
    }

    private void deleteFighter(@NonNull Fighter fighter) {

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

            this.wsTemplate.convertAndSend(WebSocketConfig.FIGHT_ENDPOINT,
                    new SimpleWSUpdate<>(SimpleWSUpdate.UpdateType.PUT_INDIRECT, savedFight));
        });

        fighter.getFights().clear();
        this.fighterRepo.save(fighter);

        toDelete.forEach(fight -> {
            this.fightRepo.delete(fight);

            this.wsTemplate.convertAndSend(WebSocketConfig.FIGHT_ENDPOINT,
                    new SimpleWSUpdate<>(SimpleWSUpdate.UpdateType.DELETE, fight));
        });

        this.fighterRepo.delete(fighter);

        this.fighterRepo.findAll().stream().filter(otherFighter -> otherFighter.getPosition() > fighter.getPosition())
                .forEach(otherFighter -> {
                    otherFighter.setPosition(otherFighter.getPosition() - 1);

                    Fighter savedFighter = this.fighterRepo.save(otherFighter);
                    this.sendFighterUpdate(SimpleWSUpdate.UpdateType.PUT, savedFighter);
                });

        this.sendFighterUpdate(SimpleWSUpdate.UpdateType.DELETE, fighter);
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

        if(GoogleServices.DRIVE_SERVICE == null || GoogleServices.SHEETS_SERVICE == null)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error has occurred with Google service initialization");

        List<CFTEvent> events = this.eventRepo.findAll(Sort.by(Sort.Direction.ASC, "date"));
        CFTEvent curEvent = events.isEmpty() ? null : events.get(events.size() - 1);

        File newSheetFile = GoogleServices.DRIVE_SERVICE.files().create(new File()
                .setName("%s Snapshot".formatted(curEvent == null ? "No Event" : curEvent.getName()))
                .setMimeType(GoogleServiceConsts.GOOGLE_SHEETS_MIME_TYPE)
                .setParents(Collections.singletonList(GoogleServiceConsts.SNAPSHOT_DRIVE_FOLDER_ID))).execute();

        Spreadsheet newSheet = GoogleServices.SHEETS_SERVICE.spreadsheets().get(newSheetFile.getId()).execute();

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
                .setValueInputOption(GoogleServiceConsts.GOOGLE_SHEETS_INPUT_OPTION)
                .setData(ranges);

        GoogleServices.SHEETS_SERVICE.spreadsheets().values()
                .batchUpdate(newSheet.getSpreadsheetId(), updateRequest).execute();

        return ResponseEntity.created(URI.create(newSheet.getSpreadsheetUrl())).build();
    }
}
