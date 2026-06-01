package org.project.data;

import lombok.Data;
import java.io.Serializable;

/**
 * 用户数据类
 */
@Data
public class UserData implements Serializable {
    private String name;
    private String password;
    private String email;
    private String id;
    private String phone_number;
    private String account;
    private String image_path;
}
