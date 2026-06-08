# How `nexus-pusher` Works

This document explains the `nexus-pusher` service in detail.

It is written for someone who is still learning Spring Boot, Kotlin, RabbitMQ, MinIO/S3, and Nexus. The goal is that you can read this file and understand not only what the code does, but also why it was written this way and what can go wrong in real life.

## Big Picture

`nexus-pusher` is a small Spring Boot microservice.

Its job is:

1. Wait for a RabbitMQ message.
2. Read the message as JSON.
3. Convert the JSON into a Kotlin DTO called `S3FileMessage`.
4. Use the bucket name and object key from that message to download a file from MinIO.
5. Save that downloaded file temporarily on the local machine.
6. Upload that temporary file to a Nexus raw hosted repository.
7. Delete the temporary file afterward, even if something fails.

The main flow looks like this:

```text
RabbitMQ message
      |
      v
NexusUploadListener
      |
      v
S3DownloadService downloads from MinIO/S3
      |
      v
Temporary local file
      |
      v
NexusUploadService uploads to Nexus
      |
      v
Temporary file is deleted
```

The service does not manually poll RabbitMQ in a loop. Spring AMQP handles that. The service does not manually open HTTP sockets to MinIO or Nexus either. The AWS SDK handles S3 communication, and Spring's `RestClient` handles Nexus HTTP upload communication.

## Project Structure

The important files are under:

```text
nexus_pusher/src/main/kotlin/com/example/np
```

The implemented structure is:

```text
com/example/np
├── NexusPusherApplication.kt
├── config
│   ├── NexusConfig.kt
│   ├── NexusProperties.kt
│   ├── RabbitConfig.kt
│   ├── S3Config.kt
│   └── S3Properties.kt
├── messaging
│   ├── NexusUploadListener.kt
│   └── S3FileMessage.kt
├── nexus
│   └── NexusUploadService.kt
└── storage
    ├── DownloadedS3File.kt
    └── S3DownloadService.kt
```

The resource configuration file is:

```text
nexus_pusher/src/main/resources/application.yml
```

The Gradle dependency file is:

```text
nexus_pusher/build.gradle.kts
```

## Configuration File: `application.yml`

The application configuration is stored in YAML:

```yaml
app:
  s3:
    endpoint: http://localhost:9000
    region: us-east-1
    access-key: minioadmin
    secret-key: minioadmin
    path-style-access: true
    allowed-buckets:
      - my-local-bucket
    temp-download-dir: ${java.io.tmpdir}/nexus-pusher-downloads
  nexus:
    base-url: http://localhost:8081
    repository: nexus-pusher-raw
    username: admin
    password: password

spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

### Why YAML?

Spring Boot supports both `.properties` and `.yml`.

YAML is easier to read for grouped configuration. Here we have grouped settings for:

- `app.s3`
- `app.nexus`
- `spring.rabbitmq`

That makes the file easier to scan than a long list of flat properties.

### `app.s3`

This section contains everything needed to connect to MinIO using the AWS SDK S3 client.

MinIO behaves like S3, but it is running locally.

Important values:

- `endpoint`: `http://localhost:9000`
  - This is the MinIO S3 API endpoint.
  - This is not the MinIO console UI. The console is usually `http://localhost:9001`.

- `region`: `us-east-1`
  - AWS S3 requires a region value.
  - MinIO usually does not care much about the actual region, but the AWS SDK still expects one.

- `access-key` and `secret-key`
  - These are the credentials used by the AWS SDK.
  - For a local default MinIO setup, both are commonly `minioadmin`.

- `path-style-access`: `true`
  - This is very important for MinIO.
  - AWS S3 often uses virtual-hosted-style URLs, like:

    ```text
    http://my-bucket.s3.amazonaws.com/uploads/example.txt
    ```

  - Local MinIO usually needs path-style URLs, like:

    ```text
    http://localhost:9000/my-local-bucket/uploads/example.txt
    ```

  - Setting path-style access avoids DNS and hostname problems on local machines.

- `allowed-buckets`
  - This is a safety feature.
  - The service only accepts downloads from buckets in this list.
  - If someone sends a RabbitMQ message with another bucket name, the service rejects it before contacting MinIO.

- `temp-download-dir`
  - This is where downloaded files are temporarily stored.
  - `${java.io.tmpdir}` is a Java system property that points to the operating system temp directory.
  - On Windows, it usually resolves to something like:

    ```text
    C:\Users\User\AppData\Local\Temp
    ```

### `app.nexus`

This section contains everything needed to upload to Nexus.

Important values:

- `base-url`: `http://localhost:8081`
  - This is the base URL of the Nexus server.

- `repository`: `nexus-pusher-raw`
  - This is the raw hosted repository where files are uploaded.
  - The code uploads to:

    ```text
    /repository/{repository}/{key}
    ```

  - For example:

    ```text
    http://localhost:8081/repository/nexus-pusher-raw/uploads/example.txt
    ```

- `username`: `admin`
- `password`: `password`
  - These are used for HTTP Basic Authentication.
  - In a real production system, these should not be committed into source control.
  - Better options for production include environment variables, Docker secrets, Kubernetes secrets, or a secret manager.

### `spring.rabbitmq`

This config tells Spring Boot how to connect to RabbitMQ.

Spring Boot autoconfigures a RabbitMQ connection factory from these values.

