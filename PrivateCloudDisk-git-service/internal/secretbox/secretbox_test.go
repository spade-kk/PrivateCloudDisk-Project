package secretbox

import "testing"

func TestSealOpenRoundTrip(t *testing.T) {
	box, err := New("test-internal-service-token")
	if err != nil {
		t.Fatal(err)
	}
	ciphertext, err := box.Seal([]byte("webhook-secret"))
	if err != nil {
		t.Fatal(err)
	}
	plaintext, err := box.Open(ciphertext)
	if err != nil || string(plaintext) != "webhook-secret" {
		t.Fatalf("unexpected plaintext %q, error=%v", plaintext, err)
	}
	if _, err := box.Open([]byte("tampered")); err == nil {
		t.Fatal("tampered ciphertext must be rejected")
	}
}
