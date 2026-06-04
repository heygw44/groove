package com.groove.auth.domain;

import com.groove.common.persistence.JpaAuditingConfig;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfig.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
@DisplayName("RefreshTokenRepository 통합 테스트 (Testcontainers MySQL)")
class RefreshTokenRepositoryTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private MemberRepository memberRepository;

    @PersistenceContext
    private EntityManager em;

    private Long memberId;
    private Long otherMemberId;

    @BeforeEach
    void setUp() {
        Member primary = memberRepository.saveAndFlush(
                Member.register("primary@example.com", "$2a$12$hash", "주", "01000000001"));
        Member other = memberRepository.saveAndFlush(
                Member.register("other@example.com", "$2a$12$hash", "타", "01000000002"));
        memberId = primary.getId();
        otherMemberId = other.getId();
    }

    @Test
    @DisplayName("save → id 발급 + revoked_at/replaced_by 는 null")
    void save_persistsActiveToken() {
        Instant now = Instant.now();
        RefreshToken token = RefreshToken.issue(memberId, hash("a"), now, now.plus(14, ChronoUnit.DAYS));

        RefreshToken saved = refreshTokenRepository.saveAndFlush(token);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.isRevoked()).isFalse();
        assertThat(saved.getReplacedByTokenId()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("findByTokenHash → 해시로 조회 가능")
    void findByTokenHash_returnsToken() {
        Instant now = Instant.now();
        String tokenHash = hash("findable");
        refreshTokenRepository.saveAndFlush(
                RefreshToken.issue(memberId, tokenHash, now, now.plus(14, ChronoUnit.DAYS)));

        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash(tokenHash);

        assertThat(found).isPresent();
        assertThat(found.get().getMemberId()).isEqualTo(memberId);
    }

    @Test
    @DisplayName("token_hash UNIQUE 제약 → 동일 해시 두 번 저장 시 위반")
    void uniqueTokenHash_violationThrows() {
        Instant now = Instant.now();
        String tokenHash = hash("dup");
        refreshTokenRepository.saveAndFlush(
                RefreshToken.issue(memberId, tokenHash, now, now.plus(14, ChronoUnit.DAYS)));

        assertThatThrownBy(() -> refreshTokenRepository.saveAndFlush(
                RefreshToken.issue(otherMemberId, tokenHash, now, now.plus(14, ChronoUnit.DAYS))
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("revokeIfActive → 활성 상태에서 1행 업데이트 + 회전 체인 연결")
    void revokeIfActive_updatesActiveRow() {
        Instant now = Instant.now();
        RefreshToken oldToken = refreshTokenRepository.saveAndFlush(
                RefreshToken.issue(memberId, hash("old"), now, now.plus(14, ChronoUnit.DAYS)));
        RefreshToken newToken = refreshTokenRepository.saveAndFlush(
                RefreshToken.issue(memberId, hash("new"), now, now.plus(14, ChronoUnit.DAYS)));

        int affected = refreshTokenRepository.revokeIfActive(oldToken.getId(), now, newToken.getId());
        em.flush();
        em.clear();

        assertThat(affected).isEqualTo(1);
        RefreshToken reloaded = refreshTokenRepository.findById(oldToken.getId()).orElseThrow();
        assertThat(reloaded.isRevoked()).isTrue();
        assertThat(reloaded.getReplacedByTokenId()).isEqualTo(newToken.getId());
    }

    @Test
    @DisplayName("revokeIfActive → 이미 revoked 상태면 0 행 (race 패자 시나리오)")
    void revokeIfActive_alreadyRevoked_returnsZero() {
        Instant now = Instant.now();
        RefreshToken token = refreshTokenRepository.saveAndFlush(
                RefreshToken.issue(memberId, hash("once"), now, now.plus(14, ChronoUnit.DAYS)));
        refreshTokenRepository.revokeIfActive(token.getId(), now, null);
        em.flush();
        em.clear();

        int second = refreshTokenRepository.revokeIfActive(token.getId(), now, null);

        assertThat(second).isEqualTo(0);
    }

    @Test
    @DisplayName("revokeAllActiveByMemberId → 같은 member 의 활성 토큰만 일괄 revoke")
    void revokeAllActiveByMemberId_revokesOnlyActiveOfMember() {
        Instant now = Instant.now();
        RefreshToken active1 = refreshTokenRepository.saveAndFlush(
                RefreshToken.issue(memberId, hash("m1-a"), now, now.plus(14, ChronoUnit.DAYS)));
        RefreshToken active2 = refreshTokenRepository.saveAndFlush(
                RefreshToken.issue(memberId, hash("m1-b"), now, now.plus(14, ChronoUnit.DAYS)));
        RefreshToken alreadyRevoked = refreshTokenRepository.saveAndFlush(
                RefreshToken.issue(memberId, hash("m1-c"), now, now.plus(14, ChronoUnit.DAYS)));
        refreshTokenRepository.revokeIfActive(alreadyRevoked.getId(), now, null);
        RefreshToken otherActive = refreshTokenRepository.saveAndFlush(
                RefreshToken.issue(otherMemberId, hash("m2"), now, now.plus(14, ChronoUnit.DAYS)));
        em.flush();
        em.clear();

        int affected = refreshTokenRepository.revokeAllActiveByMemberId(memberId, now);
        em.flush();
        em.clear();

        assertThat(affected).isEqualTo(2);
        assertThat(refreshTokenRepository.findById(active1.getId()).orElseThrow().isRevoked()).isTrue();
        assertThat(refreshTokenRepository.findById(active2.getId()).orElseThrow().isRevoked()).isTrue();
        assertThat(refreshTokenRepository.findById(otherActive.getId()).orElseThrow().isRevoked()).isFalse();
    }

    private static String hash(String suffix) {
        // 64자 hex 더미 — 테스트마다 고유 값을 만들기 위해 suffix 만 다르게.
        String base = "0".repeat(64 - suffix.length());
        String hex = (base + suffix).toLowerCase();
        // hex 만 허용
        return hex.replaceAll("[^0-9a-f]", "0");
    }
}
