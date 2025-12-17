package com.example.rtbackend.services.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.rtbackend.domain.entities.MenuItem;
import com.example.rtbackend.domain.entities.Order;
import com.example.rtbackend.domain.entities.OrderItem;
import com.example.rtbackend.domain.entities.Payment;
import com.example.rtbackend.domain.entities.Role;
import com.example.rtbackend.domain.entities.User;
import com.example.rtbackend.repo.MenuItemRepo;
import com.example.rtbackend.repo.OrderItemRepo;
import com.example.rtbackend.repo.OrderRepo;
import com.example.rtbackend.repo.PaymentRepo;
import com.example.rtbackend.services.OrderService;
import com.example.rtbackend.services.RoleService;
import com.example.rtbackend.services.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepo orderRepo;
    private final PaymentRepo paymentRepo;
    private final UserService userService;
    private final RoleService roleService;
    private final MenuItemRepo menuItemRepo;
    private final OrderItemRepo orderItemRepo;

    private static final String WAITER_ROLE = "WAITER";
    private static final String CHEF_ROLE = "CHEF";

    @Override
    @Transactional
    public Order createOrder(Long waiterId, Map<Long, Integer> items) {
        // Validate waiter
        validateWaiter(waiterId);
        
        User waiter = userService.getUserById(waiterId);
        
        // Validate items map is not empty
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }
        
        // Create order
        Order order = new Order();
        order.setUser(waiter);
        order.setDate(LocalDateTime.now());
        order.setStatus("Pending");
        order.setTotal(0.0);
        
        // Save order first to get ID
        order = orderRepo.save(order);
        
        List<OrderItem> orderItems = new ArrayList<>();
        double totalAmount = 0.0;
        
        // Process each menu item
        for (Map.Entry<Long, Integer> entry : items.entrySet()) {
            Long menuItemId = entry.getKey();
            Integer quantity = entry.getValue();
            
            // Validate quantity
            if (quantity <= 0) {
                throw new IllegalArgumentException("Quantity must be greater than 0 for menu item id: " + menuItemId);
            }
            
            // Get menu item
            MenuItem menuItem = menuItemRepo.findById(menuItemId)
                .orElseThrow(() -> new NoSuchElementException("Menu item not found with id: " + menuItemId));
            
            // Check stock availability
            if (menuItem.getStockQuantity() < quantity) {
                throw new IllegalStateException("Insufficient stock for menu item: " + menuItem.getName() + 
                    ". Available: " + menuItem.getStockQuantity() + ", Requested: " + quantity);
            }
            
            // Update stock (deduct from inventory)
            menuItem.setStockQuantity(menuItem.getStockQuantity() - quantity);
            menuItemRepo.save(menuItem);
            
            // Create order item
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setMenuItem(menuItem);
            orderItem.setQuantity(quantity);
            orderItem.setItemPrice(menuItem.getPrice());
            orderItem.setSubtotal(menuItem.getPrice() * quantity);
            
            orderItems.add(orderItem);
            totalAmount += orderItem.getSubtotal();
        }
        
        // Save all order items
        orderItemRepo.saveAll(orderItems);
        
        // Update order with items and total
        order.setItems(orderItems);
        order.setTotal(totalAmount);
        order = orderRepo.save(order);
        
        return order;
    }

    @Override
    @Transactional
    public Order markOrderAsCooking(Long orderId, Long cashierId) {
        // Get order
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new NoSuchElementException("Order not found with id: " + orderId));
        
        // Check if order is in Pending status
        if (!"Pending".equals(order.getStatus())) {
            throw new IllegalStateException("Order must be in Pending status. Current status: " + order.getStatus());
        }
        
        // Update order status to Preparing
        order.setStatus("Preparing");
        return orderRepo.save(order);
    }

    @Override
    @Transactional
    public Order markOrderAsReady(Long orderId, Long chefId) {
        // Validate chef
        validateChef(chefId);
        
        // Get order
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new NoSuchElementException("Order not found with id: " + orderId));
        
        // Check if order is paid
        Payment payment = paymentRepo.findByOrder_OrderId(orderId)
            .orElseThrow(() -> new IllegalStateException("Order must be paid before marking as ready"));
        
        if (!"Paid".equals(payment.getPaymentStatus())) {
            throw new IllegalStateException("Order payment is not confirmed");
        }
        
        // Check if order is in Preparing status
        if (!"Preparing".equals(order.getStatus())) {
            throw new IllegalStateException("Order must be in Preparing status. Current status: " + order.getStatus());
        }
        
        // Update order status to Ready
        order.setStatus("Ready");
        return orderRepo.save(order);
    }

    @Override
    @Transactional
    public Order markOrderAsServed(Long orderId, Long waiterId) {
        // Get order
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new NoSuchElementException("Order not found with id: " + orderId));
        
        // Check if order is in Ready status
        if (!"Ready".equals(order.getStatus())) {
            throw new IllegalStateException("Order must be in Ready status. Current status: " + order.getStatus());
        }
        
        // Update order status to Served
        order.setStatus("Served");
        return orderRepo.save(order);
    }

    @Override
    public List<Order> getOrdersByStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            throw new IllegalArgumentException("Status parameter is required");
        }
        return orderRepo.findByStatus(status);
    }

    @Override
    public Order getOrderById(Long orderId) {
        return orderRepo.findById(orderId)
            .orElseThrow(() -> new NoSuchElementException("Order not found with id: " + orderId));
    }

    @Override
    public List<Order> getAllOrders() {
        return orderRepo.findAll();
    }

    @Override
    @Transactional
    public void deleteOrder(Long orderId) {
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new NoSuchElementException("Order not found with id: " + orderId));
        
        // Restore stock for deleted order
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            for (OrderItem item : order.getItems()) {
                MenuItem menuItem = item.getMenuItem();
                menuItem.setStockQuantity(menuItem.getStockQuantity() + item.getQuantity());
                menuItemRepo.save(menuItem);
            }
        }
        
        // Delete associated payment if exists
        paymentRepo.findByOrder_OrderId(orderId).ifPresent(payment -> {
            paymentRepo.delete(payment);
        });
        
        // Delete all order items first
        orderItemRepo.deleteAll(order.getItems());
        
        // Delete the order
        orderRepo.delete(order);
    }

    @Override
    @Transactional
    public void deleteAllOrders() {
        List<Order> allOrders = orderRepo.findAll();
        
        for (Order order : allOrders) {
            // Restore stock for each order
            if (order.getItems() != null && !order.getItems().isEmpty()) {
                for (OrderItem item : order.getItems()) {
                    MenuItem menuItem = item.getMenuItem();
                    menuItem.setStockQuantity(menuItem.getStockQuantity() + item.getQuantity());
                    menuItemRepo.save(menuItem);
                }
            }
            
            // Delete associated payment if exists
            paymentRepo.findByOrder_OrderId(order.getOrderId()).ifPresent(payment -> {
                paymentRepo.delete(payment);
            });
            
            // Delete all order items
            orderItemRepo.deleteAll(order.getItems());
        }
        
        // Delete all orders
        orderRepo.deleteAll();
    }

    @Override
    public Payment getPaymentByOrderId(Long orderId) {
        return paymentRepo.findByOrder_OrderId(orderId)
            .orElseThrow(() -> new NoSuchElementException("Payment not found for order id: " + orderId));
    }

    // Role validation methods
    private void validateWaiter(Long userId) {
        User user = userService.getUserById(userId);
        
        if (user.getRole() == null) {
            throw new IllegalStateException("User does not have an assigned role");
        }

        Role waiterRole = roleService.getRoleByName(WAITER_ROLE);
        
        if (!user.getRole().getRoleId().equals(waiterRole.getRoleId())) {
            throw new SecurityException("Access denied. Only Waiters can create orders");
        }
    }

    private void validateChef(Long userId) {
        User user = userService.getUserById(userId);
        
        if (user.getRole() == null) {
            throw new IllegalStateException("User does not have an assigned role");
        }

        Role chefRole = roleService.getRoleByName(CHEF_ROLE);
        
        if (!user.getRole().getRoleId().equals(chefRole.getRoleId())) {
            throw new SecurityException("Access denied. Only Chefs can mark orders as ready");
        }
    }
}