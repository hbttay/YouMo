package com.youmo.core.service;

import com.youmo.common.entity.Book;
import java.util.List;
import java.util.Optional;

public interface BookService {

    Book create(Book book);

    Optional<Book> getById(Long id);

    List<Book> listByOwner(Long ownerId);

    Book update(Long id, Book updates);

    void delete(Long id);
}
