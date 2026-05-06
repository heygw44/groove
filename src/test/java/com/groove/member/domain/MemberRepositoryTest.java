package com.groove.member.domain;

import com.groove.common.persistence.JpaAuditingConfig;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfig.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
@DisplayName("MemberRepository 통합 테스트 (Testcontainers MySQL)")
class MemberRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("save → id 발급 + createdAt/updatedAt 자동 채워짐")
    void save_assignsIdAndAuditTimestamps() {
        Member member = Member.register("a@example.com", "$2a$12$hash", "홍길동", "01012345678");

        Member saved = memberRepository.save(member);
        memberRepository.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull().isBefore(Instant.now().plusSeconds(1));
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getRole()).isEqualTo(MemberRole.USER);
        assertThat(saved.isEmailVerified()).isFalse();
        assertThat(saved.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("existsByEmail → 활성/탈퇴 무관하게 true (패턴 A)")
    void existsByEmail_returnsTrueForExistingEmail() {
        memberRepository.saveAndFlush(
                Member.register("dup@example.com", "$2a$12$hash", "철수", "01011112222")
        );

        assertThat(memberRepository.existsByEmail("dup@example.com")).isTrue();
        assertThat(memberRepository.existsByEmail("other@example.com")).isFalse();
    }

    @Test
    @DisplayName("findByEmailAndDeletedAtIsNull → 활성 회원만 조회")
    void findByEmailAndDeletedAtIsNull_returnsOnlyActive() {
        memberRepository.saveAndFlush(
                Member.register("active@example.com", "$2a$12$hash", "활성", "01022223333")
        );

        Optional<Member> found = memberRepository.findByEmailAndDeletedAtIsNull("active@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("active@example.com");

        assertThat(memberRepository.findByEmailAndDeletedAtIsNull("missing@example.com")).isEmpty();
    }

    @Test
    @DisplayName("동일 이메일 두 번 저장 → DB UNIQUE 제약 위반")
    void uniqueEmailConstraint_violationThrows() {
        memberRepository.saveAndFlush(
                Member.register("uniq@example.com", "$2a$12$hash1", "A", "01044445555")
        );

        assertThatThrownBy(() -> memberRepository.saveAndFlush(
                Member.register("uniq@example.com", "$2a$12$hash2", "B", "01055556666")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
