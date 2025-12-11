package org.pl.service;

import org.pl.dao.Item;
import org.pl.dao.Order;
import org.pl.dao.OrderItem;
import org.pl.dto.ItemInOrderDTO;
import org.pl.dto.OrderWithItemsDTO;
import org.pl.repository.OrderItemRepository;
import org.pl.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrderItemService {

    private final ItemService itemService;
    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;

    public OrderItemService(
            ItemService itemService,
            OrderItemRepository orderItemRepository,
            OrderRepository orderRepository
    ) {
        this.itemService = itemService;
        this.orderItemRepository = orderItemRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public Mono<List<OrderWithItemsDTO>> getOrdersWithItems() {
        return orderItemRepository.findAllWithAssociations()
                .collectList()
                .flatMap(orderItems -> {
                    // Получаем уникальные order_id и item_id
                    List<Long> orderIds = orderItems.stream()
                            .map(OrderItem::getOrderId)
                            .distinct()
                            .toList();
                    List<Long> itemIds = orderItems.stream()
                            .map(OrderItem::getItemId)
                            .distinct()
                            .toList();

                    // Загружаем все заказы и товары параллельно
                    Mono<Map<Long, Order>> ordersMapMono = Flux.fromIterable(orderIds)
                            .flatMap(orderRepository::findById)
                            .collectMap(Order::getId);

                    Mono<Map<Long, Item>> itemsMapMono = itemService.getItemByIds(itemIds).collectMap(Item::getId);

                    return Mono.zip(ordersMapMono, itemsMapMono)
                            .map(tuple -> {
                                Map<Long, Order> ordersMap = tuple.getT1();
                                Map<Long, Item> itemsMap = tuple.getT2();

                                // Группируем по order_id
                                Map<Long, List<OrderItem>> itemsByOrder = orderItems.stream()
                                        .collect(Collectors.groupingBy(OrderItem::getOrderId));

                                // Создаем DTO
                                return itemsByOrder.entrySet().stream()
                                        .map(entry -> {
                                            Long orderId = entry.getKey();
                                            List<OrderItem> items = entry.getValue();

                                            Order order = ordersMap.get(orderId);
                                            if (order == null) {
                                                return null;
                                            }

                                            // Заполняем связанные объекты
                                            items.forEach(oi -> {
                                                oi.setOrder(order);
                                                oi.setItem(itemsMap.get(oi.getItemId()));
                                            });

                                            List<ItemInOrderDTO> itemDTOs = items.stream()
                                                    .map(item -> new ItemInOrderDTO(item.getItem(), item.getQuantity()))
                                                    .toList();

                                            return new OrderWithItemsDTO(order, itemDTOs);
                                        })
                                        .filter(dto -> dto != null)
                                        .toList();
                            });
                });
    }

    @Transactional(readOnly = true)
    public Mono<OrderWithItemsDTO> getOrderWithItems(Long orderId) {
        return Mono.zip(
                        orderRepository.findById(orderId)
                                .switchIfEmpty(Mono.error(new RuntimeException("Заказ с ID " + orderId + " не найден"))),
                        orderItemRepository.findByOrderIdWithAssociations(orderId).collectList()
                )
                .flatMap(tuple -> {
                    Order order = tuple.getT1();
                    List<OrderItem> orderItems = tuple.getT2();

                    if (orderItems.isEmpty()) {
                        return Mono.error(new RuntimeException("Заказ с ID " + orderId + " не найден"));
                    }

                    // Загружаем все товары
                    List<Long> itemIds = orderItems.stream()
                            .map(OrderItem::getItemId)
                            .distinct()
                            .toList();

                    return itemService.getItemByIds(itemIds)
                            .collectMap(Item::getId)
                            .map(itemsMap -> {
                                // Заполняем связанные объекты
                                orderItems.forEach(oi -> {
                                    oi.setOrder(order);
                                    oi.setItem(itemsMap.get(oi.getItemId()));
                                });

                                List<ItemInOrderDTO> itemDTOs = orderItems.stream()
                                        .map(item -> new ItemInOrderDTO(item.getItem(), item.getQuantity()))
                                        .toList();

                                return new OrderWithItemsDTO(order, itemDTOs);
                            });
                });
    }

    @Transactional()
    public Mono<Void> saveOrder(Order order, Map<Long, Integer> cartItems) {
        return Flux.fromIterable(cartItems.entrySet())
                .flatMap(entry -> {
                    Long itemId = entry.getKey();
                    Integer quantity = entry.getValue();

                    return itemService.getItemById(itemId)
                            .map(item -> new OrderItem(order.getId(), itemId, quantity))
                            .flatMap(orderItemRepository::save);
                })
                .then();
    }
}
