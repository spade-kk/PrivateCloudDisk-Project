package org.project.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.mapper.TagMapper;
import org.project.model.entity.TagEntity;
import org.project.model.entity.FileTagEntity;
import org.project.model.vo.TagVO;
import org.project.model.vo.TaggedFileVO;
import org.project.service.TagService;
import org.project.service.ex.InsertException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 标签服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagMapper tagMapper;

    // ==================== 标签 CRUD ====================

    @Override
    public TagVO createTag(UUID user_id, String tag_name, String tag_color) {
        // 检查是否已存在同名标签
        TagEntity existing = tagMapper.findTagByName(user_id, tag_name);
        if (existing != null) {
            log.info("标签已存在: userId={}, tagName={}", user_id, tag_name);
            return toTagVO(existing);
        }

        TagEntity tag = new TagEntity();
        tag.setTag_user_id(user_id);
        tag.setTag_name(tag_name);
        tag.setTag_color(tag_color != null ? tag_color : "#3B82F6");
        tag.setTag_created_at(LocalDateTime.now());

        int rows = tagMapper.insertTag(tag);
        if (rows != 1) {
            throw new InsertException("创建标签失败");
        }
        log.info("标签创建成功: userId={}, tagName={}, tagId={}", user_id, tag_name, tag.getTag_id());
        return toTagVO(tag);
    }

    @Override
    @Transactional
    public void deleteTag(UUID user_id, Long tag_id) {
        TagEntity tag = tagMapper.findTagById(tag_id);
        if (tag == null || !tag.getTag_user_id().equals(user_id)) {
            log.warn("标签不存在或无权删除: userId={}, tagId={}", user_id, tag_id);
            return;
        }
        // 级联删除由数据库外键处理
        tagMapper.deleteTag(tag_id, user_id);
        log.info("标签已删除: userId={}, tagId={}", user_id, tag_id);
    }

    @Override
    public TagVO updateTag(UUID user_id, Long tag_id, String tag_name, String tag_color) {
        TagEntity tag = tagMapper.findTagById(tag_id);
        if (tag == null || !tag.getTag_user_id().equals(user_id)) {
            throw new InsertException("标签不存在或无权修改");
        }

        tag.setTag_name(tag_name);
        tag.setTag_color(tag_color);
        tagMapper.updateTag(tag);
        log.info("标签已更新: userId={}, tagId={}", user_id, tag_id);
        return toTagVO(tag);
    }

    @Override
    public List<TagVO> getUserTags(UUID user_id) {
        List<TagEntity> tags = tagMapper.findTagsByUserId(user_id);
        return tags.stream().map(this::toTagVO).collect(Collectors.toList());
    }

    @Override
    public TagVO getTagById(Long tag_id) {
        TagEntity tag = tagMapper.findTagById(tag_id);
        return tag != null ? toTagVO(tag) : null;
    }

    // ==================== 文件标签关联 ====================

    @Override
    @Transactional
    public void tagFile(UUID user_id, String target_id, String target_type, List<Long> tag_ids) {
        UUID targetUuid = UUID.fromString(target_id);
        boolean isFile = "file".equals(target_type);

        for (Long tag_id : tag_ids) {
            // 检查是否已打标签
            FileTagEntity existing = isFile
                    ? tagMapper.findFileTagByTagAndFile(tag_id, user_id, targetUuid)
                    : tagMapper.findFileTagByTagAndNode(tag_id, user_id, targetUuid);

            if (existing != null) {
                log.debug("已打标签，跳过: userId={}, targetId={}, tagId={}", user_id, target_id, tag_id);
                continue;
            }

            FileTagEntity ft = new FileTagEntity();
            ft.setFt_user_id(user_id);
            ft.setFt_tag_id(tag_id);
            ft.setFt_target_type(target_type);
            if (isFile) {
                ft.setFt_file_id(targetUuid);
            } else {
                ft.setFt_node_id(targetUuid);
            }
            ft.setFt_tagged_at(LocalDateTime.now());

            tagMapper.insertFileTag(ft);
        }
        log.info("打标签成功: userId={}, targetId={}, tagIds={}", user_id, target_id, tag_ids);
    }

    @Override
    public void untagFile(UUID user_id, String target_id, String target_type, Long tag_id) {
        UUID targetUuid = UUID.fromString(target_id);
        if ("file".equals(target_type)) {
            tagMapper.deleteFileTagByTagAndFile(tag_id, user_id, targetUuid);
        } else {
            tagMapper.deleteFileTagByTagAndNode(tag_id, user_id, targetUuid);
        }
        log.info("移除标签: userId={}, targetId={}, tagId={}", user_id, target_id, tag_id);
    }

    @Override
    public List<TagVO> getFileTags(UUID user_id, String target_id, String target_type) {
        UUID targetUuid = UUID.fromString(target_id);
        List<TagEntity> tags;
        if ("file".equals(target_type)) {
            tags = tagMapper.findTagsByFileId(user_id, targetUuid);
        } else {
            tags = tagMapper.findTagsByNodeId(user_id, targetUuid);
        }
        return tags.stream().map(this::toTagVO).collect(Collectors.toList());
    }

    @Override
    public List<FileTagEntity> getFileTagsBatch(UUID user_id, List<UUID> file_ids) {
        if (file_ids == null || file_ids.isEmpty()) return new ArrayList<>();
        return tagMapper.findTagsByFileIds(user_id, file_ids);
    }

    @Override
    public List<FileTagEntity> getFolderTagsBatch(UUID user_id, List<UUID> node_ids) {
        if (node_ids == null || node_ids.isEmpty()) return new ArrayList<>();
        return tagMapper.findTagsByNodeIds(user_id, node_ids);
    }

    // ==================== 按标签查询 ====================

    @Override
    public List<TaggedFileVO> getFilesByTag(Long tag_id, UUID user_id, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<FileTagEntity> items = tagMapper.findFilesByTagId(tag_id, user_id, offset, pageSize);
        return items.stream().map(this::toTaggedFileVO).collect(Collectors.toList());
    }

    @Override
    public List<TaggedFileVO> getFoldersByTag(Long tag_id, UUID user_id, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<FileTagEntity> items = tagMapper.findFoldersByTagId(tag_id, user_id, offset, pageSize);
        return items.stream().map(this::toTaggedFileVO).collect(Collectors.toList());
    }

    @Override
    public void createDefaultTags(UUID user_id) {
        tagMapper.insertDefaultTags(user_id);
        log.info("预设标签创建成功: userId={}", user_id);
    }

    // ==================== VO 转换 ====================

    private TagVO toTagVO(TagEntity entity) {
        TagVO vo = new TagVO();
        vo.setTag_id(entity.getTag_id());
        vo.setTag_name(entity.getTag_name());
        vo.setTag_color(entity.getTag_color());
        vo.setFile_count(entity.getFile_count() != null ? entity.getFile_count() : 0);
        vo.setFolder_count(entity.getFolder_count() != null ? entity.getFolder_count() : 0);
        vo.setTag_created_at(entity.getTag_created_at());
        return vo;
    }

    private TaggedFileVO toTaggedFileVO(FileTagEntity entity) {
        TaggedFileVO vo = new TaggedFileVO();
        if ("file".equals(entity.getFt_target_type())) {
            vo.setTarget_id(entity.getFt_file_id() != null ? entity.getFt_file_id().toString() : null);
        } else {
            vo.setTarget_id(entity.getFt_node_id() != null ? entity.getFt_node_id().toString() : null);
        }
        vo.setTarget_type(entity.getFt_target_type());
        vo.setTarget_name(entity.getFile_name());
        vo.setTarget_size(entity.getFile_size());
        vo.setFile_type(entity.getFile_type());
        vo.setTagged_at(entity.getFt_tagged_at());

        TagVO tag = new TagVO();
        tag.setTag_id(entity.getFt_tag_id());
        tag.setTag_name(entity.getTag_name());
        tag.setTag_color(entity.getTag_color());
        vo.setTags(List.of(tag));
        return vo;
    }
}