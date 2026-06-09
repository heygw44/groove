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
 * 선착순 쿠폰 발급 — 동시성 헤드라인 (#90, decisions/coupon-concurrency.md).
 *
 * <p>초과발급 없는 선착순 발급을 <b>3단계</b>로 제공한다. 프로덕션은 {@link #issue} (원자적 조건부
 * UPDATE) 만 쓰며, 나머지 둘은 단계적 시연·동시성 테스트 전용이다:
 * <ol>
 *   <li>{@link #issueWithoutLock} — 베이스라인(락 없음). lost-update 로 <b>초과발급을 재현</b>한다.</li>
 *   <li>{@link #issueWithPessimisticLock} — {@code SELECT ... FOR UPDATE} 행 락. 정확하나 직렬화.</li>
 *   <li>{@link #issue} — <b>최종</b>. 원자적 조건부 UPDATE 로 한 문장에 소진 검사+증가. 행 락이 짧다.</li>
 * </ol>
 *
 * <h2>회원당 1장 보증</h2>
 * 사전 검사({@code existsByCoupon_IdAndMemberId})로 흔한 경우를 거르고, 사전 검사를 통과한 동시 중복
 * 요청은 {@code uk_member_coupon_coupon_member} UNIQUE 가 잡는다 — INSERT 충돌 시 트랜잭션이 롤백되며
 * <b>이미 증가시킨 카운터도 함께 되돌아가</b>(같은 트랜잭션) 슬롯 누수가 없다 (DoD "정확히 1장").
 *
 * <h2>트랜잭션 경계</h2>
 * {@link #issue} 는 {@code @Transactional} 자기완결 메서드다 — {@code @Idempotent} 컨트롤러가
 * 비트랜잭션으로 호출하고 이 메서드가 커밋한 뒤 멱등성 마커가 COMPLETED 로 갱신된다
 * ({@code IdempotencyService} 호출 규약, {@code PaymentController} 와 동일).
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
     * <b>프로덕션 발급 경로 — 원자적 조건부 UPDATE.</b>
     *
     * <p>status+{@code issued_count < total_quantity} 검사와 증가를 단일 UPDATE 문으로 원자 처리하므로
     * 핫 카운터 경합에도 초과발급이 없다 (affected=1 성공 / 0 발급불가 → 재조회로 소진·비ACTIVE 판별).
     * 발급 기간(validFrom·validUntil)은 UPDATE 가 검사하지 않으므로 {@link #validateIssuable} 사전 검사가
     * 게이트한다 — 기간 경계는 고정 시각이라 관리자 트리거 경합이 없어 사전 검사로 충분하다.
     */
    @Transactional
    public MemberCouponResponse issue(Long memberId, Long couponId) {
        // 토큰 유효기간 내 탈퇴(soft delete)한 회원이면 404 로 차단한다 (#187, #171 과 일관) — 익명화된
        // 탈퇴회원에 신규 쿠폰이 귀속되는 비정합을 막는다. 데모/테스트 전용 issueWithoutLock·
        // issueWithPessimisticLock 은 프로덕션 경로가 아니므로 가드를 두지 않는다.
        if (!memberRepository.existsByIdAndDeletedAtIsNull(memberId)) {
            throw new MemberNotFoundException();
        }
        validateIssuable(couponId);
        requireNotYetIssued(couponId, memberId);

        if (couponRepository.incrementIssuedCount(couponId) == 0) {
            // UPDATE 가 status+수량을 원자적으로 검사하므로 0행은 소진 또는 사전 검사 후 SUSPENDED 로 전환된
            // 경합이다. clearAutomatically 로 컨텍스트가 비었으니 재조회(fresh SELECT)로 사유를 가린다.
            throw resolveIssueFailure(couponId);
        }
        // incrementIssuedCount 의 clearAutomatically 가 영속성 컨텍스트를 비우므로 — 연관용 managed 참조를
        // 다시 얻는다 (validUntil 스냅샷은 프록시 초기화로 로딩).
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

    /**
     * 비관적 락 단계 — coupon 행을 {@code FOR UPDATE} 로 잠그고 read-check-increment 한다 (시연·테스트 전용).
     */
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
     * <b>베이스라인 — 락 없는 read-check-increment.</b> 동시 발급 시 lost-update 로 초과발급을 재현한다
     * (decisions/coupon-concurrency.md "Before"). 일반 빌드에서는 호출되지 않으며 동시성 테스트만 호출한다.
     *
     * <p>회원당 1장 사전 검사를 의도적으로 생략한다 — 초과발급 노출 테스트가 서로 다른 회원으로 글로벌
     * 카운터를 때리므로 사전 검사는 무의미하고, "순진한 구현"의 결함을 그대로 드러내기 위함이다.
     */
    @Transactional
    public MemberCouponResponse issueWithoutLock(Long memberId, Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponNotFoundException(couponId));
        requireIssuable(coupon, couponId);

        if (!coupon.tryIssueOne()) {
            throw new CouponSoldOutException(couponId);
        }
        MemberCoupon issued = memberCouponRepository.save(MemberCoupon.issue(coupon, memberId));
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
            MemberCoupon issued = memberCouponRepository.saveAndFlush(MemberCoupon.issue(coupon, memberId));
            return MemberCouponResponse.from(issued);
        } catch (DataIntegrityViolationException violation) {
            // 회원당 1장 UNIQUE 충돌(사전 검사를 통과한 동시 중복)만 ALREADY_ISSUED 로 매핑한다 —
            // 트랜잭션 롤백으로 카운터 증가도 되돌아간다. 그 외 제약 위반(FK·CHECK 등)은 의미가 다르므로
            // 원본 예외를 그대로 전파해 오분류를 막는다.
            if (isMemberCouponUniqueViolation(violation)) {
                throw new CouponAlreadyIssuedException(couponId, memberId);
            }
            throw violation;
        }
    }

    /**
     * 예외 cause 체인에서 {@code uk_member_coupon_coupon_member}(회원당 1장) UNIQUE 위반인지 판별한다.
     * Hibernate {@link ConstraintViolationException#getConstraintName()} 을 우선 보고, 드라이버/방언별로
     * null 일 수 있어 메시지 substring 도 폴백으로 확인한다.
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
