package com.pageturn.book;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pageturn.book.BookDtos.BookRequest;
import com.pageturn.book.BookDtos.BookView;
import com.pageturn.common.error.ApiException;
import com.pageturn.common.persistence.LikePatterns;
import com.pageturn.common.web.PageResponse;
import com.pageturn.common.web.TextRules;
import com.pageturn.loan.LoanRepository;

@Service
public class BookService {

    private final BookRepository books;
    private final LoanRepository loans;

    public BookService(BookRepository books, LoanRepository loans) {
        this.books = books;
        this.loans = loans;
    }

    @Transactional(readOnly = true)
    public PageResponse<BookView> search(String query, boolean availableOnly, int page, int size) {
        Page<Book> result = books.search(LikePatterns.contains(query), availableOnly,
                PageResponse.pageable(page, size, Sort.by("title", "id")));
        Map<Long, Instant> expectedBack = expectedBack(result.getContent());
        return PageResponse.of(result, book -> BookView.of(book, expectedBack.get(book.getId())));
    }

    @Transactional(readOnly = true)
    public BookView get(Long id) {
        Book book = books.findById(id).orElseThrow(BookService::notFound);
        return BookView.of(book, expectedBack(List.of(book)).get(id));
    }

    @Transactional
    public BookView create(BookRequest request) {
        String isbn = Isbn.normalize(request.isbn());
        if (isbn != null && books.existsByIsbn(isbn)) {
            throw ApiException.conflict("A book with this ISBN already exists.");
        }
        Book book = new Book(isbn, TextRules.clean(request.title()), TextRules.clean(request.author()),
                TextRules.clean(request.genre()), TextRules.clean(request.description()), request.publishedYear(),
                request.totalCopies());
        return BookView.of(books.saveAndFlush(book), null);
    }

    @Transactional
    public BookView update(Long id, BookRequest request) {
        Book book = books.findByIdForUpdate(id).orElseThrow(BookService::notFound);
        String isbn = Isbn.normalize(request.isbn());
        if (isbn != null && books.existsByIsbnAndIdNot(isbn, id)) {
            throw ApiException.conflict("Another book already has this ISBN.");
        }
        book.setDetails(isbn, TextRules.clean(request.title()), TextRules.clean(request.author()),
                TextRules.clean(request.genre()), TextRules.clean(request.description()), request.publishedYear());
        book.changeTotalCopies(request.totalCopies());
        Book saved = books.saveAndFlush(book);
        return BookView.of(saved, expectedBack(List.of(saved)).get(id));
    }

    /** Books with any borrowing history are kept, so loan records stay complete. */
    @Transactional
    public void delete(Long id) {
        Book book = books.findByIdForUpdate(id).orElseThrow(BookService::notFound);
        if (loans.existsByBookId(id)) {
            throw ApiException.conflict("This book has borrowing history and can't be deleted. "
                    + "Reduce its copies instead if some were lost.");
        }
        books.delete(book);
    }

    /** Earliest due date of borrowed copies, only for books with no copy on the shelf (one query per page). */
    private Map<Long, Instant> expectedBack(List<Book> page) {
        List<Long> soldOut = page.stream().filter(b -> b.getAvailableCopies() == 0).map(Book::getId).toList();
        Map<Long, Instant> result = new HashMap<>();
        if (!soldOut.isEmpty()) {
            for (Object[] row : loans.earliestDueByBook(soldOut)) {
                result.put((Long) row[0], (Instant) row[1]);
            }
        }
        return result;
    }

    static ApiException notFound() {
        return ApiException.notFound("Book not found.");
    }
}
