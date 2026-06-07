package com.ticketbooking.ticket.repository;

import com.ticketbooking.ticket.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    List<Ticket> findAllByUserId(UUID userId);
    List<Ticket> findAllByOrderId(UUID orderId);
    boolean existsByOrderIdAndSeatId(UUID orderId, UUID seatId);
}
