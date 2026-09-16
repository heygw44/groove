-- 대사 스케줄러가 참조하는 컬럼/인덱스와 판단 이력 테이블을 추가한다. reconcile_attempts 는 같은 결제를
-- 무한히 재시도해 토스 API 를 태우지 않도록 막는 상한 카운터다.
ALTER TABLE payment ADD COLUMN reconcile_attempts int NOT NULL DEFAULT 0;

CREATE INDEX idx_payment_status_updated ON payment (status, updated_at);

-- 대사 결과 이력. 대리키를 쓰는 이유는 sales_reconcile_log 와 같다 - 조회가 range 가 아니라 최근순 나열이다.
CREATE TABLE payment_reconcile_log (
	id bigint NOT NULL AUTO_INCREMENT,
	payment_id bigint NOT NULL,
	before_status varchar(20) NOT NULL,
	after_status varchar(20) NOT NULL,
	toss_status varchar(30) NULL,
	action varchar(20) NOT NULL,
	detail varchar(300) NULL,
	created_at datetime(6) NOT NULL,
	updated_at datetime(6) NOT NULL,
	PRIMARY KEY (id),
	CONSTRAINT fk_payment_reconcile_log_payment FOREIGN KEY (payment_id) REFERENCES payment (id)
) ENGINE=InnoDB;

CREATE INDEX idx_payment_reconcile_log_payment ON payment_reconcile_log (payment_id, created_at);
