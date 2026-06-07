package com.ticketbooking.event.controller;

import com.ticketbooking.event.dto.CreateEventRequest;
import com.ticketbooking.event.dto.EventResponse;
import com.ticketbooking.event.dto.UpdateEventRequest;
import com.ticketbooking.event.entity.EventStatus;
import com.ticketbooking.event.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService service;

    /** Public catalog (defaults to PUBLISHED). `all=true` returns every status (admin). */
    @GetMapping
    public List<EventResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) EventStatus status,
            @RequestParam(required = false, defaultValue = "false") boolean all) {
        EventStatus effective = all ? null : (status == null ? EventStatus.PUBLISHED : status);
        return service.list(q, effective);
    }

    @GetMapping("/{id}")
    public EventResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/{id}")
    public EventResponse update(@PathVariable UUID id, @RequestBody UpdateEventRequest req) {
        return service.update(id, req);
    }
}
