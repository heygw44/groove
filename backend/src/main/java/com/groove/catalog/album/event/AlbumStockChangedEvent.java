package com.groove.catalog.album.event;

import java.util.Set;

/**
 * 주문/결제 도메인이 앨범 재고를 변경(주문 차감·취소/결제실패 복원)했을 때 발행하는 이벤트.
 * catalog 가 @TransactionalEventListener(AFTER_COMMIT) 로 받아 albumDetail/albumLandingList 캐시를 무효화한다.
 */
public record AlbumStockChangedEvent(Set<Long> albumIds) {
}
