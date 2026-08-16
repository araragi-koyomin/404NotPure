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
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface OrdersRepository extends JpaRepository<Orders, Integer> {

    Optional<Orders> findByAccountIdAndIdempotencyKey(Integer userId, String idempotencyKey);

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

    @Query("select orderRecord.orderId from Orders orderRecord "
            + "where orderRecord.status=:status and orderRecord.createTime<=:cutoff "
            + "order by orderRecord.createTime asc, orderRecord.orderId asc")
    List<Integer> findExpiredPendingOrderIds(
            @Param("status") String status,
            @Param("cutoff") Timestamp cutoff,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Orders orderRecord set orderRecord.status=:cancelledStatus, "
            + "orderRecord.cancelledTime=:cancelledTime where orderRecord.orderId=:orderId "
            + "and orderRecord.status=:pendingStatus")
    int markCancelledIfPending(@Param("orderId") Integer orderId,
                               @Param("cancelledTime") Timestamp cancelledTime,
                               @Param("pendingStatus") String pendingStatus,
                               @Param("cancelledStatus") String cancelledStatus);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Orders orderRecord set orderRecord.status=:closedStatus, "
            + "orderRecord.closedTime=:closedTime where orderRecord.orderId=:orderId "
            + "and orderRecord.status=:pendingStatus")
    int markClosedIfPending(@Param("orderId") Integer orderId,
                            @Param("closedTime") Timestamp closedTime,
                            @Param("pendingStatus") String pendingStatus,
                            @Param("closedStatus") String closedStatus);
}
