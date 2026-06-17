-- V28: payment.pg_transaction_id UNIQUE 승격 (#252 / CodeRabbit 리뷰 반영).
--
-- findByPgTransactionIdForUpdate / findWithOrderAndItemsByPgTransactionId 가 Optional<Payment> 로
-- 단건을 가정하고, 콜백 멱등 키(idempotencyKeyFor(pgTransactionId))가 'PG 거래 1건 = 결제 1건'
-- 불변식에 의존한다. 기존 비유니크 idx_payment_pg_tx 는 이를 DB 레벨에서 보장하지 못했다.
--
-- 비유니크 인덱스를 UNIQUE 제약(uk_payment_pg_tx)으로 교체한다:
--   - 'PG 거래 1건 = 결제 1건' 을 DB UNIQUE 로 강제(애플리케이션 단건 조회 계약 보호)
--   - 콜백의 SELECT ... FOR UPDATE 가 REPEATABLE_READ 에서 갭/넥스트키 락 대신 정밀 레코드 락만 잡도록
-- pg_transaction_id 는 NULL 허용을 유지한다 — MySQL UNIQUE 는 다중 NULL 을 허용하며,
-- 앱(Payment.initiate)은 항상 non-null·non-blank 를 주입하므로 실질적으로 모든 결제가 유일한 값을 갖는다.

ALTER TABLE payment
    DROP INDEX idx_payment_pg_tx,
    ADD CONSTRAINT uk_payment_pg_tx UNIQUE (pg_transaction_id);
