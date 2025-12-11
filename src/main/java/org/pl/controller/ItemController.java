package org.pl.controller;

import org.pl.dao.Item;
import org.pl.dto.PagingInfoDto;
import org.pl.service.ItemService;
import org.pl.service.SessionItemsCountsService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.result.view.Rendering;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.pl.controller.Actions.*;


@Controller
@RequestMapping()
public class ItemController {

    private final ItemService itemService;
    private final SessionItemsCountsService sessionItemsCountsService;

    public ItemController(
            ItemService itemService,
            SessionItemsCountsService sessionItemsCountsService
    ) {
        this.itemService = itemService;
        this.sessionItemsCountsService = sessionItemsCountsService;
    }

    @GetMapping()
    public Mono<String> redirectToItems() {
        return Mono.just("redirect:" + itemsAction);
    }

    @GetMapping(itemsAction)
    public Mono<Rendering> getItemsSorted(
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "5") int pageSize,
            @RequestParam(defaultValue = "NO") String sort,
            @RequestParam(required = false) String search,
            ServerWebExchange exchange
    ) {
        Pageable pageable = PageRequest.of(pageNumber - 1, pageSize);

        return Mono.zip(
                        itemService.getItemsSorted(pageable, sort, search),
                        sessionItemsCountsService.getCartItems(exchange),
                        sessionItemsCountsService.checkItemsCount(exchange)
                )
                .map(tuple -> {
                    Page<List<Item>> itemPage = tuple.getT1();
                    var cartItems = tuple.getT2();
                    Integer totalItemsCounts = tuple.getT3();

                    return Rendering.view("items")
                            .modelAttribute("items", itemPage.getContent())
                            .modelAttribute("sort", sort)
                            .modelAttribute("search", search)
                            .modelAttribute("cartItems", cartItems)
                            .modelAttribute("totalItemsCounts", totalItemsCounts)
                            .modelAttribute("paging", new PagingInfoDto(
                                    itemPage.getNumber() + 1,
                                    itemPage.getTotalPages(),
                                    itemPage.getSize(),
                                    itemPage.hasPrevious(),
                                    itemPage.hasNext()
                            ))
                            .modelAttribute("ordersAction", ordersAction)
                            .modelAttribute("cartAction", cartAction)
                            .modelAttribute("itemsAction", itemsAction)
                            .modelAttribute("itemsToCartAction", itemsToCartAction)
                            .build();
                });
    }

    @PostMapping(value = itemsAction, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Mono<String> increaseDecreaseItemsCount(ServerWebExchange exchange) {
        return exchange.getFormData()
                .flatMap(formData -> {
                    try {
                        // Получаем параметры
                        String idStr = formData.getFirst("id");
                        String action = formData.getFirst("action");
                        String search = formData.getFirst("search");
                        String sort = formData.getFirst("sort");
                        String pageSizeStr = formData.getFirst("pageSize");
                        String pageNumberStr = formData.getFirst("pageNumber");

                        // Проверяем обязательные поля
                        if (idStr == null || idStr.trim().isEmpty()) {
                            return Mono.error(new IllegalArgumentException("id is required"));
                        }
                        if (action == null || action.trim().isEmpty()) {
                            return Mono.error(new IllegalArgumentException("action is required"));
                        }

                        // Парсим с проверками
                        Long id = Long.parseLong(idStr.trim());
                        int pageSize = parseOrDefault(pageSizeStr, 5);
                        int pageNumber = parseOrDefault(pageNumberStr, 1);

                        if (sort == null || sort.trim().isEmpty()) {
                            sort = "NO";
                        }

                        // Выполняем действие и редирект
                        return sessionItemsCountsService.updateItemCount(exchange, id, action)
                                .thenReturn(buildRedirectUrl(pageNumber, pageSize, sort, search));

                    } catch (NumberFormatException e) {
                        return Mono.error(new IllegalArgumentException("Invalid number format", e));
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                })
                .onErrorResume(e -> {
                    // В случае ошибки - редирект на главную страницу items
                    return Mono.just("redirect:" + itemsAction);
                });
    }

    private int parseOrDefault(String str, int defaultValue) {
        if (str == null || str.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String buildRedirectUrl(int pageNumber, int pageSize, String sort, String search) {
        StringBuilder url = new StringBuilder("redirect:").append(itemsAction);
        url.append("?pageNumber=").append(pageNumber);
        url.append("&pageSize=").append(pageSize);

        if (sort != null && !sort.isEmpty() && !"NO".equals(sort)) {
            url.append("&sort=").append(sort);
        }
        if (search != null && !search.trim().isEmpty()) {
            url.append("&search=").append(search);
        }

        return url.toString();
    }

    @GetMapping(itemsToCartAction)
    public Mono<String> redirectToItemsToCart(
            ServerWebExchange exchange
    ) {
        return sessionItemsCountsService.getCartItems(exchange)
                .thenReturn("redirect:" + cartAction);
    }

    @GetMapping(itemsAction + "/{id}")
    public Mono<Rendering> getItemById(
            @PathVariable Long id,
            ServerWebExchange exchange) {
        return Mono.zip(
                        itemService.getItemById(id).switchIfEmpty(Mono.error(new RuntimeException("Item not found"))),
                        sessionItemsCountsService.getCartItems(exchange),
                        sessionItemsCountsService.checkItemsCount(exchange)
                )
                .map(tuple -> {
                    Item item = tuple.getT1();
                    var cartItems = tuple.getT2();
                    Integer totalItemsCounts = tuple.getT3();
                    Integer itemCount = cartItems.get(id);

                    return Rendering.view("item")
                            .modelAttribute("item", item)
                            .modelAttribute("ordersAction", ordersAction)
                            .modelAttribute("cartAction", cartAction)
                            .modelAttribute("itemsAction", itemsAction)
                            .modelAttribute("itemCounts", itemCount != null ? itemCount : 0)
                            .modelAttribute("itemsToCartAction", itemsToCartAction)
                            .modelAttribute("totalItemsCounts", totalItemsCounts)
                            .modelAttribute("buyAction", buyAction)
                            .build();
                });
    }

    @PostMapping(value = itemsAction + "/{id}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Mono<String> increaseDecreaseItemCount(
            @PathVariable Long id,
            ServerWebExchange exchange) {
        return exchange.getFormData()
                .flatMap(formData -> {
                    String action = formData.getFirst("action");
                    return sessionItemsCountsService.updateItemCount(exchange, id, action)
                            .thenReturn("redirect:" + itemsAction + "/" + id);
                });
    }
}
