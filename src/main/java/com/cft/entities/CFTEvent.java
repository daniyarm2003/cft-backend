package com.cft.entities;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.util.*;

@Entity
@Table(name = "events")
@NoArgsConstructor
public class CFTEvent {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    @Type(type="org.hibernate.type.UUIDCharType")
    private @Getter UUID id;

    @Column(name = "name", nullable = false)
    private @Getter @Setter String name;

    @Column(name = "date", nullable = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private @Getter @Setter Date date;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL)
    @OrderBy("date")
    private @Getter List<Fight> fights = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        CFTEvent cftEvent = (CFTEvent) o;
        return id != null && Objects.equals(id, cftEvent.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
