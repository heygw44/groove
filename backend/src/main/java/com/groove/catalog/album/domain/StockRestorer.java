package com.groove.catalog.album.domain;

import java.util.Map;

/**
 * 재고 복원 공통 헬퍼 (#234) — 취소·환불·결제실패 보상·반품 재입고 네 경로가 공유한다.
 *
 * <p>복원량을 albumId 오름차순으로 원자적 가산 UPDATE({@link AlbumRepository#restoreStock})한다.
 * 주문 생성(place, #205)이 다중 album 락을 albumId 오름차순으로 잡는 것과 같은 순서라, place↔복원·복원↔복원이
 * 여러 album 행을 서로 다른 순서로 잠가 발생하는 데드락을 예방한다. 같은 album 의 여러 라인은 합산해 한 번의
 * UPDATE 로 처리한다(락 획득·flush 횟수 절감).
 *
 * <p>정적 무상태 유틸이라 호출 측이 이미 주입한 {@link AlbumRepository} 를 그대로 넘긴다 — 새 의존성·빈 배선
 * 없이 네 ApplicationService 가 동일 로직을 공유한다.
 */
public final class StockRestorer {

    private StockRestorer() {
    }

    /**
     * @param albumRepository    복원 UPDATE 를 수행할 저장소
     * @param quantityByAlbumId  albumId → 복원할 총 수량(양수)
     * @return 복원된 총 수량(로깅용)
     */
    public static int restore(AlbumRepository albumRepository, Map<Long, Integer> quantityByAlbumId) {
        quantityByAlbumId.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> albumRepository.restoreStock(entry.getKey(), entry.getValue()));
        return quantityByAlbumId.values().stream().mapToInt(Integer::intValue).sum();
    }
}
