package org.project.plugin.model;

/** 待投影到 Capability Hub 的插件 Outbox 事件。 */
public record PluginOutboxRow(String eventId, String payloadJson, int attempt) {
}
