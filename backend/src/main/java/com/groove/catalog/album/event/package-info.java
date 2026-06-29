/**
 * 카탈로그 앨범 도메인 이벤트. AlbumStockChangedEvent — 주문/결제/환불/반품이 재고를 바꿨을 때 발행되고,
 * catalog 가 AFTER_COMMIT 리스너로 받아 albumDetail/albumLandingList 캐시를 무효화한다.
 */
package com.groove.catalog.album.event;
