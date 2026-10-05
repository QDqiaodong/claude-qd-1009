package com.karaoke.lounge.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karaoke.lounge.common.BizException;
import com.karaoke.lounge.common.Dict;
import com.karaoke.lounge.entity.Booking;
import com.karaoke.lounge.entity.CleaningTask;
import com.karaoke.lounge.entity.CleaningTaskLog;
import com.karaoke.lounge.entity.Room;
import com.karaoke.lounge.repository.BookingRepository;
import com.karaoke.lounge.repository.CleaningTaskLogRepository;
import com.karaoke.lounge.repository.CleaningTaskRepository;
import com.karaoke.lounge.repository.RoomRepository;

/**
 * 包厢清场交接与验收放行闭环。
 *
 * <p>状态机：待领取 → 清场中 → 待验收 → 已放行；验收退回 → 整改中 → 重新提交 → 待验收。
 * 取舍口径：包厢未验收放行前一律不对外显示可用（不可预订、不参与推荐），
 * 以此换取「下一场绝不会坐进未清场包厢」；释放效率靠保洁待办 / 经理待验收 / 整改积压三条队列保障。</p>
 *
 * <p>并发口径：领取 / 提交 / 验收 / 退回 / 异常处置全部走条件 UPDATE（WHERE 当前状态），
 * 数据库保证同一时刻只有一个操作生效；后到操作影响行数为 0 时重读最新状态，
 * 抛出带当前负责人与状态的可理解提示。所有结果与历史均落库，刷新页面后保持一致。</p>
 */
@Service
public class CleaningTaskService {

    private final CleaningTaskRepository taskRepository;
    private final CleaningTaskLogRepository logRepository;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;

    /**
     * 条件 UPDATE（@Modifying）直接落库、不经过持久化上下文；
     * 迁移后必须 {@link EntityManager#clear()} 再重读，否则拿到的是一级缓存里的旧状态。
     */
    @PersistenceContext
    private EntityManager entityManager;

    public CleaningTaskService(CleaningTaskRepository taskRepository,
                               CleaningTaskLogRepository logRepository,
                               RoomRepository roomRepository,
                               BookingRepository bookingRepository) {
        this.taskRepository = taskRepository;
        this.logRepository = logRepository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
    }

    // ---------------- 查询 ----------------

    @Transactional(readOnly = true)
    public List<CleaningTask> list(String status, Long roomId) {
        if (status != null && !status.isBlank()) {
            if (!Dict.isValidCleaningStatus(status)) {
                throw new BizException("清场任务状态不合法：" + status + "，应为 " + Dict.CLEANING_STATUSES);
            }
            return taskRepository.findByStatusOrderByCreatedAtDesc(status);
        }
        if (roomId != null) {
            return taskRepository.findByRoom_IdOrderByCreatedAtDesc(roomId);
        }
        return taskRepository.findAllByOrderByCreatedAtDesc();
    }

    /** 任务详情（含完整交接历史） */
    @Transactional(readOnly = true)
    public CleaningTask get(Long id) {
        CleaningTask task = findTask(id);
        task.setLogs(logRepository.findByTaskIdOrderByCreatedAtAsc(id));
        return task;
    }

    /** 某包厢的全部清场任务及交接历史（包厢详情追溯用） */
    @Transactional(readOnly = true)
    public List<CleaningTask> listByRoomWithLogs(Long roomId) {
        List<CleaningTask> tasks = taskRepository.findByRoom_IdOrderByCreatedAtDesc(roomId);
        if (tasks.isEmpty()) {
            return tasks;
        }
        List<Long> ids = tasks.stream().map(CleaningTask::getId).collect(Collectors.toList());
        Map<Long, List<CleaningTaskLog>> byTask = logRepository.findByTaskIdInOrderByCreatedAtAsc(ids)
                .stream().collect(Collectors.groupingBy(CleaningTaskLog::getTaskId));
        for (CleaningTask t : tasks) {
            t.setLogs(byTask.getOrDefault(t.getId(), List.of()));
        }
        return tasks;
    }

    // ---------------- 散场：生成待清场任务 ----------------

