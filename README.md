# gRPC Microservices MVP - Java + Python

A minimal but production-ready microservice architecture validation project demonstrating bidirectional gRPC communication between Java and Python services, designed to work seamlessly in both local Docker Compose and AWS ECS Fargate environments.

## Overview

This project implements a microservice architecture with:

- **Java Service**: Public-facing HTTP entry point that also exposes a gRPC server and acts as a gRPC client
- **Python Service**: Internal-only service accessible only via gRPC, with bidirectional gRPC communication capability

The architecture is designed to validate service-to-service communication patterns that mirror AWS ECS Fargate with Cloud Map service discovery, ensuring zero code changes when moving from local development to production.

## Architecture

```
Client (curl/browser)
    |
    v
Java Service
(HTTP Server + gRPC Server + gRPC Client)
    |
    | gRPC
    v
Python Service
(gRPC Server + gRPC Client)
```

### Key Features

- **Java Service**:
  - HTTP endpoint (`/ping`) for external access
  - gRPC server on port 50052 (internal)
  - gRPC client to call Python service

- **Python Service**:
  - No HTTP exposure (internal only)
  - gRPC server on port 50051
  - gRPC client to call Java service
  - Automatic retry logic for service discovery

- **Service Discovery**:
  - Environment variable-based configuration
  - Docker Compose: uses service names (`java-service`, `python-service`)
  - ECS: uses Cloud Map DNS names (`java-service.flolabs.local`, `python-service.flolabs.local`)

## Project Structure

```
.
├── docker-compose.yml          # Local development setup
├── proto/
│   └── echo.proto              # gRPC service definitions
├── java-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/...            # Spring Boot application
│       └── resources/
│           └── application.properties
└── python-service/
    ├── Dockerfile
    ├── requirements.txt
    └── app/
        └── server.py           # Python gRPC server/client
```

## Prerequisites

- Docker Desktop (Windows/Mac/Linux)
- Docker Compose
- Java 17+ (for local Java development, optional)
- Maven (for local Java development, optional)
- Python 3.11+ (for local Python development, optional)

## Local Development

### Quick Start

1. **Build and start services:**
   ```bash
   docker-compose up --build
   ```

2. **Test HTTP endpoint:**
   ```bash
   curl http://localhost:8081/ping
   ```
   Expected response: `Python says: hello from http`

3. **Verify Python service is not externally accessible:**
   ```bash
   curl http://localhost:50051
   ```
   Expected: Connection refused (confirms Python is internal-only)

4. **View logs:**
   ```bash
   # All services
   docker-compose logs -f
   
   # Python service only
   docker-compose logs -f python-service
   
   # Java service only
   docker-compose logs -f java-service
   ```

### Expected Behavior

1. **HTTP Entry Point**: `curl http://localhost:8081/ping` returns a response from Python service via gRPC
2. **Python Internal Only**: Direct access to Python service port fails (connection refused)
3. **Bidirectional gRPC**: Python service successfully calls Java gRPC server on startup
4. **Service Discovery**: Services communicate using Docker Compose service names

### Port Configuration

| Purpose | Port |
|---------|------|
| External HTTP (local) | 8081 |
| Java internal HTTP | 8080 |
| Java gRPC | 50052 |
| Python gRPC | 50051 |

Note: Port 8081 is used locally because 8080 may be occupied. In ECS, this would typically be 80/443 via ALB.

### Stopping Services

```bash
docker-compose down
```

To also remove volumes and images:
```bash
docker-compose down -v --rmi all
```

## Deployment to AWS ECS

The same Docker images work in both local and ECS environments. Only configuration changes are needed.

### Code Changes Required

**None!** The code uses environment variables for service discovery, making it environment-agnostic.

### ECS Configuration Required

#### 1. Environment Variables

Set these environment variables in your ECS task definitions to use Cloud Map DNS names:

**Java Service Task Definition:**
```json
{
  "environment": [
    {
      "name": "PYTHON_SERVICE_HOST",
      "value": "python-service.flolabs.local"
    },
    {
      "name": "JAVA_SERVICE_HOST",
      "value": "java-service.flolabs.local"
    }
  ]
}
```

**Python Service Task Definition:**
```json
{
  "environment": [
    {
      "name": "JAVA_SERVICE_HOST",
      "value": "java-service.flolabs.local"
    },
    {
      "name": "PYTHON_SERVICE_HOST",
      "value": "python-service.flolabs.local"
    }
  ]
}
```

#### 2. AWS Cloud Map Service Discovery

