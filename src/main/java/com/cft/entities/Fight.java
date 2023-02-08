package com.cft.entities;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.util.*;

@Entity
@Table(name = "fights")
@NoArgsConstructor
public class Fight {

    public enum FightStatus {
        HAS_WINNER, DRAW, NO_CONTEST, NOT_STARTED
    }

    private static final String[] STATUS_VALUES = { "has_winner", "draw", "no_contest", "not_started" };

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    @Type(type="org.hibernate.type.UUIDCharType")
    private @Getter UUID id;

    @Column(name = "status", nullable = false)
    private @Getter String status;

    @Column(name = "date", nullable = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private @Getter @Setter Date date;

    @Column(name = "duration", nullable = false)
    private @Getter @Setter int durationInSeconds;

    @ManyToMany(mappedBy = "fights")
    private @Getter Set<Fighter> fighters = new HashSet<>();

    @OneToOne
    @JoinColumn(name = "winner")
    private @Getter @Setter Fighter winner;

    @ManyToOne
    @JoinColumn(name = "event")
    @JsonIgnore
    private @Getter @Setter CFTEvent event;

    public void setStatus(@NonNull String status) {

        for(String value : STATUS_VALUES)
            if(status.equals(value)) {
                this.status = status;
                return;
            }

        throw new IllegalArgumentException("Illegal fight status name");
    }

    public FightStatus getStatusEnum() {
        for(int i = 0; i < STATUS_VALUES.length; i++)
            if(STATUS_VALUES[i].equals(this.status))
                return FightStatus.values()[i];

        throw new IllegalStateException("Status name is invalid");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Fight fight = (Fight) o;
        return id != null && Objects.equals(id, fight.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
