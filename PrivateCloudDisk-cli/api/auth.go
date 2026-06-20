package api

import (
	"fmt"
	"time"
)

// ============================================================
// 认证相关 API
// ============================================================

// LoginRequest 登录请求
type LoginRequest struct {
	Account     string `json:"account,omitempty"`
	PhoneNumber string `json:"phone_number,omitempty"`
	Password    string `json:"password"`
}

// LoginResponse 登录响应
type LoginResponse struct {
	Token string `json:"token"`
}

// RegisterRequest 注册请求
type RegisterRequest struct {
	PhoneNumber string `json:"phone_number,omitempty"`
	Email       string `json:"email,omitempty"`
	Password    string `json:"password"`
	Code        string `json:"code"`
	Name        string `json:"name"`
}

// UserProfile 用户信息
type UserProfile struct {
	ID          string `json:"id"`
	Account     string `json:"account"`
	PhoneNumber string `json:"phone_number"`
	Email       string `json:"email"`
	Name        string `json:"name"`
	ImagePath   string `json:"image_path"`
}

// ChangePasswordRequest 修改密码请求
type ChangePasswordRequest struct {
	OldPassword string `json:"user_password"`
	NewPassword string `json:"new_password"`
}

// Login 登录
func (c *Client) Login(account, phoneNumber, password string) (string, error) {
	req := LoginRequest{
		Account:     account,
		PhoneNumber: phoneNumber,
		Password:    password,
	}
	var token string
	if err := c.Post("/business/users/login", req, &token); err != nil {
		return "", fmt.Errorf("登录失败: %w", err)
	}
	return token, nil
}

// Register 注册
func (c *Client) Register(phoneNumber, email, password, code, name string) (string, error) {
	req := RegisterRequest{
		PhoneNumber: phoneNumber,
		Email:       email,
		Password:    password,
		Code:        code,
		Name:        name,
	}
	var account string
	if err := c.Post("/business/users/", req, &account); err != nil {
		return "", fmt.Errorf("注册失败: %w", err)
	}
	return account, nil
}

// GetUserProfile 获取用户信息
func (c *Client) GetUserProfile() (*UserProfile, error) {
	var profile UserProfile
	if err := c.Get("/business/users/me", &profile); err != nil {
		return nil, fmt.Errorf("获取用户信息失败: %w", err)
	}
	return &profile, nil
}

// DeleteAccount 注销账号
func (c *Client) DeleteAccount() error {
	if err := c.Delete("/business/users/me", nil); err != nil {
		return fmt.Errorf("注销账号失败: %w", err)
	}
	return nil
}

// ChangePassword 修改密码
func (c *Client) ChangePassword(oldPassword, newPassword string) error {
	req := ChangePasswordRequest{
		OldPassword: oldPassword,
		NewPassword: newPassword,
	}
	if err := c.Post("/business/users/me/password", req, nil); err != nil {
		return fmt.Errorf("修改密码失败: %w", err)
	}
	return nil
}

// UpdateUserInfo 更新用户信息
func (c *Client) UpdateUserInfo(email, phone, name string) error {
	req := map[string]string{}
	if email != "" {
		req["new_email"] = email
	}
	if phone != "" {
		req["new_phone_number"] = phone
	}
	if name != "" {
		req["new_name"] = name
	}
	if err := c.Patch("/business/users/me", req, nil); err != nil {
		return fmt.Errorf("更新用户信息失败: %w", err)
	}
	return nil
}

// ParseJWTExpiration 简单解析 JWT 过期时间 (不验证签名)
func ParseJWTExpiration(token string) time.Time {
	// JWT 过期时间默认设为 24 小时后
	return time.Now().Add(24 * time.Hour)
}