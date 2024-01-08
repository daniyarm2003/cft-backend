package com.cft.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "fighters")
@NoArgsConstructor
public class Fighter {
    public record FighterStats(int wins, int losses, int draws, int noContests) {

    }

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    @Type(type="org.hibernate.type.UUIDCharType")
    private @Getter UUID id;

    @Column(name = "name", nullable = false)
    private @Getter @Setter String name;

    @Column(name = "position", nullable = false)
    private @Getter @Setter int position;

    @Column(name = "previous_position", nullable = false)
    private @Getter @Setter int prevPosition = -1;

    @Column(name = "location")
    private @Getter @Setter String location;

    @Column(name = "height")
    private @Getter @Setter Double heightInBlocks;

    @Column(name = "length")
    private @Getter @Setter Double lengthInBlocks;

    @Column(name = "team")
    private @Getter @Setter String team;

    @Column(name = "image_file_name")
    private @Getter @Setter String imageFileName;

    @ManyToMany(cascade = {
            CascadeType.MERGE,
            CascadeType.PERSIST
    })
    @JoinTable(name = "fighters_fights",
            joinColumns = @JoinColumn(name = "fighter_id",
                    referencedColumnName = "id"))
    @JsonIgnore
    @OrderBy("date")
    private @Getter Set<Fight> fights = new HashSet<>();

    @OneToMany(mappedBy = "fighter", cascade = CascadeType.PERSIST)
    @JsonIgnore
    private @Getter Set<CFTEventSnapshotEntry> snapshotEntries = new HashSet<>();

    public Fighter(UUID id, String name, int position) {
        this.id = id;
        this.name = name;
        this.position = position;
    }

    public FighterStats getStats() {
        int wins = 0, losses = 0, draws = 0, noContests = 0;

        for(Fight fight : this.fights) {
            switch (fight.getStatusEnum()) {
                case HAS_WINNER -> {
                    Fighter winner = fight.getWinner();

                    if (this.equals(winner))
                        ++wins;

                    else
                        ++losses;
                }
                case DRAW -> ++draws;
                case NO_CONTEST -> ++noContests;
            }
        }

        return new FighterStats(wins, losses, draws, noContests);
    }

    public boolean isNewFighter() {
        return this.prevPosition < 0;
    }

    public int getPositionChange() {
        return this.prevPosition < 0 ? this.prevPosition : this.prevPosition - this.position;
    }

    public String getPositionChangeText() {
        int positionChange = this.getPositionChange();

        if(this.prevPosition < 0)
            return "NEW";

        else if(positionChange == 0)
            return "-";

        return positionChange > 0 ? "+%d".formatted(positionChange) : String.valueOf(positionChange);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Fighter fighter = (Fighter) o;
        return id != null && Objects.equals(id, fighter.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
