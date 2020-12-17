# ledger-compliance-java [![License](https://img.shields.io/github/license/vchain-us/ledger-compliance-java)](LICENSE)

[![Maven Central](https://img.shields.io/maven-central/v/io.codenotary/ledger-compliance-java.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.codenotary%22%20AND%20a:%22ledger-compliance-java%22)

## Official CodeNotary Ledger Compliance client for Java 1.8 and above

## Contents

- [Introduction](#introduction)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Supported Versions](#supported-versions)
- [Quickstart](#quickstart)
- [Step by step guide](#step-by-step-guide)
    * [Creating a Client](#creating-a-client)
    * [Traditional read and write](#traditional-read-and-write)
    * [Verified or Safe read and write](#verified-or-safe-read-and-write)
    * [Multi-key read and write](#multi-key-read-and-write)
    * [Closing the client](#closing-the-client)
- [Contributing](#contributing)

## Introduction

ledger-compliance-java implements a [grpc] Ledger Compliance client. A minimalist API is exposed for applications while
cryptographic verifications and state update protocol implementation are fully implemented by this client.
Latest validated ledger state may be kept in the local filesystem using default `FileRootHolder`,
please read [immudb research paper] for details of how immutability is ensured by [immudb], the underlying technology of
CodeNotary Ledger Compliance.

[grpc]: https://grpc.io/
[immudb research paper]: https://immudb.io/
[immudb]: https://immudb.io/

## Prerequisites

ledger-compliance-java assumes an already running CodeNotary Ledger Compliance instance. You need IP address, port 
of the Ledger Compliance grpc service. You'll also need an API key, which can be easily generated within the Ledger 
Compliance UI.

This library has been tested with the latest Java LTS (version 11, at the time of writing), but it should be supported
by versions 8 or higher.

## Installation

Just include ledger-compliance-java as a dependency in your project and immudb4j for native Java access to immudb
objects:

if using `Maven`:
```xml
    <dependency>
        <groupId>io.codenotary</groupId>
        <artifactId>ledger-compliance-java</artifactId>
        <version>0.3.0</version>
    </dependency> 
    <dependency>
        <groupId>io.codenotary</groupId>
        <artifactId>immudb4j</artifactId>
        <version>0.3.0</version>
    </dependency> 
```

if using `Gradle`:
```groovy
    compile 'io.codenotary:ledger-compliance-java:0.3.0'
    compile 'io.codenotary:immudb4j:0.3.0'
```

ledger-compliance-java is currently hosted on both [Maven Central] and [Github Packages].

[Github Packages]: https://docs.github.com/en/packages
[Maven Central]: https://search.maven.org/artifact/io.codenotary/ledger-compliance-java

### How to use ledger-compliance-java packages from Github Packages

In this case, `ledger-compliance-java Github Package repository` needs to be included with authentication.
When using maven it means to include ledger-compliance-java Github Package in your `~/.m2/settings.xml`
file. See "Configuring Apache Maven for use with GitHub Packages" and "Configuring Gradle for use with GitHub Packages" 
at [Github Packages].

## Supported Versions

ledger-compliance-java supports the latest Ledger Compliance release.

## Quickstart

A simple example is provided in the "examples" subdirectory.
Follow its `README` to build and run it.

## Step by step guide

### Creating a Client

The following code snippets shows how to create a client.

Using default configuration:
```java
    LedgerComplianceClient lcClient = LedgerComplianceClient.newBuilder().build();
```

Setting Ledger Compliance url, port and API key:
```java
    LedgerComplianceClient lcClient = LedgerComplianceClient.newBuilder()
                                .setServerUrl("localhost")
                                .setServerPort(33080)
                                .setApiKey("APIKEYHERE")
                                .build();
```

Customizing the `Root Holder`:
```java
    FileRootHolder rootHolder = FileRootHolder.newBuilder()
                                    .setRootsFolder("./my_lcapp_roots")
                                    .build();

    LedgerComplianceClient lcClient = LedgerComplianceClient.newBuilder()
                                      .withRootHolder(rootHolder)
                                      .build();
```

### Traditional read and write

Ledger Compliance provides read and write operations that behave as a traditional
key-value store i.e. no cryptographic verification is done. This operations
may be used when validations can be postponed:

```java
    client.set("k123", new byte[]{1, 2, 3});
    
    byte[] v = client.get("k123");
```

### Verified or Safe read and write

Ledger Compliance provides built-in cryptographic verification for any entry. The client
implements the mathematical validations while the application uses as a traditional
read or write operation:

```java
    try {
        client.safeSet("k123", new byte[]{1, 2, 3});
        byte[] v = client.safeGet("k123");

    } (catch VerificationException e) {
        //TODO: tampering detected!
    }
```

### Multi-key read and write

Transactional multi-key read and write operations are supported by Ledger Compliance and ledger-compliance-java.
Atomic multi-key write (all entries are persisted or none):

```java
    KVList.KVListBuilder builder = KVList.newBuilder();

    builder.add("k123", new byte[]{1, 2, 3});
    builder.add("k321", new byte[]{3, 2, 1});

    KVList kvList = builder.build();

    client.setAll(kvList);
```

Atomic multi-key read (all entries are retrieved or none):

```java
    List<String> keyList = new ArrayList<String>();

    keyList.add("k123");
    keyList.add("k321");

    List<KV> result = client.getAll(keyList);

    for (KV kv : result) {
        byte[] key = kv.getKey();
        byte[] value = kv.getValue();
        ...
    }
```

### Closing the client

To programmatically close the connection with Ledger Compliance instance, use the `shutdown` operation:
 
```java
    lcClient.shutdown();
```

Note: after a shutdown, a new client needs to be created to establish a new connection.

## Contributing

We welcome contributions. Feel free to join the team!

To report bugs or get help, use [GitHub's issues].

[GitHub's issues]: https://github.com/vchain-us/ledger-compliance-java
