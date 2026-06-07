package com.ticketbooking.event.controller;

import com.ticketbooking.event.dto.VenueRequest;
import com.ticketbooking.event.dto.VenueResponse;
import com.ticketbooking.event.service.VenueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/venues")
@RequiredArgsConstructor
public class VenueController {

    private final VenueService venueService;

    @GetMapping
    public List<VenueResponse> list() {
        return venueService.list();
    }

    @PostMapping
    public ResponseEntity<VenueResponse> create(@Valid @RequestBody VenueRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(venueService.create(req));
    }
}
