package com.karaoke.lounge.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karaoke.lounge.common.BizException;
import com.karaoke.lounge.common.Dict;
import com.karaoke.lounge.entity.Room;
import com.karaoke.lounge.repository.RoomRepository;

/**
 * 包厢业务。
 *
 * <p>规则：① 包厢编号唯一；② 容量须 &gt; 0、最低消费 &ge; 0；③ 状态必须是合法枚举。
 * 另提供「列出包厢及其预订」接口，演示 {@code SUBSELECT} 一次性拉取所有包厢的预订，避免 N+1。</p>
 */
@Service
public class RoomService {

    private final RoomRepository roomRepository;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    /**
     * 列出所有包厢（并触发 SUBSELECT 预载预订，避免后续序列化时懒加载失败）。
     */
    @Transactional(readOnly = true)
    public List<Room> list() {
        List<Room> rooms = roomRepository.findAll();
        initializeBookings(rooms);
        return rooms;
    }

    /**
     * 列出包厢及其预订：一次性子查询拉取所有包厢的预订集合，避免逐包厢查询（N+1）。
     */
    @Transactional(readOnly = true)
    public List<Room> listWithBookings() {
        List<Room> rooms = roomRepository.findAll();
        // 触发 SUBSELECT：对 findAll 加载的全部包厢，仅用一条子查询取回各自预订
        initializeBookings(rooms);
        return rooms;
    }

    private void initializeBookings(List<Room> rooms) {
        for (Room room : rooms) {
            room.getBookings().size(); // 访问集合即触发 SUBSELECT
        }
    }

    @Transactional(readOnly = true)
    public Room get(Long id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new BizException("包厢不存在：" + id));
        room.getBookings().size();
        return room;
    }

    @Transactional
    public Room create(Room input) {
        if (input.getRoomCode() == null || input.getRoomCode().isBlank()) {
            throw new BizException("包厢编号不能为空");
        }
        if (roomRepository.existsByRoomCode(input.getRoomCode().trim())) {
            throw new BizException("包厢编号已存在：" + input.getRoomCode());
        }
        if (input.getRoomName() == null || input.getRoomName().isBlank()) {
            throw new BizException("包厢名称不能为空");
        }
        if (input.getCapacity() == null || input.getCapacity() <= 0) {
            throw new BizException("包厢容量必须大于 0");
        }
        if (input.getMinSpend() == null || input.getMinSpend().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("最低消费不能为负数");
        }
        String status = (input.getStatus() == null || input.getStatus().isBlank())
                ? "空闲" : input.getStatus();
        if (!Dict.isValidRoomStatus(status)) {
            throw new BizException("包厢状态不合法：" + status + "，应为 " + Dict.ROOM_STATUSES);
        }
        Room room = new Room();
        room.setRoomCode(input.getRoomCode().trim());
        room.setRoomName(input.getRoomName().trim());
        room.setCapacity(input.getCapacity());
        room.setMinSpend(input.getMinSpend());
        room.setStatus(status);
        return roomRepository.save(room);
    }

    @Transactional
    public Room updateStatus(Long id, String target) {
        Room room = get(id);
        if (!Dict.isValidRoomStatus(target)) {
            throw new BizException("包厢状态不合法：" + target);
        }
        room.setStatus(target);
        return roomRepository.save(room);
    }
}
