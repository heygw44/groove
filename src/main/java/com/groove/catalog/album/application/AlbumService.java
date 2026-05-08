package com.groove.catalog.album.application;

import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.album.exception.AlbumNotFoundException;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.artist.domain.ArtistRepository;
import com.groove.catalog.artist.exception.ArtistNotFoundException;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.genre.domain.GenreRepository;
import com.groove.catalog.genre.exception.GenreNotFoundException;
import com.groove.catalog.label.domain.Label;
import com.groove.catalog.label.domain.LabelRepository;
import com.groove.catalog.label.exception.LabelNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 앨범 CRUD + 재고 조정 트랜잭션 경계.
 *
 * <p>FK 검증 정책: artist/genre/label 각각 {@code findById} 로 엔티티를 로드하고 없으면
 * 도메인별 NotFound (404) 를 던진다. label 은 nullable — id 가 null 이면 검증을 건너뛴다.
 *
 * <p>재고 조정({@link #adjustStock(Long, int)}) 은 동시 admin 호출에 대해 last-write-wins —
 * 비관적 락 미적용이라 두 admin 이 같은 album 에 동시 delta 를 보내면 한쪽이 lost update 된다
 * (재고 음수로는 절대 가지 않으므로 안전성 위반은 아님). 비관적 락은 W6 주문 도메인에서
 * 카탈로그 전반과 함께 도입한다.
 *
 * <p>전체 갱신({@link #update(Long, AlbumCommand)}) 은 stock 을 인자로 받지 않는다 —
 * 변경 경로는 반드시 {@link #adjustStock(Long, int)}.
 *
 * <p>응답 시 LazyInitializationException 방지: open-in-view=false 환경에서 컨트롤러는 닫힌
 * 세션의 엔티티를 직렬화한다. 모든 반환 엔티티는 {@link #initializeAssociations(Album)} 으로
 * artist/genre/label 프록시를 트랜잭션 내에서 강제 초기화한다 (admin 단건 한정 — 미세한 N+1).
 */
@Service
public class AlbumService {

    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;
    private final GenreRepository genreRepository;
    private final LabelRepository labelRepository;

    public AlbumService(AlbumRepository albumRepository,
                        ArtistRepository artistRepository,
                        GenreRepository genreRepository,
                        LabelRepository labelRepository) {
        this.albumRepository = albumRepository;
        this.artistRepository = artistRepository;
        this.genreRepository = genreRepository;
        this.labelRepository = labelRepository;
    }

    @Transactional
    public Album create(AlbumCommand command, int initialStock) {
        Artist artist = loadArtist(command.artistId());
        Genre genre = loadGenre(command.genreId());
        Label label = loadLabel(command.labelId());

        Album album = Album.create(
                command.title(),
                artist,
                genre,
                label,
                command.releaseYear(),
                command.format(),
                command.price(),
                initialStock,
                command.status(),
                command.limited(),
                command.coverImageUrl(),
                command.description()
        );
        return initializeAssociations(albumRepository.save(album));
    }

    @Transactional
    public Album update(Long id, AlbumCommand command) {
        Album album = albumRepository.findById(id)
                .orElseThrow(AlbumNotFoundException::new);

        Artist artist = loadArtist(command.artistId());
        Genre genre = loadGenre(command.genreId());
        Label label = loadLabel(command.labelId());

        album.update(
                command.title(),
                artist,
                genre,
                label,
                command.releaseYear(),
                command.format(),
                command.price(),
                command.status(),
                command.limited(),
                command.coverImageUrl(),
                command.description()
        );
        return initializeAssociations(album);
    }

    @Transactional
    public Album adjustStock(Long id, int delta) {
        Album album = albumRepository.findById(id)
                .orElseThrow(AlbumNotFoundException::new);
        album.adjustStock(delta);
        return initializeAssociations(album);
    }

    @Transactional
    public void delete(Long id) {
        Album album = albumRepository.findById(id)
                .orElseThrow(AlbumNotFoundException::new);
        albumRepository.delete(album);
    }

    private Artist loadArtist(Long artistId) {
        return artistRepository.findById(artistId)
                .orElseThrow(ArtistNotFoundException::new);
    }

    private Genre loadGenre(Long genreId) {
        return genreRepository.findById(genreId)
                .orElseThrow(GenreNotFoundException::new);
    }

    private Label loadLabel(Long labelId) {
        if (labelId == null) {
            return null;
        }
        return labelRepository.findById(labelId)
                .orElseThrow(LabelNotFoundException::new);
    }

    /**
     * artist/genre/label lazy 프록시를 트랜잭션 내에서 강제 초기화. {@code create} 처럼 연관 엔티티가
     * 같은 세션에서 막 로드된 실엔티티인 경우에도, 일관성을 위해 항상 호출한다 — 미래에 새 lazy
     * 연관이 추가되어도 단일 진입점에서 처리되도록.
     */
    private Album initializeAssociations(Album album) {
        album.getArtist().getName();
        album.getGenre().getName();
        if (album.getLabel() != null) {
            album.getLabel().getName();
        }
        return album;
    }
}
