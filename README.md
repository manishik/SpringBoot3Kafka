# Spring Boot 3 + Kafka + OAuth 2.0

This project is a small learning application that connects a Spring Boot REST API to Apache Kafka. It can publish either a plain string or an `Employee` object, consume the records from the same Kafka topic, and expose the consumed records over HTTP.

The distinguishing feature is authentication: the Spring application and the Kafka broker are configured to use SASL/OAUTHBEARER, with Keycloak acting as the OAuth 2.0 identity provider.

## What the application does

```text
HTTP client
    |
    | POST /kafka/publishMessage or /kafka/publishObject
    v
KafkaController -> Producer -> Kafka topic: kafka3-topic
                                      |
                                      v
                                  Consumer
                                      |
                                      v
                              in-memory List<String>
                                      |
    GET /kafka/getMessages <----------+
```

Publishing and consuming are asynchronous. A successful POST means that the send was initiated; it does not guarantee that the consumer has already processed the record by the time the response is returned.

## Core concepts demonstrated

### Spring Boot auto-configuration

`@SpringBootApplication` starts the application and enables component scanning. Spring discovers the controller and services, then uses the `spring.kafka.*` properties to create the Kafka producer factory, consumer factory, `KafkaTemplate`, and listener infrastructure.

### REST controller

`KafkaController` exposes three endpoints:

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/kafka/publishMessage?message=...` | Publish a string |
| `POST` | `/kafka/publishObject` | Publish an `Employee` JSON object |
| `GET` | `/kafka/getMessages` | Return records consumed since this application instance started |

Spring converts the JSON request body for `/publishObject` into an `Employee`. Constructor injection supplies the controller with the producer and consumer services.

### Kafka producer

`Producer` uses Spring Kafka's `KafkaTemplate` abstraction and sends every record to `kafka3-topic`.

- `sendStringMessage(...)` sends a Java `String`.
- `sendObject(...)` sends an `Employee`.
- No key is supplied, so Kafka chooses a partition for each record.
- `KafkaTemplate.send(...)` is asynchronous; the current code does not inspect its completion result.

The configured producer value serializer is Spring Kafka's `JsonSerializer`. It serializes an `Employee` as JSON and also JSON-encodes string values. Consequently, a consumed string may include JSON quotation marks.

The two injected templates have different Java generic declarations, but they use the same Spring Boot Kafka configuration and the same JSON value serializer; they are not separate producer configurations.

### Kafka consumer and consumer groups

`Consumer.consume(...)` is registered by `@KafkaListener` for `kafka3-topic` and consumer group `group_id`.

Kafka consumer groups divide topic partitions among group members. If several instances of this application run with the same group ID, each record is normally handled by only one instance in the group, rather than being copied to all instances.

`auto-offset-reset=earliest` applies when this group has no committed offset for a partition. It tells Kafka to begin with the earliest retained record. It does not rewind a group that already has committed offsets.

The listener receives strings because the consumer uses `StringDeserializer`. An `Employee` record is therefore exposed as its raw JSON text instead of being reconstructed as an `Employee` object.

### Serialization and deserialization

Kafka stores byte arrays. The producer serializer converts Java values into bytes, and the consumer deserializer converts those bytes back into a Java value.

```text
Employee/String -> JsonSerializer -> Kafka bytes -> StringDeserializer -> String
```

This intentionally simple setup lets one listener display both message types. A more strongly typed application would usually use separate topics or a JSON deserializer with explicit trusted types and type mappings.

### OAuth 2.0 authentication

The application uses this security flow:

1. The Kafka client uses its client ID and client secret to request an access token from Keycloak.
2. It connects to Kafka using `SASL_PLAINTEXT` and the `OAUTHBEARER` SASL mechanism.
3. Kafka retrieves Keycloak's public signing keys from the JWKS endpoint.
4. Kafka validates the token before accepting the client connection.

The broker uses the same general mechanism for its inter-broker connection. Because this is `SASL_PLAINTEXT`, authentication is enabled but network traffic is not encrypted. Use `SASL_SSL` and properly managed secrets outside a local learning environment.

### KRaft mode

The Compose configuration runs a single Confluent Kafka node in KRaft mode. KRaft stores Kafka metadata using Kafka's own controller quorum, so ZooKeeper is not required. This node acts as both broker and controller and is suitable for local development, not production resilience.

## Code map

| Path | Responsibility |
| --- | --- |
| `src/main/java/manish/learn/SpringBoot3KafkaApplication.java` | Application entry point |
| `src/main/java/manish/learn/controllers/KafkaController.java` | HTTP endpoints |
| `src/main/java/manish/learn/producers/Producer.java` | Sends records to Kafka |
| `src/main/java/manish/learn/consumers/Consumer.java` | Listens for records and stores them in memory |
| `src/main/java/manish/learn/model/Employee.java` | Request/domain model; Lombok generates accessors and `toString()` |
| `src/main/resources/application.properties` | Spring, Kafka client, serialization, and OAuth settings |
| `docker-compose.yml` | Single-node Kafka/KRaft broker |
| `infra/kafka/kafka_server_jaas.conf` | Broker-side OAuth JAAS settings |
| `src/test/java/manish/learn/SpringBoot3KafkaApplicationTests.java` | Basic application-context test |

## Prerequisites

- Java 17 or newer
- Docker with Docker Compose
- A Keycloak server reachable from:
  - the host application as `localhost:8080`
  - the Kafka container as `host.docker.internal:8080`

Keycloak is not included in `docker-compose.yml`, so it must be started separately.

## Current Keycloak configuration

The working configuration uses the `master` realm and the `manish_client`
client. The token and JWKS URLs in `application.properties`,
`docker-compose.yml`, and `infra/kafka/kafka_server_jaas.conf` all point to the
`master` realm.

The repository currently contains client secrets in source-controlled files. Treat them as development-only credentials, rotate them if they have ever protected a real environment, and load real secrets from environment variables or a secret manager.

## Running locally

After Keycloak is running with the configured `manish_client` in the `master`
realm:

> **Important:** `docker compose up` starts only the Kafka broker. It does not
> start the Spring Boot REST API. Kafka and the Spring Boot application must
> both be running before you call an endpoint on port `8090`.

1. Start Kafka from the repository root:

   ```bash
   docker compose up -d
   ```

   Confirm that the broker is running:

   ```bash
   docker compose ps
   ```

2. Start the Spring Boot application in a separate terminal:

   ```bash
   ./mvnw spring-boot:run
   ```

   Alternatively, run `SpringBoot3KafkaApplication` from the IDE. Keep this
   process running and wait for a log message confirming that Tomcat started
   on port `8090`.

3. Verify that the API is reachable:

   ```bash
   curl -i http://localhost:8090/kafka/getMessages
   ```

   A successful response is HTTP `200`; an empty JSON array (`[]`) is normal
   before any messages have been consumed.

The project does not explicitly declare `kafka3-topic`. The current Kafka client permits automatic topic creation, so the topic may be created on first use if the broker also allows it. Explicit topic provisioning is preferable outside a demo.

## Trying the API

Publish a string:

```bash
curl -X POST 'http://localhost:8090/kafka/publishMessage?message=Hello%20Kafka'
```

In Postman, select **POST**, enter
`http://localhost:8090/kafka/publishMessage`, and add a query parameter named
`message` with a value such as `Manish testing kafka`. Postman will URL-encode
the spaces. Sending this endpoint as a `GET` request returns HTTP `405` because
the controller exposes it only as a `POST` endpoint.

