package org.pl.service;

import org.pl.dao.Item;
import org.pl.dao.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class CartService {

    private final ItemService itemService;
    private final OrderService orderService;
    private final OrderItemService orderItemService;
    private final SessionItemsCountsService sessionItemsCountsService;

    public CartService(
            ItemService itemService,
            OrderService orderService,
            OrderItemService orderItemService,
            SessionItemsCountsService sessionItemsCountsService
    ) {
        this.itemService = itemService;
        this.orderService = orderService;
        this.orderItemService = orderItemService;
        this.sessionItemsCountsService = sessionItemsCountsService;
    }

    @Transactional(
            rollbackFor = {RuntimeException.class}
    )
    public Mono<Order> createSaveOrders(ServerWebExchange exchange) {
        return getTotalItemsSum(exchange)
                .flatMap(totalAmount -> orderService.createOrder(totalAmount))
                .flatMap(savedOrder -> {
                    return sessionItemsCountsService.getCartItems(exchange)
                            .flatMap(cartItems -> orderItemService.saveOrder(savedOrder, cartItems)
                                    .then(sessionItemsCountsService.clearCartItems(exchange))
                                    .thenReturn(savedOrder));
                });
    }

    @Transactional(
            rollbackFor = {RuntimeException.class}
    )
    public Mono<Order> createSaveOrder(Long itemId, ServerWebExchange exchange) {
        return sessionItemsCountsService.getCartItems(exchange)
                .flatMap(cartItems -> {
                    Integer quantity = cartItems.get(itemId);
                    if (quantity == null || quantity == 0) {
                        return Mono.error(new RuntimeException("Товар не найден в корзине"));
                    }

                    return itemService.getPriceById(itemId)
                            .map(price -> price.multiply(BigDecimal.valueOf(quantity)))
                            .flatMap(totalAmount -> orderService.createOrder(totalAmount))
                            .flatMap(savedOrder -> {
                                Map<Long, Integer> singleItemCart = Map.of(itemId, quantity);
                                return orderItemService.saveOrder(savedOrder, singleItemCart)
                                        .then(sessionItemsCountsService.removeItemFromCart(exchange, itemId))
                                        .thenReturn(savedOrder);
                            });
                });
    }

    @Transactional(readOnly = true)
    public Mono<List<Item>> getItemsByItemsCounts(ServerWebExchange exchange) {
        return sessionItemsCountsService.getCartItems(exchange)
                .flatMapMany(cartItems -> Flux.fromIterable(cartItems.keySet()))
                .flatMap(itemService::getItemById)
                .collectList();
    }

    @Transactional(readOnly = true)
    public Mono<BigDecimal> getTotalItemsSum(ServerWebExchange exchange) {
        return sessionItemsCountsService.getCartItems(exchange)
                .flatMapMany(cartItems -> Flux.fromIterable(cartItems.entrySet()))
                .flatMap(entry -> {
                    Long itemId = entry.getKey();
                    Integer quantity = entry.getValue();
                    return itemService.getPriceById(itemId)
                            .map(price -> price.multiply(BigDecimal.valueOf(quantity)));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
