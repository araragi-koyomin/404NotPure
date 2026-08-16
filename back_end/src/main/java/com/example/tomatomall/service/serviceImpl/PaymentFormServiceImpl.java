package com.example.tomatomall.service.serviceImpl;

import com.alipay.api.AlipayApiException;
import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.po.OrderStatus;
import com.example.tomatomall.po.Orders;
import com.example.tomatomall.repository.OrdersRepository;
import com.example.tomatomall.service.PaymentFormGateway;
import com.example.tomatomall.service.PaymentFormService;
import com.example.tomatomall.service.OrderLifecycleService;
import com.example.tomatomall.service.order.OrderExpirationPolicy;
import com.example.tomatomall.dto.PaymentData;
import org.springframework.stereotype.Service;

@Service
public class PaymentFormServiceImpl implements PaymentFormService {
    private final OrdersRepository ordersRepository;
    private final PaymentFormGateway paymentFormGateway;
    private final OrderLifecycleService orderLifecycleService;
    private final OrderExpirationPolicy expirationPolicy;

    public PaymentFormServiceImpl(
            OrdersRepository ordersRepository,
            PaymentFormGateway paymentFormGateway,
            OrderLifecycleService orderLifecycleService,
            OrderExpirationPolicy expirationPolicy
    ) {
        this.ordersRepository = ordersRepository;
        this.paymentFormGateway = paymentFormGateway;
        this.orderLifecycleService = orderLifecycleService;
        this.expirationPolicy = expirationPolicy;
    }

    @Override
    public PaymentData createPaymentForm(int userId, int orderId) throws AlipayApiException {
        if (orderLifecycleService.closeIfExpiredForPayment(userId, orderId)) {
            throw TomatoException.orderNoLongerPayable();
        }
        Orders order = ordersRepository.findById(orderId)
                .orElseThrow(TomatoException::orderNotExist);
        if (order.getAccount() == null || order.getAccount().getId() == null
                || order.getAccount().getId() != userId) {
            throw TomatoException.noPermission();
        }
        if (OrderStatus.CANCELLED.name().equals(order.getStatus())
                || OrderStatus.CLOSED.name().equals(order.getStatus())) {
            throw TomatoException.orderNoLongerPayable();
        }
        if (!OrderStatus.PENDING.name().equals(order.getStatus())) {
            throw TomatoException.illegalOrderStatusForPayment();
        }

        PaymentData paymentData = new PaymentData();
        paymentData.setPaymentForm(paymentFormGateway.createPaymentForm(order, expirationPolicy.expiresAt(order)));
        paymentData.setOrderId(String.valueOf(order.getOrderId()));
        paymentData.setTotalAmount(order.getTotalAmount().toPlainString());
        paymentData.setPaymentMethod("Alipay");
        return paymentData;
    }
}
