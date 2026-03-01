# Config Server

The Config Server provides centralized external configuration management for all microservices in the Leafy application. It uses Spring Cloud Config Server with a native profile to serve configuration files from the local filesystem.

## Features

- **Centralized Configuration**: All service configurations in one place
- **Environment-specific Profiles**: Support for dev, test, prod profiles
- **Dynamic Refresh**: Configuration changes without service restart
- **Shared Configuration**: Common settings inherited by all services
- **Version Control Ready**: Configuration files can be moved to Git repository

## Configuration Files

### Location

All configuration files are stored in:

```
config-server/src/main/resources/config/
```

### Configuration Hierarchy

1. **[application.yaml](src/main/resources/config/application.yml)** - Shared configuration for all services
   - MongoDB connection
   - Kafka settings
   - Eureka client
   - Management endpoints
   - Common logging

2. **Service-specific configurations**:
   - **[api-gateway.yaml](src/main/resources/config/api-gateway.yml)** - Gateway routes, circuit breakers, Redis, JWT
   - **[auth-service.yaml](src/main/resources/config/auth-service.yml)** - Authentication, JWT, Redis, Email
   - **[user-service.yaml](src/main/resources/config/user-service.yaml)** - User management
   - **[farm-service.yaml](src/main/resources/config/farm-service.yaml)** - Farm operations
   - **[file-service.yaml](src/main/resources/config/file-service.yaml)** - File storage, upload limits
   - **[notification-service.yaml](src/main/resources/config/notification-service.yaml)** - Email, SMS, push notifications

### Configuration Priority

When a service starts, it loads configuration in this order:

1. `application.yaml` (shared defaults)
2. `{service-name}.yaml` (service-specific)
3. Environment variables (highest priority)

## Service Configuration

### Client Setup

Each microservice needs minimal `application.yaml`:

```yaml
spring:
  application:
    name: service-name
  config:
    import: optional:configserver:${SPRING_CLOUD_CONFIG_URI:http://localhost:8888}
  cloud:
    config:
      uri: ${SPRING_CLOUD_CONFIG_URI:http://localhost:8888}
      fail-fast: false
      retry:
        initial-interval: 1000
        max-attempts: 6
```

### Dependencies Required

Add to service `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-config</artifactId>
</dependency>
```

## Environment Variables

### Config Server

| Variable                               | Description        | Default                       |
| -------------------------------------- | ------------------ | ----------------------------- |
| `SERVER_PORT`                          | Config server port | 8888                          |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | Eureka URL         | http://localhost:8761/eureka/ |

### Shared Variables (All Services)

| Variable                  | Description      | Default        |
| ------------------------- | ---------------- | -------------- |
| `MONGODB_HOST`            | MongoDB host     | localhost      |
| `MONGODB_PORT`            | MongoDB port     | 27017          |
| `MONGODB_DATABASE`        | Database name    | leafy          |
| `MONGODB_USERNAME`        | MongoDB user     | admin          |
| `MONGODB_PASSWORD`        | MongoDB password | admin123       |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka brokers    | localhost:9092 |

### Service-Specific Variables

See individual service documentation for their specific environment variables.

## Running

### Local Development

```bash
# From config-server directory
mvn spring-boot:run

# Or from backend directory
mvn -pl config-server spring-boot:run
```

### Docker

```bash
# Build
docker build -t leafy-config-server:latest .

# Run
docker run -p 8888:8888 \
  -e EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://discovery-server:8761/eureka/ \
  leafy-config-server:latest
```

### Docker Compose

```bash
# From backend directory
docker-compose up config-server
```

## API Endpoints

### Get Configuration

```bash
# Get default profile configuration
curl http://localhost:8888/{service-name}/default

# Get specific profile configuration
curl http://localhost:8888/{service-name}/dev

# Examples
curl http://localhost:8888/api-gateway/default
curl http://localhost:8888/auth-service/default
```

### Health Check

```bash
curl http://localhost:8888/actuator/health
```

### Refresh Configuration

After updating configuration files:

```bash
# Refresh specific service (requires Spring Boot Actuator)
curl -X POST http://service-host:port/actuator/refresh
```

