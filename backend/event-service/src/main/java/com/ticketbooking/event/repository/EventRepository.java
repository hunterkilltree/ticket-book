package com.ticketbooking.event.repository;

import com.ticketbooking.event.entity.Event;
import com.ticketbooking.event.entity.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    @Query("""
            select e from Event e join fetch e.venue
            where (:q is null
                   or lower(e.title) like lower(concat('%', :q, '%'))
                   or lower(e.artist) like lower(concat('%', :q, '%')))
              and (:status is null or e.status = :status)
            order by e.eventDate asc
            """)
    List<Event> search(@Param("q") String q, @Param("status") EventStatus status);

    @Query("select e from Event e join fetch e.venue where e.id = :id")
    Optional<Event> findByIdWithVenue(@Param("id") UUID id);
}
