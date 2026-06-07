package com.ticketbooking.event.service;

import com.ticketbooking.common.exception.GeneralNotFoundException;
import com.ticketbooking.event.dto.CreateEventRequest;
import com.ticketbooking.event.dto.EventResponse;
import com.ticketbooking.event.dto.UpdateEventRequest;
import com.ticketbooking.event.entity.Event;
import com.ticketbooking.event.entity.EventStatus;
import com.ticketbooking.event.entity.Venue;
import com.ticketbooking.event.mapper.EventMapper;
import com.ticketbooking.event.repository.EventRepository;
import com.ticketbooking.event.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository repository;
    private final VenueRepository venueRepository;
    private final EventMapper mapper;

    @Transactional(readOnly = true)
    public List<EventResponse> list(String q, EventStatus status) {
        return repository.search(blankToNull(q), status).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public EventResponse get(UUID id) {
        Event event = repository.findByIdWithVenue(id)
                .orElseThrow(() -> new GeneralNotFoundException("Event", id));
        return mapper.toResponse(event);
    }

    @Transactional
    public EventResponse create(CreateEventRequest req) {
        Venue venue = venueRepository.findById(req.venueId())
                .orElseThrow(() -> new GeneralNotFoundException("Venue", req.venueId()));
        Event event = new Event();
        event.setTitle(req.title());
        event.setArtist(req.artist());
        event.setEventDate(req.startsAt());
        event.setVenue(venue);
        if (req.status() != null) {
            event.setStatus(req.status());
        }
        return mapper.toResponse(repository.save(event));
    }

    @Transactional
    public EventResponse update(UUID id, UpdateEventRequest req) {
        Event event = repository.findById(id)
                .orElseThrow(() -> new GeneralNotFoundException("Event", id));
        if (req.title() != null) event.setTitle(req.title());
        if (req.artist() != null) event.setArtist(req.artist());
        if (req.startsAt() != null) event.setEventDate(req.startsAt());
        if (req.status() != null) event.setStatus(req.status());
        if (req.venueId() != null) {
            event.setVenue(venueRepository.findById(req.venueId())
                    .orElseThrow(() -> new GeneralNotFoundException("Venue", req.venueId())));
        }
        return mapper.toResponse(repository.save(event));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
