package com.pageturn.book;

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
        return PageResponse.of(books.search(LikePatterns.contains(query), availableOnly,
                PageResponse.pageable(page, size, Sort.by("title", "id"))), BookView::of);
    }

    @Transactional(readOnly = true)
    public BookView get(Long id) {
        return BookView.of(books.findById(id).orElseThrow(BookService::notFound));
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
        return BookView.of(books.saveAndFlush(book));
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
        return BookView.of(books.saveAndFlush(book));
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

    static ApiException notFound() {
        return ApiException.notFound("Book not found.");
    }
}
