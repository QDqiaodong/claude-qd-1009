package com.karaoke.lounge.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.karaoke.lounge.common.ApiResponse;
import com.karaoke.lounge.entity.CleaningTask;
import com.karaoke.lounge.service.CleaningTaskService;

/**
 * 清场交接接口：保洁待办 / 经理待验收 / 整改积压共用一套任务查询，
 * 操作（领取 / 提交 / 异常处置 / 验收）均以后端状态机与条件更新为准。
 */
@RestController
@RequestMapping("/api/cleaning-tasks")
public class CleaningTaskController {

    private final CleaningTaskService cleaningTaskService;

    public CleaningTaskController(CleaningTaskService cleaningTaskService) {
        this.cleaningTaskService = cleaningTaskService;
    }

    @GetMapping
    public ApiResponse<List<CleaningTask>> list(@RequestParam(required = false) String status,
                                                @RequestParam(required = false) Long roomId) {
        return ApiResponse.success(cleaningTaskService.list(status, roomId));
    }

    /** 任务详情（含完整交接历史） */
    @GetMapping("/{id}")
    public ApiResponse<CleaningTask> get(@PathVariable Long id) {
        return ApiResponse.success(cleaningTaskService.get(id));
    }

    /** 保洁领取任务 */
    @PostMapping("/{id}/claim")
    public ApiResponse<CleaningTask> claim(@PathVariable Long id, @RequestParam String operator) {
        return ApiResponse.success("任务已领取", cleaningTaskService.claim(id, operator));
    }

    /** 保洁提交清场结果（整改任务重新提交同此入口） */
    @PostMapping("/{id}/submit")
    public ApiResponse<CleaningTask> submit(@PathVariable Long id, @RequestBody SubmitRequest req) {
        return ApiResponse.success("清场结果已提交，待经理验收",
                cleaningTaskService.submit(id, req.operator, req.hygieneResult,
                        req.leftoverItems, req.deviceIssue));
    }

    /** 登记设备异常处置结果 */
    @PostMapping("/{id}/device-resolve")
    public ApiResponse<CleaningTask> resolveDevice(@PathVariable Long id, @RequestBody DeviceResolveRequest req) {
        return ApiResponse.success("设备异常处置已登记",
                cleaningTaskService.resolveDevice(id, req.operator, req.resolution));
    }

    /** 经理验收：approve=true 放行（可附停用结论），approve=false 退回整改（必填原因） */
    @PostMapping("/{id}/review")
    public ApiResponse<CleaningTask> review(@PathVariable Long id, @RequestBody ReviewRequest req) {
        CleaningTask task = cleaningTaskService.review(id, req.manager, req.approve,
                req.returnReason, req.deviceResolution);
        return ApiResponse.success(req.approve ? "验收通过，包厢已放行恢复接待" : "已退回整改", task);
    }

    /** 提交清场请求体 */
    public static class SubmitRequest {
        public String operator;
        public String hygieneResult;
        public String leftoverItems;
        public String deviceIssue;
    }

    /** 设备异常处置请求体 */
    public static class DeviceResolveRequest {
        public String operator;
        public String resolution;
    }

    /** 验收请求体 */
    public static class ReviewRequest {
        public String manager;
        public boolean approve;
        public String returnReason;
        public String deviceResolution;
    }
}
