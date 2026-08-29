package mcp

import (
	"crypto/rand"
	"crypto/subtle"
	"encoding/base64"
	"errors"
	"sync"
	"time"

	"privateclouddisk/cloudflow-mcp-server/internal/identity"
)

type session struct {
	identity  identity.Identity
	expiresAt time.Time
}

type sessions struct {
	mu      sync.Mutex
	ttl     time.Duration
	entries map[string]session
}

func newSessions(ttl time.Duration) *sessions {
	return &sessions{ttl: ttl, entries: make(map[string]session)}
}

func (store *sessions) Create(identity identity.Identity, now time.Time) (string, error) {
	bytes := make([]byte, 32)
	if _, err := rand.Read(bytes); err != nil {
		return "", err
	}
	id := base64.RawURLEncoding.EncodeToString(bytes)
	store.mu.Lock()
	store.entries[id] = session{identity: identity, expiresAt: now.Add(store.ttl)}
	store.mu.Unlock()
	return id, nil
}

func (store *sessions) Require(id string, principal identity.Identity, now time.Time) error {
	store.mu.Lock()
	defer store.mu.Unlock()
	entry, ok := store.entries[id]
	if !ok || !now.Before(entry.expiresAt) {
		delete(store.entries, id)
		return errors.New("MCP session is missing or expired")
	}
	if subtle.ConstantTimeCompare([]byte(entry.identity.UserID), []byte(principal.UserID)) != 1 ||
		subtle.ConstantTimeCompare([]byte(entry.identity.TenantID), []byte(principal.TenantID)) != 1 ||
		subtle.ConstantTimeCompare([]byte(entry.identity.SpaceID), []byte(principal.SpaceID)) != 1 {
		return errors.New("MCP session does not belong to this identity")
	}
	entry.expiresAt = now.Add(store.ttl)
	store.entries[id] = entry
	return nil
}

func (store *sessions) RemoveExpired(now time.Time) {
	store.mu.Lock()
	for id, entry := range store.entries {
		if !now.Before(entry.expiresAt) {
			delete(store.entries, id)
		}
	}
	store.mu.Unlock()
}
