package com.cft.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "deleted_fighters")
@NoArgsConstructor
public class DeletedFighter {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    @Type(type="org.hibernate.type.UUIDCharType")
    private @Getter UUID id;

    @Column(name = "fighter_name")
    private @Getter @Setter String fighterName;

    @ManyToOne
    @JoinColumn(name = "debut_event", nullable = false)
    private @Getter @Setter CFTEvent debutEvent;

    @ManyToOne
    @JoinColumn(name = "final_event", nullable = false)
    private @Getter @Setter CFTEvent finalEvent;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        DeletedFighter that = (DeletedFighter) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}