package com.cft.repos;

import com.cft.entities.Fight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FightRepo extends JpaRepository<Fight, UUID> {

}
