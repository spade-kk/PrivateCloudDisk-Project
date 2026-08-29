// Package telemetry centralizes distributed tracing without making protocol,
// Adapter, or Hub code aware of a concrete telemetry exporter.
package telemetry

import (
	"context"
	"fmt"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/semconv/v1.30.0"

	"privateclouddisk/cloudflow-mcp-server/internal/config"
)

// Setup installs W3C trace propagation for every mode.  When an OTLP endpoint
// is configured, the service additionally exports spans over OTLP/HTTP; an
// empty endpoint remains deliberately safe for local and test deployments.
func Setup(ctx context.Context, cfg config.Config) (func(context.Context) error, error) {
	otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(
		propagation.TraceContext{}, propagation.Baggage{},
	))
	resource, err := resource.New(ctx,
		resource.WithAttributes(
			semconv.ServiceName("cloudflow-mcp-server"),
			semconv.ServiceVersion(cfg.Version),
		),
	)
	if err != nil {
		return nil, fmt.Errorf("create OpenTelemetry resource: %w", err)
	}
	options := []sdktrace.TracerProviderOption{sdktrace.WithResource(resource)}
	if cfg.OTLPEndpoint != "" {
		exporterOptions := []otlptracehttp.Option{otlptracehttp.WithEndpoint(cfg.OTLPEndpoint)}
		if cfg.OTLPInsecure {
			exporterOptions = append(exporterOptions, otlptracehttp.WithInsecure())
		}
		exporter, err := otlptracehttp.New(ctx, exporterOptions...)
		if err != nil {
			return nil, fmt.Errorf("create OTLP/HTTP trace exporter: %w", err)
		}
		options = append(options, sdktrace.WithBatcher(exporter))
	}
	provider := sdktrace.NewTracerProvider(options...)
	otel.SetTracerProvider(provider)
	return provider.Shutdown, nil
}
