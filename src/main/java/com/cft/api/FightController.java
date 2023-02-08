package com.cft.api;

import com.cft.config.WebSocketConfig;
import com.cft.entities.CFTEvent;
import com.cft.entities.Fight;
import com.cft.entities.Fighter;
import com.cft.entities.ws.SimpleWSUpdate;
import com.cft.repos.FightRepo;
import com.cft.repos.FighterRepo;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

@RestController
@CrossOrigin
public class FightController {

    private static final String CFT_AI_URL_TEMPLATE = "http://localhost:%d/cftai/%s";
    private static final int CFT_AI_PORT = 5000;

    @Autowired
    private FightRepo fightRepo;

    @Autowired
    private FighterRepo fighterRepo;

    @Autowired
    private SimpMessagingTemplate wsTemplate;

    private void sendFightUpdate(SimpleWSUpdate.UpdateType updateType, Fight fight) {
        this.wsTemplate.convertAndSend(WebSocketConfig.FIGHT_ENDPOINT, new SimpleWSUpdate<>(updateType, fight));
    }

    @GetMapping("/api/fights")
    public List<Fight> getFights() {
        return this.fightRepo.findAll(Sort.by(Sort.Direction.ASC, "date"));
    }

    @GetMapping("/api/fights/{uuid}")
    public Fight getFight(@PathVariable UUID uuid) {
        return this.fightRepo.findById(uuid).orElseThrow();
    }

    @PostMapping("/api/fights")
    public Fight addFight(@RequestBody Fight fight) {
        Fight newFight = this.fightRepo.save(fight);
        this.sendFightUpdate(SimpleWSUpdate.UpdateType.POST, newFight);

        return newFight;
    }

    @PutMapping("/api/fights/{uuid}")
    public Fight updateFight(@PathVariable UUID uuid, @RequestBody Fight updated) {
        Fight fight = this.fightRepo.findById(uuid).orElseThrow();

        fight.setStatus(updated.getStatus());
        fight.setDate(updated.getDate());
        fight.setDurationInSeconds(updated.getDurationInSeconds());
        fight.setWinner(updated.getWinner());

        Fight savedFight = this.fightRepo.save(fight);
        this.sendFightUpdate(SimpleWSUpdate.UpdateType.PUT, savedFight);

        return savedFight;
    }

    private void deleteFight(@NonNull Fight fight) {
        fight.getFighters().forEach(fighter -> {
            fighter.getFights().removeIf(fighterFight -> fighterFight.equals(fight));

            Fighter savedFighter = this.fighterRepo.save(fighter);
            this.wsTemplate.convertAndSend(WebSocketConfig.FIGHTER_ENDPOINT,
                    new SimpleWSUpdate<>(SimpleWSUpdate.UpdateType.PUT_INDIRECT, savedFighter));
        });

        this.fightRepo.delete(fight);
        this.sendFightUpdate(SimpleWSUpdate.UpdateType.DELETE, fight);
    }

    @DeleteMapping("/api/fights/{uuid}")
    public void deleteFight(@PathVariable UUID uuid) {
        this.deleteFight(this.fightRepo.findById(uuid).orElseThrow());
    }

    @DeleteMapping("/api/fights")
    public void deleteFights() {
        this.fightRepo.findAll().forEach(this::deleteFight);
    }

    @PutMapping("/api/fights/{uuid}/set-fighters")
    public Fight setFighters(@PathVariable UUID uuid, @RequestBody List<Fighter> fighters) {
        Fight fight = this.fightRepo.findById(uuid).orElseThrow();

        Set<Fighter> tmp = new HashSet<>();

        fight.getFighters().forEach(fighter -> {
            fighter.getFights().removeIf(fighterFight -> fighterFight.equals(fight));

            tmp.addAll(fight.getFighters().stream().filter(fightFighter -> fightFighter.equals(fighter)).toList());

            Fighter savedFighter = this.fighterRepo.save(fighter);
            this.wsTemplate.convertAndSend(WebSocketConfig.FIGHTER_ENDPOINT,
                    new SimpleWSUpdate<>(SimpleWSUpdate.UpdateType.PUT_INDIRECT, savedFighter));
        });

        fight.getFighters().removeIf(tmp::contains);

        Fight newFight = this.fightRepo.save(fight);

        List<Fighter> newFighters = fighters.stream().map(fighter -> {
            Fighter fromRepo = this.fighterRepo.findById(fighter.getId()).orElseThrow();

            fromRepo.getFights().add(newFight);

            Fighter savedFighter = this.fighterRepo.save(fromRepo);
            this.wsTemplate.convertAndSend(WebSocketConfig.FIGHTER_ENDPOINT,
                    new SimpleWSUpdate<>(SimpleWSUpdate.UpdateType.PUT_INDIRECT, savedFighter));

            return savedFighter;
        }).toList();

        newFight.getFighters().addAll(newFighters);

        Fight savedFight = this.fightRepo.save(newFight);
        this.sendFightUpdate(SimpleWSUpdate.UpdateType.PUT, savedFight);

        return savedFight;
    }

    @GetMapping("/api/fights/{uuid}/event")
    public CFTEvent getFightEvent(@PathVariable UUID uuid) {
        Fight fight = this.fightRepo.findById(uuid).orElseThrow();
        return fight.getEvent();
    }

    @GetMapping("/api/fights/{uuid}/predict-winner")
    public ResponseEntity<?> getFightPrediction(@PathVariable UUID uuid) throws IOException, InterruptedException, URISyntaxException {
        Fight fight = this.fightRepo.findById(uuid).orElseThrow();
        List<Set<Fight>> fightHistories = fight.getFighters().stream().map(Fighter::getFights).toList();

        ObjectMapper objMapper = new ObjectMapper();

        Map<String, Object> props = Map.of("fight", fight, "histories", fightHistories);

        String reqBody = objMapper.writeValueAsString(props);

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(new URI(CFT_AI_URL_TEMPLATE
                        .formatted(CFT_AI_PORT, "predict-fight-winner")))
                .POST(HttpRequest.BodyPublishers.ofString(reqBody))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        return new ResponseEntity<>(response.body(), headers, HttpStatus.OK);
    }
}
