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
 * 아티스트 CRUD 트랜잭션 경계. name UNIQUE 가 없어 중복 검증은 없다.
 * delete 는 existsByArtist_Id 사전 검사 + DataIntegrityViolationException fallback 변환으로 409 ArtistInUseException 을 보장한다.
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
        boolean nameChanged = !artist.getName().equals(command.name());
        artist.update(command.name(), command.description());
        if (nameChanged) {
            // album.artist_name 비정규화 복제본 일괄 동기화 (이름이 바뀐 경우에만).
            albumRepository.updateArtistNameByArtistId(id, command.name());
        }
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
