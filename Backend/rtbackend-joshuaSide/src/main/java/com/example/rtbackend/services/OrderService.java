package com.example.rtbackend.services;

import java.util.List;
import java.util.Map;

import com.example.rtbackend.domain.entities.Order;
import com.example.rtbackend.domain.entities.Payment;

public interface OrderService {
    Order createOrder(Long waiterId, Map<Long, Integer> items);
    Order markOrderAsCooking(Long orderId, Long cashierId);
    Order markOrderAsReady(Long orderId, Long chefId);
    Order markOrderAsServed(Long orderId, Long waiterId);
    List<Order> getOrdersByStatus(String status);
    Order getOrderById(Long orderId);
    List<Order> getAllOrders();
    void deleteOrder(Long orderId);
    void deleteAllOrders();
    Payment getPaymentByOrderId(Long orderId);
}