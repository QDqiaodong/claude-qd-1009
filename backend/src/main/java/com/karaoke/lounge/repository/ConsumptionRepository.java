package com.karaoke.lounge.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.karaoke.lounge.entity.Consumption;

public interface ConsumptionRepository extends JpaRepository<Consumption, Long> {

    List<Consumption> findByBookingId(Long bookingId);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM Consumption c WHERE c.bookingId = ?1")
    BigDecimal sumAmountByBookingId(Long bookingId);
}
