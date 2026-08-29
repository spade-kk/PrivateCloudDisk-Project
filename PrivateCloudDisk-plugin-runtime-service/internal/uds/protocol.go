// Package uds implements the per-plugin-instance Unix Domain Socket RPC
// boundary. It intentionally uses the official protobuf wire primitives rather
// than an ad-hoc JSON protocol; the formal source contract is proto/capability_socket.proto.
package uds

import (
	"bufio"
	"encoding/binary"
	"errors"
	"fmt"
	"io"

	"google.golang.org/protobuf/encoding/protowire"
)

const (
	maxRequestIDBytes    = 128
	maxCapabilityKeySize = 256
)

// ErrorInfo mirrors proto ErrorInfo without exposing protobuf implementation
// details to the session pipeline.
type ErrorInfo struct {
	Code      string
	Message   string
	Retryable bool
}

// CapabilityRequest is accepted only on the per-instance socket. InstanceID
// and Token are verified against the server-side session rather than trusted.
type CapabilityRequest struct {
	RequestID     string
	CapabilityKey string
	Parameters    []byte
	InstanceID    string
	Token         []byte
}

type CapabilityResponse struct {
	RequestID string
	Status    string
	Result    []byte
	Error     *ErrorInfo
}

func WriteRequest(writer io.Writer, request CapabilityRequest, maxFrame int) error {
	return writeFrame(writer, marshalRequest(request), maxFrame)
}

func ReadRequest(reader *bufio.Reader, maxFrame int) (CapabilityRequest, error) {
	payload, err := readFrame(reader, maxFrame)
	if err != nil {
		return CapabilityRequest{}, err
	}
	return unmarshalRequest(payload)
}

func WriteResponse(writer io.Writer, response CapabilityResponse, maxFrame int) error {
	return writeFrame(writer, marshalResponse(response), maxFrame)
}

func ReadResponse(reader *bufio.Reader, maxFrame int) (CapabilityResponse, error) {
	payload, err := readFrame(reader, maxFrame)
	if err != nil {
		return CapabilityResponse{}, err
	}
	return unmarshalResponse(payload)
}

func writeFrame(writer io.Writer, payload []byte, maxFrame int) error {
	if len(payload) == 0 || len(payload) > maxFrame {
		return fmt.Errorf("RUNTIME_SOCKET_FRAME_TOO_LARGE: payload=%d max=%d", len(payload), maxFrame)
	}
	header := make([]byte, 4)
	binary.BigEndian.PutUint32(header, uint32(len(payload)))
	if _, err := writer.Write(header); err != nil {
		return err
	}
	_, err := writer.Write(payload)
	return err
}

func readFrame(reader *bufio.Reader, maxFrame int) ([]byte, error) {
	header := make([]byte, 4)
	if _, err := io.ReadFull(reader, header); err != nil {
		return nil, err
	}
	length := binary.BigEndian.Uint32(header)
	if length == 0 || length > uint32(maxFrame) {
		return nil, fmt.Errorf("RUNTIME_SOCKET_FRAME_TOO_LARGE: frame=%d max=%d", length, maxFrame)
	}
	payload := make([]byte, int(length))
	if _, err := io.ReadFull(reader, payload); err != nil {
		return nil, err
	}
	return payload, nil
}

func marshalRequest(value CapabilityRequest) []byte {
	var out []byte
	out = appendString(out, 1, value.RequestID)
	out = appendString(out, 2, value.CapabilityKey)
	out = appendBytes(out, 3, value.Parameters)
	out = appendString(out, 4, value.InstanceID)
	return appendBytes(out, 5, value.Token)
}

func unmarshalRequest(payload []byte) (CapabilityRequest, error) {
	var result CapabilityRequest
	for len(payload) > 0 {
		number, kind, size := protowire.ConsumeTag(payload)
		if size < 0 {
			return result, wireError(size)
		}
		payload = payload[size:]
		if kind != protowire.BytesType {
			return result, errors.New("RUNTIME_SOCKET_PROTOCOL_INVALID: request field must be length-delimited")
		}
		value, consumed := protowire.ConsumeBytes(payload)
		if consumed < 0 {
			return result, wireError(consumed)
		}
		payload = payload[consumed:]
		switch number {
		case 1:
			result.RequestID = string(value)
		case 2:
			result.CapabilityKey = string(value)
		case 3:
			result.Parameters = append([]byte(nil), value...)
		case 4:
			result.InstanceID = string(value)
		case 5:
			result.Token = append([]byte(nil), value...)
		}
	}
	if len(result.RequestID) == 0 || len(result.RequestID) > maxRequestIDBytes ||
		len(result.CapabilityKey) == 0 || len(result.CapabilityKey) > maxCapabilityKeySize ||
		len(result.Parameters) == 0 || len(result.InstanceID) == 0 || len(result.Token) < 32 {
		return result, errors.New("RUNTIME_SOCKET_PROTOCOL_INVALID: required request fields are missing or out of range")
	}
	return result, nil
}

