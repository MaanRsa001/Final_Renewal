package com.insurance.renewal.broker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.List;

@Getter
@Schema(name = "RenewalDuePage", description = "One page of results.")
public class BrokerPageDTO<T> {
    private final List<T> items;
    private final int page;
    private final int size;
    private final long totalItems;
    private final int totalPages;

    public BrokerPageDTO(List<T> items, int page, int size, long totalItems, int totalPages) {
        this.items = items; this.page = page; this.size = size;
        this.totalItems = totalItems; this.totalPages = totalPages;
    }
}
