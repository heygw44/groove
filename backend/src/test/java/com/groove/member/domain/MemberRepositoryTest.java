package com.groove.member.domain;

import com.groove.common.persistence.JpaAuditingConfig;
import com.groove.support.MemberFixtures;
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
import java.util.List;
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
        Member member = MemberFixtures.register("a@example.com", "$2a$12$hash", "홍길동", "01012345678");

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
    @DisplayName("existsByEmailHash → 정규화 이메일 해시(v1)로 점유 판정 (패턴 A)")
    void existsByEmailHash_returnsTrueForExistingEmail() {
        memberRepository.saveAndFlush(
                MemberFixtures.register("dup@example.com", "$2a$12$hash", "철수", "01011112222")
        );

        assertThat(memberRepository.existsByEmailHash(MemberFixtures.hash("dup@example.com"))).isTrue();
        assertThat(memberRepository.existsByEmailHash(MemberFixtures.hash("other@example.com"))).isFalse();
    }

    @Test
    @DisplayName("existsByEmailHash → 익명화(평문 치환) 후에도 원래 이메일·대소문자 변형 재가입 차단 (#170 회귀)")
    void existsByEmailHash_blocksReSignupAfterAnonymization_caseInsensitive() {
        Member member = MemberFixtures.register("foo@x.com", "$2a$12$hash", "원회원", "01012345678");
        member.withdraw(Instant.now());
        member.anonymize(); // email 평문 → withdrawn-{id}@deleted.local, email_hash 보존
        memberRepository.saveAndFlush(member);

        // 평문 치환 후에도 해시 점유로 재가입 차단.
        assertThat(member.getEmail()).doesNotContain("foo@x.com");
        assertThat(memberRepository.existsByEmailHash(MemberFixtures.hash("foo@x.com"))).isTrue();
        // 대소문자 변형도 정규화 후 같은 해시 → 차단.
        assertThat(memberRepository.existsByEmailHash(MemberFixtures.hash("FOO@x.com"))).isTrue();
    }

    @Test
    @DisplayName("existsByEmailHashIn → 마이그레이션 이전 탈퇴 회원(legacy SHA-256 보존)도 v1·legacy 양방향으로 차단 (#186)")
    void existsByEmailHashIn_blocksLegacyHashedMember() {
        // legacy SHA-256 email_hash 를 가진 탈퇴 회원.
        Member legacy = Member.register("legacy@x.com", MemberFixtures.legacyHash("legacy@x.com"),
                "$2a$12$hash", "구회원", "01099998888");
        legacy.withdraw(Instant.now());
        legacy.anonymize();
        memberRepository.saveAndFlush(legacy);

        // v1 단독 조회로는 못 잡음.
        assertThat(memberRepository.existsByEmailHash(MemberFixtures.hash("legacy@x.com"))).isFalse();
        // v1·legacy 양방향 조회는 legacy 인자로 점유를 잡는다.
        assertThat(memberRepository.existsByEmailHashIn(
                List.of(MemberFixtures.hash("legacy@x.com"), MemberFixtures.legacyHash("legacy@x.com")))).isTrue();
    }

    @Test
    @DisplayName("findByEmailAndDeletedAtIsNull → 활성 회원만 조회")
    void findByEmailAndDeletedAtIsNull_returnsOnlyActive() {
        memberRepository.saveAndFlush(
                MemberFixtures.register("active@example.com", "$2a$12$hash", "활성", "01022223333")
        );

        Optional<Member> found = memberRepository.findByEmailAndDeletedAtIsNull("active@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("active@example.com");

        assertThat(memberRepository.findByEmailAndDeletedAtIsNull("missing@example.com")).isEmpty();
    }

    @Test
    @DisplayName("findByEmailAndDeletedAtIsNull → 탈퇴(deleted_at 있음) 회원은 제외")
    void findByEmailAndDeletedAtIsNull_excludesSoftDeleted() {
        Member deleted = MemberFixtures.register("gone@example.com", "$2a$12$hash", "탈퇴자", "01077778888");
        deleted.withdraw(Instant.now());
        memberRepository.saveAndFlush(deleted);

        assertThat(memberRepository.findByEmailAndDeletedAtIsNull("gone@example.com")).isEmpty();
        // existsByEmailHash 는 탈퇴자도 점유한 것으로 본다.
        assertThat(memberRepository.existsByEmailHash(MemberFixtures.hash("gone@example.com"))).isTrue();
    }

    @Test
    @DisplayName("동일 이메일 두 번 저장 → DB UNIQUE 제약 위반")
    void uniqueEmailConstraint_violationThrows() {
        memberRepository.saveAndFlush(
                MemberFixtures.register("uniq@example.com", "$2a$12$hash1", "A", "01044445555")
        );

        assertThatThrownBy(() -> memberRepository.saveAndFlush(
                MemberFixtures.register("uniq@example.com", "$2a$12$hash2", "B", "01055556666")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
