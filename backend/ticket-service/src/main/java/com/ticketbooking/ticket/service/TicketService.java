package com.ticketbooking.ticket.service;

import com.ticketbooking.ticket.dto.TicketResponse;
import com.ticketbooking.ticket.entity.Ticket;
import com.ticketbooking.ticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;

    /** Idempotently issue a ticket for an order+seat. Returns the (existing or new) ticket. */
    @Transactional
    public void issue(UUID orderId, UUID userId, UUID seatId) {
        if (ticketRepository.existsByOrderIdAndSeatId(orderId, seatId)) {
            return;
        }
        Ticket ticket = new Ticket();
        ticket.setOrderId(orderId);
        ticket.setUserId(userId);
        ticket.setSeatId(seatId);
        ticket.setQrCode("TKT-" + UUID.randomUUID());
        ticketRepository.save(ticket);
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> findByUser(UUID userId) {
        return ticketRepository.findAllByUserId(userId).stream().map(TicketService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> findByOrder(UUID orderId) {
        return ticketRepository.findAllByOrderId(orderId).stream().map(TicketService::toResponse).toList();
    }

    private static TicketResponse toResponse(Ticket t) {
        return new TicketResponse(t.getId(), t.getOrderId(), t.getSeatId(), t.getQrCode(), t.getStatus().name());
    }
}
