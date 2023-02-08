package com.cft.repos;

import com.cft.entities.CFTEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CFTEventRepo extends JpaRepository<CFTEvent, UUID> {

}
