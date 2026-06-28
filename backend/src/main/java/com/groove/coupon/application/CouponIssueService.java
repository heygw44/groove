package com.groove.coupon.application;

import com.groove.coupon.api.dto.MemberCouponResponse;
import com.groove.coupon.domain.Coupon;
import com.groove.coupon.domain.CouponRepository;
import com.groove.coupon.domain.MemberCoupon;
import com.groove.coupon.domain.MemberCouponRepository;
import com.groove.coupon.exception.CouponAlreadyIssuedException;
import com.groove.coupon.exception.CouponNotFoundException;
import com.groove.coupon.exception.CouponNotIssuableException;
import com.groove.coupon.exception.CouponSoldOutException;
import com.groove.member.domain.MemberRepository;
import com.groove.member.exception.MemberNotFoundException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;

/**
 * 선착순 쿠폰 발급. 세 경로 — issue(원자적 조건부 UPDATE), issueWithoutLock(lost-update 초과발급 재현),
 * issueWithPessimisticLock(FOR UPDATE 행 락).
 *
 * 회원당 1장: 사전 검사로 거르고, 통과한 동시 중복은 UNIQUE 제약이 잡아 트랜잭션 롤백(증가시킨 카운터도 복원).
 */
@Service
public class CouponIssueService {

    private static final String MEMBER_COUPON_UNIQUE_CONSTRAINT = "uk_member_coupon_coupon_member";

    private final CouponRepository couponRepository;
    private final MemberCouponRepository memberCouponRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    public CouponIssueService(CouponRepository couponRepository,
                              MemberCouponRepository memberCouponRepository,
                              MemberRepository memberRepository,
                              Clock clock) {
        this.couponRepository = couponRepository;
        this.memberCouponRepository = memberCouponRepository;
        this.memberRepository = memberRepository;
        this.clock = clock;
    }

    /**
     * 원자적 조건부 UPDATE — 검사와 증가를 단일 UPDATE 로(affected=1 성공 / 0 발급불가).
     * UPDATE 의 기간 조건이 만료 경계 TOCTOU 를 닫는다.
     */
    @Transactional
    public MemberCouponResponse issue(Long memberId, Long couponId) {
        if (!memberRepository.existsByIdAndDeletedAtIsNull(memberId)) {
            throw new MemberNotFoundException();
        }
        validateIssuable(couponId);
        requireNotYetIssued(couponId, memberId);

        if (couponRepository.incrementIssuedCount(couponId, clock.instant()) == 0) {
            throw resolveIssueFailure(couponId); // 0행 — 재조회로 사유 판별
        }
        // clearAutomatically 가 컨텍스트를 비우므로 managed 참조를 다시 얻는다.
        Coupon managed = couponRepository.getReferenceById(couponId);
        return persistIssuance(managed, memberId, couponId);
    }

    /** 발급 실패 사유를 없어짐(404)·비발급(422)·소진(409)으로 매핑. */
    private RuntimeException resolveIssueFailure(Long couponId) {
        return couponRepository.findById(couponId)
                .map(coupon -> coupon.isIssuable(clock.instant())
                        ? (RuntimeException) new CouponSoldOutException(couponId)
                        : new CouponNotIssuableException(couponId))
                .orElseGet(() -> new CouponNotFoundException(couponId));
    }

    /** 비관적 락 — coupon 행을 FOR UPDATE 로 잠그고 read-check-increment 한다. */
    @Transactional
    public MemberCouponResponse issueWithPessimisticLock(Long memberId, Long couponId) {
        Coupon coupon = couponRepository.findByIdForUpdate(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));
        requireIssuable(coupon, couponId);
        requireNotYetIssued(couponId, memberId);

        if (!coupon.tryIssueOne()) {
            throw new CouponSoldOutException(couponId);
        }
        return persistIssuance(coupon, memberId, couponId);
    }

    /** 락 없는 read-check-increment — lost-update 초과발급 재현. 회원당 1장 사전 검사 생략. */
    @Transactional
    public MemberCouponResponse issueWithoutLock(Long memberId, Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));
        requireIssuable(coupon, couponId);

        if (!coupon.tryIssueOne()) {
            throw new CouponSoldOutException(couponId);
        }
        MemberCoupon issued = memberCouponRepository.save(MemberCoupon.issue(coupon, memberId, clock.instant()));
        return MemberCouponResponse.from(issued);
    }

    private void validateIssuable(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));
        requireIssuable(coupon, couponId);
    }

    private void requireIssuable(Coupon coupon, Long couponId) {
        if (!coupon.isIssuable(clock.instant())) {
            throw new CouponNotIssuableException(couponId);
        }
    }

    private void requireNotYetIssued(Long couponId, Long memberId) {
        if (memberCouponRepository.existsByCoupon_IdAndMemberId(couponId, memberId)) {
            throw new CouponAlreadyIssuedException(couponId, memberId);
        }
    }

    private MemberCouponResponse persistIssuance(Coupon coupon, Long memberId, Long couponId) {
        try {
            MemberCoupon issued = memberCouponRepository.saveAndFlush(MemberCoupon.issue(coupon, memberId, clock.instant()));
            return MemberCouponResponse.from(issued);
        } catch (DataIntegrityViolationException violation) {
            // 회원당 1장 UNIQUE 충돌만 ALREADY_ISSUED 로 매핑하고, 그 외 제약 위반은 원본 예외를 전파한다.
            if (isMemberCouponUniqueViolation(violation)) {
                throw new CouponAlreadyIssuedException(couponId, memberId);
            }
            throw violation;
        }
    }

    /** cause 체인에서 회원당 1장 UNIQUE 위반 판별. 제약명 우선, null 이면 메시지 substring 폴백. */
    private boolean isMemberCouponUniqueViolation(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException cve && containsConstraint(cve.getConstraintName())) {
                return true;
            }
            if (containsConstraint(cause.getMessage())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsConstraint(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(MEMBER_COUPON_UNIQUE_CONSTRAINT);
    }
}
