package com.cft.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.util.*;

@Entity
@Table(name = "snapshots")
@NoArgsConstructor
public class CFTEventSnapshot {

    @Id
    @GeneratedValue
    @Column(nullable = false)
    @Type(type = "org.hibernate.type.UUIDCharType")
    private @Getter UUID id;

    @Column
    private @Getter @Setter String googleSheetURL;

    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL)
    @OrderBy("position")
    private @Getter List<CFTEventSnapshotEntry> snapshotEntries = new ArrayList<>();

    @OneToOne(mappedBy = "snapshot")
    @JsonIgnore
    private @Getter @Setter CFTEvent event;

    @Column(nullable = false)
    private @Getter @Setter Date snapshotDate;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        CFTEventSnapshot that = (CFTEventSnapshot) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
