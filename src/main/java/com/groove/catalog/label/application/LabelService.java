package com.groove.catalog.label.application;

import com.groove.catalog.label.domain.Label;
import com.groove.catalog.label.domain.LabelRepository;
import com.groove.catalog.label.exception.LabelNameDuplicatedException;
import com.groove.catalog.label.exception.LabelNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 레이블 CRUD 트랜잭션 경계. {@link GenreService} 와 동일한 이중 방어선 정책을 따른다.
 */
@Service
public class LabelService {

    private final LabelRepository labelRepository;

    public LabelService(LabelRepository labelRepository) {
        this.labelRepository = labelRepository;
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
        if (!labelRepository.existsById(id)) {
            throw new LabelNotFoundException();
        }
        labelRepository.deleteById(id);
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
