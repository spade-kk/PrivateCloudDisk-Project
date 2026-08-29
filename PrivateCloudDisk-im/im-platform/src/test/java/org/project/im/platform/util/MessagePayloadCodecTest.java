package org.project.im.platform.util;

import org.junit.jupiter.api.Test;
import org.project.im.common.protocol.v2.IMProtocolV2;
import org.project.im.platform.entity.ImMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagePayloadCodecTest {

    @Test
    void filePayloadShouldSurvivePersistenceRoundTrip() throws Exception {
        IMProtocolV2.FilePayload original = IMProtocolV2.FilePayload.newBuilder()
                .setFileName("合同.pdf")
                .setSize(4096)
                .setMimeType("application/pdf")
                .setDiskFileId("file-uuid")
                .build();

        MessagePayloadCodec.Decoded decoded = MessagePayloadCodec.decode(
                IMProtocolV2.IMMessageType.FILE_VALUE, original.toByteArray(), "{\"trace\":\"client\"}");
        ImMessage persisted = ImMessage.builder()
                .messageType(IMProtocolV2.IMMessageType.FILE_VALUE)
                .content(decoded.content())
                .extra(decoded.extra())
                .build();
        IMProtocolV2.FilePayload rebuilt = IMProtocolV2.FilePayload.parseFrom(
                MessagePayloadCodec.encode(persisted));

        assertEquals("合同.pdf", decoded.content());
        assertEquals(original.getFileName(), rebuilt.getFileName());
        assertEquals(original.getSize(), rebuilt.getSize());
        assertEquals(original.getDiskFileId(), rebuilt.getDiskFileId());
        assertTrue(decoded.extra().contains("clientExtra"));
    }

    @Test
    void replyPayloadShouldKeepQuoteAndReplyText() throws Exception {
        IMProtocolV2.ReplyPayload original = IMProtocolV2.ReplyPayload.newBuilder()
                .setQuotedMessageId("123")
                .setQuotedSenderId("sender")
                .setQuotedContentPreview("原消息")
                .setQuotedMessageType(IMProtocolV2.IMMessageType.TEXT)
                .setReplyContent(IMProtocolV2.TextPayload.newBuilder().setContent("回复正文").build().toByteString())
                .build();

        MessagePayloadCodec.Decoded decoded = MessagePayloadCodec.decode(
                IMProtocolV2.IMMessageType.REPLY_VALUE, original.toByteArray(), null);
        ImMessage persisted = ImMessage.builder()
                .messageType(IMProtocolV2.IMMessageType.REPLY_VALUE)
                .content(decoded.content())
                .extra(decoded.extra())
                .build();
        IMProtocolV2.ReplyPayload rebuilt = IMProtocolV2.ReplyPayload.parseFrom(
                MessagePayloadCodec.encode(persisted));

        assertEquals("回复正文", decoded.content());
        assertEquals("123", rebuilt.getQuotedMessageId());
        assertEquals("回复正文", IMProtocolV2.TextPayload.parseFrom(rebuilt.getReplyContent()).getContent());
    }
}
