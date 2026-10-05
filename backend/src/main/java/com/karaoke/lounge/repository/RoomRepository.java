package com.karaoke.lounge.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.karaoke.lounge.entity.Room;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByStatus(String status);

    boolean existsByRoomCode(String roomCode);

    /**
     * 条件流转房间状态：仅当前状态等于 expected 时才置为 target。
     * 作为散场清场 / 验收放行的互斥闸口，并发下只有一个操作能翻转状态。
     */
    @Modifying
    @Query("UPDATE Room r SET r.status = :target WHERE r.id = :id AND r.status = :expected")
    int flipStatusIf(@Param("id") Long id, @Param("expected") String expected,
                     @Param("target") String target);
}
