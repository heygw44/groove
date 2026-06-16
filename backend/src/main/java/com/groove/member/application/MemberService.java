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

    /** 탈퇴를 차단하는 "진행 중" 주문 상태 — PAID/PREPARING/SHIPPED. */
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
     * 회원가입 — 이메일 해시(EmailHasher) 중복 검사 → BCrypt 해시 → 영속화. DB UNIQUE 제약 위반 시
     * DataIntegrityViolationException 을 도메인 예외로 변환한다.
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

    /** 내 정보 조회 (GET /members/me). 활성 회원만 — soft delete 제외. */
    @Transactional(readOnly = true)
    public Member getMyInfo(Long memberId) {
        return memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(MemberNotFoundException::new);
    }

    /** 내 정보 부분 수정 (PATCH /members/me). name·phone 만 변경, 미전송(null) 필드는 유지. */
    @Transactional
    public Member updateMyInfo(Long memberId, UpdateProfileCommand command) {
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(MemberNotFoundException::new);
        member.updateProfile(command.name(), command.phone());
        return member;
    }

    /**
     * 회원 탈퇴 (DELETE /members/me). soft delete.
     *
     * <p>본인 비밀번호를 BCrypt 비교로 재확인한 뒤, "진행 중" 주문이 없으면 deleted_at 을 찍고 회원 PII 를
     * 익명화(Member.anonymize, email_hash 는 보존)한 다음 MemberWithdrawnEvent 를 발행한다. 이미 탈퇴 상태면
     * no-op 으로 끝나(멱등) 반복 호출은 204 로 수렴한다.
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