Create Cloud Map services for service discovery:

**Java Service:**
- Namespace: `flolabs.local` (or your preferred namespace)
- Service name: `java-service`
- DNS record: `java-service.flolabs.local`

**Python Service:**
- Namespace: `flolabs.local`
- Service name: `python-service`
- DNS record: `python-service.flolabs.local`

#### 3. Security Groups

**Java Service Security Group:**
- **Inbound Rules:**
  - Port 8080 (HTTP): Allow from ALB security group
  - Port 50052 (gRPC): Allow from Python service security group
- **Outbound Rules:** Allow all

**Python Service Security Group:**
- **Inbound Rules:**
  - Port 50051 (gRPC): Allow from Java service security group
- **Outbound Rules:**
  - Port 50052 (gRPC): Allow to Java service security group

#### 4. Network Configuration

- Use `awsvpc` network mode (each task gets its own ENI)
- All services should be in the same VPC and subnets
- Ensure VPC DNS resolution and DNS hostnames are enabled

#### 5. Container Port Mappings

**Java Service:**
- Container port 8080 → Host port 8080 (HTTP)
- Container port 50052 → Host port 50052 (gRPC)

**Python Service:**
- Container port 50051 → Host port 50051 (gRPC)

#### 6. Application Load Balancer (Optional)

If exposing HTTP endpoint externally:
- Listener: Port 80/443
- Target Group: Java service (port 8080)
- Health Check: `/ping` endpoint

### ECS Deployment Checklist

- [ ] Create Cloud Map namespace and register services
- [ ] Configure environment variables in task definitions (Cloud Map DNS names)
- [ ] Set up security groups for inter-service communication
- [ ] Configure network mode: `awsvpc`
- [ ] Set container port mappings
- [ ] (Optional) Configure ALB for external HTTP access
- [ ] (Optional) Set up CloudWatch Logs for log aggregation
- [ ] (Optional) Configure IAM roles for service permissions

### Verification on ECS

1. **Check service discovery:**
   ```bash
   # Inside ECS task
   nslookup java-service.flolabs.local
   nslookup python-service.flolabs.local
   ```

2. **Verify environment variables:**
   ```bash
   # Inside container
   echo $JAVA_SERVICE_HOST
   echo $PYTHON_SERVICE_HOST
   ```

3. **Test HTTP endpoint:**
   ```bash
   # Via ALB or direct task IP
   curl http://<alb-dns-name>/ping
   ```

## gRPC Contract

The services communicate using Protocol Buffers defined in `proto/echo.proto`:

```protobuf
service PythonService {
  rpc PythonEcho (EchoRequest) returns (EchoResponse);
}

service JavaService {
  rpc JavaEcho (EchoRequest) returns (EchoResponse);
}

message EchoRequest {
  string message = 1;
}

message EchoResponse {
  string message = 1;
}
```

## Technology Stack

- **Java Service**: Spring Boot 3.2.3, gRPC Java 1.62.2
- **Python Service**: Python 3.11, gRPC Python
- **Communication**: gRPC (Protocol Buffers)
- **Containerization**: Docker
- **Orchestration**: Docker Compose (local), ECS Fargate (production)

## Design Decisions

1. **Environment Variable-Based Service Discovery**: Enables same code to work in Docker Compose and ECS without changes
2. **No Hardcoded IPs**: All service communication uses DNS names
3. **Retry Logic**: Python service includes retry mechanism to handle Java service startup timing
4. **Internal-Only Python Service**: Python has no HTTP exposure, enforcing proper network boundaries
5. **Bidirectional gRPC**: Demonstrates both services can call each other via gRPC

## Troubleshooting

### Python service can't connect to Java

- Check that Java gRPC server is running (check logs)
- Verify environment variables are set correctly
- Ensure services are on the same Docker network (local) or VPC (ECS)
- Check security group rules (ECS)

### Connection refused errors

- Verify port mappings in `docker-compose.yml` or ECS task definition
- Check that services are using correct service names/DNS names
- Ensure firewall/security group rules allow traffic

### Build failures

- Ensure Docker has enough resources allocated
- Check that proto files are correctly copied in Dockerfiles
- Verify Maven dependencies are available (Java service)

## Future Enhancements (Out of Scope for MVP)

- TLS/mTLS for gRPC communication
- ALB for gRPC (using gRPC-Web)
- AWS App Mesh integration
- Autoscaling configuration
- Observability and distributed tracing
- Authentication and IAM integration

## License

This is an MVP/validation project for architecture testing purposes.

