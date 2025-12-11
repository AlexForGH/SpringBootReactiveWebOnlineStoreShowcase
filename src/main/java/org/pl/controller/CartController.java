package org.pl.controller;

import org.pl.service.CartService;
import org.pl.service.SessionItemsCountsService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.result.view.Rendering;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.pl.controller.Actions.*;

@Controller
@RequestMapping()
public class CartController {

    private final CartService cartService;
    private final SessionItemsCountsService sessionItemsCountsService;

    public CartController(
            CartService cartService,
            SessionItemsCountsService sessionItemsCountsService
    ) {
        this.cartService = cartService;
        this.sessionItemsCountsService = sessionItemsCountsService;
    }

    @GetMapping(cartAction)
    public Mono<Rendering> cartAction(ServerWebExchange exchange) {
        return Mono.zip(
                        sessionItemsCountsService
                                .getCartItems(exchange)
                                .doOnNext(
                                        items -> items.entrySet().removeIf(
                                                entry -> entry.getValue() == 0
                                        )
                                ),
                        cartService.getItemsByItemsCounts(exchange),
                        cartService.getTotalItemsSum(exchange)
                )
                .map(tuple -> {
                    var cartItems = tuple.getT1();
                    var items = tuple.getT2();
                    var totalItemsSum = tuple.getT3();

                    return Rendering.view("cart")
                            .modelAttribute("cartItems", cartItems)
                            .modelAttribute("items", items)
                            .modelAttribute("cartAction", cartAction)
                            .modelAttribute("itemsAction", itemsAction)
                            .modelAttribute("buyAction", buyAction)
                            .modelAttribute("totalItemsSum", totalItemsSum)
                            .build();
                });
    }

    @PostMapping(value = cartAction, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Mono<String> increaseDecreaseItemsCount(
            ServerWebExchange exchange
    ) {
        return exchange.getFormData()
                .flatMap(formData -> {
                    String idStr = formData.getFirst("id");
                    if (idStr == null || idStr.trim().isEmpty()) {
                        return Mono.error(new IllegalArgumentException("id is required"));
                    }
                    Long id = Long.parseLong(idStr.trim());
                    String action = formData.getFirst("action");
                    return sessionItemsCountsService.updateItemCount(exchange, id, action)
                            .thenReturn("redirect:" + cartAction);
                });
    }

    @PostMapping(value = buyAction, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Mono<String> buyItems(
            ServerWebExchange exchange
    ) {
        return cartService.createSaveOrders(exchange)
                .flatMap(savedOrder -> {
                    exchange.getAttributes().put("toastMessage", "Заказ №" + savedOrder.getOrderNumber() + " успешно оформлен!");
                    exchange.getAttributes().put("toastType", "success");
                    return Mono.just("redirect:" + ordersAction + "/" + savedOrder.getId());
                })
                .onErrorResume(e -> {
                    exchange.getAttributes().put("toastMessage", "Ошибка при оформлении заказа: " + e.getMessage());
                    exchange.getAttributes().put("toastType", "error");
                    return Mono.just("redirect:" + ordersAction + "/0");
                });
    }

    @PostMapping(value = buyAction + "/{id}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Mono<String> buyItem(
            @PathVariable Long id,
            ServerWebExchange exchange
    ) {
        return cartService.createSaveOrder(id, exchange)
                .flatMap(savedOrder -> {
                    exchange.getAttributes().put("toastMessage", "Заказ №" + savedOrder.getOrderNumber() + " успешно оформлен!");
                    exchange.getAttributes().put("toastType", "success");
                    return Mono.just("redirect:" + ordersAction + "/" + savedOrder.getId());
                })
                .onErrorResume(e -> {
                    exchange.getAttributes().put("toastMessage", "Ошибка при оформлении заказа: " + e.getMessage());
                    exchange.getAttributes().put("toastType", "error");
                    return Mono.just("redirect:" + ordersAction + "/0");
                });
    }
}
