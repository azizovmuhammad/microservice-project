package org.example.orderservice.service;

import lombok.RequiredArgsConstructor;
import org.example.orderservice.config.WebClientConfig;
import org.example.orderservice.dto.InventoryResponse;
import org.example.orderservice.dto.OrderLineItemDto;
import org.example.orderservice.dto.OrderRequest;
import org.example.orderservice.model.Order;
import org.example.orderservice.model.OrderLineItem;
import org.example.orderservice.repository.OrderLineItemRepository;
import org.example.orderservice.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Stream;

@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderLineItemRepository orderLineItemRepository;
    private final WebClient webClient;

    public void createOrder(OrderRequest orderRequest) throws IllegalAccessException {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());
        List<OrderLineItem> orderLineItems = orderRequest
                .getOrderLineItemDtos()
                .stream()
                .map(this::mapToDto)
                .toList();
        order.setOrderLineItems(orderLineItems);
        BigDecimal totalPrice = BigDecimal.ZERO;
        for (OrderLineItem orderLineItem : orderLineItems) {
            totalPrice = totalPrice.add(orderLineItem.getPrice().multiply(BigDecimal.valueOf(orderLineItem.getQuantity())));
        }
        order.setTotalPrice(totalPrice);
        List<String> skuCodes = order.getOrderLineItems()
                .stream()
                .map(OrderLineItem::getSkuCode)
                .toList();
        InventoryResponse[] inventoryResponses = webClient.get()
                .uri("http://localhost:8082/api/v1/inventory",
                        uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .block();

        boolean allMatch = false;
        if (inventoryResponses != null) {
            allMatch = Arrays.stream(inventoryResponses).allMatch(InventoryResponse::isInStock);
        }

        if (allMatch) {
            orderRepository.save(order);
        } else {
            throw new IllegalAccessException("Product is not in stack, please try again later");
        }

    }

    private OrderLineItem mapToDto(OrderLineItemDto orderLineItemDto) {
        OrderLineItem orderLineItem = new OrderLineItem();
        orderLineItem.setSkuCode(orderLineItemDto.getSkuCode());
        orderLineItem.setQuantity(orderLineItemDto.getQuantity());
        orderLineItem.setPrice(orderLineItemDto.getPrice());
        orderLineItemRepository.save(orderLineItem);
        return orderLineItem;
    }
}
