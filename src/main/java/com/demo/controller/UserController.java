package com.demo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demo.entity.User;
import com.demo.service.UserService;
import com.smartlibrary.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户 CRUD 接口。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public Result<User> create(@RequestBody User user) {
        boolean ok = userService.save(user);
        return ok ? Result.ok(user) : Result.fail("创建失败");
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        boolean ok = userService.removeById(id);
        return ok ? Result.ok() : Result.fail("删除失败");
    }

    @PutMapping
    public Result<User> update(@RequestBody User user) {
        if (user.getId() == null) {
            return Result.fail(400, "id 不能为空");
        }
        boolean ok = userService.updateById(user);
        return ok ? Result.ok(user) : Result.fail("更新失败");
    }

    @GetMapping("/{id}")
    public Result<User> getById(@PathVariable Long id) {
        User user = userService.getById(id);
        return user != null ? Result.ok(user) : Result.fail(404, "未找到");
    }

    /**
     * 分页查询（可按 username 模糊）。
     */
    @GetMapping("/page")
    public Result<Page<User>> page(@RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
                                   @RequestParam(value = "pageSize", defaultValue = "10") long pageSize,
                                   @RequestParam(value = "username", required = false) String username) {
        Page<User> page = userService.lambdaQuery()
                .like(username != null && !username.isBlank(), User::getUsername, username)
                .page(Page.of(pageNum, pageSize));
        return Result.ok(page);
    }
}

