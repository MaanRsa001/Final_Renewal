package com.insurance.renewal.dto;

/** Product as read from policy_master (product_id + product_name). */
public record ProductLookupDTO(Integer productId, String productName) {}
