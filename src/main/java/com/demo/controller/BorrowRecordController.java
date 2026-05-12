package com.demo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demo.entity.BorrowRecord;
import com.demo.service.BorrowRecordService;
import com.smartlibrary.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 借阅记录 CRUD 接口（{@link com.demo.entity.BorrowRecord} → {@code borrow_records}）。
 */
@RestController
@RequestMapping("/api/borrow-records")
@RequiredArgsConstructor
public class BorrowRecordController {

    private final BorrowRecordService borrowRecordService;

    @PostMapping
    public Result<BorrowRecord> create(@RequestBody BorrowRecord record) {
        boolean ok = borrowRecordService.save(record);
        return ok ? Result.ok(record) : Result.fail("创建失败");
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        boolean ok = borrowRecordService.removeById(id);
        return ok ? Result.ok() : Result.fail("删除失败");
    }

    @PutMapping
    public Result<BorrowRecord> update(@RequestBody BorrowRecord record) {
        if (record.getId() == null) {
            return Result.fail(400, "id 不能为空");
        }
        boolean ok = borrowRecordService.updateById(record);
        return ok ? Result.ok(record) : Result.fail("更新失败");
    }

    @GetMapping("/{id}")
    public Result<BorrowRecord> getById(@PathVariable Long id) {
        BorrowRecord record = borrowRecordService.getById(id);
        return record != null ? Result.ok(record) : Result.fail(404, "未找到");
    }

    /**
     * 分页查询（可按 userId / bookId 过滤）。
     */
    @GetMapping("/page")
    public Result<Page<BorrowRecord>> page(@RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
                                            @RequestParam(value = "pageSize", defaultValue = "10") long pageSize,
                                            @RequestParam(value = "userId", required = false) Long userId,
                                            @RequestParam(value = "bookId", required = false) Long bookId) {
        Page<BorrowRecord> page = borrowRecordService.lambdaQuery()
                .eq(userId != null, BorrowRecord::getUserId, userId)
                .eq(bookId != null, BorrowRecord::getBookId, bookId)
                .orderByDesc(BorrowRecord::getBorrowTime)
                .page(Page.of(pageNum, pageSize));
        return Result.ok(page);
    }
}
