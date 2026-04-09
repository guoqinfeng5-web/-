package com.demo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demo.entity.UserActivity;
import com.demo.service.UserActivityService;
import com.smartlibrary.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户活动记录 CRUD 接口。
 */
@RestController
@RequestMapping("/api/user-activities")
@RequiredArgsConstructor
public class UserActivityController {

    private final UserActivityService userActivityService;

    @PostMapping
    public Result<UserActivity> create(@RequestBody UserActivity activity) {
        boolean ok = userActivityService.save(activity);
        return ok ? Result.ok(activity) : Result.fail("创建失败");
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        boolean ok = userActivityService.removeById(id);
        return ok ? Result.ok() : Result.fail("删除失败");
    }

    @PutMapping
    public Result<UserActivity> update(@RequestBody UserActivity activity) {
        if (activity.getId() == null) {
            return Result.fail(400, "id 不能为空");
        }
        boolean ok = userActivityService.updateById(activity);
        return ok ? Result.ok(activity) : Result.fail("更新失败");
    }

    @GetMapping("/{id}")
    public Result<UserActivity> getById(@PathVariable Long id) {
        UserActivity activity = userActivityService.getById(id);
        return activity != null ? Result.ok(activity) : Result.fail(404, "未找到");
    }

    /**
     * 分页查询（可按 userId / bookId 过滤）。
     */
    @GetMapping("/page")
    public Result<Page<UserActivity>> page(@RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
                                           @RequestParam(value = "pageSize", defaultValue = "10") long pageSize,
                                           @RequestParam(value = "userId", required = false) Long userId,
                                           @RequestParam(value = "bookId", required = false) Long bookId) {
        Page<UserActivity> page = userActivityService.lambdaQuery()
                .eq(userId != null, UserActivity::getUserId, userId)
                .eq(bookId != null, UserActivity::getBookId, bookId)
                .page(Page.of(pageNum, pageSize));
        return Result.ok(page);
    }
}

