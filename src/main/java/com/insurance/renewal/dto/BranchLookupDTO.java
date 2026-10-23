package com.insurance.renewal.dto;

/** Branch as read from policy_master (branch_id + branch_name). */
public record BranchLookupDTO(Integer branchId, String branchName) {}