The listener code does not manually create a RabbitMQ connection. Spring Boot does that automatically through `spring-boot-starter-amqp`.

## Gradle Dependencies

The important dependencies in `build.gradle.kts` are:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-web")
implementation("org.springframework.boot:spring-boot-starter-amqp")
implementation("org.springframework.boot:spring-boot-starter-validation")
implementation("org.springframework.boot:spring-boot-starter-actuator")

implementation("org.jetbrains.kotlin:kotlin-reflect")
implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
implementation(platform("software.amazon.awssdk:bom:2.46.5"))
implementation("software.amazon.awssdk:s3")
```

### `spring-boot-starter-web`

This brings in Spring Web features.

In this project it is useful mainly because we use Spring's `RestClient` to upload files to Nexus over HTTP.

`RestClient` is a modern synchronous HTTP client provided by Spring Framework.

### `spring-boot-starter-amqp`

This brings in Spring AMQP and RabbitMQ support.

It enables:

- `@RabbitListener`
- RabbitMQ connection management
- listener containers
- JSON message conversion support
- queue declaration support

Without this dependency, the application would not know how to listen to RabbitMQ queues.

### `spring-boot-starter-validation`

This brings in Jakarta Bean Validation.

It enables annotations like:

```kotlin
@field:NotBlank
```

Those annotations let us describe simple validation rules on DTO fields.

### `spring-boot-starter-actuator`

Actuator adds production-style health and monitoring endpoints.

Even if the current code does not rely heavily on it yet, it is useful for microservices because you can later expose health checks, metrics, and readiness information.

### `kotlin-reflect`

Spring and Jackson need Kotlin reflection support for some Kotlin-specific features.

Kotlin classes, constructors, nullable fields, and data classes behave slightly differently from Java classes. `kotlin-reflect` helps frameworks inspect Kotlin code correctly at runtime.

### `jackson-module-kotlin`

Jackson is the JSON library used by Spring Boot.

The Kotlin module teaches Jackson how to work properly with Kotlin data classes.

For example, this JSON:

```json
{
  "bucketName": "my-local-bucket",
  "key": "uploads/example.txt"
}
```

can be converted into:

```kotlin
data class S3FileMessage(
    val bucketName: String,
    val key: String
)
```

Without the Kotlin module, Jackson can have trouble with Kotlin constructors, non-null fields, and default values.

### AWS SDK v2 S3

The dependencies:

```kotlin
implementation(platform("software.amazon.awssdk:bom:2.46.5"))
implementation("software.amazon.awssdk:s3")
```

add the AWS SDK for Java v2 S3 client.

The BOM controls the versions of AWS SDK modules. Instead of manually choosing a version for every AWS package, the BOM keeps them aligned.

The `s3` dependency provides:

- `S3Client`
- `GetObjectRequest`
- `ResponseTransformer`
- S3 exception classes like `NoSuchKeyException` and `S3Exception`

MinIO supports the S3 API, so the AWS SDK can communicate with MinIO even though MinIO is not AWS.

## Application Entry Point: `NexusPusherApplication.kt`

```kotlin
@SpringBootApplication
@EnableConfigurationProperties(S3Properties::class, NexusProperties::class)
class NexusPusherApplication

fun main(args: Array<String>) {
    runApplication<NexusPusherApplication>(*args)
}
```

### `@SpringBootApplication`

This annotation starts the Spring Boot application.

It includes several things:

- component scanning
- auto-configuration
- configuration class support

Component scanning means Spring searches the package `com.example.np` and its subpackages for Spring-managed classes, such as:

- `@Configuration`
- `@Service`
- `@Component`

That is why the package structure matters.

Because the main class is in:

```text
com.example.np
```

Spring scans:

```text
com.example.np.config
com.example.np.messaging
com.example.np.storage
com.example.np.nexus
```

### `@EnableConfigurationProperties`

This tells Spring to bind configuration from `application.yml` into Kotlin classes.

For example:

```yaml
app:
  s3:
    endpoint: http://localhost:9000
```

is bound into:

```kotlin
data class S3Properties(
    val endpoint: String,
    ...
)
```

This is cleaner and safer than injecting raw strings everywhere with `@Value`.

## S3 Configuration

### `S3Properties.kt`

```kotlin
@ConfigurationProperties(prefix = "app.s3")
data class S3Properties(
    val endpoint: String,
    val region: String,
    val accessKey: String,
    val secretKey: String,
    val pathStyleAccess: Boolean,
    val allowedBuckets: List<String>,
    val tempDownloadDir: String
)
```

This class represents the `app.s3` section of `application.yml`.

For example:

```yaml
path-style-access: true
```

maps to:

```kotlin
val pathStyleAccess: Boolean
```

Spring Boot automatically converts kebab-case YAML names into camelCase Kotlin property names.

### Why use a properties class?

Using `S3Properties` gives several benefits:

- all S3 settings are grouped together
- services receive one typed object instead of many strings
- config names are checked at startup
- the code becomes easier to test
- the meaning of each setting is clear

### `S3Config.kt`

```kotlin
@Configuration
class S3Config {

