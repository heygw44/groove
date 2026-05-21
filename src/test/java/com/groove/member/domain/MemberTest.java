package com.groove.member.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
