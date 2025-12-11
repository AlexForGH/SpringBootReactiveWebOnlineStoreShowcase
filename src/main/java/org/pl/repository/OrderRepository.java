package org.pl.repository;

import org.pl.dao.Order;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface OrderRepository extends ReactiveCrudRepository<Order, Long> {
    @Query("SELECT order_number FROM orders ORDER BY order_date DESC LIMIT 1")
    Mono<String> findLastOrderNumber();
}