    @Bean
    fun s3Client(properties: S3Properties): S3Client =
        S3Client.builder()
            .endpointOverride(URI.create(properties.endpoint))
            .region(Region.of(properties.region))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKey, properties.secretKey)
                )
            )
            .serviceConfiguration(
                S3Configuration.builder()
                    .pathStyleAccessEnabled(properties.pathStyleAccess)
                    .build()
            )
            .build()
}
```

This class creates an `S3Client` bean.

A Spring bean is an object managed by Spring. Other classes can ask for it through constructor injection.

For example, `S3DownloadService` has:

```kotlin
class S3DownloadService(
    private val s3Client: S3Client,
    private val properties: S3Properties
)
```

Spring sees that constructor and provides the `S3Client` bean automatically.

### Why create the S3 client in a config class?

This keeps infrastructure setup separate from business logic.

`S3DownloadService` should focus on downloading files. It should not also know how to build credentials, set the endpoint, set the region, and configure path-style access.

This separation makes the code easier to read and maintain.

### How the AWS SDK talks to MinIO

The AWS SDK sends HTTP requests using the S3 API.

When we call:

```kotlin
s3Client.getObject(...)
```

the SDK builds an HTTP request roughly like:

```text
GET http://localhost:9000/my-local-bucket/uploads/example.txt
Authorization: AWS4-HMAC-SHA256 ...
```

The SDK handles:

- request signing
- authentication headers
- HTTP communication
- response parsing
- mapping S3 errors into exception classes

MinIO receives this request, checks the credentials, finds the object, and returns the file bytes.

## RabbitMQ Configuration

### `RabbitConfig.kt`

```kotlin
@Configuration
class RabbitConfig {

    @Bean
    fun nexusUploadQueue(): Queue = Queue(NEXUS_UPLOAD_QUEUE, true)

    @Bean
    fun jsonMessageConverter(): Jackson2JsonMessageConverter {
        val objectMapper = ObjectMapper()
            .registerKotlinModule()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        return Jackson2JsonMessageConverter(objectMapper)
    }

    @Bean
    fun rabbitListenerContainerFactory(
        connectionFactory: ConnectionFactory,
        jsonMessageConverter: Jackson2JsonMessageConverter
    ): SimpleRabbitListenerContainerFactory {
        val factory = SimpleRabbitListenerContainerFactory()
        factory.setConnectionFactory(connectionFactory)
        factory.setMessageConverter(jsonMessageConverter)
        return factory
    }

    companion object {
        const val NEXUS_UPLOAD_QUEUE = "nexus.upload.queue"
    }
}
```

### Queue Bean

```kotlin
fun nexusUploadQueue(): Queue = Queue(NEXUS_UPLOAD_QUEUE, true)
```

This declares a durable RabbitMQ queue named:

```text
nexus.upload.queue
```

Durable means the queue definition survives a RabbitMQ restart, assuming RabbitMQ persistence is configured normally.

This does not mean every message is automatically persisted. Message durability also depends on how messages are published.

### Why define the queue in code?

Defining the queue in code helps local development.

When the app starts, Spring AMQP can declare the queue if it does not exist.

That reduces manual setup and makes the service easier to run.

### JSON Message Converter

RabbitMQ messages are just bytes plus metadata.

When a message body contains JSON, Spring needs to know how to turn those bytes into a Kotlin object.

The converter:

```kotlin
Jackson2JsonMessageConverter
```

uses Jackson to convert JSON into `S3FileMessage`.

The custom `ObjectMapper`:

```kotlin
ObjectMapper()
    .registerKotlinModule()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
