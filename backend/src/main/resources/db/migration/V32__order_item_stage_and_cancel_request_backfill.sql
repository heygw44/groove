-- V26·V27 이 운영에 적용된 뒤 두 파일에 덧붙였던 백필을 옮긴다. 적용된 마이그레이션을 고치면 체크섬이 어긋나
-- validate 가 기동을 막는다. 이미 채워진 값은 건드리지 않아 다시 돌려도 결과가 같다.

-- 재설계 전에는 발송·배송완료가 주문의 마지막 상태 변경이라 orders.updated_at 을 그 시각으로 쓴다.
UPDATE order_item oi
JOIN orders o ON o.id = oi.order_id
SET oi.prepared_at = CASE
        WHEN oi.status = 'PREPARING' AND oi.prepared_at IS NULL THEN o.updated_at
        ELSE oi.prepared_at
    END,
    oi.shipped_at = CASE
        WHEN oi.status IN ('SHIPPING', 'DELIVERED') AND oi.shipped_at IS NULL THEN o.updated_at
        ELSE oi.shipped_at
    END,
    oi.delivered_at = CASE
        WHEN oi.status = 'DELIVERED' AND oi.delivered_at IS NULL THEN o.updated_at
        ELSE oi.delivered_at
    END
WHERE (oi.status = 'PREPARING' AND oi.prepared_at IS NULL)
   OR (oi.status IN ('SHIPPING', 'DELIVERED') AND oi.shipped_at IS NULL)
   OR (oi.status = 'DELIVERED' AND oi.delivered_at IS NULL);

-- 배포 순간 CANCEL_REQUESTED 인 전액취소는 런타임(PaymentCancelWriter)이 이 결제의 REQUESTED 행으로 마무리한다.
-- 순번 키로 이미 REQUESTED 행이 있으면 넣지 않는다. 두 행이 생기면 어느 쪽으로 확정할지 모호해진다.
INSERT INTO payment_cancel (payment_id, idempotency_key, cancel_amount, status, requested_at, done_at,
    created_at, updated_at)
SELECT p.id, CONCAT('cancel-', p.payment_key), p.amount - p.canceled_amount, 'REQUESTED', p.updated_at, NULL,
    NOW(6), NOW(6)
FROM payment p
WHERE p.status = 'CANCEL_REQUESTED'
  AND NOT EXISTS (
      SELECT 1 FROM payment_cancel pc
      WHERE pc.payment_id = p.id
        AND (pc.status = 'REQUESTED' OR pc.idempotency_key = CONCAT('cancel-', p.payment_key))
  );

-- 불변식 검증. 위반이 있으면 CHECK 제약이 실패해 마이그레이션 전체가 롤백된다(DDL 이 없어 한 트랜잭션이다).
CREATE TEMPORARY TABLE v32_invariant (
    violations bigint NOT NULL,
    CONSTRAINT chk_v32_invariant CHECK (violations = 0)
);

-- 준비중·배송중·배송완료 상품주문에 해당 단계 시각이 비어 있는 건.
INSERT INTO v32_invariant (violations)
SELECT COUNT(*)
FROM order_item
WHERE (status = 'PREPARING' AND prepared_at IS NULL)
   OR (status IN ('SHIPPING', 'DELIVERED') AND shipped_at IS NULL)
   OR (status = 'DELIVERED' AND delivered_at IS NULL);

-- CANCEL_REQUESTED 결제인데 런타임이 찾을 REQUESTED 취소 행이 없는 건.
INSERT INTO v32_invariant (violations)
SELECT COUNT(*) FROM payment p
WHERE p.status = 'CANCEL_REQUESTED'
  AND NOT EXISTS (
      SELECT 1 FROM payment_cancel pc WHERE pc.payment_id = p.id AND pc.status = 'REQUESTED'
  );

DROP TEMPORARY TABLE v32_invariant;
