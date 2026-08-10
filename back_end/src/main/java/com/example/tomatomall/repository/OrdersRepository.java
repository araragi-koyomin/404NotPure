package com.example.tomatomall.repository;

import com.example.tomatomall.po.Orders;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.sql.Timestamp;
import java.util.Optional;

public interface OrdersRepository extends JpaRepository<Orders, Integer> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Orders orderRecord "
            + "set orderRecord.status = :paidStatus, "
            + "orderRecord.alipayTradeNo = :tradeNo, "
            + "orderRecord.paidTime = :paidTime "
            + "where orderRecord.orderId = :orderId "
            + "and orderRecord.status = :pendingStatus")
    int markPaidIfPending(
            @Param("orderId") Integer orderId,
            @Param("tradeNo") String tradeNo,
            @Param("paidTime") Timestamp paidTime,
            @Param("pendingStatus") String pendingStatus,
            @Param("paidStatus") String paidStatus
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select orderRecord from Orders orderRecord where orderRecord.orderId = :orderId")
    Optional<Orders> findByIdForUpdate(@Param("orderId") Integer orderId);
}
