import grpc
from concurrent import futures
import os
import logging
import time
import echo_pb2, echo_pb2_grpc

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
def call_java(max_retries=10, retry_delay=2):
    java_host = os.getenv("JAVA_SERVICE_HOST", "java-service")
    java_port = 50052
    java_address = f"{java_host}:{java_port}"
    
    for attempt in range(max_retries):
        try:
            logger.info(f"Python connecting to Java at {java_address} (attempt {attempt + 1}/{max_retries})")
            channel = grpc.insecure_channel(java_address)
            stub = echo_pb2_grpc.JavaServiceStub(channel)
            resp = stub.JavaEcho(
                echo_pb2.EchoRequest(message="hello from python")
            )
            logger.info(f"Python got from Java: {resp.message}")
            channel.close()
            return
        except grpc.RpcError as e:
            if attempt < max_retries - 1:
                logger.warning(f"Failed to connect to Java (attempt {attempt + 1}/{max_retries}): {e.code()}. Retrying in {retry_delay}s...")
                time.sleep(retry_delay)
            else:
                logger.error(f"Failed to connect to Java after {max_retries} attempts: {e}")
                raise
        except Exception as e:
            if attempt < max_retries - 1:
                logger.warning(f"Error connecting to Java (attempt {attempt + 1}/{max_retries}): {e}. Retrying in {retry_delay}s...")
                time.sleep(retry_delay)
            else:
                logger.error(f"Failed to connect to Java after {max_retries} attempts: {e}")
                raise

def serve():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=5))
    echo_pb2_grpc.add_PythonServiceServicer_to_server(
        PythonService(), server
    )
    server.add_insecure_port("[::]:50051")
    server.start()
    logger.info("Python gRPC server started on port 50051")

    # call Java once at startup
    call_java()

    server.wait_for_termination()

if __name__ == "__main__":
    serve()
