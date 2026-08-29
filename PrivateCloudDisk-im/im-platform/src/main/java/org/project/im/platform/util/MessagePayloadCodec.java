package org.project.im.platform.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.ByteString;
import org.project.im.common.protocol.v2.IMProtocolV2;
import org.project.im.platform.entity.ImMessage;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * IM 富媒体负载与持久化 JSON 之间的无状态编解码器。
 *
 * <p>AUDIT FIX [1.7/5.4/14.4/14.25]：原实现仅保留 TEXT，图片、文件、语音、视频、
 * 位置和引用消息经 IM Business 持久化后会丢失 Protobuf payload。新行为把负载转换为
 * {@code extra.payload} JSON 入库，并在推送/离线补偿时重建权威 IMProtocolV2 负载。</p>
 */
public final class MessagePayloadCodec {

    private static final ObjectMapper JSON = new ObjectMapper();

    private MessagePayloadCodec() {
    }

    public record Decoded(String content, String extra) {
    }

    public static Decoded decode(int type, byte[] bytes, String clientExtra) throws Exception {
        ObjectNode payload = JSON.createObjectNode();
        String content = "[消息]";
        IMProtocolV2.IMMessageType messageType = IMProtocolV2.IMMessageType.forNumber(type);
        if (messageType == null) throw new IllegalArgumentException("不支持的消息类型: " + type);

        switch (messageType) {
            case TEXT -> {
                IMProtocolV2.TextPayload value = IMProtocolV2.TextPayload.parseFrom(bytes);
                content = value.getContent();
                payload.put("isAtAll", value.getIsAtAll()).put("isMarkdown", value.getIsMarkdown());
                ArrayNode mentions = payload.putArray("mentionedUserIds");
                value.getMentionedUserIdsList().forEach(mentions::add);
                payload.set("emojiMap", JSON.valueToTree(value.getEmojiMapMap()));
            }
            case IMAGE -> {
                IMProtocolV2.ImagePayload value = IMProtocolV2.ImagePayload.parseFrom(bytes);
                content = value.getAltText().isEmpty() ? "[图片]" : value.getAltText();
                payload.put("url", value.getUrl()).put("thumbnailUrl", value.getThumbnailUrl())
                        .put("width", value.getWidth()).put("height", value.getHeight())
                        .put("size", value.getSize()).put("format", value.getFormat())
                        .put("md5", value.getMd5()).put("isAnimated", value.getIsAnimated())
                        .put("altText", value.getAltText());
            }
            case FILE -> {
                IMProtocolV2.FilePayload value = IMProtocolV2.FilePayload.parseFrom(bytes);
                content = value.getFileName().isEmpty() ? "[文件]" : value.getFileName();
                payload.put("url", value.getUrl()).put("fileName", value.getFileName())
                        .put("size", value.getSize()).put("extension", value.getExtension())
                        .put("md5", value.getMd5()).put("mimeType", value.getMimeType())
                        .put("diskFileId", value.getDiskFileId());
            }
            case VOICE -> {
                IMProtocolV2.VoicePayload value = IMProtocolV2.VoicePayload.parseFrom(bytes);
                content = value.getTranscription().isEmpty() ? "[语音]" : value.getTranscription();
                payload.put("url", value.getUrl()).put("duration", value.getDuration())
                        .put("size", value.getSize()).put("format", value.getFormat())
                        .put("sampleRate", value.getSampleRate()).put("isPlayed", value.getIsPlayed())
                        .put("transcription", value.getTranscription());
            }
            case VIDEO -> {
                IMProtocolV2.VideoPayload value = IMProtocolV2.VideoPayload.parseFrom(bytes);
                content = "[视频]";
                payload.put("url", value.getUrl()).put("coverUrl", value.getCoverUrl())
                        .put("duration", value.getDuration()).put("width", value.getWidth())
                        .put("height", value.getHeight()).put("size", value.getSize())
                        .put("format", value.getFormat()).put("md5", value.getMd5());
            }
            case STICKER -> {
                IMProtocolV2.StickerPayload value = IMProtocolV2.StickerPayload.parseFrom(bytes);
                content = value.getDescription().isEmpty() ? "[表情]" : value.getDescription();
                payload.put("stickerId", value.getStickerId()).put("stickerPackId", value.getStickerPackId())
                        .put("url", value.getUrl()).put("thumbnailUrl", value.getThumbnailUrl())
                        .put("width", value.getWidth()).put("height", value.getHeight())
                        .put("isAnimated", value.getIsAnimated()).put("format", value.getFormat())
                        .put("description", value.getDescription());
            }
            case LOCATION -> {
                IMProtocolV2.LocationPayload value = IMProtocolV2.LocationPayload.parseFrom(bytes);
                content = value.getName().isEmpty() ? value.getAddress() : value.getName();
                payload.put("latitude", value.getLatitude()).put("longitude", value.getLongitude())
                        .put("address", value.getAddress()).put("name", value.getName())
                        .put("staticMapUrl", value.getStaticMapUrl());
            }
            case REPLY -> {
                IMProtocolV2.ReplyPayload value = IMProtocolV2.ReplyPayload.parseFrom(bytes);
                content = decodeReplyContent(value.getReplyContent());
                payload.put("quotedMessageId", value.getQuotedMessageId())
                        .put("quotedSenderId", value.getQuotedSenderId())
                        .put("quotedContentPreview", value.getQuotedContentPreview())
                        .put("quotedMessageType", value.getQuotedMessageTypeValue());
            }
            case SYSTEM_NOTICE -> {
                IMProtocolV2.SystemPayload value = IMProtocolV2.SystemPayload.parseFrom(bytes);
                content = value.getContent();
                payload.put("noticeType", value.getNoticeType()).put("content", value.getContent())
                        .put("paramsJson", value.getParamsJson());
                ArrayNode related = payload.putArray("relatedUserIds");
                value.getRelatedUserIdsList().forEach(related::add);
            }
            case CUSTOM -> {
                IMProtocolV2.CustomPayload value = IMProtocolV2.CustomPayload.parseFrom(bytes);
                content = "[自定义消息]";
                payload.put("customType", value.getCustomType());
                String text = value.getData().toString(StandardCharsets.UTF_8);
                try { payload.set("data", JSON.readTree(text)); } catch (Exception ignored) {
                    payload.put("dataBase64", Base64.getEncoder().encodeToString(value.getData().toByteArray()));
                }
            }
            default -> throw new IllegalArgumentException("该消息类型不是可持久化聊天负载: " + messageType);
        }

        ObjectNode root = JSON.createObjectNode();
        root.set("payload", payload);
        if (clientExtra != null && !clientExtra.isBlank()) {
            try { root.set("clientExtra", JSON.readTree(clientExtra)); }
            catch (Exception ignored) { root.put("clientExtraRaw", clientExtra); }
        }
        return new Decoded(content, JSON.writeValueAsString(root));
    }

