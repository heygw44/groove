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
import java.time.Instant;

/**
 * 관리자 쿠폰 CRUD · 직접지급 트랜잭션 경계 (API.md §3.10, 이슈 #92).
 *
 * <p>도메인 단일 Aggregate({@link Coupon}/{@link MemberCoupon}) 만 조작하므로 {@code coupon}
 * 모듈 안에 둔다 — 주문·결제를 함께 조율하는 {@code AdminOrderService} 가 {@code admin} 모듈에
 * 있는 것과 대조된다.
 *
 * <h2>입력 검증</h2>
 * <p>형식·필수값은 DTO Bean Validation 으로 1차 차단되고, 정률 1~100·{@code validUntil>validFrom}
 * ·정률 상한 필수 같은 의미 검증은 {@link Coupon.Builder#build()} 가 처리한다 — 도메인이 던진
 * {@link IllegalArgumentException} 을 {@link ValidationException} 으로 매핑해 400 으로 응답한다.
 * try 범위는 {@code build()} 한 줄로 좁혀 의도가 명확하다.
 *
 * <h2>상태 변경</h2>
 * <p>{@link #changeStatus} 는 행 락({@code findByIdForUpdate}) 으로 동시 변경 race 를 직렬화하고,
 * 사전 검증({@link CouponStatus#canTransitionTo}) 으로 불법 전이를 409
 * {@link IllegalCouponStateTransitionException} 로 응답한다. 멱등 PATCH 관용에 따라 동일 상태로의
 * self-transition({@code from == target}) 은 거부하지 않고 현재 상태를 그대로 반환한다.
 *
 * <h2>직접지급</h2>
 * <p>{@link #grant} 는 선착순 한정수량({@code total_quantity})과 독립적으로 {@code member_coupon}
 * 1행을 INSERT 한다 — {@link CouponRepository#incrementIssuedCount} 를 호출하지 않으므로 정책의
 * {@code issuedCount} 는 변하지 않는다. 정책이 {@link Coupon#isIssuable(Instant)} 인지 가드해
 * SUSPENDED/ENDED/만료된 쿠폰을 즉시-만료 회원 쿠폰으로 발급하는 운영 사고를 차단한다. 활성
 * 회원만 허용하고, 이미 보유한 회원은 409 — 동시 발급 race 의 UNIQUE 충돌 시에도 같은 409 로
 * 응답한다.
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

    /**
     * 쿠폰 정책 생성. 도메인 빌더 검증 실패는 400 {@code VALIDATION_FAILED} 로 매핑한다.
     */
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

    /**
     * 쿠폰 정책 목록 — {@code status} 가 주어지면 필터, 없으면 전체.
     */
    @Transactional(readOnly = true)
    public Page<Coupon> list(CouponStatus status, Pageable pageable) {
        return status == null
                ? couponRepository.findAll(pageable)
                : couponRepository.findByStatus(status, pageable);
    }

    /**
     * 쿠폰 정책 상태 변경. 행 락으로 동시 변경 race 를 직렬화한다.
     *
     * <ul>
     *   <li>self-transition ({@code from == target}) — 멱등 처리, 현재 상태 그대로 반환 (200).</li>
     *   <li>{@code canTransitionTo} 위반 — 409 {@link IllegalCouponStateTransitionException}.</li>
     * </ul>
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
     * 특정 회원에게 쿠폰 직접지급. 선착순 한정수량과 독립 — {@code issuedCount} 비증가.
     *
     * @throws CouponNotFoundException      쿠폰 미존재 (404)
     * @throws CouponNotIssuableException   쿠폰이 ACTIVE 가 아니거나 발급 기간 밖 (422)
     * @throws MemberNotFoundException      활성 회원 미존재 / 탈퇴자 (404)
     * @throws CouponAlreadyIssuedException 회원이 이미 해당 쿠폰을 보유 (409, UNIQUE 충돌 race 포함)
     */
    @Transactional
    public MemberCoupon grant(Long couponId, Long memberId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));
        if (!coupon.isIssuable(clock.instant())) {
            // SUSPENDED/ENDED 또는 validUntil 경과 — 즉시-만료 회원쿠폰 생성 차단 (#92 리뷰).
            throw new CouponNotIssuableException(couponId);
        }
        memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(MemberNotFoundException::new);
        if (memberCouponRepository.existsByCoupon_IdAndMemberId(couponId, memberId)) {
            throw new CouponAlreadyIssuedException(couponId, memberId);
        }
        MemberCoupon granted;
        try {
            granted = memberCouponRepository.saveAndFlush(MemberCoupon.issue(coupon, memberId));
        } catch (DataIntegrityViolationException uniqueRace) {
            // 사전 검사 통과 후 다른 트랜잭션이 먼저 INSERT 한 경우 — 같은 의미 코드로 응답.
            throw new CouponAlreadyIssuedException(couponId, memberId);
        }
        log.info("관리자 쿠폰 직접지급: couponId={}, memberId={}, memberCouponId={}",
                couponId, memberId, granted.getId());
        return granted;
    }
}
