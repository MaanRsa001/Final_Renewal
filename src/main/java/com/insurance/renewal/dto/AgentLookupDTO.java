package com.insurance.renewal.dto;

/**
 * Agent as read from policy_master. agentName is policy_master.agent_name (from login_user_info.USER_NAME),
 * falling back to the agent id as text when no name is stored.
 */
public record AgentLookupDTO(Integer agentId, String agentName, Integer branchId) {}