```

does two important things:

1. `registerKotlinModule()`
   - Makes Jackson understand Kotlin data classes.

2. `FAIL_ON_UNKNOWN_PROPERTIES = false`
   - Allows messages with extra fields.
   - For example, this still works:

     ```json
     {
       "bucketName": "my-local-bucket",
       "key": "uploads/example.txt",
       "extraField": "ignored"
     }
     ```

### Listener Container Factory

The `rabbitListenerContainerFactory` tells Spring how Rabbit listeners should behave.

It provides:

- the RabbitMQ connection
- the message converter

When Spring sees:

```kotlin
@RabbitListener(queues = [RabbitConfig.NEXUS_UPLOAD_QUEUE])
```

it creates a listener container.

The listener container is a background component that:

- connects to RabbitMQ
- waits for messages
- receives messages from the queue
- converts the message body
- calls your Kotlin method
- acknowledges or rejects the message depending on success/failure

This is why the application does not need manual threading or polling code.

## Message DTO

### `S3FileMessage.kt`

```kotlin
data class S3FileMessage(
    @field:NotBlank
    val bucketName: String,

    @field:NotBlank
    val key: String
)
```

This class describes the expected RabbitMQ message body.

Expected JSON:

```json
{
  "bucketName": "my-local-bucket",
  "key": "uploads/example.txt"
}
```

### Why use a DTO?

DTO means Data Transfer Object.

It is a simple object used to move data between systems.

Here it represents the message contract between whoever publishes RabbitMQ messages and this service.

Using a DTO is better than manually reading JSON fields from a map because:

- the expected structure is clear
- validation is easier
- code completion works
- mistakes are caught earlier

### Why `@field:NotBlank`?

Kotlin properties can generate several Java-level targets:

- constructor parameter
- field
- getter

Bean Validation usually checks fields/getters. `@field:NotBlank` tells Kotlin to put the annotation on the generated field.

`NotBlank` means:

- not null
- not empty
- not only whitespace

## Downloaded File Model

### `DownloadedS3File.kt`

```kotlin
data class DownloadedS3File(
    val bucketName: String,
    val key: String,
    val localPath: Path,
    val contentLength: Long?,
    val contentType: String?,
    val eTag: String?
)
```

This object represents a file after it has been downloaded from S3/MinIO.

It contains:

- the original bucket name
- the original key
- the local temp file path
- S3 metadata

### Why return a model instead of only a `Path`?

Returning only a `Path` would tell the caller where the file is, but it would lose useful context.

Nexus upload needs the original key so it can upload to the same path.

Logs are better when they include content length, content type, and ETag.

Returning `DownloadedS3File` keeps all related information together.

## S3 Download Service

### `S3DownloadService.kt`

This service is responsible for downloading an object from MinIO/S3 to a local temporary file.

It exposes:

```kotlin
fun downloadToTempFile(bucketName: String, key: String): DownloadedS3File
```

### Why a service class?

This is business logic. It is more than simple configuration.

It validates input, creates directories, creates temp file names, calls S3, handles exceptions, and cleans up partial files.

Putting this in a dedicated service keeps the Rabbit listener small.

The listener coordinates workflow.

The service handles storage details.

### Step-by-step download flow

#### 1. Validate the request

```kotlin
validateRequest(bucketName, key)
```

The service checks:

- bucket name is not blank
- key is not blank
- key does not start with `/`
- key does not contain `..`
- bucket is in `allowedBuckets`, if the list is not empty

#### Why reject keys starting with `/`?

S3 keys are object names, not local file paths.

A key like:

```text
/etc/passwd
```

looks like an absolute file path.

Even though the code does not directly write using the original key as a local path, rejecting suspicious keys is still a good defensive rule.

#### Why reject `..`?

`..` is commonly used for path traversal.

For example:

```text
../../somewhere/secret.txt
```

Again, this code uses `Files.createTempFile` and not the original filename directly, but validating early prevents dangerous keys from spreading deeper into the system.

#### Why validate allowed buckets?

RabbitMQ is an input boundary.

If someone publishes:

```json
{
  "bucketName": "some-other-bucket",
  "key": "secret.txt"
}
```

the service should not blindly download from any bucket the message says.

The allowlist prevents accidental or malicious access to buckets this service should not handle.

#### 2. Create the temp download directory

```kotlin
val downloadDir = Path(properties.tempDownloadDir)
Files.createDirectories(downloadDir)
```

`Files.createDirectories` is safe to call even if the directory already exists.

It creates missing parent directories too.

#### 3. Create a temp filename

```kotlin
val localFile = Files.createTempFile(downloadDir, TEMP_FILE_PREFIX, extensionFromKey(key))
```

This creates a unique temporary filename, such as:

```text
s3-download-11404076237291403640.txt
```

The code preserves the extension if possible.

For key:

```text
uploads/example.txt
```

the temp file gets a `.txt` suffix.

For key:

```text
uploads/archive.tar.gz
```

the current logic preserves only `.gz`, because it extracts the substring after the last dot.

### Why use `Files.createTempFile`?

This avoids using the original S3 key as a local filename.

That is important because S3 keys can contain slashes and other characters that may not be safe or convenient as local filenames.

Instead of writing to:

```text
uploads/example.txt
```

the service writes to a safe temp path created by Java.

This avoids:

- accidental directory traversal
- filename collisions
- invalid local filenames
- overwriting user files

### Important AWS SDK issue: existing temp files

There was an important problem here.

`Files.createTempFile` creates the file immediately.

But AWS SDK's:

```kotlin
ResponseTransformer.toFile(localFile)
```

expects the target file to not already exist.

If the file already exists, the SDK can throw:

```text
FileAlreadyExistsException
```

Even if MinIO returns `200 OK`, the SDK can fail locally while trying to write the response into the file.

The solution used here is:

```kotlin
val localFile = Files.createTempFile(...)

return try {
    Files.deleteIfExists(localFile)

    val response = s3Client.getObject(..., ResponseTransformer.toFile(localFile))
    ...
}
```

This keeps the benefit of generating a safe unique filename, then deletes the empty placeholder so the AWS SDK can create the file itself.

### 4. Download from S3/MinIO

```kotlin
val response = s3Client.getObject(
    GetObjectRequest.builder()
        .bucket(bucketName)
        .key(key)
        .build(),
    ResponseTransformer.toFile(localFile)
)
```

This tells the AWS SDK:

- bucket: `my-local-bucket`
- key: `uploads/example.txt`
- write the response body into `localFile`

The SDK handles the HTTP request and writes the response stream to disk.

The returned `response` contains metadata, such as:

- content length
- content type
- ETag

### 5. Return `DownloadedS3File`

```kotlin
DownloadedS3File(
    bucketName = bucketName,
    key = key,
    localPath = localFile,
    contentLength = response.contentLength(),
    contentType = response.contentType(),
    eTag = response.eTag()
)
```

This object is passed to the Nexus upload service.

### Exception handling in S3 download

The service handles several exception types.

#### `NoSuchKeyException`

This means the bucket exists, but the object key was not found.

The code wraps it as:

```kotlin
S3ObjectNotFoundException
```

This makes the error clearer in this application's language.

#### `S3Exception`

This represents errors returned by S3/MinIO.

Examples:

- access denied
- bucket not found
- invalid credentials
- server error

The code wraps these as:

```kotlin
S3DownloadException
```

#### `SdkClientException`

This usually means a client-side SDK problem.

Examples:

- connection refused
- timeout
- DNS problem
- response could not be written to disk

This is also wrapped as `S3DownloadException`.

#### `IOException`

This represents local file system problems.

Examples:

- cannot create temp directory
- cannot write file
- disk full
- permission denied

This is wrapped as `S3DownloadException`.

#### Partial file cleanup

If the download fails after the temp file path has been chosen, the service calls:

```kotlin
deletePartialFile(localFile)
```

This prevents half-written files from accumulating in the temp directory.

## Nexus Configuration

### `NexusProperties.kt`

```kotlin
@ConfigurationProperties(prefix = "app.nexus")
data class NexusProperties(
    val baseUrl: String,
    val repository: String,
    val username: String,
    val password: String
)
```

This maps the `app.nexus` YAML config into a Kotlin object.

### `NexusConfig.kt`

```kotlin
@Configuration
class NexusConfig {

