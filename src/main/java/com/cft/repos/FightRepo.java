package com.cft.repos;

import com.cft.entities.Fight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface FightRepo extends JpaRepository<Fight, UUID> {

    @Query("select f from Fight f where f.event.id = ?1 and f.fightNum >= ?2 and f.fightNum < ?3")
    List<Fight> findFightNumChangeAffectedFights(UUID id, Integer fightNum, Integer fightNum1);
    
}
