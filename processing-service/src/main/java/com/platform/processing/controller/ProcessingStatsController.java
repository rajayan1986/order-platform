package com.platform.processing.controller;

import com.platform.processing.dto.ProcessingStatsResponse;
import com.platform.processing.repository.OrderRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/stats")
public class ProcessingStatsController {

    private final OrderRepository orderRepository;

    public ProcessingStatsController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @GetMapping
    public ResponseEntity<ProcessingStatsResponse> getStats() {
        Instant startOfToday = Instant.now().atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        long ordersProcessedToday = orderRepository.countByUpdatedAtGreaterThanEqual(startOfToday);
        long pending = orderRepository.countByStatus("PENDING");
        long processing = orderRepository.countByStatus("PAYMENT_VALIDATED")
                + orderRepository.countByStatus("COMPLIANCE_CHECKED")
                + orderRepository.countByStatus("APPROVED");
        long shipped = orderRepository.countByStatus("SHIPPED");
        return ResponseEntity.ok(new ProcessingStatsResponse(ordersProcessedToday, pending, processing, shipped));
    }
}
