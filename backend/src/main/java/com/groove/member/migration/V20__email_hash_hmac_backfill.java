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
 * 이메일 점유 해시 결정적 SHA-256 → 서버 비밀키 HMAC(v1) 백필. 활성 회원(deleted_at IS NULL)만 EmailHasher
 * 로 v1 재계산하고, 탈퇴 회원은 legacy SHA-256 을 그대로 둔다. @Component 로 두어 Flyway 가 JavaMigration 빈을
 * 자동 등록한다. 재해시는 email 평문에서 계산하므로 재실행해도 동일 값으로 수렴한다(멱등).
 */
@Component
public class V20__email_hash_hmac_backfill extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V20__email_hash_hmac_backfill.class);

    /** id keyset 페이지 크기. */
    private static final int BATCH_SIZE = 500;

    private final EmailHasher emailHasher;

    public V20__email_hash_hmac_backfill(EmailHasher emailHasher) {
        this.emailHasher = emailHasher;
    }

    @Override
    public void migrate(Context context) throws Exception {
        int rehashed = backfill(context.getConnection()); // Flyway 소유 커넥션
        log.info("[V20] email_hash HMAC 백필 완료 — 활성 회원 {}건 재계산(v1), 탈퇴 회원은 legacy 보존", rehashed);
    }

    /** 활성 회원만 v1(HMAC) 해시로 재계산한다. id keyset 페이지네이션 + PreparedStatement 배치. 재계산된 활성 회원 수 반환. */
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
