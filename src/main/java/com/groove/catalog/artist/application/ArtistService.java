package com.groove.catalog.artist.application;

import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.artist.domain.ArtistRepository;
import com.groove.catalog.artist.exception.ArtistInUseException;
import com.groove.catalog.artist.exception.ArtistNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아티스트 CRUD 트랜잭션 경계.
 *
 * <p>Genre/Label 과 달리 name UNIQUE 가 없어 중복 검증 단계가 존재하지 않는다 — 동명이인을
 * 의도적으로 허용한다 (ERD §4.3). 목록은 호출 측에서 전달한 {@link Pageable} 로 페이징된다.
 *
 * <p>동시 PUT 정책: {@code @Version} 미적용 — 두 PUT 이 겹치면 last-write-wins 로 처리된다.
 * Genre/Label 과 동일 정책이며 W10 운영 부하 측정 후 카탈로그 전반에 걸쳐 재평가한다.
 *
 * <p>delete 시 album 참조 검사: W5-3 (Album) 도입으로 ON DELETE RESTRICT FK 가 활성화되었다.
 * {@link AlbumRepository#existsByArtist_Id} 사전 검사 + {@link DataIntegrityViolationException}
 * fallback 변환으로 항상 409 {@link ArtistInUseException} 응답을 보장한다 (race-safe).
 */
@Service
public class ArtistService {

    private final ArtistRepository artistRepository;
    private final AlbumRepository albumRepository;

    public ArtistService(ArtistRepository artistRepository, AlbumRepository albumRepository) {
        this.artistRepository = artistRepository;
        this.albumRepository = albumRepository;
    }

    @Transactional
    public Artist create(ArtistCommand command) {
        return artistRepository.save(Artist.create(command.name(), command.description()));
    }

    @Transactional
    public Artist update(Long id, ArtistCommand command) {
        Artist artist = artistRepository.findById(id)
                .orElseThrow(ArtistNotFoundException::new);
        artist.update(command.name(), command.description());
        return artist;
    }

    @Transactional
    public void delete(Long id) {
        Artist artist = artistRepository.findById(id)
                .orElseThrow(ArtistNotFoundException::new);
        if (albumRepository.existsByArtist_Id(id)) {
            throw new ArtistInUseException();
        }
        try {
            artistRepository.delete(artist);
            artistRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ArtistInUseException(ex);
        }
    }

    @Transactional(readOnly = true)
    public Page<Artist> findAll(Pageable pageable) {
        return artistRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Artist findById(Long id) {
        return artistRepository.findById(id)
                .orElseThrow(ArtistNotFoundException::new);
    }
}
