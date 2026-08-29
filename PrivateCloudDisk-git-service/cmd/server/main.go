package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"privateclouddisk/git-service/internal/api"
	"privateclouddisk/git-service/internal/auth"
	"privateclouddisk/git-service/internal/config"
	"privateclouddisk/git-service/internal/events"
	"privateclouddisk/git-service/internal/gitproto"
	"privateclouddisk/git-service/internal/gitrepo"
	"privateclouddisk/git-service/internal/platform"
	"privateclouddisk/git-service/internal/sshserver"
	storageclient "privateclouddisk/git-service/internal/storage"
	"privateclouddisk/git-service/internal/store"
)

func main() {
	cfg, err := config.Load()
	if err != nil {
		log.Fatal(err)
	}
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	dataStore, err := store.Open(ctx, cfg.DatabaseDSN, cfg.MigrationPath, cfg.AutoMigrate)
	if err != nil {
		log.Fatalf("open Git database: %v", err)
	}
	defer dataStore.Close()

	platformClient := platform.New(cfg.PlatformURL, cfg.InternalServiceToken)
	objectStorage := storageclient.New(cfg.StorageURL, cfg.InternalServiceToken)
	authorizer := auth.NewAuthorizer(platformClient, dataStore)
	manager := gitrepo.New(cfg, dataStore, objectStorage)
	managementAPI := api.New(cfg, dataStore, manager, platformClient, authorizer)
	smartHTTP := gitproto.NewHTTPHandler(cfg, dataStore, manager, authorizer)
	sshService, err := sshserver.New(cfg, dataStore, manager, authorizer)
	if err != nil {
		log.Fatalf("initialize SSH service: %v", err)
	}
	publisher, err := events.NewPublisher(cfg, dataStore)
	if err != nil {
		log.Fatalf("initialize event publisher: %v", err)
	}

	root := http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		if smartHTTP.Match(request.URL.Path) {
			smartHTTP.ServeHTTP(response, request)
			return
		}
		managementAPI.ServeHTTP(response, request)
	})
	httpServer := &http.Server{
		Addr: cfg.HTTPAddr, Handler: root,
		ReadHeaderTimeout: 10 * time.Second, IdleTimeout: 2 * time.Minute,
		MaxHeaderBytes: 64 * 1024,
	}
	errorsChannel := make(chan error, 2)
	go func() {
		log.Printf("Git HTTP listening on %s", cfg.HTTPAddr)
		if err := httpServer.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			errorsChannel <- err
		}
	}()
	go func() {
		log.Printf("Git SSH listening on %s", cfg.SSHAddr)
		if err := sshService.ListenAndServe(ctx); err != nil {
			errorsChannel <- err
		}
	}()
	go publisher.Run(ctx)

	select {
	case <-ctx.Done():
	case err := <-errorsChannel:
		log.Printf("Git service stopped after error: %v", err)
		stop()
	}
	shutdownContext, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	_ = httpServer.Shutdown(shutdownContext)
	os.Exit(0)
}