## Configuration Management

### Adding a New Service Configuration

1. Create `{service-name}.yaml` in `config/` directory:

```yaml
server:
  port: ${SERVER_PORT:8090}

# Service-specific configuration
custom:
  property: value
```

2. Update service's `application.yaml`:

```yaml
spring:
  application:
    name: service-name
  config:
    import: optional:configserver:http://localhost:8888
```

3. Restart config server to load new file

### Adding Environment-Specific Configuration

Create profile-specific files:

- `{service-name}-dev.yaml` - Development
- `{service-name}-test.yaml` - Testing
- `{service-name}-prod.yaml` - Production

Activate profile in service:

```yaml
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
```

### Updating Configuration

1. **Without Service Restart**:
   - Update configuration file
   - Call `/actuator/refresh` endpoint on the service
   - Service reloads configuration

2. **With Service Restart**:
   - Update configuration file
   - Restart the service

### Moving to Git Backend

To use Git instead of native filesystem:

1. Update `application.yaml`:

```yaml
spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/your-org/config-repo
          default-label: main
          search-paths: config
  profiles:
    active: git
```

2. Create Git repository with configuration files
3. Push configuration files to repository
4. Restart config server

## Security Considerations

### Sensitive Information

**DO NOT commit sensitive data to Git!**

Use environment variables for:

- Passwords
- API keys
- JWT secrets
- Database credentials

Example:

```yaml
jwt:
  secret: ${JWT_SECRET} # Set via environment variable
```

### Encryption

For storing encrypted values in configuration:

1. Add encryption key:

```yaml
encrypt:
  key: ${CONFIG_ENCRYPTION_KEY}
```

2. Encrypt sensitive values:

```bash
curl http://localhost:8888/encrypt -d "mysecretpassword"
```

3. Use encrypted value:

```yaml
password: "{cipher}ENCRYPTED_VALUE_HERE"
```

## Monitoring

### Health Check

```bash
curl http://localhost:8888/actuator/health
```

Expected response:

```json
{
  "status": "UP"
}
```

### Configuration Endpoints

- `/actuator/health` - Health status
- `/actuator/info` - Application info
- `/{service}/default` - Service configuration
- `/{service}/{profile}` - Profile-specific configuration

## Troubleshooting

### Service Cannot Connect to Config Server

**Problem**: Service fails to start with connection refused

**Solution**:

1. Verify config server is running: `curl http://localhost:8888/actuator/health`
2. Check `SPRING_CLOUD_CONFIG_URI` environment variable
3. Use `fail-fast: false` for graceful degradation

### Configuration Not Updating

**Problem**: Changes to config files not reflected in service

**Solution**:

1. Restart config server to reload files
2. Call `/actuator/refresh` on the service
3. Verify configuration file name matches service name

### 404 Not Found for Configuration

**Problem**: `curl http://localhost:8888/service-name/default` returns 404

**Solution**:

1. Verify file exists in `config/` directory
2. Check file name matches service name exactly
3. Restart config server

## Development Workflow

1. **Add/Update Configuration**:

   ```bash
   # Edit configuration file
   vim config-server/src/main/resources/config/service-name.yaml
   ```

2. **Test Locally**:

   ```bash
   # Start config server
   mvn -pl config-server spring-boot:run

   # Verify configuration
   curl http://localhost:8888/service-name/default
   ```

3. **Deploy Changes**:

   ```bash
   # Rebuild Docker image
   docker-compose build config-server

   # Restart services
   docker-compose up -d config-server
   ```

## Best Practices

1. **Use Environment Variables**: Never hardcode sensitive values
2. **Common Configuration**: Put shared settings in `application.yaml`
3. **Profile Management**: Use profiles for environment-specific configs
4. **Documentation**: Comment configuration properties
5. **Validation**: Test configuration changes before deployment
6. **Version Control**: Track configuration changes in Git
7. **Encryption**: Encrypt sensitive values in production

## Dependencies

Requires these services:

- Discovery Server (Eureka) - For service registration

## Contributing

When adding new services:

1. Create configuration file in `config/` directory
2. Document environment variables
3. Update this README
4. Test configuration loading
