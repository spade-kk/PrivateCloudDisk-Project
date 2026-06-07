package org.project.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoginDeviceVO {
    private String device_id;
    private String client_type;
    private String client_name;
    private String platform;
    private LocalDateTime last_seen_at;
    private String status;
}
