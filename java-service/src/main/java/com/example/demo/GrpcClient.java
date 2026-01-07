package com.example.demo;

import com.example.demo.proto.EchoRequest;
import com.example.demo.proto.EchoResponse;
import com.example.demo.proto.PythonServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GrpcClient {

  // BlockingStub means the call waits for a response (Synchronous)
  private final PythonServiceGrpc.PythonServiceBlockingStub stub;
  private final ManagedChannel channel;

  public GrpcClient(
      @Value("${python.service.host}") String host,
      @Value("${python.service.port}") int port) {
    // Creates a connection channel to the Python service
    // ManagedChannelBuilder.forAddress uses the HOSTNAME (dns) to find the service
    this.channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext() // No SSL for internal private network
        .build();
    this.stub = PythonServiceGrpc.newBlockingStub(channel);
  }

  public String callPython(String msg) {
    EchoResponse resp = stub.pythonEcho(
        EchoRequest.newBuilder().setMessage(msg).build());
    return resp.getMessage();
  }

  @PreDestroy
  public void shutdown() {
    if (channel != null) {
      channel.shutdown();
    }
  }
}
