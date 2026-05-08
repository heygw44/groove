package com.groove.catalog.album.domain;

import com.groove.catalog.album.exception.IllegalStockAdjustmentException;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.label.domain.Label;
import com.groove.common.persistence.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * LP 상품 엔티티 (ERD §4.6 ★).
 *
 * <p>Artist/Genre 는 NOT NULL FK, Label 은 NULL 허용. 모든 연관은 {@link FetchType#LAZY} 로
 * 잡아 N+1 노출은 의도적으로 후속 검색/조회 컨트롤러(W6) 에서 발생시킨다 (W10 시연).
 *
 * <p>재고/가격 비음수 보증은 DB CHECK + 도메인 메서드 이중 방어선이다.
 * {@link #adjustStock(int)} 은 결과가 음수면 {@link IllegalStockAdjustmentException} 으로
 * 거절해 DB CHECK 는 최종 방어선으로만 동작한다.
 */
@Entity
@Table(name = "album")
public class Album extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artist_id", nullable = false)
    private Artist artist;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "genre_id", nullable = false)
    private Genre genre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "label_id")
    private Label label;

    @Column(name = "release_year", nullable = false)
    private short releaseYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 30)
    private AlbumFormat format;

    @Column(name = "price", nullable = false)
    private long price;

    @Column(name = "stock", nullable = false)
    private int stock;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AlbumStatus status;

    @Column(name = "is_limited", nullable = false)
    private boolean limited;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    protected Album() {
    }

    private Album(String title, Artist artist, Genre genre, Label label,
                  short releaseYear, AlbumFormat format, long price, int stock,
                  AlbumStatus status, boolean limited, String coverImageUrl, String description) {
        this.title = title;
        this.artist = artist;
        this.genre = genre;
        this.label = label;
        this.releaseYear = releaseYear;
        this.format = format;
        this.price = price;
        this.stock = stock;
        this.status = status;
        this.limited = limited;
        this.coverImageUrl = coverImageUrl;
        this.description = description;
    }

    /**
     * 정적 팩토리. price/stock 비음수와 입력 검증은 호출 측 (Bean Validation + 서비스) 에서 끝낸 상태로 전달된다고 가정한다.
     */
    public static Album create(String title, Artist artist, Genre genre, Label label,
                               short releaseYear, AlbumFormat format, long price, int stock,
                               AlbumStatus status, boolean limited, String coverImageUrl, String description) {
        return new Album(title, artist, genre, label, releaseYear, format, price, stock,
                status, limited, coverImageUrl, description);
    }

    /**
     * PUT 전체 교체 정책. 재고는 {@link #adjustStock(int)} 으로만 변경하므로 본 메서드의 인자에서 제외한다 —
     * stock 만 별도 변경 경로를 두어 PATCH /stock 과 PUT 의 책임을 분리한다.
     */
    public void update(String title, Artist artist, Genre genre, Label label,
                       short releaseYear, AlbumFormat format, long price,
                       AlbumStatus status, boolean limited, String coverImageUrl, String description) {
        this.title = title;
        this.artist = artist;
        this.genre = genre;
        this.label = label;
        this.releaseYear = releaseYear;
        this.format = format;
        this.price = price;
        this.status = status;
        this.limited = limited;
        this.coverImageUrl = coverImageUrl;
        this.description = description;
    }

    /**
     * 재고 변화량 적용. 결과가 {@code [0, Integer.MAX_VALUE]} 범위를 벗어나면
     * {@link IllegalStockAdjustmentException} 으로 거절된다 — int 오버플로 시 캐스트가
     * 음수로 wrap 되어 DB CHECK 가 500 으로 노출되는 경로를 막는다.
     * 0 delta 는 비즈니스적으로 무의미하지만 컨트롤러 레벨에서 막을 필요는 없어 그대로 통과시킨다.
     */
    public void adjustStock(int delta) {
        long next = (long) this.stock + delta;
        if (next < 0 || next > Integer.MAX_VALUE) {
            throw new IllegalStockAdjustmentException();
        }
        this.stock = (int) next;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Artist getArtist() {
        return artist;
    }

    public Genre getGenre() {
        return genre;
    }

    public Label getLabel() {
        return label;
    }

    public short getReleaseYear() {
        return releaseYear;
    }

    public AlbumFormat getFormat() {
        return format;
    }

    public long getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
    }

    public AlbumStatus getStatus() {
        return status;
    }

    public boolean isLimited() {
        return limited;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public String getDescription() {
        return description;
    }
}
