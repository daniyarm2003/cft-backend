package com.cft.api;

import com.cft.entities.*;
import com.cft.entities.ws.SimpleWSUpdate;
import com.cft.repos.*;
import com.cft.services.ISnapshotService;
import com.cft.utils.ws.WebSocketMessageHelper;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.util.*;

@RestController
@CrossOrigin
public class CFTEventController {

    @Autowired
    public CFTEventRepo eventRepo;

    @Autowired
    public FightRepo fightRepo;

    @Autowired
    public FighterRepo fighterRepo;

    @Autowired
    private SimpMessagingTemplate wsTemplate;

    @Autowired
    @Qualifier("mainSnapshotService")
    private ISnapshotService snapshotService;

    @GetMapping("/api/events")
    public List<CFTEvent> getEvents() {
        return this.eventRepo.findAll(Sort.by(Sort.Direction.ASC, "date"));
    }

    @GetMapping("/api/events/{uuid}")
    public CFTEvent getEvent(@PathVariable UUID uuid) {
        return this.eventRepo.findById(uuid).orElseThrow();
    }

    @PostMapping("/api/events")
    public CFTEvent addEvent(@RequestBody CFTEvent event) {
        CFTEvent curEvent = this.getCurrentEvent();

        if(curEvent != null && curEvent.getSnapshot() == null) {
            CFTEventSnapshot snapshot = this.snapshotService.takeEventSnapshot(curEvent);

            try {
                this.snapshotService.uploadSnapshotToGoogleSheets(snapshot);
            }
            catch(Exception ex) {
                System.err.println("Unable to upload current event snapshot due to error: " + ex.getMessage());
            }
        }

        this.fighterRepo.findAll().forEach(fighter -> {
            fighter.setPrevPosition(fighter.getPosition());
            Fighter savedFighter = this.fighterRepo.save(fighter);

            WebSocketMessageHelper.sendFighterUpdate(this.wsTemplate, SimpleWSUpdate.UpdateOrigin.EVENTS,
                    SimpleWSUpdate.UpdateType.PUT, savedFighter);
        });

        event.setNextFightNum(0);
        CFTEvent newEvent = this.eventRepo.save(event);

        WebSocketMessageHelper.sendEventUpdate(this.wsTemplate, SimpleWSUpdate.UpdateOrigin.EVENTS,
                SimpleWSUpdate.UpdateType.POST, newEvent);

        return newEvent;
    }

    @PutMapping("/api/events/{uuid}")
    public CFTEvent updateEvent(@PathVariable UUID uuid, @RequestBody CFTEvent updated) {
        CFTEvent event = this.eventRepo.findById(uuid).orElseThrow();

        event.setName(updated.getName());
        event.setDate(updated.getDate());

        CFTEvent savedEvent = this.eventRepo.save(event);

        WebSocketMessageHelper.sendEventUpdate(this.wsTemplate, SimpleWSUpdate.UpdateOrigin.EVENTS,
                SimpleWSUpdate.UpdateType.PUT, savedEvent);

        return savedEvent;
    }

    private void emptyEventFights(@NonNull CFTEvent event) {
        event.getFights().forEach(fight -> {
            fight.getFighters().forEach(fighter -> {
                fighter.getFights().removeIf(fighterFight -> fighterFight.equals(fight));
                Fighter savedFighter = this.fighterRepo.save(fighter);

                WebSocketMessageHelper.sendFighterUpdate(this.wsTemplate, SimpleWSUpdate.UpdateOrigin.EVENTS,
                        SimpleWSUpdate.UpdateType.PUT, savedFighter);
            });

            WebSocketMessageHelper.sendFightUpdate(this.wsTemplate, SimpleWSUpdate.UpdateOrigin.EVENTS,
                    SimpleWSUpdate.UpdateType.DELETE, fight);
        });
    }

