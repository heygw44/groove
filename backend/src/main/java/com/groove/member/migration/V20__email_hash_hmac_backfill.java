package com.groove.member.migration;

import com.groove.member.security.EmailHasher;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * 이메일 점유 해시 결정적 SHA-256 → 서버 비밀키 HMAC(v1) 백필 (#186).
 *
 * <p>컬럼 폭 확장(CHAR(64)→VARCHAR(72))은 선행 SQL 마이그레이션 {@code V19__widen_email_hash_column} 이
 * 모든 컨텍스트에서 수행한다. 본 마이그레이션은 <b>활성 회원의 해시 재계산</b>만 맡는다.
 *
 * <p><b>왜 Java 마이그레이션인가</b>: 새 해시는 서버 비밀키 기반 {@code "v1:" + HMAC-SHA256(정규화 이메일)} 인데
 * MySQL 에는 이와 동치인 HMAC 내장 함수가 없다(V18 의 {@code SHA2(...)} SQL 백필로는 재현 불가). 그래서 앱과
 * 동일한 {@link EmailHasher} 를 DI 받아 Java 에서 재계산한다. Spring Boot {@code FlywayAutoConfiguration} 이
 * {@code JavaMigration} 빈을 자동 등록하므로 본 클래스는 {@code @Component} 로 둔다 — {@code db.migration}
 * 패키지 밖에 두어 Flyway 의 classpath 스캔(SQL 위치)과 빈 등록이 겹쳐 중복 등록되는 것을 피한다.
 *
 * <p><b>빈 테이블 안전</b>: 컬럼 확장은 V19(SQL)가 책임지므로, 본 백필이 일부 컨텍스트(@Component 빈을 로딩하지
 * 않는 {@code @DataJpaTest} 슬라이스 등)에서 실행되지 않아도 무해하다 — 그런 컨텍스트의 member 테이블은 비어
 * 있고, 새로 저장되는 행은 이미 v1 해시를 갖기 때문이다. 백필이 의미를 갖는 곳은 운영 등 기존 데이터가 있는 DB 뿐이다.
 *
 * <p><b>대상</b>: 활성 회원({@code deleted_at IS NULL})만 v1 으로 재계산한다. 탈퇴 회원은 평문이 익명화
 * ({@code withdrawn-{id}@deleted.local})로 파기돼 HMAC 재계산이 불가능하므로 legacy SHA-256 을 그대로 둔다 —
 * 가입 점유 검사({@code MemberService.signup})가 v1·legacy 두 해시를 함께 보아 재가입 차단(패턴 A)을 유지한다.
 *
 * <p><b>멱등</b>: 재해시는 {@code email_hash} 가 아니라 {@code email}(평문)로부터 계산하므로 재실행해도
 * {@code v1:v1:...} 같은 이중 prefix 가 생기지 않고 동일 값으로 수렴한다. v1 값({@code v1:} prefix)과 legacy
 * 값(bare 64 hex)은 문자열 공간이 disjoint 하고 활성 재해시는 단사라 {@code uk_member_email_hash} UNIQUE
 * 위반이 발생하지 않는다.
 */
@Component
public class V20__email_hash_hmac_backfill extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V20__email_hash_hmac_backfill.class);

    /** id keyset 페이지 크기 — 대용량 회원도 메모리를 bound 하기 위함. */
    private static final int BATCH_SIZE = 500;

    private final EmailHasher emailHasher;

    public V20__email_hash_hmac_backfill(EmailHasher emailHasher) {
        this.emailHasher = emailHasher;
    }

    @Override
    public void migrate(Context context) throws Exception {
        int rehashed = backfill(context.getConnection()); // Flyway 소유 커넥션 — close/commit 금지
        log.info("[V20] email_hash HMAC 백필 완료 — 활성 회원 {}건 재계산(v1), 탈퇴 회원은 legacy 보존", rehashed);
    }

    /**
     * 활성 회원만 v1(HMAC) 해시로 재계산한다. id keyset 페이지네이션 + {@link PreparedStatement} 배치로 수행한다.
     * 패키지 가시성 — 마이그레이션 동작 검증 테스트가 행을 직접 insert 한 뒤 호출한다(재사용 Testcontainer 는
     * Flyway 가 본 마이그레이션을 재실행하지 않으므로).
     *
     * @return 재계산된 활성 회원 수
     */
    int backfill(Connection connection) throws Exception {
        String selectSql = "SELECT id, email FROM member "
                + "WHERE deleted_at IS NULL AND id > ? ORDER BY id LIMIT ?";
        String updateSql = "UPDATE member SET email_hash = ? WHERE id = ?";

        long lastId = 0;
        int total = 0;
        while (true) {
            List<Row> page = new ArrayList<>();
            try (PreparedStatement select = connection.prepareStatement(selectSql)) {
                select.setLong(1, lastId);
                select.setInt(2, BATCH_SIZE);
                try (ResultSet rs = select.executeQuery()) {
                    while (rs.next()) {
                        page.add(new Row(rs.getLong("id"), rs.getString("email")));
                    }
                }
            }
            if (page.isEmpty()) {
                break;
            }
            try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                for (Row row : page) {
                    update.setString(1, emailHasher.hash(row.email()));
                    update.setLong(2, row.id());
                    update.addBatch();
                }
                update.executeBatch();
            }
            lastId = page.get(page.size() - 1).id();
            total += page.size();
        }
        return total;
    }

    private record Row(long id, String email) {
    }
}
