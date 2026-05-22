package com.groove.member.application;

import com.groove.common.exception.AuthException;
import com.groove.common.exception.ErrorCode;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.member.event.MemberWithdrawnEvent;
import com.groove.member.exception.MemberEmailDuplicatedException;
import com.groove.member.exception.MemberNotFoundException;
import com.groove.member.exception.MemberWithdrawalBlockedException;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemberService 단위 테스트")
class MemberServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-22T00:00:00Z");

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(memberRepository, passwordEncoder, orderRepository, eventPublisher, clock);
    }

    @Test
    @DisplayName("정상 회원가입 → 평문 패스워드는 인코더로 해시 후 저장")
    void signup_validRequest_savesHashedPassword() {
        SignupCommand command = new SignupCommand(
                "user@example.com",
                "P@ssw0rd!2024",
                "김철수",
                "01012345678"
        );
        when(memberRepository.existsByEmail(command.email())).thenReturn(false);
        when(passwordEncoder.encode(command.password())).thenReturn("$2a$12$hashed");
        when(memberRepository.saveAndFlush(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        Member saved = memberService.signup(command);

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).saveAndFlush(captor.capture());
        Member persisted = captor.getValue();

        assertThat(persisted.getEmail()).isEqualTo("user@example.com");
        assertThat(persisted.getPassword())
                .as("DB에 평문이 아닌 해시가 저장되어야 함")
                .isEqualTo("$2a$12$hashed")
                .isNotEqualTo("P@ssw0rd!2024");
        assertThat(persisted.getName()).isEqualTo("김철수");
        assertThat(persisted.getPhone()).isEqualTo("01012345678");
        assertThat(saved).isSameAs(persisted);
    }

    @Test
    @DisplayName("이메일 중복 → MemberEmailDuplicatedException, save 호출되지 않음")
    void signup_duplicatedEmail_throwsAndDoesNotSave() {
        SignupCommand command = new SignupCommand(
                "dup@example.com",
                "P@ssw0rd!2024",
                "박영희",
                "01099998888"
        );
        when(memberRepository.existsByEmail("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> memberService.signup(command))
                .isInstanceOf(MemberEmailDuplicatedException.class);

        verify(passwordEncoder, never()).encode(any());
        verify(memberRepository, never()).save(any(Member.class));
        verify(memberRepository, never()).saveAndFlush(any(Member.class));
        verify(memberRepository, times(1)).existsByEmail("dup@example.com");
    }

    @Test
    @DisplayName("선체크 통과 후 DB UNIQUE 위반 → MemberEmailDuplicatedException 으로 변환 (레이스 안전)")
    void signup_uniqueViolationOnSave_translatedToDomainException() {
        SignupCommand command = new SignupCommand(
                "race@example.com",
                "P@ssw0rd!2024",
                "동시가입",
                "01077776666"
        );
        when(memberRepository.existsByEmail("race@example.com")).thenReturn(false);
        when(passwordEncoder.encode(command.password())).thenReturn("$2a$12$hashed");
        when(memberRepository.saveAndFlush(any(Member.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("uk_member_email"));

        assertThatThrownBy(() -> memberService.signup(command))
                .isInstanceOf(MemberEmailDuplicatedException.class)
                .hasCauseInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("getMyInfo: 활성 회원 존재 → 회원 반환")
    void getMyInfo_activeMember_returnsMember() {
        Member member = Member.register("user@example.com", "$2a$12$hash", "김철수", "01012345678");
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));

        Member result = memberService.getMyInfo(1L);

        assertThat(result).isSameAs(member);
    }

    @Test
    @DisplayName("getMyInfo: 활성 회원 없음(탈퇴/미존재) → MemberNotFoundException")
    void getMyInfo_missing_throws() {
        when(memberRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.getMyInfo(99L))
                .isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    @DisplayName("updateMyInfo: name·phone 모두 전달 → 둘 다 반영, email 불변")
    void updateMyInfo_bothFields_updatesMember() {
        Member member = Member.register("user@example.com", "$2a$12$hash", "김철수", "01012345678");
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));

        Member result = memberService.updateMyInfo(1L, new UpdateProfileCommand("김영희", "01098765432"));

        assertThat(result.getName()).isEqualTo("김영희");
        assertThat(result.getPhone()).isEqualTo("01098765432");
        assertThat(result.getEmail()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("updateMyInfo: 미전송 필드(null)는 미변경")
    void updateMyInfo_partial_keepsUnsentField() {
        Member member = Member.register("user@example.com", "$2a$12$hash", "김철수", "01012345678");
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));

        Member result = memberService.updateMyInfo(1L, new UpdateProfileCommand("김영희", null));

        assertThat(result.getName()).isEqualTo("김영희");
        assertThat(result.getPhone()).isEqualTo("01012345678");
    }

    @Test
    @DisplayName("updateMyInfo: 활성 회원 없음 → MemberNotFoundException")
    void updateMyInfo_missing_throws() {
        when(memberRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                memberService.updateMyInfo(99L, new UpdateProfileCommand("X", "01000000000")))
                .isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    @DisplayName("withdraw: 정상 → soft delete + MemberWithdrawnEvent 1회 발행")
    void withdraw_success_softDeletesAndPublishesEvent() {
        Member member = Member.register("user@example.com", "$2a$12$hash", "김철수", "01012345678");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("P@ssw0rd!2024", "$2a$12$hash")).thenReturn(true);
        when(orderRepository.existsByMemberIdAndStatusIn(eq(1L), any())).thenReturn(false);

        memberService.withdraw(1L, "P@ssw0rd!2024");

        assertThat(member.isWithdrawn()).isTrue();
        assertThat(member.getDeletedAt()).isEqualTo(NOW);
        verify(eventPublisher).publishEvent(new MemberWithdrawnEvent(1L));
    }

    @Test
    @DisplayName("withdraw: 차단 상태 집합은 {PAID, PREPARING, SHIPPED} 으로 조회한다")
    void withdraw_queriesBlockingStatuses() {
        Member member = Member.register("user@example.com", "$2a$12$hash", "김철수", "01012345678");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(passwordEncoder.matches(anyString(), eq("$2a$12$hash"))).thenReturn(true);
        when(orderRepository.existsByMemberIdAndStatusIn(eq(1L), any())).thenReturn(false);

        memberService.withdraw(1L, "P@ssw0rd!2024");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<OrderStatus>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(orderRepository).existsByMemberIdAndStatusIn(eq(1L), captor.capture());
        assertThat(captor.getValue())
                .containsExactlyInAnyOrder(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.SHIPPED);
    }

    @Test
    @DisplayName("withdraw: 진행 중 주문 존재 → MemberWithdrawalBlockedException, soft delete·이벤트 없음")
    void withdraw_blockingOrder_throwsAndDoesNotWithdraw() {
        Member member = Member.register("user@example.com", "$2a$12$hash", "김철수", "01012345678");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("P@ssw0rd!2024", "$2a$12$hash")).thenReturn(true);
        when(orderRepository.existsByMemberIdAndStatusIn(eq(1L), any())).thenReturn(true);

        assertThatThrownBy(() -> memberService.withdraw(1L, "P@ssw0rd!2024"))
                .isInstanceOf(MemberWithdrawalBlockedException.class);

        assertThat(member.isWithdrawn()).isFalse();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("withdraw: 비밀번호 불일치 → AuthException(MEMBER_PASSWORD_MISMATCH), 주문 검사·이벤트 없음")
    void withdraw_passwordMismatch_throwsBeforeOrderCheck() {
        Member member = Member.register("user@example.com", "$2a$12$hash", "김철수", "01012345678");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("wrong", "$2a$12$hash")).thenReturn(false);

        assertThatThrownBy(() -> memberService.withdraw(1L, "wrong"))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_PASSWORD_MISMATCH);

        assertThat(member.isWithdrawn()).isFalse();
        verifyNoInteractions(orderRepository);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("withdraw: 이미 탈퇴한 회원 → 멱등 no-op (이벤트 미발행, 주문 검사 생략)")
    void withdraw_alreadyWithdrawn_idempotentNoOp() {
        Member member = Member.register("user@example.com", "$2a$12$hash", "김철수", "01012345678");
        member.withdraw(Instant.parse("2026-05-01T00:00:00Z"));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("P@ssw0rd!2024", "$2a$12$hash")).thenReturn(true);

        memberService.withdraw(1L, "P@ssw0rd!2024");

        assertThat(member.getDeletedAt()).isEqualTo(Instant.parse("2026-05-01T00:00:00Z"));
        // 재탈퇴여도 비밀번호 재확인 계약은 유지된다 — no-op 분기 전에 검증되는지 명시적으로 확인.
        verify(passwordEncoder).matches("P@ssw0rd!2024", "$2a$12$hash");
        verifyNoInteractions(orderRepository);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("withdraw: 회원 없음 → MemberNotFoundException")
    void withdraw_missingMember_throws() {
        when(memberRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.withdraw(99L, "P@ssw0rd!2024"))
                .isInstanceOf(MemberNotFoundException.class);

        verifyNoInteractions(eventPublisher);
    }
}
