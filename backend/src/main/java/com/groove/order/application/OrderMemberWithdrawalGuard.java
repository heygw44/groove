package com.groove.order.application;

import com.groove.member.application.MemberOrderGuard;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * 회원 탈퇴 차단 가드의 {@link MemberOrderGuard} 구현(member→order 역참조 차단, #349).
 * 차단 대상 "진행 중" 상태 정의를 주문 도메인 안에 둔다.
 */
@Component
public class OrderMemberWithdrawalGuard implements MemberOrderGuard {

    /** 탈퇴를 차단하는 "진행 중" 주문 상태 — PAID/PREPARING/SHIPPED. */
    private static final Set<OrderStatus> WITHDRAWAL_BLOCKING_STATUSES =
            EnumSet.of(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.SHIPPED);

    private final OrderRepository orderRepository;

    public OrderMemberWithdrawalGuard(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public boolean hasBlockingOrders(Long memberId) {
        return orderRepository.existsByMemberIdAndStatusIn(memberId, WITHDRAWAL_BLOCKING_STATUSES);
    }
}
