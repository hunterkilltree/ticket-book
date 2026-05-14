package com.ticketbooking.booking.entity;

import com.ticketbooking.common.audit.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "seats")
public class Seat extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private String section;

    @Column(nullable = false)
    private String row;

    @Column(nullable = false)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatState state = SeatState.AVAILABLE;
}
