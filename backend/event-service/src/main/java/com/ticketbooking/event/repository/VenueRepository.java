package com.ticketbooking.event.repository;

import com.ticketbooking.event.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface VenueRepository extends JpaRepository<Venue, UUID> {}
