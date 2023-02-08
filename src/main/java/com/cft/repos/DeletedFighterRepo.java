package com.cft.repos;

import com.cft.entities.DeletedFighter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeletedFighterRepo extends JpaRepository<DeletedFighter, UUID> {
}