    @DeleteMapping("/api/events/{uuid}")
    public void deleteEvent(@PathVariable UUID uuid) {
        CFTEvent event = this.eventRepo.findById(uuid).orElseThrow();

        this.emptyEventFights(event);
        this.eventRepo.delete(event);

        WebSocketMessageHelper.sendEventUpdate(this.wsTemplate, SimpleWSUpdate.UpdateOrigin.EVENTS,
                SimpleWSUpdate.UpdateType.DELETE, event);
    }

    @DeleteMapping("/api/events")
    public void deleteEvents() {
        this.eventRepo.findAll().forEach(this::emptyEventFights);
        this.eventRepo.deleteAll();
    }

    @PutMapping("/api/events/{uuid}/add-fight")
    public CFTEvent addFight(@PathVariable UUID uuid, @RequestBody Fight fight) {
        CFTEvent event = this.eventRepo.findById(uuid).orElseThrow();

        Fight fromRepo = this.fightRepo.findById(fight.getId()).orElseThrow();

        fromRepo.setEvent(event);
        fromRepo.setFightNum(event.getNextFightNum());

        Fight savedFight = this.fightRepo.save(fromRepo);

        WebSocketMessageHelper.sendFightUpdate(this.wsTemplate, SimpleWSUpdate.UpdateOrigin.EVENTS,
                SimpleWSUpdate.UpdateType.PUT, savedFight);

        event.getFights().add(fromRepo);
        event.setNextFightNum(event.getNextFightNum() + 1);

        CFTEvent savedEvent = this.eventRepo.save(event);

        WebSocketMessageHelper.sendEventUpdate(this.wsTemplate, SimpleWSUpdate.UpdateOrigin.EVENTS,
                SimpleWSUpdate.UpdateType.PUT, savedEvent);

        return savedEvent;
    }

    @GetMapping("/api/events/current")
    public CFTEvent getCurrentEvent() {
        List<CFTEvent> events = this.getEvents();
        return events.isEmpty() ? null : events.get(events.size() - 1);
    }

    @PostMapping("/api/events/{uuid}/snapshot")
    public ResponseEntity<?> takeSnapshot(@PathVariable UUID uuid) {
        Optional<CFTEvent> eventQuery = this.eventRepo.findById(uuid);

        if(eventQuery.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        CFTEvent event = eventQuery.get();

        if(event.getSnapshot() != null) {
            return ResponseEntity.badRequest().body("Snapshot already exists for this event");
        }

        CFTEventSnapshot snapshot = this.snapshotService.takeEventSnapshot(event);

        try {
            snapshot = this.snapshotService.uploadSnapshotToGoogleSheets(snapshot);
        }
        catch(Exception ex) {
            ex.printStackTrace();
        }

        return ResponseEntity.ok(snapshot);
    }

    @PostMapping("/api/events/{uuid}/snapshot/upload")
    public ResponseEntity<?> uploadSnapshotToDrive(@PathVariable UUID uuid) {
        Optional<CFTEvent> eventQuery = this.eventRepo.findById(uuid);

        if(eventQuery.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        CFTEvent event = eventQuery.get();
        CFTEventSnapshot snapshot = event.getSnapshot();

        URI snapshotURI;

        if(snapshot == null) {
            return ResponseEntity.badRequest().body("Event %s (ID %s) does not have a snapshot to upload"
                    .formatted(event.getName(), uuid));
        }

        else if(snapshot.getGoogleSheetURL() != null) {
            return ResponseEntity.badRequest().body("Event %s (ID %s) snapshot has already been uploaded to Google Sheets"
                    .formatted(event.getName(), uuid));
        }

        try {
            snapshot = this.snapshotService.uploadSnapshotToGoogleSheets(snapshot);
            snapshotURI = URI.create(snapshot.getGoogleSheetURL());
        }
        catch(Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.internalServerError().body("Unable to upload snapshot to Google Sheets due to an unexpected error");
        }

        return ResponseEntity.created(snapshotURI).body(snapshot);
    }
}
