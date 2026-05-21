package com.groove.member.application;

import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.member.exception.MemberEmailDuplicatedException;
import com.groove.member.exception.MemberNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemberService 단위 테스트")
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(memberRepository, passwordEncoder);
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
}