    @Bean
    fun nexusRestClient(builder: RestClient.Builder, properties: NexusProperties): RestClient =
        builder
            .baseUrl(properties.baseUrl.trimEnd('/'))
            .defaultHeaders { headers ->
                headers.setBasicAuth(properties.username, properties.password)
            }
            .build()
}
```

This creates a Spring `RestClient` bean configured for Nexus.

### What is `RestClient`?

`RestClient` is a Spring HTTP client.

It lets the application make HTTP requests like:

- GET
- POST
- PUT
- DELETE

In this project we use it to send a `PUT` request to Nexus.

### Why use `RestClient`?

Nexus raw repositories accept normal HTTP uploads.

For a raw hosted repository, uploading can be done with:

```text
PUT /repository/{repository-name}/{path-inside-repo}
```

So we do not need a special Nexus Java SDK.

`RestClient` is enough because Nexus speaks HTTP.

### Basic Authentication

The config sets:

```kotlin
headers.setBasicAuth(properties.username, properties.password)
```

This causes every Nexus request made by this client to include an HTTP Authorization header.

The header looks conceptually like:

```text
Authorization: Basic base64(username:password)
```

Spring builds this header for us.

## Nexus Upload Service

### `NexusUploadService.kt`

This service uploads a downloaded file into Nexus.

It exposes:

```kotlin
fun upload(downloadedFile: DownloadedS3File): NexusUploadResult
```

### Why a separate Nexus service?

It keeps the code clean.

The Rabbit listener should not know:

- how to build a Nexus URL
- how to set content type
- how to perform a PUT request
- how to interpret Nexus errors

The listener only coordinates:

```text
download file -> upload file -> cleanup
```

The upload details belong in `NexusUploadService`.

### Step-by-step Nexus upload flow

#### 1. Validate the key

```kotlin
validateKey(downloadedFile.key)
```

The key must:

- not be blank
- not start with `/`
- not contain `..`

This protects the Nexus path in the same way the S3 download path is protected.

#### 2. Build the Nexus upload URI

```kotlin
val uri = UriComponentsBuilder.fromPath("/")
    .pathSegment("repository", properties.repository)
    .pathSegment(*downloadedFile.key.split('/').filter { it.isNotBlank() }.toTypedArray())
    .build()
    .toUriString()
```

If:

```text
repository = nexus-pusher-raw
key = uploads/example.txt
```

then the final URI path becomes:

```text
/repository/nexus-pusher-raw/uploads/example.txt
```

Combined with:

```text
base-url = http://localhost:8081
```

the full upload URL is:

```text
http://localhost:8081/repository/nexus-pusher-raw/uploads/example.txt
```

### Why use `UriComponentsBuilder`?

It safely builds URI paths.

Instead of manually concatenating strings like:

```kotlin
"/repository/" + repository + "/" + key
```

the builder understands path segments.

This helps avoid bugs with missing slashes, double slashes, and characters that need escaping.

### 3. Upload with HTTP PUT

```kotlin
val response = nexusRestClient.put()
    .uri(uri)
    .contentType(mediaType(downloadedFile.contentType))
    .body(FileSystemResource(downloadedFile.localPath))
    .retrieve()
    .toBodilessEntity()
```

This sends the local file to Nexus.

Line by line:

- `put()`
  - Use HTTP PUT.
  - Raw Nexus uploads commonly use PUT.

- `.uri(uri)`
  - Send to `/repository/nexus-pusher-raw/uploads/example.txt`.

- `.contentType(...)`
  - Send the content type from S3 if available.
  - Otherwise use `application/octet-stream`.

- `.body(FileSystemResource(downloadedFile.localPath))`
  - Stream the file from disk as the request body.

- `.retrieve()`
  - Execute the request and prepare to read the response.

- `.toBodilessEntity()`
  - We only care about the status code, not the response body.

### Why `FileSystemResource`?

Spring knows how to upload a file from disk when the body is a `FileSystemResource`.

It avoids manually reading the whole file into memory.

That matters because files might be large.

Reading the entire file into a `ByteArray` would be simple, but it could waste memory or crash for big files.

Using a resource lets Spring stream the file more efficiently.

### Content type handling

The code uses:

```kotlin
private fun mediaType(contentType: String?): MediaType =
    contentType
        ?.takeIf { it.isNotBlank() }
        ?.let(MediaType::parseMediaType)
        ?: MediaType.APPLICATION_OCTET_STREAM
