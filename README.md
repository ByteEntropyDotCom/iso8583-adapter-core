# ISO 8583 Adapter Core

A high-performance, asynchronous ISO 8583 message engine built on Java 21 and Netty. This adapter is designed for ultra-low latency financial transaction processing, providing a seamless bridge between raw network byte streams and structured Java domain objects.

## 🚀 Features

* Asynchronous Processing: Built on Netty's event-driven framework for high-throughput capability.

* Modern Java: Fully optimized for Java 21, leveraging Virtual Threads and Pattern Matching.
* Recursive Decoding: Supports nested sub-fields (e.g., Field 48.1, 48.2) through a configurable JSON schema.

* Robust Field Support:
  * Primary and Secondary Bitmaps (up to 128 fields).
  * FIXED, LLVAR, and LLLVAR types.
  * Configurable ASCII/BCD encoding and padding (RIGHT_ZERO/LEFT_F).

* Cloud-Native & Secure:
  * Dockerized: Multi-stage Alpine build for a minimal footprint (~150MB).
  * Secret Management: Environment variable injection for MAC keys and sensitive  
    credentials.
  * Non-Root Execution: Optimized for production security.


## 🛠 Tech Stack

Component	Technology
Runtime	Java 21 (Eclipse Temurin)
Network Engine	Netty 4.1.x
JSON Handling	Jackson (Schema Registry)
Build Tool	Maven 3.9+
Containerization	Docker & Docker Compose
CI/CD	GitHub Actions (Build & Docker)


## 📦 Getting Started

1. Prerequisites
   * JDK 21+
   * Maven 3.9+
   * Docker & Docker Compose

2. Build and Test
   Run the full suite of integration tests, including BCD length validation and recursive  
   sub-field decoding:

   ``
   mvn clean verify
   ``

 3. Local Deployment

The project includes a docker-compose.yml for rapid deployment.

Configure Environment: Create a .env file (ignored by git):

```env
   MAC_KEY=your-secure-production-key
   ADAPTER_PORT=8080
``` 

4. Launch:

```bash
   docker-compose up --build -d
```

## 🔧 Configuration

The engine behavior is governed by application.properties and a central iso-schema.json.

Schema Definition (iso-schema.json)

Fields are defined with specific encodings to ensure the decoder slices the byte stream correctly:

```json

{
  "id": 2,
  "name": "PAN",
  "type": "LLVAR",
  "encoding": "ASCII",
  "length": 19
}
```

 ## 🧪 Testing Strategy
The project employs a "Shift-Left" testing approach:

 * Unit Tests: Validate individual field parsing and Registry fallback logic.
 * Pipeline Integration: Uses Netty's EmbeddedChannel to simulate real-world hex-to-object   
   flows, verifying MTI mapping and Bitmap parsing.
 * CI/CD: GitHub Actions automatically verify that every PR maintains a valid Docker build  
   and passing test suite.
 
## 📄 License
This project is licensed under the MIT License — see the LICENSE file for details.