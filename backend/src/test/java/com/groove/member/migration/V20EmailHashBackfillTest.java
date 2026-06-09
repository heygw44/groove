package com.groove.member.migration;

import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.member.security.EmailHasher;
import com.groove.support.MemberFixtures;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link V20__email_hash_hmac_backfill} 동작 검증 (#186).
 *
 * <p>재사용 Testcontainer 는 부팅 시 Flyway 가 V19(SQL 컬럼 확장)·V20(빈 테이블 백필 no-op)을 이미 적용했으므로,
 * Flyway 를 재실행하는 대신 마이그레이션의 {@code backfill(Connection)} 을 직접 호출해 재해시 로직만 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("V20 email_hash HMAC 백필 마이그레이션")
class V20EmailHashBackfillTest {

    private static final String ACTIVE_EMAIL = "v20-active@x.com";
    private static final String WITHDRAWN_EMAIL = "v20-withdrawn@x.com";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EmailHasher emailHasher;

    @Autowired
    private MemberRepository memberRepository;

    private Long activeId;
    private Long withdrawnId;

    @BeforeEach
    void seedLegacyRows() {
        // 마이그레이션 이전 상태 모사 — email_hash 가 prefix 없는 legacy SHA-256.
        Member active = memberRepository.saveAndFlush(Member.register(
                ACTIVE_EMAIL, MemberFixtures.legacyHash(ACTIVE_EMAIL), "$2a$12$h", "활성", "01000000001"));
        Member withdrawn = memberRepository.saveAndFlush(Member.register(
                WITHDRAWN_EMAIL, MemberFixtures.legacyHash(WITHDRAWN_EMAIL), "$2a$12$h", "탈퇴", "01000000002"));
        withdrawn.withdraw(Instant.now()); // deleted_at 설정 — 평문 파기로 재계산 불가한 탈퇴 회원
        withdrawn.anonymize();
        memberRepository.saveAndFlush(withdrawn);

        activeId = active.getId();
        withdrawnId = withdrawn.getId();
    }

    @AfterEach
    void cleanup() {
        memberRepository.deleteAllById(List.of(activeId, withdrawnId));
    }

    @Test
    @DisplayName("backfill → 활성 회원만 v1(HMAC) 재계산, 탈퇴 회원은 legacy SHA-256 보존")
    void backfill_rehashesActiveMembersOnly() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            new V20__email_hash_hmac_backfill(emailHasher).backfill(connection);
        }

        assertThat(emailHashOf(activeId))
                .as("활성 회원은 v1(HMAC) 로 재계산")
                .isEqualTo(emailHasher.hash(ACTIVE_EMAIL));
        assertThat(emailHashOf(withdrawnId))
                .as("탈퇴 회원은 재계산 불가 — legacy SHA-256 보존")
                .isEqualTo(MemberFixtures.legacyHash(WITHDRAWN_EMAIL));
    }

    @Test
    @DisplayName("Flyway 적용 후 email_hash 컬럼은 VARCHAR(72) (V19 SQL 마이그레이션)")
    void emailHashColumnIsVarchar72() throws Exception {
        try (Connection connection = dataSource.getConnection();
             ResultSet rs = connection.getMetaData()
                     .getColumns(connection.getCatalog(), null, "member", "email_hash")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("TYPE_NAME")).containsIgnoringCase("VARCHAR");
            assertThat(rs.getInt("COLUMN_SIZE")).isEqualTo(72);
        }
    }

    private String emailHashOf(Long id) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT email_hash FROM member WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getString(1);
            }
        }
    }
}
