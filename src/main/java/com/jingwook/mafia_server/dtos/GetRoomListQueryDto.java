package com.jingwook.mafia_server.dtos;

import com.jingwook.mafia_server.enums.Order;
import com.jingwook.mafia_server.enums.Sort;
import lombok.Data;

@Data
public class GetRoomListQueryDto {
    private Integer page = 0;
    private Integer limit = 10;
    private Order order = Order.DESC;
    private Sort sort = Sort.Id;
}





