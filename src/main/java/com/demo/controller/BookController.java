package com.demo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demo.entity.Book;
import com.demo.service.BookService;
import com.smartlibrary.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 书籍 CRUD 接口。
 */
@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    /**
     * 新增书籍。
     */
    @PostMapping
    public Result<Book> create(@RequestBody Book book) {
        boolean ok = bookService.save(book);
        return ok ? Result.ok(book) : Result.fail("创建失败");
    }

    /**
     * 根据 ID 删除书籍。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        boolean ok = bookService.removeById(id);
        return ok ? Result.ok() : Result.fail("删除失败");
    }

    /**
     * 根据 ID 更新书籍（要求 body 中包含 id）。
     */
    @PutMapping
    public Result<Book> update(@RequestBody Book book) {
        if (book.getId() == null) {
            return Result.fail(400, "id 不能为空");
        }
        boolean ok = bookService.updateById(book);
        return ok ? Result.ok(book) : Result.fail("更新失败");
    }

    /**
     * 根据 ID 查询书籍详情。
     */
    @GetMapping("/{id}")
    public Result<Book> getById(@PathVariable Long id) {
        Book book = bookService.getById(id);
        return book != null ? Result.ok(book) : Result.fail(404, "未找到");
    }

    /**
     * 分页查询（可按标题模糊）。
     *
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页大小
     * @param title    标题关键词（可选）
     */
    @GetMapping("/page")
    public Result<Page<Book>> page(@RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
                                   @RequestParam(value = "pageSize", defaultValue = "10") long pageSize,
                                   @RequestParam(value = "keyword", required = false) String keyword) {
        Page<Book> page = bookService.lambdaQuery()
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(Book::getTitle, keyword)
                        .or()
                        .like(Book::getAuthor, keyword)
                        .or()
                        .like(Book::getTags, keyword))
                .page(Page.of(pageNum, pageSize));
        return Result.ok(page);
    }

    /**
     * 热门推荐分页（按借阅次数倒序）。
     */
    @GetMapping("/hot")
    public Result<Page<Book>> hot(@RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
                                  @RequestParam(value = "pageSize", defaultValue = "10") long pageSize,
                                  @RequestParam(value = "keyword", required = false) String keyword) {
        Page<Book> page = bookService.lambdaQuery()
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(Book::getTitle, keyword)
                        .or()
                        .like(Book::getAuthor, keyword)
                        .or()
                        .like(Book::getTags, keyword))
                .orderByDesc(Book::getBorrowCount)
                .orderByDesc(Book::getScore)
                .page(Page.of(pageNum, pageSize));
        return Result.ok(page);
    }

    /**
     * 高分好评分页（按评分倒序）。
     */
    @GetMapping("/high-score")
    public Result<Page<Book>> highScore(@RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
                                        @RequestParam(value = "pageSize", defaultValue = "10") long pageSize,
                                        @RequestParam(value = "keyword", required = false) String keyword) {
        Page<Book> page = bookService.lambdaQuery()
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(Book::getTitle, keyword)
                        .or()
                        .like(Book::getAuthor, keyword)
                        .or()
                        .like(Book::getTags, keyword))
                .orderByDesc(Book::getScore)
                .orderByDesc(Book::getBorrowCount)
                .page(Page.of(pageNum, pageSize));
        return Result.ok(page);
    }
}

