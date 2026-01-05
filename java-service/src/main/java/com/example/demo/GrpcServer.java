package com.example.demo;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class GrpcServer {

  @Value("${grpc.server.port}")
  private int port;

  private Server server;

  @PostConstruct
  public void start() throws IOException {
    server = ServerBuilder.forPort(port)
        .addService(new JavaServiceImpl())
        .build()
        .start();

    System.out.println("Java gRPC server started on " + port);
  }

  @PreDestroy
  public void stop() {
    if (server != null) {
      server.shutdown();
    }
  }
}
