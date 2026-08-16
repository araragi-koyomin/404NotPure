package com.example.tomatomall.service.order;

import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.po.Account;
import com.example.tomatomall.po.OrderItem;
import com.example.tomatomall.po.OrderStatus;
import com.example.tomatomall.po.Orders;
import com.example.tomatomall.po.Product;
import com.example.tomatomall.repository.OrdersRepository;
import com.example.tomatomall.repository.ProductRepository;
import com.example.tomatomall.repository.StockPileRepository;
import com.example.tomatomall.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrderCheckoutTransactionService {

    private final UserRepository userRepository;
    private final OrdersRepository ordersRepository;
    private final ProductRepository productRepository;
    private final StockPileRepository stockPileRepository;

    public OrderCheckoutTransactionService(UserRepository userRepository,
                                           OrdersRepository ordersRepository,
                                           ProductRepository productRepository,
                                           StockPileRepository stockPileRepository) {
        this.userRepository = userRepository;
        this.ordersRepository = ordersRepository;
        this.productRepository = productRepository;
        this.stockPileRepository = stockPileRepository;
    }

    @Transactional
    public Orders create(Integer userId,
                         String idempotencyKey,
                         NormalizedCheckoutRequest request) {
        Account account = userRepository.findById(userId).orElseThrow(TomatoException::userNotExist);
        List<Integer> productIds = new ArrayList<>(request.getQuantitiesByProduct().keySet());
        Map<Integer, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));
        if (products.size() != productIds.size()) {
            throw TomatoException.productNotExist();
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Map.Entry<Integer, Integer> entry : request.getQuantitiesByProduct().entrySet()) {
            totalAmount = totalAmount.add(
                    products.get(entry.getKey()).getPrice().multiply(BigDecimal.valueOf(entry.getValue())));
        }

        Orders order = new Orders();
        order.setAccount(account);
        order.setPaymentMethod(request.getPaymentMethod());
        order.setStatus(OrderStatus.PENDING.name());
        order.setCreateTime(new Timestamp(System.currentTimeMillis()));
        order.setTotalAmount(totalAmount);
        order.setIdempotencyKey(idempotencyKey);
        order.setRequestFingerprint(request.getFingerprint());

        // Flush the complete order first. The database unique constraint becomes the
        // cross-process ownership decision before any inventory can be frozen twice.
        ordersRepository.saveAndFlush(order);

        List<OrderItem> orderItems = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : request.getQuantitiesByProduct().entrySet()) {
            Integer productId = entry.getKey();
            Integer quantity = entry.getValue();
            int updatedRows = stockPileRepository.freezeStockIfAvailable(productId, quantity);
            if (updatedRows == 0) {
                if (stockPileRepository.countByProductId(productId) > 1) {
                    throw TomatoException.stockDataInconsistent();
                }
                throw TomatoException.stockNotEnough();
            }
            if (updatedRows != 1) {
                throw TomatoException.stockDataInconsistent();
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(products.get(productId));
            orderItem.setQuantity(quantity);
            orderItem.setOrder(order);
            orderItems.add(orderItem);
        }
        order.getOrderItems().addAll(orderItems);
        return ordersRepository.saveAndFlush(order);
    }
}
