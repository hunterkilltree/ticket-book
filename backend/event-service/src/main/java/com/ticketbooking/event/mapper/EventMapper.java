package com.ticketbooking.event.mapper;

import com.ticketbooking.event.dto.EventResponse;
import com.ticketbooking.event.dto.VenueResponse;
import com.ticketbooking.event.entity.Event;
import com.ticketbooking.event.entity.Venue;
import org.springframework.stereotype.Component;

@Component
public class EventMapper {

    public EventResponse toResponse(Event e) {
        Venue v = e.getVenue();
        return new EventResponse(
                e.getId(),
                e.getTitle(),
                e.getArtist(),
                e.getEventDate(),
                e.getStatus().name(),
                new VenueResponse(v.getId(), v.getName(), v.getAddress(), v.getTotalCapacity()));
    }
}