    /**
     * 场次结束：包厢由「使用中」进入「待清场」并生成待领取任务。
     * 以房间状态的条件更新作为互斥闸口，并发散场只会有一个成功。
     */
    @Transactional
    public CleaningTask checkout(Long roomId, String operator) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new BizException("包厢不存在：" + roomId));
        String op = defaultOperator(operator, "前台");
        int flipped = roomRepository.flipStatusIf(roomId, "使用中", "待清场");
        if (flipped == 0) {
            throw new BizException("包厢「" + room.getRoomName() + "」当前状态为「" + room.getStatus()
                    + "」，仅「使用中」的包厢可散场清场（可能刚被他人操作，请刷新）");
        }
        if (!taskRepository.findByRoom_IdAndStatusNot(roomId, Dict.CLEAN_RELEASED).isEmpty()) {
            // 理论上与上面的状态闸口互斥，双保险；抛错回滚，房间状态一并恢复
            throw new BizException("该包厢已存在未闭环的清场任务，请勿重复生成");
        }
        Booking last = bookingRepository.findTopByRoom_IdAndStatusNotOrderByEndAtDesc(roomId, "已取消");
        CleaningTask task = new CleaningTask();
        task.setRoom(room);
        task.setBookingId(last == null ? null : last.getId());
        task.setStatus(Dict.CLEAN_PENDING_CLAIM);
        task.setReturnCount(0);
        LocalDateTime now = LocalDateTime.now();
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        CleaningTask saved = taskRepository.save(task);
        addLog(saved.getId(), Dict.LOG_CREATE, op,
                "包厢「" + room.getRoomName() + "」场次结束，生成待清场任务"
                        + (last == null ? "" : "（关联场次 #" + last.getId() + "）"));
        return reload(saved.getId());
    }

    // ---------------- 保洁：领取 / 提交 ----------------

    /** 领取任务：两名保洁同时领取时只有一个生效 */
    @Transactional
    public CleaningTask claim(Long id, String operator) {
        findTask(id); // 存在性校验
        String op = requireOperator(operator, "请填写保洁姓名再领取");
        int n = taskRepository.claimIfPending(id, op, LocalDateTime.now());
        if (n == 0) {
            CleaningTask cur = reload(id);
            if (Dict.CLEAN_CLEANING.equals(cur.getStatus()) && cur.getCleanerName() != null) {
                throw new BizException("来晚一步：任务刚被「" + cur.getCleanerName() + "」领取，请刷新后领取其它任务");
            }
            throw staleException(cur);
        }
        addLog(id, Dict.LOG_CLAIM, op, "保洁领取任务，开始清场");
        return reload(id);
    }

    /** 提交清场结果：记录卫生结果、遗留物、设备异常；整改任务重新提交亦走此入口 */
    @Transactional
    public CleaningTask submit(Long id, String operator, String hygieneResult,
                               String leftoverItems, String deviceIssue) {
        CleaningTask task = findTask(id);
        String op = requireOperator(operator, "请填写提交人姓名");
        if (!Dict.isValidHygieneResult(hygieneResult)) {
            throw new BizException("卫生结果不合法：" + hygieneResult + "，应为 " + Dict.HYGIENE_RESULTS);
        }
        boolean wasRectifying = Dict.CLEAN_RECTIFYING.equals(task.getStatus());
        int n = taskRepository.submitIfCleaning(id, hygieneResult,
                trimToNull(leftoverItems), trimToNull(deviceIssue), LocalDateTime.now());
        if (n == 0) {
            throw staleException(reload(id));
        }
        addLog(id, Dict.LOG_SUBMIT, op,
                (wasRectifying ? "整改完成重新提交；" : "")
                        + submitSummary(hygieneResult, leftoverItems, deviceIssue));
        return reload(id);
    }

    // ---------------- 设备异常处置 ----------------

    /** 登记设备异常处置结果（保洁 / 工程 / 经理均可登记，登记后任务才具备放行条件之一） */
    @Transactional
    public CleaningTask resolveDevice(Long id, String operator, String resolution) {
        CleaningTask task = findTask(id);
        String op = requireOperator(operator, "请填写处置人姓名");
        if (resolution == null || resolution.isBlank()) {
            throw new BizException("请填写设备异常处置结果");
        }
        int n = taskRepository.resolveDeviceIfOpen(id, resolution.trim(), LocalDateTime.now());
        if (n == 0) {
            CleaningTask cur = reload(id);
            if (Dict.CLEAN_RELEASED.equals(cur.getStatus())) {
                throw new BizException("任务已验收闭环，无需再登记处置");
            }
            if (cur.getDeviceIssue() == null || cur.getDeviceIssue().isBlank()) {
                throw new BizException("该任务没有设备异常，无需登记处置");
            }
            throw new BizException("设备异常已登记处置（结论：" + cur.getDeviceResolution() + "），请勿重复提交");
        }
        addLog(id, Dict.LOG_DEVICE_RESOLVE, op,
                "设备异常「" + task.getDeviceIssue() + "」处置结果：" + resolution.trim());
        return reload(id);
    }

    // ---------------- 经理：验收放行 / 退回整改 ----------------

    /**
     * 验收：approve=true 放行（带未处置设备异常时必须在本次验收中留下明确的停用结论）；
     * approve=false 退回整改（必须填退回原因，前一次问题与处理记录随日志保留）。
     * 多人同时验收 / 重复提交时，条件 UPDATE 保证只有一个生效。
     */
    @Transactional
    public CleaningTask review(Long id, String manager, boolean approve,
                               String returnReason, String deviceResolution) {
        CleaningTask task = findTask(id);
        String op = requireOperator(manager, "请填写值班经理姓名");
        if (!Dict.CLEAN_PENDING_REVIEW.equals(task.getStatus())) {
            throw staleException(task);
        }
        LocalDateTime now = LocalDateTime.now();
        if (approve) {
            boolean issueOpen = hasText(task.getDeviceIssue()) && !hasText(task.getDeviceResolution());
            String resolution = trimToNull(deviceResolution);
            if (issueOpen && resolution == null) {
                throw new BizException("存在未处置的设备异常「" + task.getDeviceIssue()
                        + "」，即使卫生合格也不能放行：请先登记异常处置，或在本次验收中填写明确的停用结论");
            }
            int n = resolution != null
                    ? taskRepository.releaseWithResolutionIfPendingReview(id, resolution, now)
                    : taskRepository.releaseIfPendingReview(id, now);
            if (n == 0) {
                throw staleException(reload(id));
            }
            // 闭环即释放包厢：待清场 → 空闲（条件更新，与散场闸口同一互斥口径）
            int flipped = roomRepository.flipStatusIf(task.getRoom().getId(), "待清场", "空闲");
            if (flipped == 0) {
                throw new BizException("包厢状态已变化，放行未生效，请刷新后重试");
            }
            addLog(id, Dict.LOG_REVIEW_PASS, op,
                    "验收合格，包厢放行恢复接待"
                            + (resolution != null ? "；设备停用结论：" + resolution : ""));
        } else {
            if (returnReason == null || returnReason.isBlank()) {
                throw new BizException("退回整改必须填写退回原因");
            }
            String snapshot = "上次提交[" + submitSummary(task.getHygieneResult(),
                    task.getLeftoverItems(), task.getDeviceIssue()) + "]";
            int n = taskRepository.returnIfPendingReview(id, returnReason.trim(), now);
            if (n == 0) {
                throw staleException(reload(id));
            }
            addLog(id, Dict.LOG_REVIEW_RETURN, op,
                    "退回原因：" + returnReason.trim() + "；" + snapshot);
        }
        return reload(id);
    }

    // ---------------- 内部工具 ----------------

    private CleaningTask findTask(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new BizException("清场任务不存在：" + id));
    }

    /** 清掉持久化上下文后重读任务及交接历史（条件 UPDATE 之后的一级缓存是旧状态） */
    private CleaningTask reload(Long id) {
        entityManager.flush(); // 先把本事务已产生的写入（新任务 / 日志）落库，再清缓存
        entityManager.clear();
        // 当前读：并发冲突场景下必须越过 REPEATABLE READ 快照，看到对方已提交的最新状态
        CleaningTask task = taskRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("清场任务不存在：" + id));
        task.setLogs(logRepository.findByTaskIdOrderByCreatedAtAsc(id));
        return task;
    }

    private void addLog(Long taskId, String action, String operator, String detail) {
        CleaningTaskLog log = new CleaningTaskLog();
        log.setTaskId(taskId);
        log.setAction(action);
        log.setOperator(operator);
        log.setDetail(detail);
        log.setCreatedAt(LocalDateTime.now());
        logRepository.save(log);
    }

    /** 状态已被他人抢先变更时的统一提示：重读最新状态与负责人，引导刷新 */
    private BizException staleException(CleaningTask cur) {
        StringBuilder sb = new StringBuilder("任务状态已变更为「").append(cur.getStatus()).append("」");
        if (cur.getCleanerName() != null && !cur.getCleanerName().isBlank()) {
            sb.append("，负责人：").append(cur.getCleanerName());
        }
        sb.append("，本次操作未生效，请刷新页面");
        return new BizException(sb.toString());
    }

    private String submitSummary(String hygiene, String leftover, String deviceIssue) {
        return "卫生：" + (hygiene == null ? "—" : hygiene)
                + "；遗留物：" + (hasText(leftover) ? leftover : "无")
                + "；设备异常：" + (hasText(deviceIssue) ? deviceIssue : "无");
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String trimToNull(String s) {
        return hasText(s) ? s.trim() : null;
    }

    private static String requireOperator(String operator, String message) {
        if (operator == null || operator.isBlank()) {
            throw new BizException(message);
        }
        return operator.trim();
    }

    private static String defaultOperator(String operator, String fallback) {
        return (operator == null || operator.isBlank()) ? fallback : operator.trim();
    }
}
