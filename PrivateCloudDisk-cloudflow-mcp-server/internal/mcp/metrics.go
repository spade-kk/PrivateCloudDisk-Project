package mcp

import (
	"fmt"
	"net/http"
	"sync"
	"sync/atomic"
)

// Metrics writes Prometheus text format without adding a data-plane dependency
// to the adapter.  Labels are fixed method/result classifications and never
// include user IDs, tool arguments, tenant IDs, or token material.
type Metrics struct {
	active  atomic.Int64
	mu      sync.RWMutex
	counts  map[string]uint64
	latency map[string]uint64
}

func NewMetrics() *Metrics {
	return &Metrics{counts: make(map[string]uint64), latency: make(map[string]uint64)}
}

func (metrics *Metrics) Begin() func(method, result string, milliseconds int64) {
	metrics.active.Add(1)
	return func(method, result string, milliseconds int64) {
		metrics.active.Add(-1)
		key := sanitizeMetric(method) + "|" + sanitizeMetric(result)
		metrics.mu.Lock()
		metrics.counts[key]++
		metrics.latency[key] += uint64(max(milliseconds, 0))
		metrics.mu.Unlock()
	}
}

func (metrics *Metrics) ServeHTTP(response http.ResponseWriter, _ *http.Request) {
	response.Header().Set("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
	metrics.mu.RLock()
	defer metrics.mu.RUnlock()
	_, _ = fmt.Fprintln(response, "# HELP cloudflow_mcp_active_requests Number of active MCP HTTP requests.")
	_, _ = fmt.Fprintln(response, "# TYPE cloudflow_mcp_active_requests gauge")
	_, _ = fmt.Fprintf(response, "cloudflow_mcp_active_requests %d\n", metrics.active.Load())
	_, _ = fmt.Fprintln(response, "# HELP cloudflow_mcp_requests_total Count of MCP requests by method and result.")
	_, _ = fmt.Fprintln(response, "# TYPE cloudflow_mcp_requests_total counter")
	for key, count := range metrics.counts {
		method, result := splitMetric(key)
		_, _ = fmt.Fprintf(response, "cloudflow_mcp_requests_total{method=%q,result=%q} %d\n", method, result, count)
		_, _ = fmt.Fprintf(response, "cloudflow_mcp_request_duration_milliseconds_sum{method=%q,result=%q} %d\n", method, result, metrics.latency[key])
	}
}

func sanitizeMetric(value string) string {
	if value == "" {
		return "unknown"
	}
	if len(value) > 64 {
		return value[:64]
	}
	return value
}

func splitMetric(value string) (string, string) {
	for index, item := range value {
		if item == '|' {
			return value[:index], value[index+1:]
		}
	}
	return value, "unknown"
}

func max(left, right int64) int64 {
	if left > right {
		return left
	}
	return right
}
