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
    @DisplayName("existsByEmailHash → 정규화 이메일 해시로 점유 판정 (패턴 A)")
    void existsByEmailHash_returnsTrueForExistingEmail() {
        memberRepository.saveAndFlush(
                Member.register("dup@example.com", "$2a$12$hash", "철수", "01011112222")
        );

        assertThat(memberRepository.existsByEmailHash(Member.hashEmail("dup@example.com"))).isTrue();
        assertThat(memberRepository.existsByEmailHash(Member.hashEmail("other@example.com"))).isFalse();
    }

    @Test
    @DisplayName("existsByEmailHash → 익명화(평문 치환) 후에도 원래 이메일·대소문자 변형 재가입 차단 (#170 회귀)")
    void existsByEmailHash_blocksReSignupAfterAnonymization_caseInsensitive() {
        Member member = Member.register("foo@x.com", "$2a$12$hash", "원회원", "01012345678");
        member.withdraw(Instant.now());
        member.anonymize(); // email 평문 → withdrawn-{id}@deleted.local, email_hash 는 보존
        memberRepository.saveAndFlush(member);

        // 평문은 치환됐어도 해시 점유가 남아 재가입을 막는다.
        assertThat(member.getEmail()).doesNotContain("foo@x.com");
        assertThat(memberRepository.existsByEmailHash(Member.hashEmail("foo@x.com"))).isTrue();
        // 대소문자 변형도 정규화 후 같은 해시 → 차단 (collation ci 차단 시맨틱 유지).
        assertThat(memberRepository.existsByEmailHash(Member.hashEmail("FOO@x.com"))).isTrue();
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
    @DisplayName("findByEmailAndDeletedAtIsNull → 탈퇴(deleted_at 있음) 회원은 제외")
    void findByEmailAndDeletedAtIsNull_excludesSoftDeleted() {
        Member deleted = Member.register("gone@example.com", "$2a$12$hash", "탈퇴자", "01077778888");
        deleted.withdraw(Instant.now());
        memberRepository.saveAndFlush(deleted);

        assertThat(memberRepository.findByEmailAndDeletedAtIsNull("gone@example.com")).isEmpty();
        // 패턴 A: existsByEmailHash 는 탈퇴자도 점유한 것으로 본다 (재가입 차단).
        assertThat(memberRepository.existsByEmailHash(Member.hashEmail("gone@example.com"))).isTrue();
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
