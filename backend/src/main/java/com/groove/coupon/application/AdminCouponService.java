package com.groove.coupon.application;

import com.groove.admin.api.dto.AdminCouponCreateRequest;
import com.groove.common.exception.ErrorCode;
import com.groove.common.exception.ValidationException;
import com.groove.coupon.domain.Coupon;
import com.groove.coupon.domain.CouponRepository;
import com.groove.coupon.domain.CouponStatus;
import com.groove.coupon.domain.MemberCoupon;
import com.groove.coupon.domain.MemberCouponRepository;
import com.groove.coupon.exception.CouponAlreadyIssuedException;
import com.groove.coupon.exception.CouponNotFoundException;
import com.groove.coupon.exception.CouponNotIssuableException;
import com.groove.coupon.exception.IllegalCouponStateTransitionException;
import com.groove.member.domain.MemberRepository;
import com.groove.member.exception.MemberNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * 관리자 쿠폰 CRUD · 직접지급 트랜잭션 경계.
 *
 * 입력 검증: 형식·필수값은 DTO Bean Validation, 정률 1~100·validUntil>validFrom·정률 상한 필수 같은 의미 검증은
 * Coupon.Builder.build() 가 처리한다 — 도메인이 던진 IllegalArgumentException 을 ValidationException 으로 매핑해
 * 400 으로 응답한다.
 *
 * 상태 변경: changeStatus 는 행 락(findByIdForUpdate)으로 동시 변경 race 를 직렬화하고, canTransitionTo 위반은
 * 409(IllegalCouponStateTransitionException). self-transition(from == target)은 현재 상태를 그대로 반환한다.
 *
 * 직접지급: grant 는 선착순 한정수량(total_quantity)과 독립적으로 member_coupon 1행을 INSERT 한다 —
 * issuedCount 는 변하지 않는다. isIssuable 가드로 SUSPENDED/ENDED/만료 쿠폰 발급을 차단한다. 활성 회원만
 * 허용하고, 이미 보유한 회원은 409 — UNIQUE 충돌 race 시에도 같은 409 로 응답한다.
 */
@Service
public class AdminCouponService {

    private static final Logger log = LoggerFactory.getLogger(AdminCouponService.class);

    private final CouponRepository couponRepository;
    private final MemberCouponRepository memberCouponRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    public AdminCouponService(CouponRepository couponRepository,
                              MemberCouponRepository memberCouponRepository,
                              MemberRepository memberRepository,
                              Clock clock) {
        this.couponRepository = couponRepository;
        this.memberCouponRepository = memberCouponRepository;
        this.memberRepository = memberRepository;
        this.clock = clock;
    }

    /** 쿠폰 정책 생성. 도메인 빌더 검증 실패는 400 VALIDATION_FAILED 로 매핑한다. */
    @Transactional
    public Coupon create(AdminCouponCreateRequest request) {
        Coupon.Builder builder = Coupon.builder(
                        request.name(), request.discountType(), request.discountValue(),
                        request.validFrom(), request.validUntil())
                .maxDiscountAmount(request.maxDiscountAmount())
                .minOrderAmount(request.minOrderAmount())
                .totalQuantity(request.totalQuantity())
                .perMemberLimit(request.perMemberLimit());
        Coupon coupon;
        try {
            coupon = builder.build();
        } catch (IllegalArgumentException invalid) {
            throw new ValidationException(ErrorCode.VALIDATION_FAILED, invalid.getMessage());
        }
        Coupon saved = couponRepository.save(coupon);
        log.info("관리자 쿠폰 정책 생성: couponId={}, name='{}'", saved.getId(), saved.getName());
        return saved;
    }

    /** 쿠폰 정책 목록 — status 가 주어지면 필터, 없으면 전체. */
    @Transactional(readOnly = true)
    public Page<Coupon> list(CouponStatus status, Pageable pageable) {
        return status == null
                ? couponRepository.findAll(pageable)
                : couponRepository.findByStatus(status, pageable);
    }

    /**
     * 쿠폰 정책 상태 변경. 행 락으로 동시 변경 race 를 직렬화한다.
     * self-transition(from == target)은 현재 상태 그대로 반환(200), canTransitionTo 위반은 409(IllegalCouponStateTransitionException).
     */
    @Transactional
    public Coupon changeStatus(Long couponId, CouponStatus target) {
        Coupon coupon = couponRepository.findByIdForUpdate(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));
        CouponStatus from = coupon.getStatus();
        if (from == target) {
            return coupon;
        }
        if (!from.canTransitionTo(target)) {
            throw new IllegalCouponStateTransitionException(from, target);
        }
        coupon.changeStatus(target);
        log.info("관리자 쿠폰 상태 변경: couponId={}, {} -> {}", couponId, from, target);
        return coupon;
    }

    /**
     * 특정 회원에게 쿠폰 직접지급. 선착순 한정수량과 독립 — issuedCount 비증가.
     * 쿠폰 미존재 404, ACTIVE 아님·발급 기간 밖 422, 활성 회원 미존재 404, 이미 보유 409(UNIQUE 충돌 race 포함).
     */
    @Transactional
    public MemberCoupon grant(Long couponId, Long memberId) {
        // 행 락으로 상태 전이와 발급 가능성 검사를 직렬화한다.
        Coupon coupon = couponRepository.findByIdForUpdate(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));
        if (!coupon.isIssuable(clock.instant())) {
            // SUSPENDED/ENDED 또는 validUntil 경과.
            throw new CouponNotIssuableException(couponId);
        }
        memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(MemberNotFoundException::new);
        if (memberCouponRepository.existsByCoupon_IdAndMemberId(couponId, memberId)) {
            throw new CouponAlreadyIssuedException(couponId, memberId);
        }
        MemberCoupon granted;
        try {
            granted = memberCouponRepository.saveAndFlush(MemberCoupon.issue(coupon, memberId, clock.instant()));
        } catch (DataIntegrityViolationException uniqueRace) {
            // UNIQUE 충돌이면 409, 그 외 무결성 오류는 원예외를 재던진다.
            if (memberCouponRepository.existsByCoupon_IdAndMemberId(couponId, memberId)) {
                throw new CouponAlreadyIssuedException(couponId, memberId);
            }
            throw uniqueRace;
        }
        log.info("관리자 쿠폰 직접지급: couponId={}, memberId={}, memberCouponId={}",
                couponId, memberId, granted.getId());
        return granted;
    }
}
