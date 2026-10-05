package com.karaoke.lounge.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.karaoke.lounge.entity.CleaningTask;

public interface CleaningTaskRepository extends JpaRepository<CleaningTask, Long> {

    List<CleaningTask> findAllByOrderByCreatedAtDesc();

    List<CleaningTask> findByStatusOrderByCreatedAtDesc(String status);

    List<CleaningTask> findByRoom_IdOrderByCreatedAtDesc(Long roomId);

    List<CleaningTask> findByRoom_IdAndStatusNot(Long roomId, String status);

    List<CleaningTask> findByStatusNot(String status);

    /**
     * 当前读（SELECT ... FOR UPDATE）：并发冲突后的重读必须看到对方事务已提交的最新状态，
     * 否则在 REPEATABLE READ 下快照读只能拿到本事务开始时的旧值，提示信息会失真。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM CleaningTask t WHERE t.id = :id")
    Optional<CleaningTask> findByIdForUpdate(@Param("id") Long id);

    /**
     * 领取：仅「待领取」可被领取。两名保洁同时领取时，条件 UPDATE 只放行一个，
     * 后到者影响行数为 0，由 Service 重读状态给出「已被 XX 领取」提示。
     */
    @Modifying
    @Query("UPDATE CleaningTask t SET t.status = '清场中', t.cleanerName = :operator,"
            + " t.claimedAt = :now, t.updatedAt = :now"
            + " WHERE t.id = :id AND t.status = '待领取'")
    int claimIfPending(@Param("id") Long id, @Param("operator") String operator,
                       @Param("now") LocalDateTime now);

    /** 提交清场：仅「清场中 / 整改中」可提交；提交即清空旧的异常处置结论（新异常须重新处置）。 */
    @Modifying
    @Query("UPDATE CleaningTask t SET t.status = '待验收', t.hygieneResult = :hygiene,"
            + " t.leftoverItems = :leftover, t.deviceIssue = :deviceIssue, t.deviceResolution = NULL,"
            + " t.submittedAt = :now, t.updatedAt = :now"
            + " WHERE t.id = :id AND t.status IN ('清场中', '整改中')")
    int submitIfCleaning(@Param("id") Long id, @Param("hygiene") String hygiene,
                         @Param("leftover") String leftover, @Param("deviceIssue") String deviceIssue,
                         @Param("now") LocalDateTime now);

    /** 登记设备异常处置：任务未闭环、确有异常且尚无处置结论时才生效（防重复登记）。 */
    @Modifying
    @Query("UPDATE CleaningTask t SET t.deviceResolution = :resolution, t.updatedAt = :now"
            + " WHERE t.id = :id AND t.status <> '已放行'"
            + " AND t.deviceIssue IS NOT NULL AND t.deviceIssue <> ''"
            + " AND (t.deviceResolution IS NULL OR t.deviceResolution = '')")
    int resolveDeviceIfOpen(@Param("id") Long id, @Param("resolution") String resolution,
                            @Param("now") LocalDateTime now);

    /** 验收放行（无新结论）：仅「待验收」可放行，多人同时验收只有一个生效。 */
    @Modifying
    @Query("UPDATE CleaningTask t SET t.status = '已放行', t.finishedAt = :now, t.updatedAt = :now"
            + " WHERE t.id = :id AND t.status = '待验收'")
    int releaseIfPendingReview(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** 验收放行（同时落下经理停用结论）：用于带设备异常任务的放行。 */
    @Modifying
    @Query("UPDATE CleaningTask t SET t.status = '已放行', t.deviceResolution = :resolution,"
            + " t.finishedAt = :now, t.updatedAt = :now"
            + " WHERE t.id = :id AND t.status = '待验收'")
    int releaseWithResolutionIfPendingReview(@Param("id") Long id, @Param("resolution") String resolution,
                                             @Param("now") LocalDateTime now);

    /** 退回整改：仅「待验收」可退回，退回原因与次数落库，前次问题快照写入交接日志。 */
    @Modifying
    @Query("UPDATE CleaningTask t SET t.status = '整改中', t.lastReturnReason = :reason,"
            + " t.returnCount = t.returnCount + 1, t.updatedAt = :now"
            + " WHERE t.id = :id AND t.status = '待验收'")
    int returnIfPendingReview(@Param("id") Long id, @Param("reason") String reason,
                              @Param("now") LocalDateTime now);
}
