package com.ticketbooking.event.service;

import com.ticketbooking.common.exception.GeneralNotFoundException;
import com.ticketbooking.event.dto.CreateEventRequest;
import com.ticketbooking.event.dto.EventResponse;
import com.ticketbooking.event.dto.VenueResponse;
import com.ticketbooking.event.entity.Event;
import com.ticketbooking.event.entity.EventStatus;
import com.ticketbooking.event.mapper.EventMapper;
import com.ticketbooking.event.repository.EventRepository;
import com.ticketbooking.event.repository.VenueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock EventRepository eventRepository;
    @Mock VenueRepository venueRepository;
    @Mock EventMapper mapper;
    @InjectMocks EventService eventService;

    private EventResponse sample() {
        return new EventResponse(UUID.randomUUID(), "t", "a", Instant.now(), "PUBLISHED",
                new VenueResponse(UUID.randomUUID(), "v", "addr", 100));
    }

    @Test
    void list_maps_repository_results() {
        Event event = new Event();
        when(eventRepository.search(null, EventStatus.PUBLISHED)).thenReturn(List.of(event));
        when(mapper.toResponse(event)).thenReturn(sample());

        assertEquals(1, eventService.list(null, EventStatus.PUBLISHED).size());
    }

    @Test
    void get_missing_event_throws() {
        UUID id = UUID.randomUUID();
        when(eventRepository.findByIdWithVenue(id)).thenReturn(Optional.empty());
        assertThrows(GeneralNotFoundException.class, () -> eventService.get(id));
    }

    @Test
    void create_with_unknown_venue_throws() {
        UUID venueId = UUID.randomUUID();
        when(venueRepository.findById(venueId)).thenReturn(Optional.empty());
        assertThrows(GeneralNotFoundException.class, () -> eventService.create(
                new CreateEventRequest("t", "a", Instant.now(), venueId, null)));
    }
}
