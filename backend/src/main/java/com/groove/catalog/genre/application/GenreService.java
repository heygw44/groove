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
 * 장르 CRUD 트랜잭션 경계. 중복은 existsByName 선검사 + DB UNIQUE 이중 방어선
 * (DataIntegrityViolationException 을 GenreNameDuplicatedException 으로 변환).
 * delete 시 existsByGenre_Id 사전 검사 + FK 위반 fallback 으로 GenreInUseException 보장.
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
