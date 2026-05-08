package com.groove.catalog.genre.application;

import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.genre.domain.GenreRepository;
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
 */
@Service
public class GenreService {

    private final GenreRepository genreRepository;

    public GenreService(GenreRepository genreRepository) {
        this.genreRepository = genreRepository;
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
        genreRepository.delete(genre);
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
