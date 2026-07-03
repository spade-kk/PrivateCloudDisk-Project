// Package templatefile 提供基于嵌入文件系统的模板加载器。
// 模板文件存放在 templates/ 目录下，通过 Go embed 编译进二进制文件。
package templatefile

import (
	"embed"
	"encoding/json"
	"fmt"
	"path/filepath"
	"strings"
	"sync"

	"github.com/privateclouddisk/notification-service/internal/domain"
)

//go:embed templates/*
var templatesFS embed.FS

// Loader 模板文件加载器
type Loader struct {
	cache map[string]*domain.Template // key: code:channel:lang
	mu    sync.RWMutex
}

// NewLoader 创建模板加载器
func NewLoader() *Loader {
	l := &Loader{
		cache: make(map[string]*domain.Template),
	}
	l.loadAll()
	return l
}

// Get 获取模板（code: 模板编码, channel: 渠道, lang: 语言）
func (l *Loader) Get(code, channel, lang string) (*domain.Template, error) {
	key := l.cacheKey(code, channel, lang)

	l.mu.RLock()
	t, ok := l.cache[key]
	l.mu.RUnlock()

	if ok {
		return t, nil
	}

	// 降级：去掉区域后缀
	if len(lang) > 2 {
		fallback := lang[:2]
		key = l.cacheKey(code, channel, fallback)
		l.mu.RLock()
		t, ok = l.cache[key]
		l.mu.RUnlock()
		if ok {
			return t, nil
		}
	}

	return nil, fmt.Errorf("模板文件不存在: code=%s, channel=%s, lang=%s", code, channel, lang)
}

// loadAll 加载所有嵌入模板文件
func (l *Loader) loadAll() {
	entries, err := templatesFS.ReadDir("templates")
	if err != nil {
		return
	}

	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}
		if filepath.Ext(entry.Name()) != ".json" {
			continue
		}

		data, err := templatesFS.ReadFile("templates/" + entry.Name())
		if err != nil {
			continue
		}

		var t domain.Template
		if err := json.Unmarshal(data, &t); err != nil {
			continue
		}

		key := l.cacheKey(t.Code, t.Channel, t.Lang)
		l.mu.Lock()
		l.cache[key] = &t
		l.mu.Unlock()
	}
}

// Reload 重新加载所有模板（热加载）
func (l *Loader) Reload() {
	l.mu.Lock()
	l.cache = make(map[string]*domain.Template)
	l.mu.Unlock()
	l.loadAll()
}

// List 列出所有已加载的模板
func (l *Loader) List() []string {
	l.mu.RLock()
	defer l.mu.RUnlock()

	keys := make([]string, 0, len(l.cache))
	for k := range l.cache {
		keys = append(keys, k)
	}
	return keys
}

func (l *Loader) cacheKey(code, channel, lang string) string {
	return strings.ToLower(code) + ":" + strings.ToLower(channel) + ":" + strings.ToLower(lang)
}