```

If S3 says the object is:

```text
text/plain
```

then Nexus receives:

```text
Content-Type: text/plain
```

If S3 does not provide a content type, the code uses:

```text
application/octet-stream
```

That is a generic binary file content type.

### Upload result

After a successful upload, the service returns:

```kotlin
data class NexusUploadResult(
    val repository: String,
    val key: String,
    val statusCode: Int
)
```

This gives the listener useful logging information.

### Nexus upload exceptions

#### `RestClientResponseException`

This means Nexus responded with an HTTP error status.

Examples:

- `401 Unauthorized`
  - username/password wrong

- `403 Forbidden`
  - user does not have permission

- `404 Not Found`
  - repository does not exist or URL is wrong

- `409 Conflict`
  - repository write policy may reject overwrites

The code wraps these in:

```kotlin
NexusUploadException
```

and includes:

- repository name
- key
- status code
- response body

This makes debugging much easier.

#### `ResourceAccessException`

This usually means the client could not connect to Nexus.

Examples:

- Nexus is not running
- wrong port
- firewall issue
- connection refused

This is also wrapped in `NexusUploadException`.

## Rabbit Listener Workflow

### `NexusUploadListener.kt`

```kotlin
@Component
@Validated
class NexusUploadListener(
    private val s3DownloadService: S3DownloadService,
    private val nexusUploadService: NexusUploadService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @RabbitListener(queues = [RabbitConfig.NEXUS_UPLOAD_QUEUE])
    fun handle(@Valid message: S3FileMessage) {
        var downloadedFile: DownloadedS3File? = null

        try {
            logger.info("Received Nexus upload message for bucket={} key={}", message.bucketName, message.key)

            downloadedFile = s3DownloadService.downloadToTempFile(message.bucketName, message.key)

            logger.info(
                "Downloaded object to localPath={} size={} contentType={} eTag={}",
                downloadedFile.localPath,
                downloadedFile.contentLength,
                downloadedFile.contentType,
                downloadedFile.eTag
            )

            val uploadResult = nexusUploadService.upload(downloadedFile)
            logger.info(
                "Uploaded file to Nexus repository={} key={} statusCode={}",
                uploadResult.repository,
                uploadResult.key,
                uploadResult.statusCode
            )
        } finally {
            downloadedFile?.let {
                Files.deleteIfExists(it.localPath)
                logger.info("Deleted temporary file: {}", it.localPath)
            }
        }
    }
}
```

This class is the workflow coordinator.

It receives the message, calls the services, and cleans up.

### `@Component`

This makes the class a Spring bean.

Spring discovers it during component scanning.

### Constructor injection

```kotlin
class NexusUploadListener(
    private val s3DownloadService: S3DownloadService,
    private val nexusUploadService: NexusUploadService
)
```

Spring automatically provides these dependencies.

This is called constructor injection.

It is preferred because:

- dependencies are explicit
- fields can be immutable
- the class is easier to test
- the object cannot exist without required dependencies

### `@RabbitListener`

```kotlin
@RabbitListener(queues = [RabbitConfig.NEXUS_UPLOAD_QUEUE])
```

This tells Spring:

```text
When a message arrives on nexus.upload.queue, call this method.
```

The listener method receives:

```kotlin
fun handle(@Valid message: S3FileMessage)
```

Spring AMQP:

1. receives the raw RabbitMQ message
2. reads the JSON body
3. uses Jackson to deserialize it into `S3FileMessage`
4. validates it
5. calls `handle`

### Why `downloadedFile` is nullable

```kotlin
var downloadedFile: DownloadedS3File? = null
```

This variable needs to be visible in the `finally` block.

If download fails before returning a file, there is no file to delete.

So the code checks:

```kotlin
downloadedFile?.let {
    Files.deleteIfExists(it.localPath)
}
```

The `?.let` syntax means:

```text
If downloadedFile is not null, run this block.
```

This prevents null pointer errors.

### Why cleanup is in `finally`

A `finally` block runs whether the `try` block succeeds or fails.

This is important because temp files should be deleted even when:

- upload to Nexus fails
- Nexus is down
- credentials are wrong
- Rabbit listener throws an exception

If cleanup were only after successful upload, failed uploads could leave temp files behind.

### Full runtime workflow

Here is the complete sequence:

1. User or another service publishes JSON to RabbitMQ:

   ```json
   {
     "bucketName": "my-local-bucket",
     "key": "uploads/example.txt"
   }
   ```

2. RabbitMQ stores the message in `nexus.upload.queue`.

3. Spring AMQP listener receives the message.

4. Jackson converts JSON into:

   ```kotlin
   S3FileMessage("my-local-bucket", "uploads/example.txt")
   ```

5. Bean Validation checks that fields are not blank.

6. `NexusUploadListener.handle` logs that it received the message.

7. `S3DownloadService.downloadToTempFile` validates bucket and key.

8. The temp download directory is created if needed.

9. A safe temp filename is generated.

10. The empty placeholder file is deleted so AWS SDK can write to that path.

11. AWS SDK sends a signed S3 request to MinIO.

12. MinIO returns the object bytes and metadata.

13. AWS SDK writes the object bytes to the temp file.

14. `S3DownloadService` returns a `DownloadedS3File`.

15. Listener logs the temp path and metadata.

16. `NexusUploadService.upload` validates the key.

17. `NexusUploadService` builds the Nexus upload URL.

18. Spring `RestClient` sends an authenticated HTTP PUT request to Nexus.

19. Nexus stores the file in the raw hosted repository.

20. `NexusUploadService` returns the status code.

21. Listener logs upload success.

22. `finally` deletes the temp file.

## Why This Design Was Chosen

### Small focused classes

Each class has one main job.

- `RabbitConfig`
  - RabbitMQ queue and message conversion.

- `S3Config`
  - S3 client creation.

- `NexusConfig`
  - Nexus HTTP client creation.

- `S3DownloadService`
  - Download from MinIO/S3.

- `NexusUploadService`
  - Upload to Nexus.

- `NexusUploadListener`
  - Coordinate the workflow.

This is easier to understand than putting everything in the listener.

### Configuration properties instead of hardcoded values

Values like URLs, usernames, repository names, and allowed buckets are in `application.yml`.

This means the code can stay the same while configuration changes between local development, test, and production.

### AWS SDK instead of MinIO Java client

The request specifically required AWS SDK Java v2.

This is also a good choice when you want your code to work with S3-compatible storage now and possibly real AWS S3 later.

MinIO supports the S3 API, so the AWS SDK can communicate with it.

### Raw hosted Nexus repository

A Nexus raw hosted repository is a good fit because the files are arbitrary uploaded objects.

This service is not publishing Maven artifacts, npm packages, Docker images, or NuGet packages.

It is simply pushing files to paths.

A raw repository supports that with simple HTTP PUT.

### Temporary file instead of direct streaming from S3 to Nexus

Direct streaming from S3 to Nexus might be possible, but the current design intentionally downloads to a temp file first.

Advantages:

- easier to debug
- easier to log file metadata
- matches the requested workflow
- upload retry logic can be added later
- scanning/checking the file before upload can be added later

Disadvantages:

- uses local disk
- slower than pure streaming for very large files
- requires cleanup

The cleanup is handled carefully with `finally`.

## Frequent Problems and How the Code Handles Them

### Problem: RabbitMQ is not running

Symptoms:

- app starts but logs connection errors
- listener cannot consume messages

Cause:

- RabbitMQ is not started
- wrong host or port

Relevant config:

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
```

