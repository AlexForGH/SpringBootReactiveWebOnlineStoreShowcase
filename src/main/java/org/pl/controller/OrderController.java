package org.pl.controller;

import org.pl.service.OrderItemService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.reactive.result.view.Rendering;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.pl.controller.Actions.itemsAction;
import static org.pl.controller.Actions.ordersAction;

@Controller
@RequestMapping()
public class OrderController {

    private final OrderItemService orderItemService;

    public OrderController(OrderItemService orderItemService) {
        this.orderItemService = orderItemService;
    }

    @GetMapping(ordersAction)
    public Mono<Rendering> getOrders() {
        return orderItemService.getOrdersWithItems()
                .map(ordersWithItems -> Rendering.view("orders")
                        .modelAttribute("ordersWithItems", ordersWithItems)
                        .modelAttribute("itemsAction", itemsAction)
                        .modelAttribute("ordersAction", ordersAction)
                        .build());
    }

    @GetMapping(ordersAction + "/{id}")
    public Mono<Rendering> getOrderById(
            @PathVariable Long id,
            ServerWebExchange exchange) {

        return exchange.getSession()
                .flatMap(session -> {
                    // Получаем toast из сессии
                    String toastMessage = (String) session.getAttributes().get("toastMessage");
                    String toastType = (String) session.getAttributes().get("toastType");

                    // Удаляем из сессии после получения
                    session.getAttributes().remove("toastMessage");
                    session.getAttributes().remove("toastType");

                    return orderItemService.getOrderWithItems(id)
                            .map(orderWithItems -> Rendering.view("order")
                                    .modelAttribute("orderWithItems", orderWithItems)
                                    .modelAttribute("ordersAction", ordersAction)
                                    .modelAttribute("itemsAction", itemsAction)
                                    .modelAttribute("toastMessage", toastMessage)
                                    .modelAttribute("toastType", toastType)
                                    .build());
                })
                .switchIfEmpty(Mono.error(new RuntimeException("Order not found")));
    }
}
