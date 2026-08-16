package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.dto.CreateOrderDTO;
import com.example.tomatomall.exception.OrderCheckoutConflictException;
import com.example.tomatomall.exception.OrderCheckoutUnavailableException;
import com.example.tomatomall.po.Orders;
import com.example.tomatomall.repository.OrdersRepository;
import com.example.tomatomall.service.OrderService;
import com.example.tomatomall.service.order.NormalizedCheckoutRequest;
import com.example.tomatomall.service.order.OrderCheckoutRequestNormalizer;
import com.example.tomatomall.service.order.OrderCheckoutResult;
import com.example.tomatomall.service.order.OrderCheckoutTransactionService;
import com.example.tomatomall.service.order.OrderIdempotencyKey;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionTimedOutException;

import java.util.Locale;
import java.util.Optional;
import java.sql.SQLException;
import java.util.concurrent.locks.LockSupport;

@Service
public class OrderServiceImpl implements OrderService {

    private static final String METRIC_NAME = "tomatomall.order.checkout.requests";
    private static final String IDEMPOTENCY_CONSTRAINT = "uk_orders_user_idempotency_key";

    private final OrdersRepository ordersRepository;
    private final OrderCheckoutRequestNormalizer requestNormalizer;
    private final OrderCheckoutTransactionService transactionService;
    private final MeterRegistry meterRegistry;

    public OrderServiceImpl(OrdersRepository ordersRepository,
                            OrderCheckoutRequestNormalizer requestNormalizer,
                            OrderCheckoutTransactionService transactionService,
                            MeterRegistry meterRegistry) {
        this.ordersRepository = ordersRepository;
        this.requestNormalizer = requestNormalizer;
        this.transactionService = transactionService;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public OrderCheckoutResult addOrder(Integer userId,
                                        String idempotencyKey,
                                        CreateOrderDTO dto) {
        try {
            OrderCheckoutResult result = execute(userId, idempotencyKey, dto);
            increment(result.isReplayed() ? "replayed" : "created");
            return result;
        } catch (OrderCheckoutConflictException exception) {
            increment("conflict");
            throw exception;
        } catch (OrderCheckoutUnavailableException exception) {
            increment("timeout");
            throw exception;
        } catch (RuntimeException exception) {
            increment("failed");
            throw exception;
        }
    }

    private OrderCheckoutResult execute(Integer userId,
                                        String idempotencyKey,
                                        CreateOrderDTO dto) {
        String canonicalKey = OrderIdempotencyKey.requireCanonical(idempotencyKey);
        NormalizedCheckoutRequest request = requestNormalizer.normalize(dto);
        Optional<Orders> existing = ordersRepository
                .findByAccountIdAndIdempotencyKey(userId, canonicalKey);
        if (existing.isPresent()) {
            return replayOrConflict(existing.get(), request);
        }

        try {
            Orders created = transactionService.create(userId, canonicalKey, request);
            return OrderCheckoutResult.created(created.toVO());
        } catch (DataIntegrityViolationException exception) {
            if (!causedByNamedIdempotencyConstraint(exception)) {
                throw exception;
            }
            Orders winner = ordersRepository.findByAccountIdAndIdempotencyKey(userId, canonicalKey)
                    .orElseThrow(() -> exception);
            return replayOrConflict(winner, request);
        } catch (RuntimeException exception) {
            if (isDeadlock(exception)) {
                Optional<Orders> winner = waitForDeadlockWinner(userId, canonicalKey);
                if (winner.isPresent()) {
                    return replayOrConflict(winner.get(), request);
                }
            }
            if (isLockOrTransactionTimeout(exception)) {
                throw new OrderCheckoutUnavailableException(exception);
            }
            throw exception;
        }
    }

    private OrderCheckoutResult replayOrConflict(Orders existing,
                                                  NormalizedCheckoutRequest request) {
        if (!request.getFingerprint().equals(existing.getRequestFingerprint())) {
            throw new OrderCheckoutConflictException();
        }
        return OrderCheckoutResult.replayed(existing.toVO());
    }

    private boolean causedByNamedIdempotencyConstraint(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT)
                    .contains(IDEMPOTENCY_CONSTRAINT)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isLockOrTransactionTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof CannotAcquireLockException
                    || current instanceof PessimisticLockingFailureException
                    || current instanceof QueryTimeoutException
                    || current instanceof TransactionTimedOutException) {
                return true;
            }
            String className = current.getClass().getName();
            if ("org.hibernate.exception.LockAcquisitionException".equals(className)
                    || "org.hibernate.PessimisticLockException".equals(className)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isDeadlock(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException) {
                SQLException sqlException = (SQLException) current;
                if (sqlException.getErrorCode() == 1213
                        || "40001".equals(sqlException.getSQLState())) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private Optional<Orders> waitForDeadlockWinner(Integer userId, String idempotencyKey) {
        for (int attempt = 0; attempt < 50; attempt++) {
            Optional<Orders> winner = ordersRepository
                    .findByAccountIdAndIdempotencyKey(userId, idempotencyKey);
            if (winner.isPresent()) {
                return winner;
            }
            if (Thread.currentThread().isInterrupted()) {
                return Optional.empty();
            }
            LockSupport.parkNanos(10_000_000L);
        }
        return Optional.empty();
    }

    private void increment(String outcome) {
        meterRegistry.counter(METRIC_NAME, "outcome", outcome).increment();
    }
}
