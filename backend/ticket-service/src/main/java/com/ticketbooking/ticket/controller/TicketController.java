package com.ticketbooking.ticket.controller;

import com.ticketbooking.ticket.dto.TicketResponse;
import com.ticketbooking.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    /** List a user's tickets: GET /api/tickets?userId={uuid}  (or ?orderId={uuid}). */
    @GetMapping
    public List<TicketResponse> list(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID orderId) {
        if (orderId != null) return ticketService.findByOrder(orderId);
        if (userId != null) return ticketService.findByUser(userId);
        return List.of();
    }
}