    public static byte[] encode(ImMessage message) throws Exception {
        JsonNode payload = payload(message.getExtra());
        String content = message.getContent() == null ? "" : message.getContent();
        IMProtocolV2.IMMessageType type = IMProtocolV2.IMMessageType.forNumber(message.getMessageType());
        if (type == null) throw new IllegalArgumentException("不支持的消息类型: " + message.getMessageType());
        return switch (type) {
            case TEXT -> IMProtocolV2.TextPayload.newBuilder().setContent(content)
                    .addAllMentionedUserIds(strings(payload.path("mentionedUserIds")))
                    .setIsAtAll(payload.path("isAtAll").asBoolean(false))
                    .setIsMarkdown(payload.path("isMarkdown").asBoolean(false)).build().toByteArray();
            case IMAGE -> IMProtocolV2.ImagePayload.newBuilder().setUrl(text(payload, "url"))
                    .setThumbnailUrl(text(payload, "thumbnailUrl")).setWidth(uint(payload, "width"))
                    .setHeight(uint(payload, "height")).setSize(ulong(payload, "size"))
                    .setFormat(text(payload, "format")).setMd5(text(payload, "md5"))
                    .setIsAnimated(payload.path("isAnimated").asBoolean(false))
                    .setAltText(text(payload, "altText")).build().toByteArray();
            case FILE -> IMProtocolV2.FilePayload.newBuilder().setUrl(text(payload, "url"))
                    .setFileName(text(payload, "fileName")).setSize(ulong(payload, "size"))
                    .setExtension(text(payload, "extension")).setMd5(text(payload, "md5"))
                    .setMimeType(text(payload, "mimeType")).setDiskFileId(text(payload, "diskFileId"))
                    .build().toByteArray();
            case VOICE -> IMProtocolV2.VoicePayload.newBuilder().setUrl(text(payload, "url"))
                    .setDuration(uint(payload, "duration")).setSize(ulong(payload, "size"))
                    .setFormat(text(payload, "format")).setSampleRate(uint(payload, "sampleRate"))
                    .setIsPlayed(payload.path("isPlayed").asBoolean(false))
                    .setTranscription(text(payload, "transcription")).build().toByteArray();
            case VIDEO -> IMProtocolV2.VideoPayload.newBuilder().setUrl(text(payload, "url"))
                    .setCoverUrl(text(payload, "coverUrl")).setDuration(uint(payload, "duration"))
                    .setWidth(uint(payload, "width")).setHeight(uint(payload, "height"))
                    .setSize(ulong(payload, "size")).setFormat(text(payload, "format"))
                    .setMd5(text(payload, "md5")).build().toByteArray();
            case STICKER -> IMProtocolV2.StickerPayload.newBuilder().setStickerId(text(payload, "stickerId"))
                    .setStickerPackId(text(payload, "stickerPackId")).setUrl(text(payload, "url"))
                    .setThumbnailUrl(text(payload, "thumbnailUrl")).setWidth(uint(payload, "width"))
                    .setHeight(uint(payload, "height")).setIsAnimated(payload.path("isAnimated").asBoolean(false))
                    .setFormat(text(payload, "format")).setDescription(text(payload, "description"))
                    .build().toByteArray();
            case LOCATION -> IMProtocolV2.LocationPayload.newBuilder()
                    .setLatitude(payload.path("latitude").asDouble()).setLongitude(payload.path("longitude").asDouble())
                    .setAddress(text(payload, "address")).setName(text(payload, "name"))
                    .setStaticMapUrl(text(payload, "staticMapUrl")).build().toByteArray();
            case REPLY -> IMProtocolV2.ReplyPayload.newBuilder()
                    .setQuotedMessageId(text(payload, "quotedMessageId"))
                    .setQuotedSenderId(text(payload, "quotedSenderId"))
                    .setQuotedContentPreview(text(payload, "quotedContentPreview"))
                    .setQuotedMessageTypeValue(payload.path("quotedMessageType").asInt(IMProtocolV2.IMMessageType.TEXT_VALUE))
                    .setReplyContent(ByteString.copyFrom(IMProtocolV2.TextPayload.newBuilder().setContent(content).build().toByteArray()))
                    .build().toByteArray();
            case SYSTEM_NOTICE -> IMProtocolV2.SystemPayload.newBuilder().setNoticeType(text(payload, "noticeType"))
                    .setContent(content).addAllRelatedUserIds(strings(payload.path("relatedUserIds")))
                    .setParamsJson(text(payload, "paramsJson")).build().toByteArray();
            case CUSTOM -> IMProtocolV2.CustomPayload.newBuilder().setCustomType(text(payload, "customType"))
                    .setData(ByteString.copyFrom(customData(payload))).build().toByteArray();
            default -> throw new IllegalArgumentException("该消息类型不是可持久化聊天负载: " + type);
        };
    }

