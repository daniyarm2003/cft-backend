package com.cft.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.hibernate.Hibernate;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "snapshot_entries")
@NoArgsConstructor
public class CFTEventSnapshotEntry {

    @Id
    @GeneratedValue
    @Column(nullable = false)
    @Type(type = "org.hibernate.type.UUIDCharType")
    private @Getter UUID id;

    @ManyToOne
    @JoinColumn
    private @Getter @Setter Fighter fighter;

    @Column(nullable = false)
    private @Getter @Setter String fighterName;

    @Column(nullable = false)
    private @Getter @Setter int position;

    @Column(nullable = false)
    private @Getter @Setter int wins, losses, draws, noContests;

    @Column(nullable = false)
    private @Getter @Setter int positionChange;

    @Column(nullable = false)
    private @Getter @Setter boolean isNewFighter;

    @ManyToOne
    @JoinColumn
    @JsonIgnore
    private @Getter @Setter CFTEventSnapshot snapshot;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        CFTEventSnapshotEntry that = (CFTEventSnapshotEntry) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
