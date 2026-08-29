package storage

import (
	"context"
	"io"
	"net/http"
	"strings"
	"testing"
)

func TestExistsReportsStorageBrokerDetail(t *testing.T) {
	client := New("http://storage.test", "token")
	client.http.Transport = roundTripFunc(func(request *http.Request) (*http.Response, error) {
		if request.Method != http.MethodHead {
			t.Fatalf("expected HEAD, got %s", request.Method)
		}
		if request.Header.Get("X-PCD-Service-Token") != "token" {
			t.Fatalf("service token was not forwarded")
		}
		return &http.Response{
			StatusCode: http.StatusUnauthorized,
			Body:       io.NopCloser(strings.NewReader(`{"detail":"内部服务认证失败"}`)),
			Header:     make(http.Header),
			Request:    request,
		}, nil
	})
	_, err := client.Exists(context.Background(), "sha1", strings.Repeat("a", 40))
	if err == nil || !strings.Contains(err.Error(), "内部服务认证失败") {
		t.Fatalf("expected broker detail in error, got %v", err)
	}
}

func TestExistsTreatsMissingObjectAsNotFound(t *testing.T) {
	client := New("http://storage.test", "token")
	client.http.Transport = roundTripFunc(func(request *http.Request) (*http.Response, error) {
		return &http.Response{StatusCode: http.StatusNotFound, Body: io.NopCloser(strings.NewReader("")), Header: make(http.Header), Request: request}, nil
	})
	exists, err := client.Exists(context.Background(), "sha1", strings.Repeat("b", 40))
	if err != nil || exists {
		t.Fatalf("expected missing object without error, exists=%v err=%v", exists, err)
	}
}

type roundTripFunc func(*http.Request) (*http.Response, error)

func (fn roundTripFunc) RoundTrip(request *http.Request) (*http.Response, error) {
	return fn(request)
}
