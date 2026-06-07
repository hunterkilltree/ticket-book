package com.ticketbooking.ticket.service;

import com.ticketbooking.ticket.dto.TicketResponse;
import com.ticketbooking.ticket.entity.Ticket;
import com.ticketbooking.ticket.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock TicketRepository ticketRepository;
    @InjectMocks TicketService ticketService;

    @Test
    void issue_creates_ticket_when_absent() {
        UUID orderId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        when(ticketRepository.existsByOrderIdAndSeatId(orderId, seatId)).thenReturn(false);
        ticketService.issue(orderId, UUID.randomUUID(), seatId);
        verify(ticketRepository).save(any(Ticket.class));
    }

    @Test
    void issue_is_idempotent_per_order_seat() {
        UUID orderId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        when(ticketRepository.existsByOrderIdAndSeatId(orderId, seatId)).thenReturn(true);
        ticketService.issue(orderId, UUID.randomUUID(), seatId);
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void findByUser_maps_tickets() {
        UUID userId = UUID.randomUUID();
        Ticket t = new Ticket();
        t.setOrderId(UUID.randomUUID());
        t.setSeatId(UUID.randomUUID());
        t.setQrCode("Q");
        when(ticketRepository.findAllByUserId(userId)).thenReturn(List.of(t));

        List<TicketResponse> res = ticketService.findByUser(userId);
        assertEquals(1, res.size());
        assertEquals("Q", res.get(0).qrCode());
    }
}