How to fix:

- start RabbitMQ
- verify port `5672`
- check username/password

### Problem: RabbitMQ message is not valid JSON

Symptoms:

- message conversion error
- listener method is not called successfully

Bad example:

```text
{ bucketName: my-local-bucket }
```

Good example:

```json
{
  "bucketName": "my-local-bucket",
  "key": "uploads/example.txt"
}
```

How the code helps:

- `Jackson2JsonMessageConverter` expects JSON and maps it into `S3FileMessage`.

### Problem: JSON field names are wrong

Bad example:

```json
{
  "bucket": "my-local-bucket",
  "path": "uploads/example.txt"
}
```

The DTO expects:

```json
{
  "bucketName": "my-local-bucket",
  "key": "uploads/example.txt"
}
```

How the code helps:

- `S3FileMessage` clearly defines the expected contract.
- validation rejects blank values.

### Problem: Bucket is not allowed

Symptoms:

```text
bucketName is not in the allowed bucket list
```

Cause:

- message contains a bucket not listed under `allowed-buckets`

How the code helps:

- rejects unexpected buckets before contacting MinIO.

### Problem: S3 key starts with `/` or contains `..`

Symptoms:

```text
key must not start with '/' or contain '..'
```

Cause:

- suspicious or unsafe object key

How the code helps:

- prevents risky path-like values from being used.

### Problem: MinIO is not running

Symptoms:

- `SdkClientException`
- connection refused
- timeout

How the code helps:

- wraps SDK client errors as `S3DownloadException`.
- logs include bucket and key.

How to fix:

- start MinIO
- verify `http://localhost:9000`
- verify credentials

### Problem: MinIO console URL is used as S3 endpoint

Bad:

```yaml
endpoint: http://localhost:9001
```

Good:

```yaml
endpoint: http://localhost:9000
```

Port `9001` is usually the browser console.

Port `9000` is usually the S3 API.

### Problem: Path-style access is disabled

Symptoms:

- hostname or DNS errors
- bucket name appears in host instead of path

How the code avoids it:

```kotlin
S3Configuration.builder()
    .pathStyleAccessEnabled(properties.pathStyleAccess)
```

And config:

```yaml
path-style-access: true
```

### Problem: Object does not exist in MinIO

Symptoms:

- `S3ObjectNotFoundException`

Cause:

- wrong key
- file uploaded to different path
- bucket has no such object

How to fix:

- verify bucket `my-local-bucket`
- verify key `uploads/example.txt`
- remember S3 keys are case-sensitive

### Problem: Temp file already exists

This happened during development.

Cause:

- `Files.createTempFile` creates a file.
- AWS SDK `ResponseTransformer.toFile` expected the file not to exist.

How the code avoids it:

```kotlin
val localFile = Files.createTempFile(...)
Files.deleteIfExists(localFile)
s3Client.getObject(..., ResponseTransformer.toFile(localFile))
```

### Problem: Temp files are left behind

How the code avoids it:

- partial download files are deleted inside `S3DownloadService` when download fails
- successfully downloaded files are deleted in the listener's `finally` block

### Problem: Nexus repository does not exist

