package com.cft.repos;

import com.cft.entities.CFTEventSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CFTEventShapshotRepo extends JpaRepository<CFTEventSnapshot, UUID> {

}