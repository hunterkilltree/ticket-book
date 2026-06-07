package com.ticketbooking.event.service;

import com.ticketbooking.event.dto.VenueRequest;
import com.ticketbooking.event.dto.VenueResponse;
import com.ticketbooking.event.entity.Venue;
import com.ticketbooking.event.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VenueService {

    private final VenueRepository venueRepository;

    @Transactional(readOnly = true)
    public List<VenueResponse> list() {
        return venueRepository.findAll().stream().map(VenueService::toResponse).toList();
    }

    @Transactional
    public VenueResponse create(VenueRequest req) {
        Venue venue = new Venue();
        venue.setName(req.name());
        venue.setAddress(req.address());
        venue.setTotalCapacity(req.capacity());
        return toResponse(venueRepository.save(venue));
    }

    static VenueResponse toResponse(Venue v) {
        return new VenueResponse(v.getId(), v.getName(), v.getAddress(), v.getTotalCapacity());
    }
}
