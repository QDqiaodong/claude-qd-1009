package com.karaoke.lounge.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.karaoke.lounge.common.ApiResponse;
import com.karaoke.lounge.entity.Consumption;
import com.karaoke.lounge.service.ConsumptionService;

/** 消费接口（含结账最低消费校验） */
@RestController
@RequestMapping("/api/consumptions")
public class ConsumptionController {

    private final ConsumptionService consumptionService;

    public ConsumptionController(ConsumptionService consumptionService) {
        this.consumptionService = consumptionService;
    }

    @GetMapping
    public ApiResponse<List<Consumption>> list(@RequestParam(required = false) Long bookingId) {
        return ApiResponse.success(consumptionService.listByBooking(bookingId));
    }

    @PostMapping
    public ApiResponse<Consumption> create(@RequestBody Consumption input) {
        return ApiResponse.success("消费已记录", consumptionService.create(input));
    }

    /** 结账开单：校验合计是否达到包厢最低消费 */
    @PostMapping("/settle")
    public ApiResponse<BigDecimal> settle(@RequestParam Long bookingId) {
        BigDecimal total = consumptionService.settle(bookingId);
        return ApiResponse.success("结账成功，合计 " + total + " 元", total);
    }
}
