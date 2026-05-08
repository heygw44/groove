package com.groove.order.domain;

import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.label.domain.Label;
import com.groove.order.exception.InvalidOrderItemException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OrderItem 도메인 — 스냅샷/검증")
class OrderItemTest {

    private Album album(long id, String title, long price) {
        Artist artist = Artist.create("A", null);
        Genre genre = Genre.create("G");
        Label label = Label.create("L");
        Album a = Album.create(title, artist, genre, label,
                (short) 2020, AlbumFormat.LP_12, price, 100,
                AlbumStatus.SELLING, false, null, null);
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    @Test
    @DisplayName("create — 가격/제목을 주문 시점 값으로 스냅샷")
    void create_snapshotsPriceAndTitle() {
        Album a = album(1L, "Kind of Blue", 30000L);

        OrderItem item = OrderItem.create(a, 2);

        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(item.getUnitPrice()).isEqualTo(30000L);
        assertThat(item.getAlbumTitleSnapshot()).isEqualTo("Kind of Blue");
        assertThat(item.getAlbum()).isSameAs(a);
    }

    @Test
    @DisplayName("create 이후 album 가격/제목이 변경되어도 스냅샷은 보존")
    void snapshot_isImmutableAgainstAlbumChange() {
        Album a = album(1L, "Old Title", 10000L);
        OrderItem item = OrderItem.create(a, 1);

        ReflectionTestUtils.setField(a, "title", "New Title");
        ReflectionTestUtils.setField(a, "price", 99999L);

        assertThat(item.getAlbumTitleSnapshot()).isEqualTo("Old Title");
        assertThat(item.getUnitPrice()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("getSubtotal — unit_price * quantity")
    void subtotal_computed() {
        Album a = album(1L, "T", 25000L);
        OrderItem item = OrderItem.create(a, 3);

        assertThat(item.getSubtotal()).isEqualTo(75000L);
    }

    @Test
    @DisplayName("create — quantity 0 또는 음수 → InvalidOrderItemException")
    void create_rejectsNonPositiveQuantity() {
        Album a = album(1L, "T", 1000L);

        assertThatThrownBy(() -> OrderItem.create(a, 0)).isInstanceOf(InvalidOrderItemException.class);
        assertThatThrownBy(() -> OrderItem.create(a, -1)).isInstanceOf(InvalidOrderItemException.class);
    }

    @Test
    @DisplayName("create — album 가격 0원 허용 (무료 증정 케이스 보호)")
    void create_allowsZeroUnitPrice() {
        Album a = album(1L, "Free", 0L);

        OrderItem item = OrderItem.create(a, 1);

        assertThat(item.getUnitPrice()).isZero();
        assertThat(item.getSubtotal()).isZero();
    }

    @Test
    @DisplayName("create — null album 거부")
    void create_rejectsNullAlbum() {
        assertThatThrownBy(() -> OrderItem.create(null, 1))
                .isInstanceOf(InvalidOrderItemException.class);
    }
}