func marshalResponse(value CapabilityResponse) []byte {
	var out []byte
	out = appendString(out, 1, value.RequestID)
	out = appendString(out, 2, value.Status)
	out = appendBytes(out, 3, value.Result)
	if value.Error != nil {
		var nested []byte
		nested = appendString(nested, 1, value.Error.Code)
		nested = appendString(nested, 2, value.Error.Message)
		if value.Error.Retryable {
			nested = protowire.AppendTag(nested, 3, protowire.VarintType)
			nested = protowire.AppendVarint(nested, 1)
		}
		out = appendBytes(out, 4, nested)
	}
	return out
}

func unmarshalResponse(payload []byte) (CapabilityResponse, error) {
	var result CapabilityResponse
	for len(payload) > 0 {
		number, kind, size := protowire.ConsumeTag(payload)
		if size < 0 {
			return result, wireError(size)
		}
		payload = payload[size:]
		if kind != protowire.BytesType {
			return result, errors.New("RUNTIME_SOCKET_PROTOCOL_INVALID: response field must be length-delimited")
		}
		value, consumed := protowire.ConsumeBytes(payload)
		if consumed < 0 {
			return result, wireError(consumed)
		}
		payload = payload[consumed:]
		switch number {
		case 1:
			result.RequestID = string(value)
		case 2:
			result.Status = string(value)
		case 3:
			result.Result = append([]byte(nil), value...)
		case 4:
			errorInfo, err := unmarshalError(value)
			if err != nil {
				return result, err
			}
			result.Error = &errorInfo
		}
	}
	if result.RequestID == "" || result.Status == "" {
		return result, errors.New("RUNTIME_SOCKET_PROTOCOL_INVALID: response missing request_id or status")
	}
	return result, nil
}

func unmarshalError(payload []byte) (ErrorInfo, error) {
	var result ErrorInfo
	for len(payload) > 0 {
		number, kind, size := protowire.ConsumeTag(payload)
		if size < 0 {
			return result, wireError(size)
		}
		payload = payload[size:]
		switch number {
		case 1, 2:
			if kind != protowire.BytesType {
				return result, errors.New("RUNTIME_SOCKET_PROTOCOL_INVALID: error string has invalid wire type")
			}
			value, consumed := protowire.ConsumeBytes(payload)
			if consumed < 0 {
				return result, wireError(consumed)
			}
			payload = payload[consumed:]
			if number == 1 {
				result.Code = string(value)
			} else {
				result.Message = string(value)
			}
		case 3:
			if kind != protowire.VarintType {
				return result, errors.New("RUNTIME_SOCKET_PROTOCOL_INVALID: error retryable has invalid wire type")
			}
			value, consumed := protowire.ConsumeVarint(payload)
			if consumed < 0 {
				return result, wireError(consumed)
			}
			payload = payload[consumed:]
			result.Retryable = value != 0
		default:
			consumed := protowire.ConsumeFieldValue(number, kind, payload)
			if consumed < 0 {
				return result, wireError(consumed)
			}
			payload = payload[consumed:]
		}
	}
	return result, nil
}

func appendString(out []byte, number protowire.Number, value string) []byte {
	if value == "" {
		return out
	}
	return appendBytes(out, number, []byte(value))
}

func appendBytes(out []byte, number protowire.Number, value []byte) []byte {
	if len(value) == 0 {
		return out
	}
	out = protowire.AppendTag(out, number, protowire.BytesType)
	return protowire.AppendBytes(out, value)
}

func wireError(code int) error {
	return fmt.Errorf("RUNTIME_SOCKET_PROTOCOL_INVALID: protobuf parse error %d", code)
}
