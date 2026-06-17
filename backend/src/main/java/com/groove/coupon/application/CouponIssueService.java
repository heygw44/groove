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
 * 선착순 쿠폰 발급.
 *
 * 발급을 3가지로 제공한다 — issue(원자적 조건부 UPDATE 로 한 문장에 소진 검사+증가),
 * issueWithoutLock(락 없음, lost-update 로 초과발급 재현), issueWithPessimisticLock(SELECT ... FOR UPDATE 행 락).
 *
 * 회원당 1장 보증: 사전 검사(existsByCoupon_IdAndMemberId)로 거르고, 통과한 동시 중복은
 * uk_member_coupon_coupon_member UNIQUE 가 잡는다 — INSERT 충돌 시 트랜잭션이 롤백되며 증가시킨 카운터도
 * 함께 되돌아간다.
 *
 * 트랜잭션 경계: issue 는 @Transactional 자기완결 메서드다 — @Idempotent 컨트롤러가 비트랜잭션으로 호출하고 이
 * 메서드가 커밋한 뒤 멱등성 마커가 COMPLETED 로 갱신된다.
 */
@Service
public class CouponIssueService {

    /** 회원당 1장 UNIQUE 제약명 (V14) — 소문자 비교용. */
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
     * 발급 경로 — 원자적 조건부 UPDATE.
     *
     * status + issued_count < total_quantity 검사와 증가를 단일 UPDATE 로 원자 처리한다(affected=1 성공 / 0
     * 발급불가 → 재조회로 소진·비ACTIVE 판별). 발급 기간(validFrom·validUntil)은 UPDATE 가 검사하지 않으므로
     * validateIssuable 사전 검사가 게이트한다.
     */
    @Transactional
    public MemberCouponResponse issue(Long memberId, Long couponId) {
        // soft delete 된 회원이면 404.
        if (!memberRepository.existsByIdAndDeletedAtIsNull(memberId)) {
            throw new MemberNotFoundException();
        }
        validateIssuable(couponId);
        requireNotYetIssued(couponId, memberId);

        if (couponRepository.incrementIssuedCount(couponId) == 0) {
            // 0행은 소진 또는 SUSPENDED 전환. clearAutomatically 로 컨텍스트가 비었으니 재조회로 사유를 가린다.
            throw resolveIssueFailure(couponId);
        }
        // clearAutomatically 가 영속성 컨텍스트를 비우므로 managed 참조를 다시 얻는다.
        Coupon managed = couponRepository.getReferenceById(couponId);
        return persistIssuance(managed, memberId, couponId);
    }

    /** 발급 UPDATE 0행의 사유 판별 — 없어짐(404)·비발급(422)·소진(409) 중 하나로 매핑한다. */
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

    /**
     * 락 없는 read-check-increment. 동시 발급 시 lost-update 로 초과발급을 재현한다. 회원당 1장 사전 검사를 생략한다.
     */
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

    /**
     * 예외 cause 체인에서 uk_member_coupon_coupon_member UNIQUE 위반인지 판별한다.
     * ConstraintViolationException.getConstraintName() 을 우선 보고, null 이면 메시지 substring 으로 폴백한다.
     */
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
