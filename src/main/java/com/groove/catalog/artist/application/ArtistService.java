package com.groove.catalog.artist.application;

import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.artist.domain.ArtistRepository;
import com.groove.catalog.artist.exception.ArtistNotFoundException;
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
 * <p>W5-3 (Album) 도입 이후의 추가 처리: Album 이 artist_id 를 FK 로 참조하면 delete 시
 * ON DELETE RESTRICT 로 인한 {@link org.springframework.dao.DataIntegrityViolationException}
 * 을 도메인 예외로 변환해야 한다. 본 처리는 W5-3 이슈 범위에서 추가한다.
 */
@Service
public class ArtistService {

    private final ArtistRepository artistRepository;

    public ArtistService(ArtistRepository artistRepository) {
        this.artistRepository = artistRepository;
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
        if (!artistRepository.existsById(id)) {
            throw new ArtistNotFoundException();
        }
        artistRepository.deleteById(id);
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
