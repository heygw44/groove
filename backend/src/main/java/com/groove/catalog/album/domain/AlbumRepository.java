package com.groove.catalog.album.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 앨범 영속성.
 *
 * <p>FK 역참조 검사용 {@code existsByXxxId} 는 Artist/Genre/Label 의 delete 시
 * IN_USE(409) 사전 검사 (race condition 은 DB ON DELETE RESTRICT 가 최종 방어).
 *
 * <p>공개 검색/조회({@code GET /albums})는 {@link JpaSpecificationExecutor} 로 동적 필터를 조합한다.
 * Specification 정의는 {@link AlbumSpecs} 참조.
 */
public interface AlbumRepository extends JpaRepository<Album, Long>, JpaSpecificationExecutor<Album> {

    /**
     * 공개 검색/목록(#203)용 {@code findAll} 오버라이드. artist/genre/label(모두 {@code @ManyToOne(LAZY)})을
     * ad-hoc {@link EntityGraph} 로 동반 페치해 {@code AlbumSummaryResponse.from()} 의 N+1 SELECT 를 제거한다.
     *
     * <p>세 연관 모두 to-one 이라 컬렉션 페치가 아니므로 페이징 in-memory(HHH000104) 경고 없이
     * 본 쿼리에 OUTER JOIN 으로 인라인된다. Specification 동적 필터·count 쿼리는 그대로 유지된다
     * (Spring Data 는 count 쿼리에 EntityGraph 를 적용하지 않음).
     */
    @Override
    @EntityGraph(attributePaths = {"artist", "genre", "label"})
    Page<Album> findAll(Specification<Album> spec, Pageable pageable);

    /**
     * 주문 재고 차감(#205)용 비관적 락 조회 — album 행을 {@code SELECT ... FOR UPDATE} 로 잠근다
     * ({@code CouponRepository.findByIdForUpdate} 와 동일 패턴). SELECT 시점에 행 락을 선점하므로
     * 동시 주문의 read-modify-write 구간이 직렬화되어 lost-update(오버셀)가 제거된다.
     *
     * <p>락은 재고가 있는 album 행만 잡으면 되므로 artist/genre/label 페치 없이 최소 쿼리로 둔다
     * ({@code OrderService.place} 는 이 연관들을 읽지 않는다). 행 락은 트랜잭션 종료 시 해제된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Album a WHERE a.id = :id")
    Optional<Album> findByIdForUpdate(@Param("id") Long id);

    boolean existsByArtist_Id(Long artistId);

    boolean existsByGenre_Id(Long genreId);

    boolean existsByLabel_Id(Long labelId);

    /**
     * 비정규화 {@code artist_name}(#204) 동기화 — artist 이름 변경 시 해당 artist 의 모든 album 을
     * 일괄 갱신한다. FULLTEXT 검색용 복제본이라 ArtistService.update 의 이름 변경 경로에서만 호출된다.
     *
     * <p>{@code clearAutomatically}/{@code flushAutomatically} 는 의도적으로 끈다 — 호출 시점에
     * 영속성 컨텍스트에 dirty 상태인 {@code Artist} 가 있는데, 컨텍스트를 clear 하면 그 변경이 flush
     * 전에 유실된다. 두 변경은 서로 다른 테이블(artist/album)이라 같은 트랜잭션 커밋 시 안전하게 함께
     * 반영되며, update 흐름은 이후 album 을 읽지 않으므로 stale 우려가 없다.
     */
    @Modifying
    @Query("UPDATE Album a SET a.artistName = :name WHERE a.artist.id = :artistId")
    int updateArtistNameByArtistId(@Param("artistId") Long artistId, @Param("name") String name);

    /**
     * 재고 복원(취소·환불·결제실패 보상·반품 재입고)용 원자적 가산 UPDATE — place 의 비관락(#205)과 달리
     * 행을 미리 잠그지 않고 DB 가 한 문장 안에서 {@code stock = stock + :delta} 를 상대 증분으로 적용한다
     * ({@code CouponRepository.incrementIssuedCount} 패턴). InnoDB 가 매칭 행에 X-락을 걸고 절대값이
     * 아닌 증분을 쓰므로, 동시 place(SELECT … FOR UPDATE)·동시 복원과 같은 행에서 직렬화되어
     * place↔복원 간 album.stock lost-update 창이 제거된다 (#234). delta 는 복원량(양수)만 전달된다.
     *
     * <p>{@code clearAutomatically} 는 의도적으로 끈다 — 호출 측은 이 메서드 이후에도 같은 트랜잭션에서
     * 관리 엔티티를 변경한다(취소·환불의 {@code CouponApplicationService.restoreForOrder} → MemberCoupon,
     * 반품의 {@code order.markReturned}/{@code claim.markRefunded}). 컨텍스트를 clear 하면 이 엔티티들이
     * detach 되어 이후 변경이 커밋 시 dirty-check 되지 않고 유실된다. {@code flushAutomatically} 는 켜서
     * 직전 dirty 상태를 먼저 flush 해 쓰기 순서를 결정적으로 둔다(보류 변경은 order/payment/claim 등 다른
     * 테이블이라 album UPDATE 와 충돌하지 않는다).
     *
     * <p>벌크 UPDATE 는 영속성 컨텍스트를 우회하므로 그래프로 로드된 Album 의 in-memory stock 은 stale 로
     * 남는다 — 복원 경로는 이후 stock 을 재조회/직렬화하지 않으므로 무해하다. 단, 호출 측이 Album 을
     * {@code adjustStock} 으로 dirty 화하면 dirty-check 가 stale 절대값 UPDATE 를 내보내 이 증분을 덮어쓰므로,
     * 복원 루프에서는 {@code adjustStock} 을 호출하지 않고 이 메서드만 쓴다.
     *
     * <p>네 복원 경로는 직접 호출하지 않고 {@link StockRestorer} 를 통해 albumId 오름차순으로 정렬·합산한 뒤
     * 호출한다 — place(#205)가 다중 album 락을 albumId 오름차순으로 잡는 것과 같은 순서라 데드락을 피한다.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Album a SET a.stock = a.stock + :delta WHERE a.id = :id")
    int restoreStock(@Param("id") Long id, @Param("delta") int delta);
}
