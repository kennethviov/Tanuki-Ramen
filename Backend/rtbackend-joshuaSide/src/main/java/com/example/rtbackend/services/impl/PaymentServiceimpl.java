package com.example.rtbackend.services.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.rtbackend.domain.entities.Order;
import com.example.rtbackend.domain.entities.Payment;
import com.example.rtbackend.domain.entities.Role;
import com.example.rtbackend.domain.entities.User;
import com.example.rtbackend.repo.OrderRepo;
import com.example.rtbackend.repo.PaymentRepo;
import com.example.rtbackend.services.PaymentService;
import com.example.rtbackend.services.RoleService;
import com.example.rtbackend.services.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentServiceimpl implements PaymentService {

    private final PaymentRepo paymentRepo;
    private final OrderRepo orderRepo;
    private final UserService userService;
    private final RoleService roleService;

    private static final String CASHIER_ROLE = "CASHIER";

    @Override
    @Transactional
    public Payment processPayment(Long orderId, String paymentMethod, Long cashierId) {
        
        validateCashier(cashierId);
        
        User cashier = userService.getUserById(cashierId);
        
        // Get order
        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new NoSuchElementException("Order not found with id: " + orderId));
        
        // Check if order is cancelled
        if ("Cancelled".equals(order.getStatus())) {
            throw new IllegalStateException("Cannot process payment for a cancelled order");
        }
        
        // Check if order is already paid
        paymentRepo.findByOrder_OrderId(orderId).ifPresent(p -> {
            if ("Paid".equals(p.getPaymentStatus())) {
                throw new IllegalStateException("Order is already paid");
            }
        });
        
        // Validate payment method
        if (paymentMethod == null || paymentMethod.trim().isEmpty()) {
            throw new IllegalArgumentException("Payment method is required");
        }
        
        // Create payment
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(order.getTotal());
        payment.setPaymentStatus("Paid");
        payment.setPaymentMethod(paymentMethod);
        payment.setCashier(cashier);
        payment.setCreatedAt(LocalDateTime.now());
        payment.setProcessedAt(LocalDateTime.now());
        
        payment = paymentRepo.save(payment);
        
        // Update order status to Preparing (after payment is confirmed)
        order.setStatus("Preparing");
        orderRepo.save(order);
        
        return payment;
    }

    @Override
    public Payment getPaymentById(Long paymentId) {
        return paymentRepo.findById(paymentId)
            .orElseThrow(() -> new NoSuchElementException("Payment not found with id: " + paymentId));
    }

    @Override
    public Payment getPaymentByOrderId(Long orderId) {
        return paymentRepo.findByOrder_OrderId(orderId)
            .orElseThrow(() -> new NoSuchElementException("Payment not found for order id: " + orderId));
    }

    @Override
    public List<Payment> getAllPayments() {
        return paymentRepo.findAll();
    }

    @Override
    public List<Payment> getPaymentsByStatus(String paymentStatus) {
        if (paymentStatus == null || paymentStatus.trim().isEmpty()) {
            throw new IllegalArgumentException("Payment status parameter is required");
        }
        return paymentRepo.findByPaymentStatus(paymentStatus);
    }

    private void validateCashier(Long userId) {
        User user = userService.getUserById(userId);
        
        if (user.getRole() == null) {
            throw new IllegalStateException("User does not have an assigned role");
        }

        Role cashierRole = roleService.getRoleByName(CASHIER_ROLE);
        
        if (!user.getRole().getRoleId().equals(cashierRole.getRoleId())) {
            throw new SecurityException("Access denied. Only Cashiers can process payments");
        }
    }
}