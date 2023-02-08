package com.cft.api;

import com.cft.entities.DeletedFighter;
import com.cft.repos.DeletedFighterRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@CrossOrigin
public class DeletedFighterController {

    @Autowired
    private DeletedFighterRepo deletedFighterRepo;

    @GetMapping("/api/fighters/deleted")
    public List<DeletedFighter> getDeletedFighters() {
        return this.deletedFighterRepo.findAll(Sort.by(Sort.Direction.ASC, "fighterName"));
    }

    @GetMapping("/api/fighters/deleted/{uuid}")
    public DeletedFighter getDeletedFighter(@PathVariable UUID uuid) {
        return this.deletedFighterRepo.findById(uuid).orElseThrow();
    }

    @PostMapping("/api/fighters/deleted")
    public DeletedFighter addDeletedFighter(@RequestBody DeletedFighter deletedFighter) {
        return this.deletedFighterRepo.save(deletedFighter);
    }

    @PutMapping("/api/fighters/deleted/{uuid}")
    public DeletedFighter updateDeletedFighter(@PathVariable UUID uuid, @RequestBody DeletedFighter newDeletedFighter) {
        DeletedFighter deletedFighter = this.deletedFighterRepo.findById(uuid).orElseThrow();

        deletedFighter.setFighterName(newDeletedFighter.getFighterName());
        deletedFighter.setDebutEvent(newDeletedFighter.getDebutEvent());
        deletedFighter.setFinalEvent(newDeletedFighter.getFinalEvent());

        return this.deletedFighterRepo.save(deletedFighter);
    }

    @DeleteMapping("/api/fighters/deleted")
    public void deleteAllDeletedFighters() {
        this.deletedFighterRepo.deleteAll();
    }

    @DeleteMapping("/api/fighters/deleted/{uuid}")
    public void deleteDeletedFighter(@PathVariable UUID uuid) {
        this.deletedFighterRepo.deleteById(uuid);
    }
}
