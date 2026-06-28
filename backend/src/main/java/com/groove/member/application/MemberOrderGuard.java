package com.groove.member.application;

/**
 * 회원 탈퇴를 막아야 할 "진행 중" 주문이 있는지 확인하는 읽기 전용 포트.
 *
 * 차단 대상 상태 판단은 주문 도메인이 소유하므로 member 는 이 인터페이스만 의존하고 order 가 구현한다 —
 * member→order 역참조를 끊어 슬라이스 단방향(order→member)을 유지한다.
 */
public interface MemberOrderGuard {

    /** 해당 회원에게 탈퇴를 차단하는 진행 중 주문이 하나라도 있으면 true. */
    boolean hasBlockingOrders(Long memberId);
}
