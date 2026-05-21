package com.youmo.api.dto.response;

import com.youmo.common.entity.Book;
import lombok.Data;

@Data
public class BookResponse {
    private Long id;
    private String title;
    private String theme;
    private String oneSentence;
    private String creationMode;
    private String lengthType;
    private String status;
    private Long ownerId;

    public static BookResponse from(Book book) {
        BookResponse r = new BookResponse();
        r.setId(book.getId());
        r.setTitle(book.getTitle());
        r.setTheme(book.getTheme());
        r.setOneSentence(book.getOneSentence());
        r.setCreationMode(book.getCreationMode().name());
        r.setLengthType(book.getLengthType().name());
        r.setStatus(book.getStatus().name());
        r.setOwnerId(book.getOwner().getId());
        return r;
    }
}
