package org.pl.controller;

import org.pl.service.OrderItemService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.reactive.result.view.Rendering;
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
    public Mono<Rendering> getOrderById(@PathVariable Long id) {
        return orderItemService.getOrderWithItems(id)
                .map(orderWithItems -> Rendering.view("order")
                        .modelAttribute("orderWithItems", orderWithItems)
                        .modelAttribute("itemsAction", itemsAction)
                        .modelAttribute("ordersAction", ordersAction)
                        .build())
                .onErrorResume(e -> Mono.just(Rendering.view("order")
                        .modelAttribute("error", "Заказ не найден")
                        .modelAttribute("itemsAction", itemsAction)
                        .modelAttribute("ordersAction", ordersAction)
                        .build()));
    }
}
