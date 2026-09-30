-- 부분취소 도입. 기존 취소 멱등키(cancel-{paymentKey})는 결제 한 건에 고정돼 있어 두 번째 부분취소를 보내면
-- 토스가 첫 번째 취소 결과를 그대로 재생했다. 취소 건마다 다른 Idempotency-Key 를 쓰도록 취소 요청 자체를
-- payment_cancel 이력으로 남기고, payment.canceled_amount 로 지금까지 취소된 누적 금액을 추적한다.
ALTER TABLE payment
    ADD COLUMN canceled_amount decimal(10,2) NOT NULL DEFAULT 0;

CREATE TABLE payment_cancel (
    id                   bigint          NOT NULL AUTO_INCREMENT,
    payment_id           bigint          NOT NULL,
    -- 토스 paymentKey 자체가 최대 200자라 cancel-{paymentKey}-{seq} 가 64자를 넘을 수 있어 넉넉히 잡는다.
    idempotency_key      varchar(300)    NOT NULL,
    cancel_amount        decimal(10,2)   NOT NULL,
    status               varchar(20)     NOT NULL DEFAULT 'REQUESTED',
    toss_transaction_key varchar(200)    NULL,
    reason               varchar(200)    NULL,
    requested_at         datetime(6)     NOT NULL,
    done_at              datetime(6)     NULL,
    created_at           datetime(6)     NOT NULL,
    updated_at           datetime(6)     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_cancel_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_payment_cancel_payment FOREIGN KEY (payment_id) REFERENCES payment (id)
) ENGINE=InnoDB;

CREATE INDEX idx_payment_cancel_payment_status ON payment_cancel (payment_id, status);

-- 기존 전액취소 결제마다 완료된 취소 건을 1행씩 백필한다. requested_at/done_at 은 실제 요청 시각을 알 수 없어
-- canceled_at(없으면 updated_at)을 그대로 쓴다. approved_at IS NOT NULL 로 좁히는 이유: 입금 전 가상계좌
-- 폐쇄(cancelVirtualAccount)는 승인된 적이 없어 실제로 돈이 오간 적이 없다 - "환불"이 아니므로 payment_cancel
-- 행을 만들지 않는다(런타임 경로도 이 결제 유형은 payment_cancel 을 남기지 않는다).
INSERT INTO payment_cancel (payment_id, idempotency_key, cancel_amount, status, requested_at, done_at,
    created_at, updated_at)
SELECT id, CONCAT('legacy-', id), amount, 'DONE', COALESCE(canceled_at, updated_at), canceled_at,
    NOW(6), NOW(6)
FROM payment
WHERE status = 'CANCELED' AND approved_at IS NOT NULL;

UPDATE payment
SET canceled_amount = amount
WHERE status = 'CANCELED' AND approved_at IS NOT NULL;

-- 배포 순간 CANCEL_REQUESTED 인 전액취소는 런타임(PaymentCancelWriter)이 cancel-{payment_key} 로 행을 찾는다.
INSERT INTO payment_cancel (payment_id, idempotency_key, cancel_amount, status, requested_at, done_at,
    created_at, updated_at)
SELECT id, CONCAT('cancel-', payment_key), amount - canceled_amount, 'REQUESTED', updated_at, NULL,
    NOW(6), NOW(6)
FROM payment
WHERE status = 'CANCEL_REQUESTED';

-- 불변식 검증. 위반이 있으면 CHECK 제약이 실패해 마이그레이션 자체가 롤백된다.
CREATE TEMPORARY TABLE v27_invariant (
    violations bigint NOT NULL,
    CONSTRAINT chk_v27_invariant CHECK (violations = 0)
);

-- 취소 누적액이 결제 금액을 넘는 건.
INSERT INTO v27_invariant (violations)
SELECT COUNT(*) FROM payment WHERE canceled_amount > amount;

-- 승인 이력이 있는 CANCELED 결제인데 백필된 완료 취소 건이 없는 건.
INSERT INTO v27_invariant (violations)
SELECT COUNT(*) FROM payment p
WHERE p.status = 'CANCELED' AND p.approved_at IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM payment_cancel pc WHERE pc.payment_id = p.id AND pc.status = 'DONE'
  );

-- CANCEL_REQUESTED 결제인데 런타임이 찾는 cancel-{payment_key} REQUESTED 행이 없는 건.
INSERT INTO v27_invariant (violations)
SELECT COUNT(*) FROM payment p
WHERE p.status = 'CANCEL_REQUESTED'
  AND NOT EXISTS (
      SELECT 1 FROM payment_cancel pc
      WHERE pc.payment_id = p.id AND pc.status = 'REQUESTED'
        AND pc.idempotency_key = CONCAT('cancel-', p.payment_key)
  );

DROP TEMPORARY TABLE v27_invariant;
