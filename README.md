# ISO 8583 Adapter Core
A high-performance, asynchronous ISO 8583 message decoder built on Java 21 and Netty. This adapter is designed for low-latency financial transaction processing, providing a seamless bridge between raw network byte streams and structured Java domain objects.

## 🚀 Features
### Asynchronous Processing: 
Built on Netty's event-driven framework for high-throughput capability.

### Modern Java: 
Leverages Java 21 features like enhanced switch expressions and Pattern Matching.

### Robust Decoding:
* Supports Primary and Secondary Bitmaps (128 fields).
* Handles FIXED, LLVAR, and LLLVAR field types.
* Strict validation of length headers and message structure.

### Dockerized: 
Multi-stage build for a minimal, production-ready JRE footprint.

### Comprehensive Testing: 
includes unit tests and full-pipeline integration tests using EmbeddedChannel.

## 🛠 Tech Stack
* Runtime: Java 21 (Eclipse Temurin)
* Network Engine: Netty 4.1.x
* Build Tool: Maven 3.9+
* Containerization: Docker (Multi-stage Alpine build)
* Testing: JUnit 5, AssertJ


## 📦 Getting Started

### Prerequisites

* JDK 21
* Maven 3.9+
* Docker (Optional)

### Build and Test
To compile the project and run the full suite of integration tests:

```
Bash
mvn clean install
```


### Docker Deployment
Build the optimized production image (approx. 150MB):

```
Bash
docker build -t iso8583-adapter .
docker run -p 8080:8080 iso8583-adapter
```

## 🔧 Usage Example
The Iso8583Decoder can be easily plugged into any Netty ChannelPipeline.

```
Java
public void initChannel(SocketChannel ch) {
    ChannelPipeline p = ch.pipeline();
    // Add frame decoder (e.g., LengthFieldBasedFrameDecoder)
    p.addLast(new Iso8583Decoder());
    p.addLast(new TransactionHandler()); // Your business logic here
}
```


### Decoded Output Structure

When a raw ISO 8583 packet is received, it is transformed into an IsoMessage object:

* MTI: e.g., 0200 (Financial Transaction Request)
* Bitmap: Automatically parsed to identify active fields.
* Fields: Accessible via a thread-safe map (e.g., message.fields().get(48)).

## 🧪 Testing Strategy
The project employs a "Shift-Left" testing approach:

### Unit Tests: 
Validate individual field parsing and registry logic.

### Integration Tests: 
Use Netty's EmbeddedChannel to simulate real-world hex-to-object pipeline flows, including error handling for malformed data and truncated packets.

## 📄 License
This project is licensed under the MIT License - see the LICENSE file for details.