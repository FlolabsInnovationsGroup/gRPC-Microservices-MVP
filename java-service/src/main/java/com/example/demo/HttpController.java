package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HttpController {

  private final GrpcClient grpcClient;

  @Autowired
  public HttpController(GrpcClient grpcClient) {
    this.grpcClient = grpcClient;
  }

  @GetMapping("/ping")
  public String ping() {
    return grpcClient.callPython("hello from http");
  }
}