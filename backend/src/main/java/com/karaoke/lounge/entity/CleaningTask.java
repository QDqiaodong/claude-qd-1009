package com.karaoke.lounge.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 包厢清场交接任务。
 *
 * <p>每场使用结束后由「散场清场」生成，闭环前包厢一律不可接待：
 * 待领取 → 清场中 → 待验收 → 已放行；验收退回 → 整改中 → 重新提交 → 待验收。</p>
 *
 * <p>并发口径：所有状态迁移均走 Repository 的条件 UPDATE（WHERE 当前状态），
 * 同一时刻只会有一个操作生效，后到操作依据影响行数=0 重读状态并给出可理解提示。</p>
 */
@Entity
@Table(name = "cleaning_task")
@Getter
@Setter
public class CleaningTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属包厢（序列化时忽略其预订集合，避免拖出整棵对象树） */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "room_id", nullable = false)
    @JsonIgnoreProperties({"bookings", "cleaningStatus", "cleaningTaskId", "available"})
    private Room room;

    /** 关联的场次（仅存 id；无预订开台的散场可为空） */
    @Column(name = "booking_id")
    private Long bookingId;

    /** 待领取 / 清场中 / 待验收 / 整改中 / 已放行 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 保洁负责人（领取人） */
    @Column(name = "cleaner_name", length = 32)
    private String cleanerName;

    /** 领取时间 */
    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    /** 最近一次提交的卫生结果：合格 / 不合格 */
    @Column(name = "hygiene_result", length = 16)
    private String hygieneResult;

    /** 遗留物记录 */
    @Column(name = "leftover_items", length = 255)
    private String leftoverItems;

    /** 设备异常描述（麦克风、点歌屏等；空 = 无异常） */
    @Column(name = "device_issue", length = 255)
    private String deviceIssue;

    /** 设备异常处置结果 / 经理停用结论（未处置时为空，此时禁止放行） */
    @Column(name = "device_resolution", length = 255)
    private String deviceResolution;

    /** 最近一次验收退回原因 */
    @Column(name = "last_return_reason", length = 255)
    private String lastReturnReason;

    /** 被退回整改次数 */
    @Column(name = "return_count", nullable = false)
    private Integer returnCount = 0;

    /** 最近一次提交时间 */
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /** 验收放行时间 */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 交接历史（transient：由 Service 按需装配，JPA 不持久化，Jackson 仍序列化） */
    private transient List<CleaningTaskLog> logs = new ArrayList<>();
}
