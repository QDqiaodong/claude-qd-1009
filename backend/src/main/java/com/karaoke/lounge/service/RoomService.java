package com.karaoke.lounge.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karaoke.lounge.common.BizException;
import com.karaoke.lounge.common.Dict;
import com.karaoke.lounge.entity.CleaningTask;
import com.karaoke.lounge.entity.Room;
import com.karaoke.lounge.repository.CleaningTaskRepository;
import com.karaoke.lounge.repository.RoomRepository;

/**
 * 包厢业务。
 *
 * <p>规则：① 包厢编号唯一；② 容量须 &gt; 0、最低消费 &ge; 0；③ 状态必须是合法枚举。
 * 另提供「列出包厢及其预订」接口，演示 {@code SUBSELECT} 一次性拉取所有包厢的预订，避免 N+1。</p>
 *
 * <p>清场联动：包厢可用性 = 业务状态（空闲/预订/使用中/待清场）× 清场闭环。
 * 列表会为每个包厢装配当前未闭环清场任务状态，{@code available} 派生属性据此计算；
 * 「使用中 → 空闲」不允许直接跳，必须散场生成清场任务；「待清场」只能经验收放行回到空闲。</p>
 */
@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final CleaningTaskRepository cleaningTaskRepository;

    public RoomService(RoomRepository roomRepository, CleaningTaskRepository cleaningTaskRepository) {
        this.roomRepository = roomRepository;
        this.cleaningTaskRepository = cleaningTaskRepository;
    }

    /**
     * 列出所有包厢（并触发 SUBSELECT 预载预订，避免后续序列化时懒加载失败）。
     */
    @Transactional(readOnly = true)
    public List<Room> list() {
        List<Room> rooms = roomRepository.findAll();
        initializeBookings(rooms);
        fillCleaningStatus(rooms);
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
        fillCleaningStatus(rooms);
        return rooms;
    }

    private void initializeBookings(List<Room> rooms) {
        for (Room room : rooms) {
            room.getBookings().size(); // 访问集合即触发 SUBSELECT
        }
    }

    /** 一次性拉取全部未闭环清场任务，按包厢装配到 transient 字段（避免逐包厢查询） */
    private void fillCleaningStatus(List<Room> rooms) {
        Map<Long, CleaningTask> openByRoom = cleaningTaskRepository.findByStatusNot(Dict.CLEAN_RELEASED)
                .stream().collect(Collectors.toMap(t -> t.getRoom().getId(), t -> t, (a, b) -> a));
        for (Room room : rooms) {
            CleaningTask open = openByRoom.get(room.getId());
            if (open != null) {
                room.setCleaningStatus(open.getStatus());
                room.setCleaningTaskId(open.getId());
            }
        }
    }

    @Transactional(readOnly = true)
    public Room get(Long id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new BizException("包厢不存在：" + id));
        room.getBookings().size();
        fillCleaningStatus(List.of(room));
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
        if ("待清场".equals(status)) {
            throw new BizException("「待清场」只能由散场清场流程进入，不能手工设置");
        }
        Room room = new Room();
        room.setRoomCode(input.getRoomCode().trim());
        room.setRoomName(input.getRoomName().trim());
        room.setCapacity(input.getCapacity());
        room.setMinSpend(input.getMinSpend());
        room.setStatus(status);
        return roomRepository.save(room);
    }

    /**
     * 手工房态调整。清场闭环相关流转被拦截：
     * 「使用中 → 空闲」必须走散场清场；「待清场」必须经验收放行才能回空闲。
     */
    @Transactional
    public Room updateStatus(Long id, String target) {
        Room room = get(id);
        if (!Dict.isValidRoomStatus(target)) {
            throw new BizException("包厢状态不合法：" + target);
        }
        if ("待清场".equals(target)) {
            throw new BizException("「待清场」只能由散场清场流程进入，不能手工设置");
        }
        if ("待清场".equals(room.getStatus())) {
            throw new BizException("包厢清场未验收放行，暂不能调整房态；请先在清场管理完成验收");
        }
        if ("使用中".equals(room.getStatus()) && "空闲".equals(target)) {
            throw new BizException("场次结束不能直接置空闲：请使用「散场清场」生成清场任务，验收放行后自动恢复空闲");
        }
        room.setStatus(target);
        return roomRepository.save(room);
    }
}
