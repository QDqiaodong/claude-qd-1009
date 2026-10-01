package com.karaoke.lounge.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.karaoke.lounge.entity.Booking;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByRoom_Id(Long roomId);

    List<Booking> findByMemberId(Long memberId);

    List<Booking> findByStatus(String status);

    List<Booking> findByRoom_IdAndStatusNot(Long roomId, String status);

    List<Booking> findByRoom_IdAndStatus(Long roomId, String status);
}
