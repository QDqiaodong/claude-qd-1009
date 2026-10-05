package com.karaoke.lounge.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 清场交接历史：任务的每一次操作（生成/领取/提交/验收/退回/异常处置）都留痕。
 * 退回整改时，前一次提交的问题快照与处理记录随日志完整保留，供包厢详情追溯。
 */
@Entity
@Table(name = "cleaning_task_log")
@Getter
@Setter
public class CleaningTaskLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属任务（仅存 id，不关联实体） */
    @Column(name = "task_id", nullable = false)
    private Long taskId;

    /** 生成任务 / 领取 / 提交清场 / 验收放行 / 退回整改 / 异常处置 */
    @Column(name = "action", nullable = false, length = 16)
    private String action;

    /** 操作人（保洁 / 值班经理 / 前台） */
    @Column(name = "operator", nullable = false, length = 32)
    private String operator;

    /** 详情：卫生结果、遗留物、设备异常、退回原因、处置结论等 */
    @Column(name = "detail", length = 500)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
