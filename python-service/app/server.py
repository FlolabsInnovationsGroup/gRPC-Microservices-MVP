import grpc
from concurrent import futures
import os
import logging
import time
import echo_pb2, echo_pb2_grpc
from grpc_health.v1 import health
from grpc_health.v1 import health_pb2
from grpc_health.v1 import health_pb2_grpc

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# -------- Python gRPC Server --------
class PythonService(echo_pb2_grpc.PythonServiceServicer):
    def PythonEcho(self, request, context):
        logger.info(f"Python received: {request.message}")
        return echo_pb2.EchoResponse(
            message=f"Python says: {request.message}"
        )

# -------- Python gRPC Client (call Java) --------
def call_java():
    java_host = os.getenv("JAVA_SERVICE_HOST", "java-service")
    java_port = 50052
    java_address = f"{java_host}:{java_port}"
    
    try:
        logger.info(f"Python connecting to Java at {java_address}...")
        channel = grpc.insecure_channel(java_address)
        stub = echo_pb2_grpc.JavaServiceStub(channel)
        
        # wait_for_ready=True handles the connection retry logic automatically
        resp = stub.JavaEcho(
            echo_pb2.EchoRequest(message="hello from python"),
            wait_for_ready=True
        )
        logger.info(f"Python got from Java: {resp.message}")
        channel.close()
    except Exception as e:
        logger.error(f"Failed to connect to Java: {e}")
        # In a real app we might want to crash here, but for now we log error
        pass

def serve():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=5))
    echo_pb2_grpc.add_PythonServiceServicer_to_server(
        PythonService(), server
    )
    
    # Create a health check servicer
    health_servicer = health.HealthServicer(
        experimental_non_blocking=True,
        experimental_thread_pool=futures.ThreadPoolExecutor(max_workers=5),
    )
    health_pb2_grpc.add_HealthServicer_to_server(health_servicer, server)

    server.add_insecure_port("[::]:50051")
    server.start()
    
    # Set the status to SERVING
    health_servicer.set("", health_pb2.HealthCheckResponse.SERVING)
    
    logger.info("Python gRPC server started on port 50051")

    # call Java once at startup
    call_java()

    server.wait_for_termination()

if __name__ == "__main__":
    serve()
