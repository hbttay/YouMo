package com.youmo.core.service.impl;

import com.youmo.common.base.BusinessException;
import com.youmo.common.entity.Book;
import com.youmo.core.repository.BookRepository;
import com.youmo.core.service.BookService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookServiceImpl implements BookService {

    private final BookRepository bookRepository;

    @Override
    @Transactional
    public Book create(Book book) {
        return bookRepository.save(book);
    }

    @Override
    public Optional<Book> getById(Long id) {
        return bookRepository.findById(id);
    }

    @Override
    public List<Book> listAll() {
        return bookRepository.findAll();
    }

    @Override
    @Transactional
    public Book update(Long id, Book updates) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "书籍不存在"));
        if (updates.getTitle() != null) book.setTitle(updates.getTitle());
        if (updates.getTheme() != null) book.setTheme(updates.getTheme());
        if (updates.getCoreIdea() != null) book.setCoreIdea(updates.getCoreIdea());
        if (updates.getToneLabels() != null) book.setToneLabels(updates.getToneLabels());
        if (updates.getOneSentence() != null) book.setOneSentence(updates.getOneSentence());
        if (updates.getTargetReaderProfile() != null) book.setTargetReaderProfile(updates.getTargetReaderProfile());
        if (updates.getViolenceLevel() != null) book.setViolenceLevel(updates.getViolenceLevel());
        if (updates.getRomanceLevel() != null) book.setRomanceLevel(updates.getRomanceLevel());
        if (updates.getPoliticsLevel() != null) book.setPoliticsLevel(updates.getPoliticsLevel());
        if (updates.getCivilityLevel() != null) book.setCivilityLevel(updates.getCivilityLevel());
        if (updates.getEstimatedWords() != null) book.setEstimatedWords(updates.getEstimatedWords());
        if (updates.getExtraAttributes() != null) book.setExtraAttributes(updates.getExtraAttributes());
        if (updates.getNegativeConstraints() != null) book.setNegativeConstraints(updates.getNegativeConstraints());
        if (updates.getStatus() != null) book.setStatus(updates.getStatus());
        if (updates.getLengthType() != null) book.setLengthType(updates.getLengthType());
        if (updates.getCreationMode() != null) book.setCreationMode(updates.getCreationMode());
        if (updates.getCharacterMode() != null) book.setCharacterMode(updates.getCharacterMode());
        return bookRepository.save(book);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!bookRepository.existsById(id)) {
            throw new BusinessException(404, "书籍不存在");
        }
        bookRepository.deleteById(id);
    }
}