Publish an employee:

```bash
curl -X POST http://localhost:8090/kafka/publishObject \
  -H 'Content-Type: application/json' \
  -d '{"employeeId":101,"firstName":"Ada","lastName":"Lovelace","empSalary":125000}'
```

Read what this application instance has consumed:

```bash
curl http://localhost:8090/kafka/getMessages
```

The GET endpoint is not reading Kafka on demand. It returns the listener's local in-memory list. That list is empty after an application restart and is independent for each running application instance.

## Current limitations and useful next steps

- **The context test requires live infrastructure.** The Kafka listener requests an OAuth token while the Spring context starts. `mvn test` therefore fails when Keycloak is unavailable. A test profile that disables listener auto-startup, mocks Kafka, or uses test containers would make the test isolated and repeatable.
- **Consumed messages are kept in a mutable `ArrayList`.** The Kafka listener writes while HTTP requests may read, which is not thread-safe. The list is also unbounded and is not durable.
- **Producer results are ignored.** Returning or handling the send future would expose broker acknowledgement and errors.
- **Configuration is duplicated.** The topic and group ID appear in Java and/or properties. Externalizing them into named application properties would avoid drift.
- **Credentials are hard-coded.** Move them to environment variables and do not commit real secrets.
- **The domain model is a Spring component.** `Employee` is used as request data, so `@Component` is unnecessary; ordinary DTOs do not need to be singleton Spring beans.
- **There is no explicit error handling.** Production consumers normally define retry, backoff, and dead-letter-topic behavior.
- **The API returns raw text responses.** HTTP status codes and structured response objects would make send outcomes clearer.

## Build and test status

The source compiles during the Maven test lifecycle. The existing `contextLoads` test is an integration-style context test rather than an isolated unit test: it currently fails if the configured Keycloak token endpoint is unavailable because the Kafka listener authenticates during application startup.
