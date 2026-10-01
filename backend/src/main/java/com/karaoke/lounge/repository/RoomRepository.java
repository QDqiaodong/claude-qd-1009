package com.karaoke.lounge.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.karaoke.lounge.entity.Room;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByStatus(String status);

    boolean existsByRoomCode(String roomCode);
}
