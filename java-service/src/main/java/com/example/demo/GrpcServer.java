package com.example.demo;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

import io.grpc.protobuf.services.HealthStatusManager;

@Service
public class GrpcServer {

  @Value("${grpc.server.port}")
  private int port;

  private Server server;
  private HealthStatusManager healthStatusManager;

  @PostConstruct // Runs after Spring starts
  public void start() throws IOException {
    healthStatusManager = new HealthStatusManager();

    server = ServerBuilder.forPort(port)
        .addService(new JavaServiceImpl()) // Register our implementation
        .addService(healthStatusManager.getHealthService())
        .build()
        .start();

    System.out.println("Java gRPC server started on " + port);
  }

  @PreDestroy // Runs before Spring stops
  public void stop() {
    if (healthStatusManager != null) {
      healthStatusManager.enterTerminalState();
    }
    if (server != null) {
      server.shutdown();
    }
  }
}
