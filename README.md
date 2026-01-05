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

2. **Test:**
   ```bash
   ./test.sh
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

## Deployment to AWS ECS using CloudFormation

The same Docker images work in both local and ECS environments. Only configuration changes are needed.

### Code Changes Required

**None!** The code uses environment variables for service discovery, making it environment-agnostic.

### CloudFormation Configuration Points

Assuming you have existing stacks (`stack-01-storage-iam.yaml` and `stack-02-networking.yaml`), configure the following in your ECS stack:

**Already Available (from existing stacks):**
- ✓ VPC with DNS enabled
- ✓ Public and Private Subnets
- ✓ ALB Security Group (port 443)
- ✓ ECS Security Group (basic, may need modification)
- ✓ IAM Task Role (`AppIamRole` from storage-iam stack)

**Need to Create/Modify:**
- Cloud Map namespace and services
- IAM Role(s) - For testing, one role is sufficient; for production, separate execution and task roles
- Separate security groups for Java and Python services (or modify existing)
- ECS Cluster, Task Definitions, and Services
- Application Load Balancer and Target Groups

#### 1. Import Existing Resources

Reference existing resources from previous stacks using `Fn::ImportValue`:

- **VPC**: Import `flolabs-${Env}-VpcId` from networking stack
- **Subnets**: Import `flolabs-${Env}-PrivateSubnetId` (use private subnet for ECS tasks)
- **ALB Security Group**: Import `flolabs-${Env}-AlbSecurityGroupId` (note: existing stack allows port 443, you may need to add port 80)
- **IAM Task Role**: Import `AppIamRoleArn` from storage-iam stack (for application permissions)

#### 2. Additional Security Groups

The existing `EcsSecurityGroup` in `stack-02-networking.yaml` only allows port 50051 from ALB. You need to create separate security groups or modify rules:

**Java Service Security Group** (new or modify existing):
- Inbound: Port 8080 from ALB security group (imported)
- Inbound: Port 50052 from Python service security group
- Outbound: Allow all

**Python Service Security Group** (new):
- Inbound: Port 50051 from Java service security group
- Outbound: Port 50052 to Java service security group

**Note**: Consider updating `stack-02-networking.yaml` to add port 80 to `AlbSecurityGroup` if you want HTTP access.

#### 3. IAM Roles

**For Testing/MVP (Simplified):**
You can use a single IAM role for both execution and task purposes:
- Create one `AWS::IAM::Role` with:
  - Trust policy: `ecs-tasks.amazonaws.com`
  - Attach managed policy: `AmazonECSTaskExecutionRolePolicy` (for pulling images, CloudWatch logs)
  - Add any additional permissions needed by your application
- Use this same role for both `ExecutionRoleArn` and `TaskRoleArn` in task definitions

**For Production (Recommended):**
- **Execution Role**: For ECS to pull images, write logs (attach `AmazonECSTaskExecutionRolePolicy`)
- **Task Role**: For application permissions (you can reuse `AppIamRole` from `stack-01-storage-iam.yaml` via ImportValue)

#### 4. Cloud Map Service Discovery

Create `AWS::ServiceDiscovery::PrivateDnsNamespace` and `AWS::ServiceDiscovery::Service` resources:

- **Namespace**: `flolabs.local` (use the VPC imported from networking stack)
- **Java Service Discovery**: 
  - Service name: `java-service`
  - DNS record: `java-service.flolabs.local`
- **Python Service Discovery**:
  - Service name: `python-service`
  - DNS record: `python-service.flolabs.local`

Link services to ECS services using `ServiceRegistries` in `AWS::ECS::Service`.

#### 5. Environment Variables in Task Definitions

In your `AWS::ECS::TaskDefinition` resources, set environment variables to use Cloud Map DNS names:

**Java Service Task Definition:**
- `PYTHON_SERVICE_HOST`: `python-service.flolabs.local`
- `JAVA_SERVICE_HOST`: `java-service.flolabs.local`

**Python Service Task Definition:**
- `JAVA_SERVICE_HOST`: `java-service.flolabs.local`
- `PYTHON_SERVICE_HOST`: `python-service.flolabs.local`

#### 6. Network Configuration

In `AWS::ECS::TaskDefinition`:
- Set `NetworkMode: awsvpc`
- In `AWS::ECS::Service`, use imported private subnet(s) from networking stack
- VPC DNS is already enabled in `stack-02-networking.yaml` ✓

#### 7. Container Port Mappings

In task definition `ContainerDefinitions`:

**Java Service:**
- Container port 8080 (HTTP)
- Container port 50052 (gRPC)

**Python Service:**
- Container port 50051 (gRPC)

#### 8. Application Load Balancer

Create `AWS::ElasticLoadBalancingV2::LoadBalancer`:
- Use imported public subnet(s) from networking stack
- Use imported `AlbSecurityGroup` (or update it to allow port 80)
- Create `AWS::ElasticLoadBalancingV2::TargetGroup` pointing to Java service port 8080
- Set health check path to `/ping`
- Link to Java service in `AWS::ECS::Service` using `LoadBalancers`

### Verification

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

