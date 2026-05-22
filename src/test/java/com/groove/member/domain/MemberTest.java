package com.groove.member.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Member 도메인 단위 테스트")
class MemberTest {

    private Member newMember() {
        return Member.register("user@example.com", "$2a$10$hash", "김철수", "01012345678");
    }

    @Test
    @DisplayName("name·phone 모두 전달 → 둘 다 변경")
    void updateProfile_bothProvided_updatesBoth() {
        Member member = newMember();

        member.updateProfile("김영희", "01098765432");

        assertThat(member.getName()).isEqualTo("김영희");
        assertThat(member.getPhone()).isEqualTo("01098765432");
    }

    @Test
    @DisplayName("name 만 전달(phone=null) → name 변경, phone 미변경")
    void updateProfile_onlyName_keepsPhone() {
        Member member = newMember();

        member.updateProfile("김영희", null);

        assertThat(member.getName()).isEqualTo("김영희");
        assertThat(member.getPhone()).isEqualTo("01012345678");
    }

    @Test
    @DisplayName("phone 만 전달(name=null) → phone 변경, name 미변경")
    void updateProfile_onlyPhone_keepsName() {
        Member member = newMember();

        member.updateProfile(null, "01098765432");

        assertThat(member.getName()).isEqualTo("김철수");
        assertThat(member.getPhone()).isEqualTo("01098765432");
    }

    @Test
    @DisplayName("둘 다 null → 아무것도 변경되지 않음")
    void updateProfile_bothNull_noChange() {
        Member member = newMember();

        member.updateProfile(null, null);

        assertThat(member.getName()).isEqualTo("김철수");
        assertThat(member.getPhone()).isEqualTo("01012345678");
    }

    @Test
    @DisplayName("email·role·password 는 updateProfile 로 변경되지 않음")
    void updateProfile_doesNotTouchImmutableFields() {
        Member member = newMember();

        member.updateProfile("김영희", "01098765432");

        assertThat(member.getEmail()).isEqualTo("user@example.com");
        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
        assertThat(member.getPassword()).isEqualTo("$2a$10$hash");
    }

    @Test
    @DisplayName("changePassword → 비밀번호 해시가 새 값으로 교체됨")
    void changePassword_replacesHash() {
        Member member = newMember();

        member.changePassword("$2a$12$newhash");

        assertThat(member.getPassword()).isEqualTo("$2a$12$newhash");
    }

    @Test
    @DisplayName("changePassword 는 password 외 다른 필드를 건드리지 않음")
    void changePassword_doesNotTouchOtherFields() {
        Member member = newMember();

        member.changePassword("$2a$12$newhash");

        assertThat(member.getEmail()).isEqualTo("user@example.com");
        assertThat(member.getName()).isEqualTo("김철수");
        assertThat(member.getPhone()).isEqualTo("01012345678");
        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
    }

    @Test
    @DisplayName("신규 회원은 탈퇴 상태가 아니며 deletedAt 이 null")
    void newMember_isNotWithdrawn() {
        Member member = newMember();

        assertThat(member.isWithdrawn()).isFalse();
        assertThat(member.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("withdraw → deletedAt 기록 + isWithdrawn true")
    void withdraw_recordsDeletedAt() {
        Member member = newMember();
        Instant now = Instant.parse("2026-05-22T00:00:00Z");

        member.withdraw(now);

        assertThat(member.isWithdrawn()).isTrue();
        assertThat(member.getDeletedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("withdraw 멱등 → 재호출해도 최초 deletedAt 을 덮어쓰지 않음")
    void withdraw_idempotent_keepsFirstTimestamp() {
        Member member = newMember();
        Instant first = Instant.parse("2026-05-22T00:00:00Z");
        Instant later = Instant.parse("2026-05-23T00:00:00Z");

        member.withdraw(first);
        member.withdraw(later);

        assertThat(member.getDeletedAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("withdraw 는 password·email 등 다른 필드를 건드리지 않음")
    void withdraw_doesNotTouchOtherFields() {
        Member member = newMember();

        member.withdraw(Instant.parse("2026-05-22T00:00:00Z"));

        assertThat(member.getEmail()).isEqualTo("user@example.com");
        assertThat(member.getName()).isEqualTo("김철수");
        assertThat(member.getPhone()).isEqualTo("01012345678");
        assertThat(member.getPassword()).isEqualTo("$2a$10$hash");
        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
    }
}