Symptoms:

- Nexus upload returns `404`

How to fix:

- create a raw hosted repository named:

```text
nexus-pusher-raw
```

### Problem: Nexus credentials are wrong

Symptoms:

- Nexus upload returns `401 Unauthorized`

Current config:

```yaml
username: admin
password: password
```

How to fix:

- verify the Nexus admin password
- update `application.yml`

### Problem: Nexus rejects overwrite

Symptoms:

- upload may return `400`, `403`, or `409`, depending on repository policy/version

Cause:

- raw hosted repository may have write policy `allow_once`
- file already exists at the target path

How to fix:

- delete the old asset from Nexus
- use a new key
- configure repository write policy to allow redeploy if appropriate for local testing

### Problem: File uploads with wrong content type

Cause:

- S3 object may not have content type metadata

How the code handles it:

- uses S3 content type if present
- otherwise falls back to:

```text
application/octet-stream
```

### Problem: Large files use local disk

This design downloads to disk before uploading.

For very large files, possible problems include:

- disk full
- slow processing
- temp directory fills up

How the code helps:

- temp files are deleted after processing
- partial files are deleted on failure

Future improvements:

- file size limits
- disk space checks
- direct streaming from S3 to Nexus
- retry policies
- dead-letter queues

## How Spring Connects Everything

Spring uses dependency injection.

When the application starts:

1. Spring reads `application.yml`.
2. Spring creates `S3Properties`.
3. Spring creates `NexusProperties`.
4. Spring creates `S3Client` from `S3Config`.
5. Spring creates `RestClient` from `NexusConfig`.
6. Spring creates `S3DownloadService`.
7. Spring creates `NexusUploadService`.
8. Spring creates `NexusUploadListener`.
9. Spring starts the Rabbit listener container.
10. The listener waits for RabbitMQ messages.

You do not manually instantiate these classes with `new`.

Spring does it for you.

That is why constructors can ask for dependencies directly:

```kotlin
class NexusUploadListener(
    private val s3DownloadService: S3DownloadService,
    private val nexusUploadService: NexusUploadService
)
```

## How Communication Works

### RabbitMQ communication

RabbitMQ is a message broker.

The producer sends a message to a queue.

This service consumes the message from the queue.

The message body is JSON.

Spring AMQP handles:

- connection to RabbitMQ
- consuming messages
- converting JSON to Kotlin
- calling the listener method
- message acknowledgement behavior

### MinIO/S3 communication

MinIO exposes an S3-compatible HTTP API.

The AWS SDK handles:

- building the S3 request
- signing the request with credentials
- sending HTTP to MinIO
- reading the response body
- writing the response body to a file
- translating S3 error responses into exceptions

The application only says:

```kotlin
get object from bucket/key and write it to this path
```

### Nexus communication

Nexus raw repositories accept HTTP uploads.

The app uses Spring `RestClient`.

The upload is:

```text
PUT /repository/nexus-pusher-raw/uploads/example.txt
```

The request includes:

- Basic Auth credentials
- Content-Type header
- file body

Nexus stores the file at that path inside the raw repository.

## Local Test Checklist

1. Start MinIO.
2. Open:

   ```text
   http://localhost:9001
   ```

3. Create bucket:

   ```text
   my-local-bucket
   ```

4. Upload a file to MinIO with key:

   ```text
   uploads/example.txt
   ```

5. Start RabbitMQ.

6. Start Nexus.

7. Create a raw hosted Nexus repository:

   ```text
   nexus-pusher-raw
   ```

8. Start the Spring Boot app.

9. Open RabbitMQ UI:

   ```text
   http://localhost:15672
   ```

10. Publish this JSON to queue `nexus.upload.queue`:

    ```json
    {
      "bucketName": "my-local-bucket",
      "key": "uploads/example.txt"
    }
    ```

11. Expected logs:

    ```text
    Received Nexus upload message...
    Downloading S3 object...
    Downloaded object to localPath...
    Uploading file to Nexus...
    Uploaded file to Nexus...
    Deleted temporary file...
    ```

12. Verify in Nexus that the file exists at:

    ```text
    nexus-pusher-raw/uploads/example.txt
    ```

## Important Production Notes

This project is currently configured for local development.

Before production, consider:

- move passwords out of `application.yml`
- add retry logic
- add dead-letter queue support
- add structured error handling
- add metrics
- add integration tests with Testcontainers
- add file size limits
- add upload overwrite policy decisions
- add correlation IDs for tracing
- add security around who can publish RabbitMQ messages
- avoid logging sensitive data

## Summary

The service is built around a simple and clean pipeline:

```text
RabbitMQ JSON message
    -> Kotlin DTO
    -> validate input
    -> download object from MinIO using AWS SDK S3 client
    -> store object in a safe temp file
    -> upload temp file to Nexus raw repository using HTTP PUT
    -> delete temp file
```

The important design choices are:

- Spring Boot manages wiring and configuration.
- Spring AMQP handles RabbitMQ listening.
- Jackson handles JSON deserialization.
- Jakarta Validation checks DTO fields.
- AWS SDK v2 talks to MinIO through the S3 API.
- Spring `RestClient` uploads to Nexus over HTTP.
- Temp files are created safely and cleaned up carefully.
- Config classes keep infrastructure setup separate from workflow logic.
- Small services keep the code understandable and easier to debug.

