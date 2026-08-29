package secretbox

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/sha256"
	"fmt"
	"io"
)

type Box struct{ aead cipher.AEAD }

func New(master string) (*Box, error) {
	key := sha256.Sum256([]byte("pcd.git.webhook.v1\x00" + master))
	block, err := aes.NewCipher(key[:])
	if err != nil {
		return nil, err
	}
	aead, err := cipher.NewGCM(block)
	if err != nil {
		return nil, err
	}
	return &Box{aead: aead}, nil
}

func (b *Box) Seal(plaintext []byte) ([]byte, error) {
	nonce := make([]byte, b.aead.NonceSize())
	if _, err := io.ReadFull(rand.Reader, nonce); err != nil {
		return nil, err
	}
	return append(nonce, b.aead.Seal(nil, nonce, plaintext, nil)...), nil
}

func (b *Box) Open(ciphertext []byte) ([]byte, error) {
	if len(ciphertext) < b.aead.NonceSize() {
		return nil, fmt.Errorf("ciphertext is truncated")
	}
	nonce := ciphertext[:b.aead.NonceSize()]
	return b.aead.Open(nil, nonce, ciphertext[b.aead.NonceSize():], nil)
}
