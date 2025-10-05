package com.jingwook.mafia_server.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OffsetPaginationMetadata {
    private Integer page;
    private Integer totalPage;
    private Integer limit;
}
