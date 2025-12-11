package org.pl.service;

import org.pl.dao.Item;
import org.pl.repository.ItemRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class ItemService {

    private final ItemRepository itemRepository;

    public ItemService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    public Flux<Item> getItemByIds(List<Long> ids) {
        return Flux.fromIterable(ids).flatMap(itemRepository::findById);
    }

    @Transactional(readOnly = true)
    public Mono<Page<List<Item>>> getItemsSorted(Pageable pageable, String sortBy, String title) {
        Mono<List<Item>> itemsMono;
        Mono<Long> countMono;

        if (title == null || title.isEmpty()) {
            itemsMono = itemRepository.findAll()
                    .collectList();
            countMono = itemRepository.count();
        } else {
            itemsMono = itemRepository.findByTitleContainingIgnoreCase(title)
                    .collectList();
            countMono = itemRepository.findByTitleContainingIgnoreCase(title)
                    .count();
        }

        return Mono.zip(itemsMono, countMono)
                .map(tuple -> {
                    List<Item> allItems = tuple.getT1();
                    Long totalCount = tuple.getT2();

                    // Применяем сортировку
                    List<Item> sortedItems = sortItems(allItems, sortBy);

                    // Применяем пагинацию
                    int start = pageable.getPageNumber() * pageable.getPageSize();
                    int end = Math.min(start + pageable.getPageSize(), sortedItems.size());
                    List<Item> pagedItems = sortedItems.subList(start, end);

                    // Разбиваем на чанки по 3
                    List<List<Item>> chunkedItems = chunkList(pagedItems);

                    return new PageImpl<>(
                            chunkedItems,
                            pageable,
                            totalCount
                    );
                });
    }

    private List<Item> sortItems(List<Item> items, String sortBy) {
        return switch (sortBy) {
            case "PRICE_ASC" -> items.stream()
                    .sorted(Comparator.comparing(Item::getPrice))
                    .collect(Collectors.toList());
            case "PRICE_DESC" -> items.stream()
                    .sorted(Comparator.comparing(Item::getPrice).reversed())
                    .collect(Collectors.toList());
            case "ALPHA_ASC" -> items.stream()
                    .sorted(Comparator.comparing(item -> item.getTitle().toLowerCase()))
                    .collect(Collectors.toList());
            case "ALPHA_DESC" -> items.stream()
                    .sorted(Comparator.comparing((Item item) -> item.getTitle().toLowerCase()).reversed())
                    .collect(Collectors.toList());
            case "NO" -> items;
            default -> throw new IllegalStateException("Unexpected value: " + sortBy);
        };
    }

    @Transactional(readOnly = true)
    public Mono<Item> getItemById(Long id) {
        return itemRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Mono<BigDecimal> getPriceById(Long id) {
        return getItemById(id)
                .map(Item::getPrice)
                .switchIfEmpty(Mono.error(new RuntimeException("Item not found")));
    }

    private List<List<Item>> chunkList(List<Item> items) {
        int chunkSize = 3;
        return IntStream.range(0, (items.size() + chunkSize - 1) / chunkSize)
                .mapToObj(i -> items.subList(
                        i * chunkSize,
                        Math.min(items.size(), (i + 1) * chunkSize)
                ))
                .collect(Collectors.toList());
    }
}
