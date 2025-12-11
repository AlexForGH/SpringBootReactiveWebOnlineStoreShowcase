package org.pl.service;

import org.pl.dao.Order;
import org.pl.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional()
    public Mono<Order> createOrder(BigDecimal totalAmount) {
        return generateNextOrderNumber()
                .map(orderNumber -> new Order(
                        orderNumber,
                        totalAmount,
                        LocalDateTime.now()
                ))
                .flatMap(orderRepository::save);
    }

    @Transactional(readOnly = true)
    public Mono<String> generateNextOrderNumber() {
        int currentYear = LocalDate.now().getYear();

        return orderRepository.findLastOrderNumber()
                .defaultIfEmpty("")
                .map(lastNumber -> {
                    int nextSequence = 1;
                    if (lastNumber != null && !lastNumber.isEmpty()) {
                        String[] parts = lastNumber.split("-");
                        if (parts.length == 3) {
                            try {
                                int lastSequence = Integer.parseInt(parts[2]);
                                nextSequence = lastSequence + 1;
                            } catch (NumberFormatException e) {
                                nextSequence = 1;
                            }
                        }
                    }
                    return String.format("ORD-%d-%03d", currentYear, nextSequence);
                });
    }
}
