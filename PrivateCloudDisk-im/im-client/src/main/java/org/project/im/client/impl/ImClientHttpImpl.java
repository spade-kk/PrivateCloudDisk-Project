package org.project.im.client.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.im.client.ImClient;
import org.project.im.common.dto.ConversationDTO;
import org.project.im.common.dto.GroupDTO;
import org.project.im.common.dto.GroupMemberDTO;
import org.project.im.common.dto.MessageDTO;
import org.project.im.common.dto.Result;
import org.project.im.common.enums.MessageType;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.project.im.common.constant.ImConstants.*;

/**
 * IM 客户端实现（HTTP 模式）
 * <p>
 * 通过 HTTP REST API 调用 im-platform 服务，实现消息收发、
 * 会话管理、群组管理等功能。同时通过 RabbitMQ 发送消息事件。
 * </p>
 * <p>
 * 使用方式：在 Spring Boot 项目引入 im-client 依赖后，直接注入
 * {@code @Autowired private ImClient imClient;} 即可使用。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class ImClientHttpImpl implements ImClient {

    private final RestTemplate restTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${im.platform.url:http://localhost:8088/im}")
    private String platformBaseUrl;

    // ==================== 消息相关 ====================

    @Override
    public Result<MessageDTO> sendMessage(MessageDTO messageDTO) {
        String url = platformBaseUrl + "/api/v1/messages/send";
        try {
            ResponseEntity<Result<MessageDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(messageDTO),  // 将请求体包装为 HttpEntity
                    new ParameterizedTypeReference<Result<MessageDTO>>() {}
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("发送消息失败", e);
            return Result.error(500, "发送消息失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Void> recallMessage(String messageId, String userId) {
        String url = platformBaseUrl + "/api/v1/messages/recall?messageId={messageId}&userId={userId}";
        try {
            ResponseEntity<Result<Void>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    null,  // 无请求体
                    new ParameterizedTypeReference<Result<Void>>() {},
                    messageId, userId
            );
            return Result.success(null);
        } catch (Exception e) {
            log.error("撤回消息失败", e);
            return Result.error(500, "撤回消息失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Void> sendSystemNotice(List<String> userIds, String title, String content) {
        for (String userId : userIds) {
            MessageDTO notice = MessageDTO.builder()
                    .conversationId("SYSTEM_" + userId)
                    .conversationType(3)
                    .messageType(MessageType.SYSTEM_NOTICE.getCode())
                    .senderId("SYSTEM")
                    .receiverId(userId)
                    .content(title + "\n" + content)
                    .sendTime(LocalDateTime.now())
                    .build();
            rabbitTemplate.convertAndSend(MQ_EXCHANGE_MESSAGE, MQ_ROUTING_SYSTEM, notice);
        }
        return Result.success(null);
    }

    // ==================== 会话相关 ====================

    @Override
    public Result<ConversationDTO> getOrCreateConversation(String userId, String targetId,
                                                            int conversationType) {
        String url = platformBaseUrl
                + "/api/v1/conversations/create?userId={userId}&targetId={targetId}&conversationType={type}";
        try {
            ResponseEntity<Result<ConversationDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    null,   // 没有请求体时可以直接传 null（或 new HttpEntity<>(null)）
                    new ParameterizedTypeReference<Result<ConversationDTO>>() {},
                    userId, targetId, conversationType
            );
            return response.getBody();   // 类型为 Result<ConversationDTO>ersationType);
        } catch (Exception e) {
            log.error("创建会话失败", e);
            return Result.error(500, "创建会话失败: " + e.getMessage());
        }
    }

    @Override
    public Result<List<ConversationDTO>> getConversations(String userId) {
        String url = platformBaseUrl + "/api/v1/conversations/list?userId={userId}";
        try {
            ResponseEntity<Result<List<ConversationDTO>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<Result<List<ConversationDTO>>>() {}, userId);
            return response.getBody();
        } catch (Exception e) {
            log.error("获取会话列表失败", e);
            return Result.error(500, "获取会话列表失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Integer> getTotalUnreadCount(String userId) {
        String url = platformBaseUrl + "/api/v1/conversations/unread/count?userId={userId}";
        try {
            ResponseEntity<Result<Integer>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<Result<Integer>>() {}, userId);
            return response.getBody();
        } catch (Exception e) {
            log.error("获取未读消息数失败", e);
            return Result.error(500, "获取未读消息数失败: " + e.getMessage());
        }
    }

    // ==================== 群组相关 ====================

    @Override
    public Result<GroupDTO> createGroup(String ownerId, String groupName, String avatar) {
        String url = platformBaseUrl
                + "/api/v1/groups/create?ownerId={ownerId}&groupName={groupName}&avatar={avatar}";
        try {
            ResponseEntity<Result<GroupDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    null,
                    new ParameterizedTypeReference<Result<GroupDTO>>() {},
                    ownerId, groupName, avatar
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("创建群组失败", e);
            return Result.error(500, "创建群组失败: " + e.getMessage());
        }
    }

    @Override
    public Result<GroupDTO> getGroupDetail(String groupId) {
        String url = platformBaseUrl + "/api/v1/groups/{groupId}";
        try {
            ResponseEntity<Result<GroupDTO>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<Result<GroupDTO>>() {}, groupId);
            return response.getBody();
        } catch (Exception e) {
            log.error("获取群组详情失败", e);
            return Result.error(500, "获取群组详情失败: " + e.getMessage());
        }
    }

    @Override
    public Result<List<GroupDTO>> getUserGroups(String userId) {
        String url = platformBaseUrl + "/api/v1/groups/user/{userId}";
        try {
            ResponseEntity<Result<List<GroupDTO>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<Result<List<GroupDTO>>>() {}, userId);
            return response.getBody();
        } catch (Exception e) {
            log.error("获取用户群组失败", e);
            return Result.error(500, "获取用户群组失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Void> joinGroup(String groupId, String userId) {
        String url = platformBaseUrl + "/api/v1/groups/{groupId}/join?userId={userId}";
        try {
            ResponseEntity<Result<Void>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    null,
                    new ParameterizedTypeReference<Result<Void>>() {},
                    groupId, userId
            );
            return Result.success(null);
        } catch (Exception e) {
            log.error("加入群组失败", e);
            return Result.error(500, "加入群组失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Void> leaveGroup(String groupId, String userId) {
        String url = platformBaseUrl + "/api/v1/groups/{groupId}/leave?userId={userId}";
        try {
            ResponseEntity<Result<Void>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    null,
                    new ParameterizedTypeReference<Result<Void>>() {},
                    groupId, userId
            );
            return Result.success(null);
        } catch (Exception e) {
            log.error("退出群组失败", e);
            return Result.error(500, "退出群组失败: " + e.getMessage());
        }
    }

    @Override
    public Result<List<GroupMemberDTO>> getGroupMembers(String groupId) {
        String url = platformBaseUrl + "/api/v1/groups/{groupId}/members";
        try {
            ResponseEntity<Result<List<GroupMemberDTO>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<Result<List<GroupMemberDTO>>>() {}, groupId);
            return response.getBody();
        } catch (Exception e) {
            log.error("获取群成员列表失败", e);
            return Result.error(500, "获取群成员列表失败: " + e.getMessage());
        }
    }

    // ==================== 在线状态 ====================

    @Override
    public boolean isUserOnline(String userId) {
        try {
            String key = String.format(REDIS_USER_CHANNEL, userId);
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("查询在线状态失败", e);
            return false;
        }
    }

    @Override
    public int getOnlineUserCount() {
        try {
            return stringRedisTemplate.keys(String.format(REDIS_USER_CHANNEL, "*")).size();
        } catch (Exception e) {
            log.error("获取在线用户数失败", e);
            return 0;
        }
    }
}