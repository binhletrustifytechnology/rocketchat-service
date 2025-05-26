# Rocket.Chat Spring Boot Integration

This project provides a Spring Boot service for integrating with Rocket.Chat. It allows you to interact with Rocket.Chat's API to perform operations like sending messages, creating channels, and more.

## Features

- Authentication with Rocket.Chat API
- Channel/Room operations (create, get info, list)
- Message operations (send, retrieve)
- RESTful API endpoints to interact with Rocket.Chat

## Prerequisites

- Java 11 or higher
- Maven
- Rocket.Chat server (accessible via HTTP/HTTPS)

## Configuration

Configure the application by editing the `application.properties` file:

```properties
# Server configuration
server.port=8080

# Rocket.Chat configuration
rocketchat.api.url=http://your-rocketchat-server:3000/api/v1
rocketchat.api.user=your-username
rocketchat.api.password=your-password
```

## API Endpoints

### Authentication

- `POST /api/rocketchat/login` - Authenticate with Rocket.Chat
  - Example curl command:
    ```bash
    curl -X POST "http://localhost:8080/api/rocketchat/login"
    ```

### Channels

- `GET /api/rocketchat/channels` - Get list of public channels
  - Example curl command:
    ```bash
    curl -X GET "http://localhost:8080/api/rocketchat/channels"
    ```
- `GET /api/rocketchat/channels/{roomId}` - Get information about a specific channel
  - Example curl command:
    ```bash
    curl -X GET "http://localhost:8080/api/rocketchat/channels/ByehQjC44FwMeiLbX"
    ```
- `POST /api/rocketchat/channels` - Create a new channel
  - Parameters:
    - `name` (required) - Channel name
    - `members` (optional) - Array of usernames to add to the channel
    - `readOnly` (optional, default: false) - Whether the channel is read-only
    - `description` (optional) - Channel description
  - Example curl command:
    ```bash
    # Create a basic channel
    curl -X POST "http://localhost:8080/api/rocketchat/channels?name=general"

    # Create a channel with members
    curl -X POST "http://localhost:8080/api/rocketchat/channels?name=team-channel&members=user1&members=user2"

    # Create a read-only channel with description
    curl -X POST "http://localhost:8080/api/rocketchat/channels?name=announcements&readOnly=true&description=Company%20announcements"
    ```

### Messages

- `GET /api/rocketchat/channels/{roomId}/messages` - Get messages from a channel
  - Parameters:
    - `limit` (optional, default: 50) - Maximum number of messages to retrieve
  - Example curl command:
    ```bash
    # Get default number of messages
    curl -X GET "http://localhost:8080/api/rocketchat/channels/ByehQjC44FwMeiLbX/messages"

    # Get specific number of messages
    curl -X GET "http://localhost:8080/api/rocketchat/channels/ByehQjC44FwMeiLbX/messages?limit=10"
    ```
- `POST /api/rocketchat/channels/{roomId}/messages` - Send a message to a channel
  - Request body:
    ```json
    {
      "message": "Your message text"
    }
    ```
  - Example curl command:
    ```bash
    curl -X POST "http://localhost:8080/api/rocketchat/channels/ByehQjC44FwMeiLbX/messages" \
      -H "Content-Type: application/json" \
      -d '{"message": "Hello, this is a test message!"}'
    ```

## Building and Running

### Build the project

```bash
mvn clean package
```

### Run the application

```bash
java -jar target/rocketchat-service-0.0.1-SNAPSHOT.jar
```

Or using Maven:

```bash
mvn spring-boot:run
```

## Implementation Details

The service is built using:

- Spring Boot 2.7.x
- Spring WebFlux for reactive programming
- Project Reactor for asynchronous operations
- Lombok for reducing boilerplate code

The implementation follows a layered architecture:

1. **Controller Layer** - REST API endpoints
2. **Service Layer** - Business logic and Rocket.Chat API communication
3. **Model Layer** - Data models/DTOs for Rocket.Chat entities

## Future Improvements

- Add support for more Rocket.Chat API endpoints
- Implement caching for frequently accessed data
- Add unit and integration tests
- Add authentication and authorization for the API endpoints
- Implement rate limiting
