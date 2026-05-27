package com.youmo.core.service;

import com.youmo.common.entity.Book;
import java.util.List;
import java.util.Optional;

public interface BookService {

    Book create(Book book);

    Optional<Book> getById(Long id);

    /** @deprecated use listByOwner for multi-tenant isolation */
    List<Book> listAll();

    List<Book> listByOwner(Long userId);

    Book update(Long id, Book updates);

    void delete(Long id);

    boolean isOwner(Long bookId, Long userId);

    Book getOwnedBook(Long bookId, Long userId);

    boolean existsById(Long id);

    void reorder(Long userId, List<Long> bookIds);
}
