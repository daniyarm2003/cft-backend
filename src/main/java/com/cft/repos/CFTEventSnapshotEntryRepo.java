package com.cft.repos;

import com.cft.entities.CFTEventSnapshotEntry;
import com.cft.entities.Fighter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CFTEventSnapshotEntryRepo extends JpaRepository<CFTEventSnapshotEntry, UUID> {

    List<CFTEventSnapshotEntry> findByFighterOrderBySnapshot_SnapshotDateAsc(Fighter fighter);

    CFTEventSnapshotEntry findFirstByOrderByPositionDesc();
}
