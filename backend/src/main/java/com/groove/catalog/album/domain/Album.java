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
import org.hibernate.annotations.BatchSize;

/**
 * LP 상품 엔티티. Artist/Genre 는 NOT NULL FK, Label 은 NULL 허용. 모든 연관은 LAZY.
 * 클래스 레벨 @BatchSize 는 Album 이 LAZY 프록시로 로드될 때(예: cart_item.album) IN 쿼리 1회로 일괄 초기화한다.
 */
@Entity
@Table(name = "album")
@BatchSize(size = 100)
public class Album extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    /** artist.name 의 비정규화 복제본 (FULLTEXT 검색 전용, API 미노출). */
    @Column(name = "artist_name", nullable = false, length = 200)
    private String artistName;

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
        this.artistName = artist.getName();
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

    /** 정적 팩토리. */
    public static Album create(String title, Artist artist, Genre genre, Label label,
                               short releaseYear, AlbumFormat format, long price, int stock,
                               AlbumStatus status, boolean limited, String coverImageUrl, String description) {
        return new Album(title, artist, genre, label, releaseYear, format, price, stock,
                status, limited, coverImageUrl, description);
    }

    /** PUT 전체 교체. 재고는 adjustStock 으로만 변경하므로 인자에서 제외. */
    public void update(String title, Artist artist, Genre genre, Label label,
                       short releaseYear, AlbumFormat format, long price,
                       AlbumStatus status, boolean limited, String coverImageUrl, String description) {
        this.title = title;
        this.artist = artist;
        this.artistName = artist.getName();
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

    /** 재고 변화량 적용. 결과가 [0, Integer.MAX_VALUE] 범위를 벗어나면 IllegalStockAdjustmentException. */
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

    /** artist.name 의 비정규화 복제본 (FULLTEXT 검색 전용, API 미노출). */
    public String getArtistName() {
        return artistName;
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

    /** 구매 가능 상태인지 — SELLING 만 주문·장바구니에 담을 수 있다. */
    public boolean isSelling() {
        return status == AlbumStatus.SELLING;
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
