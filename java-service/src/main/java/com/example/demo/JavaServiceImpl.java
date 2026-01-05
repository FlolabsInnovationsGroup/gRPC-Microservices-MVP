package com.example.demo;

import com.example.demo.proto.EchoRequest;
import com.example.demo.proto.EchoResponse;
import com.example.demo.proto.JavaServiceGrpc;
import io.grpc.stub.StreamObserver;

public class JavaServiceImpl extends JavaServiceGrpc.JavaServiceImplBase {

  @Override
  public void javaEcho(EchoRequest req, StreamObserver<EchoResponse> resp) {
    resp.onNext(
        EchoResponse.newBuilder()
            .setMessage("Java says: " + req.getMessage())
            .build());
    resp.onCompleted();
  }
}