    private static JsonNode payload(String extra) {
        if (extra == null || extra.isBlank()) return JSON.createObjectNode();
        try {
            JsonNode root = JSON.readTree(extra);
            return root.has("payload") && root.get("payload").isObject() ? root.get("payload") : root;
        } catch (Exception ignored) { return JSON.createObjectNode(); }
    }

    private static String decodeReplyContent(ByteString bytes) {
        try { return IMProtocolV2.TextPayload.parseFrom(bytes).getContent(); }
        catch (Exception ignored) { return "[回复]"; }
    }

    private static java.util.List<String> strings(JsonNode node) {
        java.util.List<String> values = new java.util.ArrayList<>();
        if (node.isArray()) node.forEach(item -> values.add(item.asText()));
        return values;
    }

    private static byte[] customData(JsonNode payload) throws Exception {
        if (payload.hasNonNull("data")) {
            JsonNode data = payload.get("data");
            return data.isTextual() ? data.asText().getBytes(StandardCharsets.UTF_8) : JSON.writeValueAsBytes(data);
        }
        if (payload.hasNonNull("dataBase64")) return Base64.getDecoder().decode(payload.get("dataBase64").asText());
        return JSON.writeValueAsBytes(payload);
    }

    private static String text(JsonNode node, String field) { return node.path(field).asText(""); }
    private static int uint(JsonNode node, String field) { return Math.max(0, node.path(field).asInt(0)); }
    private static long ulong(JsonNode node, String field) { return Math.max(0L, node.path(field).asLong(0L)); }
}
