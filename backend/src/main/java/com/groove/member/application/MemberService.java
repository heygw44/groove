package com.groove.member.application;

import com.groove.common.exception.AuthException;
import com.groove.common.exception.ErrorCode;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.member.event.MemberWithdrawnEvent;
import com.groove.member.exception.MemberEmailDuplicatedException;
import com.groove.member.exception.MemberNotFoundException;
import com.groove.member.exception.MemberWithdrawalBlockedException;
import com.groove.member.security.EmailHasher;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.EnumSet;
import java.util.Set;

@Service
public class MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberService.class);

    /**
     * 탈퇴를 차단하는 "진행 중" 주문 상태. 결제됐고 아직 배송이 끝나지 않은 상태들 — 미결제(PENDING)와
     * 종착 상태(DELIVERED/COMPLETED/CANCELLED/PAYMENT_FAILED)는 제외한다.
     */
    private static final Set<OrderStatus> WITHDRAWAL_BLOCKING_STATUSES =
            EnumSet.of(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.SHIPPED);

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final EmailHasher emailHasher;
    private final Clock clock;

    public MemberService(MemberRepository memberRepository,
                         PasswordEncoder passwordEncoder,
                         OrderRepository orderRepository,
                         ApplicationEventPublisher eventPublisher,
                         EmailHasher emailHasher,
                         Clock clock) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.emailHasher = emailHasher;
        this.clock = clock;
    }

    /**
     * 회원가입.
     *
     * <p>이메일 중복(soft delete·익명화 포함) 검사 → BCrypt 해시 → 영속화. 중복 검사는 평문이 아닌 정규화
     * 이메일 해시({@link EmailHasher})로 한다 (#170 패턴 A, #186) — 탈퇴 익명화가 평문을 치환해도
     * {@code email_hash} 는 보존되므로 같은 이메일의 재가입이 차단된다. 신규 회원은 v1(HMAC) 해시로 저장하되,
     * 점유 검사는 v1 과 legacy(SHA-256) 를 함께 보아 마이그레이션 이전 탈퇴 회원(평문 파기로 재계산 불가)도
     * 차단한다.
     *
     * <p>선체크({@code existsByEmailHashIn})와 {@code save} 사이에 동시 가입 요청이 끼어들 수 있다.
     * DB UNIQUE 제약({@code uk_member_email_hash})이 최종 방어선이며, 위반 시 {@link DataIntegrityViolationException}
     * 을 도메인 예외로 변환해 409 응답이 보장된다.
     *
     * @throws MemberEmailDuplicatedException 동일 이메일이 이미 존재
     */
    @Transactional
    public Member signup(SignupCommand command) {
        String email = command.email();
        String emailHash = emailHasher.hash(email);
        if (memberRepository.existsByEmailHashIn(emailHasher.occupancyHashes(email))) {
            throw new MemberEmailDuplicatedException();
        }
        String passwordHash = passwordEncoder.encode(command.password());
        Member member = Member.register(email, emailHash, passwordHash, command.name(), command.phone());
        try {
            return memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException ex) {
            throw new MemberEmailDuplicatedException(ex);
        }
    }

    /**
     * 내 정보 조회 (#76, API.md §3.2 — GET /members/me). 활성 회원만 — soft delete 제외.
     *
     * @throws MemberNotFoundException 활성 회원이 없음 (탈퇴 후 토큰 만료 전 윈도 등)
     */
    @Transactional(readOnly = true)
    public Member getMyInfo(Long memberId) {
        return memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(MemberNotFoundException::new);
    }

    /**
     * 내 정보 부분 수정 (#76, API.md §3.2 — PATCH /members/me).
     *
     * <p>name·phone 만 변경하며, 미전송(null) 필드는 유지된다 ({@link Member#updateProfile}).
     * 더티 체킹으로 트랜잭션 커밋 시 반영되므로 별도 save 호출은 불필요하다.
     *
     * @throws MemberNotFoundException 활성 회원이 없음
     */
    @Transactional
    public Member updateMyInfo(Long memberId, UpdateProfileCommand command) {
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(MemberNotFoundException::new);
        member.updateProfile(command.name(), command.phone());
        return member;
    }

    /**
     * 회원 탈퇴 (#78, API.md §3.2 — DELETE /members/me). soft delete.
     *
     * <p>본인 비밀번호를 BCrypt 비교로 재확인한 뒤, "진행 중" 주문이 없으면 {@code deleted_at} 을 찍고
     * 회원 PII 를 익명화({@link Member#anonymize} — email 평문 치환·name/phone 마스킹, email_hash 는 보존)한 다음
     * {@link MemberWithdrawnEvent} 를 발행한다. 이벤트의 AFTER_COMMIT 리스너가 장바구니 삭제(cart)와
     * 리프레시 토큰 정리(auth)를 각 모듈에서 수행한다 — 본 서비스는 자기 도메인(member soft delete·익명화)만
     * 책임지고, 타 모듈 데이터는 직접 건드리지 않는다. 주문/배송 PII 는 배송완료 후 보존기간이 지나면
     * {@code OrderPiiAnonymizationScheduler} 가 별도로 익명화한다 (#170 Part B).
     *
     * <p><b>멱등</b>: access 토큰은 stateless 라 탈퇴 직후에도 만료 전까지 유효하므로 재요청이 들어올 수
     * 있다. 활성/탈퇴 회원을 모두 조회({@code findById})해 이미 탈퇴 상태면 no-op 으로 끝낸다 — 이벤트를
     * 다시 발행하지 않고 주문 검사도 생략한다. 따라서 반복 호출은 항상 204 로 수렴한다.
     *
     * <p><b>조회에 {@code findById} 를 쓰는 이유</b>: {@code getMyInfo}·{@code updateMyInfo} 는
     * {@code findByIdAndDeletedAtIsNull}(활성 한정, 탈퇴 시 404)이지만, 탈퇴는 멱등이어야 하므로 탈퇴자도
     * 조회해 no-op 분기로 보낸다.
     *
     * @throws MemberNotFoundException        해당 id 의 회원이 아예 없음 (404)
     * @throws AuthException                  비밀번호 불일치 ({@link ErrorCode#MEMBER_PASSWORD_MISMATCH}, 400)
     * @throws MemberWithdrawalBlockedException 진행 중 주문 존재 (409)
     */
    @Transactional
    public void withdraw(Long memberId, String rawPassword) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);

        if (!passwordEncoder.matches(rawPassword, member.getPassword())) {
            log.warn("회원 탈퇴 실패 - 비밀번호 불일치 memberId={}", memberId);
            throw new AuthException(ErrorCode.MEMBER_PASSWORD_MISMATCH);
        }

        if (member.isWithdrawn()) {
            log.info("회원 탈퇴 멱등 처리 - 이미 탈퇴한 회원 memberId={}", memberId);
            return;
        }

        if (orderRepository.existsByMemberIdAndStatusIn(memberId, WITHDRAWAL_BLOCKING_STATUSES)) {
            log.warn("회원 탈퇴 차단 - 진행 중 주문 존재 memberId={}", memberId);
            throw new MemberWithdrawalBlockedException();
        }

        member.withdraw(clock.instant());
        member.anonymize();
        eventPublisher.publishEvent(new MemberWithdrawnEvent(memberId));
        log.info("회원 탈퇴 성공 memberId={}", memberId);
    }
}
