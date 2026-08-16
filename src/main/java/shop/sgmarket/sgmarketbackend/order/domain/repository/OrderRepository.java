package shop.sgmarket.sgmarketbackend.order.domain.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import shop.sgmarket.sgmarketbackend.member.domain.Member;
import shop.sgmarket.sgmarketbackend.order.domain.Order;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByMemberId(Long memberId);

    @Query("""
        select o from Order o
        left join fetch o.payment p
        left join fetch o.member  m
        where o.orderUid = :orderUid
    """)
    Optional<Order> findOrderAndPaymentAndMember(String orderUid);

    @Query("""
        select o from Order o
        left join fetch o.payment p
        where o.orderUid = :orderUid
    """)
    Optional<Order> findOrderAndPayment(String orderUid);

    /* 결제 승인 처리 전용 — 콜백·웹훅이 동시에 도착해도 orderUid 단위로 직렬화되도록 행 락을 건다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select o from Order o
        where o.orderUid = :orderUid
    """)
    Optional<Order> findByOrderUidForUpdate(String orderUid);

    Slice<Order> findByMember(Member member, Pageable pageable);
}

