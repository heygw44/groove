-- 멱등성 마커 소유권 토큰(#317 회수 race fencing).
-- 처리 타임아웃 초과로 마커가 회수되고 다른 요청이 같은 키로 새 마커/캐시를 만든 뒤, 뒤늦게 끝난 원소유자가
-- 그 행을 finalize(complete)하거나 삭제(removeMarker)하지 못하도록 complete·소유자 삭제를 owner_token 으로 제약한다.
-- 기존 행은 NULL(이전 버전 생성분 — TTL 정리로 회수).
ALTER TABLE idempotency_record ADD COLUMN owner_token VARCHAR(36) NULL;
