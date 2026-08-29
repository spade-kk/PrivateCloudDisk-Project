package org.project.plugin.model;

/** 仅服务层使用的执行记录权限范围投影。 */
public record PluginExecutionAccessScope(String executionId, String ownerUserId, String spaceId) {
}
