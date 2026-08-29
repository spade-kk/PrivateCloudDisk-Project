package platform

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"strings"
	"time"

	"privateclouddisk/git-service/internal/domain"
)

type Client struct {
	baseURL string
	token   string
	http    *http.Client
}

func New(baseURL, token string) *Client {
	return &Client{baseURL: strings.TrimRight(baseURL, "/"), token: token, http: &http.Client{Timeout: 3 * time.Second}}
}

// Authorization 每次敏感操作都实时读取 Platform 的空间状态和权限开关，
// [REQ-GIT-PERM-9.1/9.6] 不把跨服务权限快照长期缓存为事实源。
func (c *Client) Authorization(ctx context.Context, spaceID, userID string) (domain.SpaceAuthorization, error) {
	endpoint := c.baseURL + "/business/internal/git/spaces/" + url.PathEscape(spaceID) + "/authorization"
	if userID != "" {
		endpoint += "?userId=" + url.QueryEscape(userID)
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return domain.SpaceAuthorization{}, err
	}
	req.Header.Set("X-PCD-Service-Token", c.token)
	res, err := c.http.Do(req)
	if err != nil {
		return domain.SpaceAuthorization{}, err
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		return domain.SpaceAuthorization{}, fmt.Errorf("platform authorization returned %d", res.StatusCode)
	}
	var authorization domain.SpaceAuthorization
	if err := json.NewDecoder(res.Body).Decode(&authorization); err != nil {
		return domain.SpaceAuthorization{}, err
	}
	return authorization, nil
}

// TeamMembership 复用 Platform 的团队/企业空间成员事实源；Git Service 不复制成员表。
func (c *Client) TeamMembership(ctx context.Context, teamID, userID string) (domain.TeamMembership, error) {
	endpoint := c.baseURL + "/business/internal/git/teams/" + url.PathEscape(teamID) + "/members/" + url.PathEscape(userID)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return domain.TeamMembership{}, err
	}
	req.Header.Set("X-PCD-Service-Token", c.token)
	res, err := c.http.Do(req)
	if err != nil {
		return domain.TeamMembership{}, err
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		return domain.TeamMembership{}, fmt.Errorf("platform team membership returned %d", res.StatusCode)
	}
	var membership domain.TeamMembership
	if err := json.NewDecoder(res.Body).Decode(&membership); err != nil {
		return domain.TeamMembership{}, err
	}
	return membership, nil
}
