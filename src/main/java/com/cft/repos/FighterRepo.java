package com.cft.repos;

import com.cft.entities.Fighter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FighterRepo extends JpaRepository<Fighter, UUID> {

}
