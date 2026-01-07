package com.example.demo;

import com.example.demo.proto.EchoRequest;
import com.example.demo.proto.EchoResponse;
import com.example.demo.proto.JavaServiceGrpc;
import io.grpc.stub.StreamObserver;

// Extends the base class generated from the .proto file
public class JavaServiceImpl extends JavaServiceGrpc.JavaServiceImplBase {

  @Override
  public void javaEcho(EchoRequest req, StreamObserver<EchoResponse> resp) {
    // 1. Process request
    String responseMsg = "Java says: " + req.getMessage();

    // 2. Build response object
    EchoResponse response = EchoResponse.newBuilder().setMessage(responseMsg).build();

    // 3. Send response back
    resp.onNext(response);
    resp.onCompleted(); // Signal stream is finished
  }
}
