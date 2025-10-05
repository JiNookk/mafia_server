package com.jingwook.mafia_server.dtos;

import lombok.Getter;

import java.util.List;

@Getter
public class OffsetPaginationDto <T> {
    private List<T> list;
    private OffsetPaginationMetadata meta;

    public OffsetPaginationDto(List<T> list, OffsetPaginationMetadata meta) {
        this.list = list;
        this.meta = meta;
    }
}





