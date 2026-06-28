package com.groove.catalog.album.application;

/**
 * 앨범이 다른 도메인(주문·장바구니 등)에서 참조 중인지 확인하는 읽기 전용 포트.
 *
 * 삭제 가드는 동기 조회라 이벤트화가 부적합해, 데이터를 가진 도메인이 이 인터페이스를 구현하고
 * catalog 는 구현 묶음만 주입받아 역참조(catalog→order/cart) 없이 단방향을 유지한다.
 */
public interface AlbumReferenceGuard {

    /** 해당 앨범을 참조하는 행이 하나라도 있으면 true. */
    boolean isReferenced(Long albumId);
}
