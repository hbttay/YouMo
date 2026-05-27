package com.youmo.core.repository;

import com.youmo.common.entity.Book;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    List<Book> findAllByOwnerIdOrderBySequenceAsc(Long ownerId);

    java.util.Optional<Book> findByIdAndOwnerId(Long id, Long ownerId);
}
