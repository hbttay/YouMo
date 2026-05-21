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
    public List<Book> listByOwner(Long ownerId) {
        return bookRepository.findByOwnerId(ownerId);
    }

    @Override
    @Transactional
    public Book update(Long id, Book updates) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "书籍不存在"));
        book.setTitle(updates.getTitle());
        book.setTheme(updates.getTheme());
        book.setCoreIdea(updates.getCoreIdea());
        book.setToneLabels(updates.getToneLabels());
        book.setOneSentence(updates.getOneSentence());
        book.setTargetReaderProfile(updates.getTargetReaderProfile());
        book.setViolenceLevel(updates.getViolenceLevel());
        book.setRomanceLevel(updates.getRomanceLevel());
        book.setPoliticsLevel(updates.getPoliticsLevel());
        book.setCivilityLevel(updates.getCivilityLevel());
        book.setEstimatedWords(updates.getEstimatedWords());
        book.setStatus(updates.getStatus());
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
