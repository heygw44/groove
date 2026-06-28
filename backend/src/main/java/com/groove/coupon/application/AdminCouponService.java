package com.groove.coupon.application;

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
 * 의미 검증은 Coupon.Builder.build() 가 담당하고 도메인 예외를 400 으로 매핑한다.
 * changeStatus·grant 는 행 락으로 동시 변경 race 를 직렬화한다.
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

    /** 빌더 검증 실패는 400 으로 매핑. */
    @Transactional
    public Coupon create(CouponCreateCommand command) {
        Coupon.Builder builder = Coupon.builder(
                        command.name(), command.discountType(), command.discountValue(),
                        command.validFrom(), command.validUntil())
                .maxDiscountAmount(command.maxDiscountAmount())
                .minOrderAmount(command.minOrderAmount())
                .totalQuantity(command.totalQuantity())
                .perMemberLimit(command.perMemberLimit());
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

    @Transactional(readOnly = true)
    public Page<Coupon> list(CouponStatus status, Pageable pageable) {
        return status == null
                ? couponRepository.findAll(pageable)
                : couponRepository.findByStatus(status, pageable);
    }

    /** 행 락으로 동시 변경 직렬화. self-transition 은 현재 상태 그대로 반환. */
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

    /** 회원에게 직접지급. 선착순 한정수량과 독립 — issuedCount 비증가. */
    @Transactional
    public MemberCoupon grant(Long couponId, Long memberId) {
        Coupon coupon = couponRepository.findByIdForUpdate(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));
        if (!coupon.isIssuable(clock.instant())) {
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
            // UNIQUE 충돌이면 409, 그 외 무결성 오류는 재던진다.
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
