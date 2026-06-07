package org.project.model.vo;

import lombok.Data;

@Data
public class UserProfileVO {
    private String id;
    private String account;
    private String phone_number;
    private String email;
    private String name;
    private String image_path;
}
