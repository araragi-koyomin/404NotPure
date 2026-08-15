package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.po.OrderItem;
import com.example.tomatomall.po.OrderStatus;
import com.example.tomatomall.po.Orders;
import com.example.tomatomall.repository.OrdersRepository;
import com.example.tomatomall.repository.StockPileRepository;
import com.example.tomatomall.service.OrderLifecycleService;
import com.example.tomatomall.service.order.OrderExpirationPolicy;
import com.example.tomatomall.vo.OrdersVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.Map;
import java.util.TreeMap;

@Service
public class OrderLifecycleServiceImpl implements OrderLifecycleService {
    private final OrdersRepository ordersRepository;
    private final StockPileRepository stockPileRepository;
    private final OrderExpirationPolicy expirationPolicy;

    public OrderLifecycleServiceImpl(OrdersRepository ordersRepository,
                                     StockPileRepository stockPileRepository,
                                     OrderExpirationPolicy expirationPolicy) {
        this.ordersRepository = ordersRepository;
        this.stockPileRepository = stockPileRepository;
        this.expirationPolicy = expirationPolicy;
    }

    @Override
    @Transactional
    public OrdersVO cancelOrder(int userId, int orderId) {
        Orders order = ordersRepository.findById(orderId)
                .orElseThrow(TomatoException::orderNotExist);
        if (order.getAccount() == null || order.getAccount().getId() == null
                || order.getAccount().getId() != userId) {
            throw TomatoException.noPermission();
        }
        if (OrderStatus.CANCELLED.name().equals(order.getStatus())
                || OrderStatus.CLOSED.name().equals(order.getStatus())) {
            return order.toVO();
        }
        if (!OrderStatus.PENDING.name().equals(order.getStatus())) {
            throw TomatoException.illegalOrderStatusForCancellation();
        }
        return transitionAndRestore(order,
                expirationPolicy.isExpired(order) ? OrderStatus.CLOSED : OrderStatus.CANCELLED).order;
    }

    @Override
    @Transactional
    public boolean closeExpiredOrder(int orderId) {
        Orders order = ordersRepository.findById(orderId).orElse(null);
        if (order == null || !OrderStatus.PENDING.name().equals(order.getStatus())
                || !expirationPolicy.isExpired(order)) {
            return false;
        }
        try {
            return transitionAndRestore(order, OrderStatus.CLOSED).changed;
        } catch (TomatoException exception) {
            if ("409".equals(exception.getCode())) {
                return false;
            }
            throw exception;
        }
    }

    @Override
    @Transactional
    public boolean closeIfExpiredForPayment(int userId, int orderId) {
        Orders order = ordersRepository.findById(orderId)
                .orElseThrow(TomatoException::orderNotExist);
        if (order.getAccount() == null || order.getAccount().getId() == null
                || order.getAccount().getId() != userId) {
            throw TomatoException.noPermission();
        }
        if (!OrderStatus.PENDING.name().equals(order.getStatus()) || !expirationPolicy.isExpired(order)) {
            return false;
        }
        transitionAndRestore(order, OrderStatus.CLOSED);
        return true;
    }

    private TransitionResult transitionAndRestore(Orders order, OrderStatus target) {
        Map<Integer, Integer> quantities = aggregateItems(order);
        Timestamp changedAt = Timestamp.from(expirationPolicy.now());
        int changedRows = target == OrderStatus.CANCELLED
                ? ordersRepository.markCancelledIfPending(order.getOrderId(), changedAt,
                    OrderStatus.PENDING.name(), OrderStatus.CANCELLED.name())
                : ordersRepository.markClosedIfPending(order.getOrderId(), changedAt,
                    OrderStatus.PENDING.name(), OrderStatus.CLOSED.name());
        if (changedRows == 0) {
            Orders current = ordersRepository.findByIdForUpdate(order.getOrderId())
                    .orElseThrow(TomatoException::orderNotExist);
            if (OrderStatus.CANCELLED.name().equals(current.getStatus())
                    || OrderStatus.CLOSED.name().equals(current.getStatus())) {
                return new TransitionResult(current.toVO(), false);
            }
            throw TomatoException.illegalOrderStatusForCancellation();
        }
        if (changedRows != 1) {
            throw TomatoException.orderDataInconsistent();
        }

        for (Map.Entry<Integer, Integer> entry : quantities.entrySet()) {
            int updated = stockPileRepository.restoreFrozenStockIfAvailable(entry.getKey(), entry.getValue());
            if (updated != 1) {
                throw TomatoException.frozenStockRestoreFailure();
            }
        }
        OrdersVO current = ordersRepository.findById(order.getOrderId())
                .orElseThrow(TomatoException::orderNotExist).toVO();
        return new TransitionResult(current, true);
    }

    private Map<Integer, Integer> aggregateItems(Orders order) {
        Map<Integer, Integer> quantities = new TreeMap<>();
        try {
            for (OrderItem item : order.getOrderItems()) {
                if (item == null || item.getProduct() == null
                        || item.getQuantity() == null || item.getQuantity() <= 0) {
                    throw TomatoException.orderDataInconsistent();
                }
                quantities.merge(item.getProduct().getId(), item.getQuantity(), Math::addExact);
            }
        } catch (ArithmeticException exception) {
            throw TomatoException.orderDataInconsistent();
        }
        if (quantities.isEmpty()) {
            throw TomatoException.orderDataInconsistent();
        }
        return quantities;
    }

    private static final class TransitionResult {
        private final OrdersVO order;
        private final boolean changed;

        private TransitionResult(OrdersVO order, boolean changed) {
            this.order = order;
            this.changed = changed;
        }
    }
}
