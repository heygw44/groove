package com.groove.catalog.album.domain;

import java.util.Map;

/**
 * 재고 복원 공통 헬퍼. 복원량을 albumId 오름차순으로 원자적 가산 UPDATE(AlbumRepository.restoreStock)한다.
 * 같은 album 의 여러 라인은 합산해 한 번의 UPDATE 로 처리한다.
 */
public final class StockRestorer {

    private StockRestorer() {
    }

    /** quantityByAlbumId(albumId → 복원 수량)를 복원하고 복원된 총 수량을 반환한다. */
    public static int restore(AlbumRepository albumRepository, Map<Long, Integer> quantityByAlbumId) {
        quantityByAlbumId.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> albumRepository.restoreStock(entry.getKey(), entry.getValue()));
        return quantityByAlbumId.values().stream().mapToInt(Integer::intValue).sum();
    }
}
