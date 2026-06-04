package com.groove.catalog.album.domain;

import com.groove.catalog.album.exception.IllegalStockAdjustmentException;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.label.domain.Label;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Album.adjustStock — 재고 변화량 적용 도메인 룰")
class AlbumStockTest {

    private Album fixture(int initialStock) {
        Artist artist = Artist.create("A", null);
        Genre genre = Genre.create("G");
        Label label = Label.create("L");
        return Album.create("Title", artist, genre, label,
                (short) 2020, AlbumFormat.LP_12, 30000L, initialStock,
                AlbumStatus.SELLING, false, null, null);
    }

    @Test
    @DisplayName("양수 delta → 재고 증가")
    void positiveDeltaIncrements() {
        Album album = fixture(5);

        album.adjustStock(3);

        assertThat(album.getStock()).isEqualTo(8);
    }

    @Test
    @DisplayName("음수 delta 로 0 까지 도달 → 통과 (정확히 0)")
    void negativeDeltaToZeroAllowed() {
        Album album = fixture(5);

        album.adjustStock(-5);

        assertThat(album.getStock()).isZero();
    }

    @Test
    @DisplayName("결과가 음수가 되는 delta → IllegalStockAdjustmentException, 재고 불변")
    void negativeResultRejected() {
        Album album = fixture(2);

        assertThatThrownBy(() -> album.adjustStock(-3))
                .isInstanceOf(IllegalStockAdjustmentException.class);
        assertThat(album.getStock()).isEqualTo(2);
    }

    @Test
    @DisplayName("delta 0 → no-op 통과")
    void zeroDeltaIsNoop() {
        Album album = fixture(7);

        album.adjustStock(0);

        assertThat(album.getStock()).isEqualTo(7);
    }

    @Test
    @DisplayName("결과가 Integer.MAX_VALUE 초과 → 거절 (int 오버플로 방지, 재고 불변)")
    void resultOverflowingIntRejected() {
        Album album = fixture(1_000_000);

        assertThatThrownBy(() -> album.adjustStock(Integer.MAX_VALUE))
                .isInstanceOf(IllegalStockAdjustmentException.class);
        assertThat(album.getStock()).isEqualTo(1_000_000);
    }
}
