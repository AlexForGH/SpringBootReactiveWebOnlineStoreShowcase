package org.pl.repository;

import org.pl.dao.Item;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface ItemRepository extends ReactiveCrudRepository<Item, Long> {
    @Query("SELECT * FROM items WHERE LOWER(title) LIKE LOWER(CONCAT('%', :title, '%'))")
    Flux<Item> findByTitleContainingIgnoreCase(String title);
}
