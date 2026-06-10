package org.project.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.io.Serializable;
import java.util.UUID;

/**
 * 用户数据类
 */
@Data
public class UserEntity implements Serializable {
    private String name;
    @JsonIgnore
    private String password;
    private String email;
    private UUID id;
    private String phone_number;
    private String account;
    private String image_path;
}
