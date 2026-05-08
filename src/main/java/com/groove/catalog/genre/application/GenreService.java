package com.groove.catalog.genre.application;

import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.genre.domain.GenreRepository;
import com.groove.catalog.genre.exception.GenreInUseException;
import com.groove.catalog.genre.exception.GenreNameDuplicatedException;
import com.groove.catalog.genre.exception.GenreNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 장르 CRUD 트랜잭션 경계.
 *
 * <p>중복 검증은 선검사({@code existsByName} / {@code existsByNameAndIdNot}) + DB UNIQUE 이중 방어선이며,
 * 두 검사 사이에 끼어든 동시 INSERT 는 {@link DataIntegrityViolationException} 을
 * {@link GenreNameDuplicatedException} 으로 변환해 항상 409 로 응답된다.
 *
 * <p>delete 시 album 참조 검사 (W5-3 도입): {@link AlbumRepository#existsByGenre_Id} 사전 검사 +
 * FK 위반 fallback 으로 {@link GenreInUseException} (409) 응답을 보장한다.
 */
@Service
public class GenreService {

    private final GenreRepository genreRepository;
    private final AlbumRepository albumRepository;

    public GenreService(GenreRepository genreRepository, AlbumRepository albumRepository) {
        this.genreRepository = genreRepository;
        this.albumRepository = albumRepository;
    }

    @Transactional
    public Genre create(GenreCommand command) {
        if (genreRepository.existsByName(command.name())) {
            throw new GenreNameDuplicatedException();
        }
        try {
            return genreRepository.saveAndFlush(Genre.create(command.name()));
        } catch (DataIntegrityViolationException ex) {
            throw new GenreNameDuplicatedException(ex);
        }
    }

    @Transactional
    public Genre update(Long id, GenreCommand command) {
        Genre genre = genreRepository.findById(id)
                .orElseThrow(GenreNotFoundException::new);
        if (genreRepository.existsByNameAndIdNot(command.name(), id)) {
            throw new GenreNameDuplicatedException();
        }
        genre.rename(command.name());
        try {
            return genreRepository.saveAndFlush(genre);
        } catch (DataIntegrityViolationException ex) {
            throw new GenreNameDuplicatedException(ex);
        }
    }

    @Transactional
    public void delete(Long id) {
        Genre genre = genreRepository.findById(id)
                .orElseThrow(GenreNotFoundException::new);
        if (albumRepository.existsByGenre_Id(id)) {
            throw new GenreInUseException();
        }
        try {
            genreRepository.delete(genre);
            genreRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new GenreInUseException(ex);
        }
    }

    @Transactional(readOnly = true)
    public List<Genre> findAll() {
        return genreRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    @Transactional(readOnly = true)
    public Genre findById(Long id) {
        return genreRepository.findById(id)
                .orElseThrow(GenreNotFoundException::new);
    }
}
