package api

import (
	"testing"

	"privateclouddisk/git-service/internal/config"
)

func TestRoutesRegisterWithoutServeMuxConflict(t *testing.T) {
	// Go 1.22+ validates method/path pattern overlap during registration.
	// This must remain a construction-level regression test so it does not require
	// MySQL, Platform Service, Storage Service, or RabbitMQ to be running.
	api := New(config.Config{InternalServiceToken: "test"}, nil, nil, nil, nil)
	if api == nil {
		t.Fatal("expected API instance")
	}
}
