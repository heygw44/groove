package com.groove.catalog.label.application;

import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.label.domain.Label;
import com.groove.catalog.label.domain.LabelRepository;
import com.groove.catalog.label.exception.LabelInUseException;
import com.groove.catalog.label.exception.LabelNameDuplicatedException;
import com.groove.catalog.label.exception.LabelNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 레이블 CRUD 트랜잭션 경계. 중복 검사 + delete 시 album 참조 검사 모두
 * {@link com.groove.catalog.genre.application.GenreService} 와 동일 이중 방어선 정책을 따른다.
 */
@Service
public class LabelService {

    private final LabelRepository labelRepository;
    private final AlbumRepository albumRepository;

    public LabelService(LabelRepository labelRepository, AlbumRepository albumRepository) {
        this.labelRepository = labelRepository;
        this.albumRepository = albumRepository;
    }

    @Transactional
    public Label create(LabelCommand command) {
        if (labelRepository.existsByName(command.name())) {
            throw new LabelNameDuplicatedException();
        }
        try {
            return labelRepository.saveAndFlush(Label.create(command.name()));
        } catch (DataIntegrityViolationException ex) {
            throw new LabelNameDuplicatedException(ex);
        }
    }

    @Transactional
    public Label update(Long id, LabelCommand command) {
        Label label = labelRepository.findById(id)
                .orElseThrow(LabelNotFoundException::new);
        if (labelRepository.existsByNameAndIdNot(command.name(), id)) {
            throw new LabelNameDuplicatedException();
        }
        label.rename(command.name());
        try {
            return labelRepository.saveAndFlush(label);
        } catch (DataIntegrityViolationException ex) {
            throw new LabelNameDuplicatedException(ex);
        }
    }

    @Transactional
    public void delete(Long id) {
        Label label = labelRepository.findById(id)
                .orElseThrow(LabelNotFoundException::new);
        if (albumRepository.existsByLabel_Id(id)) {
            throw new LabelInUseException();
        }
        try {
            labelRepository.delete(label);
            labelRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new LabelInUseException(ex);
        }
    }

    @Transactional(readOnly = true)
    public List<Label> findAll() {
        return labelRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    @Transactional(readOnly = true)
    public Label findById(Long id) {
        return labelRepository.findById(id)
                .orElseThrow(LabelNotFoundException::new);
    }
}
