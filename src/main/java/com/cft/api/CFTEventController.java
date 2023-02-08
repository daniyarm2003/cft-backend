package com.cft.api;

import com.cft.config.WebSocketConfig;
import com.cft.entities.*;
import com.cft.entities.ws.SimpleWSUpdate;
import com.cft.repos.*;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

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

    private void sendEventUpdate(SimpleWSUpdate.UpdateType updateType, CFTEvent event) {
        this.wsTemplate.convertAndSend(WebSocketConfig.EVENT_ENDPOINT, new SimpleWSUpdate<>(updateType, event));
    }

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
        this.fighterRepo.findAll().forEach(fighter -> {
            fighter.setPrevPosition(fighter.getPosition());
            Fighter savedFighter = this.fighterRepo.save(fighter);

            this.wsTemplate.convertAndSend(WebSocketConfig.FIGHTER_ENDPOINT,
                    new SimpleWSUpdate<>(SimpleWSUpdate.UpdateType.PUT, savedFighter));
        });

        CFTEvent newEvent = this.eventRepo.save(event);
        this.sendEventUpdate(SimpleWSUpdate.UpdateType.POST, newEvent);

        return newEvent;
    }

    @PutMapping("/api/events/{uuid}")
    public CFTEvent updateEvent(@PathVariable UUID uuid, @RequestBody CFTEvent updated) {
        CFTEvent event = this.eventRepo.findById(uuid).orElseThrow();

        event.setName(updated.getName());
        event.setDate(updated.getDate());

        CFTEvent savedEvent = this.eventRepo.save(event);
        this.sendEventUpdate(SimpleWSUpdate.UpdateType.PUT, savedEvent);

        return savedEvent;
    }

    private void emptyEventFights(@NonNull CFTEvent event) {
        event.getFights().forEach(fight -> {
            fight.getFighters().forEach(fighter -> {
                fighter.getFights().removeIf(fighterFight -> fighterFight.equals(fight));
                Fighter savedFighter = this.fighterRepo.save(fighter);

                this.wsTemplate.convertAndSend(WebSocketConfig.FIGHTER_ENDPOINT,
                        new SimpleWSUpdate<>(SimpleWSUpdate.UpdateType.PUT_INDIRECT, savedFighter));
            });

            this.wsTemplate.convertAndSend(WebSocketConfig.FIGHT_ENDPOINT,
                    new SimpleWSUpdate<>(SimpleWSUpdate.UpdateType.DELETE, fight));
        });
    }

    @DeleteMapping("/api/events/{uuid}")
    public void deleteEvent(@PathVariable UUID uuid) {
        CFTEvent event = this.eventRepo.findById(uuid).orElseThrow();

        this.emptyEventFights(event);
        this.eventRepo.delete(event);

        this.sendEventUpdate(SimpleWSUpdate.UpdateType.DELETE, event);
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

        Fight savedFight = this.fightRepo.save(fromRepo);
        this.wsTemplate.convertAndSend(WebSocketConfig.FIGHT_ENDPOINT,
                new SimpleWSUpdate<>(SimpleWSUpdate.UpdateType.PUT_INDIRECT, savedFight));

        event.getFights().add(fromRepo);

        CFTEvent savedEvent = this.eventRepo.save(event);
        this.sendEventUpdate(SimpleWSUpdate.UpdateType.PUT, savedEvent);

        return savedEvent;
    }

    @GetMapping("/api/events/current")
    public CFTEvent getCurrentEvent() {
        List<CFTEvent> events = this.getEvents();
        return events.isEmpty() ? null : events.get(events.size() - 1);
    }
}
