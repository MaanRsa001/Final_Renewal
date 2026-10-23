package com.insurance.renewal.dto;

/** Customer as read from policy_master (customer_id, customer_name, mobile_no, email). */
public record CustomerLookupDTO(String customerId, String fullName, String mobileNo, String email) {}
