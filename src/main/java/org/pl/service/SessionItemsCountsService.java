package org.pl.service;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
public class SessionItemsCountsService {

    public Mono<Map<Long, Integer>> getCartItems(ServerWebExchange exchange) {
        return exchange.getSession()
                .map(session -> {
                    Map<Long, Integer> items = session.getAttribute("cartItems");
                    if (items == null) {
                        items = new HashMap<>();
                        session.getAttributes().put("cartItems", items);
                    }
                    return items;
                });
    }

    public Mono<Void> clearCartItems(ServerWebExchange exchange) {
        return exchange.getSession()
                .flatMap(session -> {
                    Map<Long, Integer> items = session.getAttribute("cartItems");
                    if (items != null) {
                        items.clear();
                    }
                    return session.save();
                });
    }

    public Mono<Void> removeItemFromCart(ServerWebExchange exchange, Long itemId) {
        return exchange.getSession()
                .flatMap(session -> {
                    Map<Long, Integer> items = session.getAttribute("cartItems");
                    if (items != null) {
                        items.remove(itemId);
                    }
                    return session.save();
                });
    }

    public Mono<Void> updateItemCount(ServerWebExchange exchange, Long itemId, String action) {
        return exchange.getSession()
                .flatMap(session -> {
                    Map<Long, Integer> items = session.getAttribute("cartItems");
                    if (items == null) {
                        items = new HashMap<>();
                        session.getAttributes().put("cartItems", items);
                    }

                    int current = items.getOrDefault(itemId, 0);

                    switch (action) {
                        case "PLUS" -> items.put(itemId, current + 1);
                        case "MINUS" -> {
                            if (current > 0) items.put(itemId, current - 1);
                        }
                        case "DELETE" -> items.remove(itemId);
                    }

                    return session.save();
                });
    }

    public Mono<Integer> checkItemsCount(ServerWebExchange exchange) {
        return exchange.getSession()
                .flatMap(session -> {
                    Map<Long, Integer> items = session.getAttribute("cartItems");
                    if (items == null) {
                        items = new HashMap<>();
                        session.getAttributes().put("cartItems", items);
                    }
                    items.entrySet().removeIf(entry -> entry.getValue() == 0);
                    return session.save().thenReturn(items.values().stream().mapToInt(value -> value).sum());
                });
    }
}
