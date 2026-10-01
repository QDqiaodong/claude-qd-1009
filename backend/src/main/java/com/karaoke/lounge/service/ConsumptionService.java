package com.karaoke.lounge.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karaoke.lounge.common.BizException;
import com.karaoke.lounge.common.Dict;
import com.karaoke.lounge.entity.Booking;
import com.karaoke.lounge.entity.Consumption;
import com.karaoke.lounge.repository.BookingRepository;
import com.karaoke.lounge.repository.ConsumptionRepository;
import com.karaoke.lounge.repository.RoomRepository;

/**
 * 消费业务。
 *
 * <p>规则：① 消费必须挂在有效预订上；② 金额须 &gt; 0；
 * ③ 结账开单时，该预订合计消费须达到包厢最低消费，否则拒绝开单。</p>
 */
@Service
public class ConsumptionService {

    private final ConsumptionRepository consumptionRepository;
    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;

    public ConsumptionService(ConsumptionRepository consumptionRepository,
                              BookingRepository bookingRepository,
                              RoomRepository roomRepository) {
        this.consumptionRepository = consumptionRepository;
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
    }

    @Transactional(readOnly = true)
    public List<Consumption> listByBooking(Long bookingId) {
        if (bookingId == null) {
            return consumptionRepository.findAll();
        }
        return consumptionRepository.findByBookingId(bookingId);
    }

    @Transactional
    public Consumption create(Consumption input) {
        if (input.getBookingId() == null) {
            throw new BizException("消费必须挂在预订上");
        }
        Booking booking = bookingRepository.findById(input.getBookingId())
                .orElseThrow(() -> new BizException("预订不存在：" + input.getBookingId()));
        if ("已取消".equals(booking.getStatus())) {
            throw new BizException("已取消的预订不能挂消费");
        }
        if (input.getItemName() == null || input.getItemName().isBlank()) {
            throw new BizException("消费项目名称不能为空");
        }
        if (input.getAmount() == null || input.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("消费金额必须大于 0");
        }
        Consumption c = new Consumption();
        c.setBookingId(input.getBookingId());
        c.setItemName(input.getItemName().trim());
        c.setAmount(input.getAmount());
        c.setAt(LocalDateTime.now());
        return consumptionRepository.save(c);
    }

    /**
     * 结账开单：校验该预订合计消费是否达到包厢最低消费。
     *
     * @return 该预订消费合计
     */
    @Transactional
    public BigDecimal settle(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BizException("预订不存在：" + bookingId));
        if ("已取消".equals(booking.getStatus())) {
            throw new BizException("已取消的预订无法结账");
        }
        BigDecimal total = consumptionRepository.sumAmountByBookingId(bookingId);
        BigDecimal minSpend = booking.getRoom().getMinSpend();
        if (total.compareTo(minSpend) < 0) {
            throw new BizException("包厢 " + booking.getRoom().getRoomName()
                    + " 最低消费 " + minSpend + " 元，当前消费合计 " + total + " 元，未达标无法开单结账");
        }
        booking.setStatus("已完成");
        bookingRepository.save(booking);
        booking.getRoom().setStatus("使用中");
        roomRepository.save(booking.getRoom());
        return total;
    }
}
