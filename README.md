# ledger-compliance-java [![License](https://img.shields.io/github/license/vchain-us/ledger-compliance-java)](LICENSE)

[![Maven Central](https://img.shields.io/maven-central/v/io.codenotary/ledger-compliance-java.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.codenotary%22%20AND%20a:%22ledger-compliance-java%22)

__The Official Java SDK (Client) for CodeNotary Ledger Compliance__

Using Java 1.8 and newer.

## Contents

- [Introduction](#introduction)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Supported Versions](#supported-versions)
- [Quickstart](#quickstart)
- [Step-by-step guide](#step-by-step-guide)
    * [Creating a Client](#creating-a-client)
    * [Standard Read and Write](#standard-read-and-write)
    * [Verified or Safe read and write](#verified-or-safe-read-and-write)
    * [Multi-key Read](#multi-key-read-and-write)
    * [Closing the Client](#closing-the-client)
- [Contributing](#contributing)

## Introduction

`ledger-compliance-java` implements a [gRPC] client for CodeNotary Ledger Compliance (CNLC). A minimalist API is exposed for applications while
cryptographic verifications and state update protocol implementation are fully implemented by this client.
Latest validated ledger state may be kept in the local filesystem using default `FileImmuStateHolder`.<br/>
Please read [immudb Research Paper] for further details and understanding how immutability is ensured by [immudb], the underlying technology of
CNLC solution.

[gRPC]: https://grpc.io/
[immudb Research Paper]: https://immudb.io/
[immudb]: https://immudb.io/

## Prerequisites

`ledger-compliance-java` assumes you have access to a CodeNotary Ledger Compliance instance.<br/>
For using it, you need the followings:
- The IP address and port where CNLC gRPC service is listening; 
- An API key, which can be easily generated within CNLC Mgmt UI.

This library has been tested with the latest Java LTS (version 11, at the time of writing), but it should be supported
by versions 8 or higher.

## Installation

Just include `ledger-compliance-java` as a dependency in your project and `immudb4j` for native Java access to immudb
objects:

- using `Maven`:
  ```xml
    <dependency>
        <groupId>io.codenotary</groupId>
        <artifactId>ledger-compliance-java</artifactId>
        <version>2.1.5.0</version>
    </dependency> 
    <dependency>
        <groupId>io.codenotary</groupId>
        <artifactId>immudb4j</artifactId>
        <version>0.9.0.4</version>
    </dependency> 
  ```
- using `Gradle`:
  ```groovy
    compile 'io.codenotary:ledger-compliance-java:2.1.5.0'
    compile 'io.codenotary:immudb4j:0.9.0.4'
  ```

Both, `ledger-compliance-java` and `immudb4j` are currently being hosted on [Maven Central] and [Github Packages].

[Github Packages]: https://docs.github.com/en/packages
[Maven Central]: https://search.maven.org/artifact/io.codenotary/ledger-compliance-java

### Using Github Packages

In this case, `ledger-compliance-java Github Package repository` needs to be included with authentication.
When using maven it means to include ledger-compliance-java Github Package in your `~/.m2/settings.xml`
file. See "Configuring Apache Maven for use with GitHub Packages" and "Configuring Gradle for use with GitHub Packages" 
at [Github Packages].

## Supported Versions

`ledger-compliance-java` supports the latest CodeNotary Ledger Compliance release.

## Quickstart

A simple example is provided in the "examples" subdirectory.
Follow its `README` to build and run it.

## Step-by-step guide

### Creating a Client

The following code snippets shows how to create a client.

Using default configuration:
```java
    LedgerComplianceClient lcClient = LedgerComplianceClient.newBuilder().build();
```

Setting the URL, Port and API key for accessing the CNLC solution:
```java
    LedgerComplianceClient lcClient = LedgerComplianceClient.newBuilder()
                                .withServerUrl("localhost")
                                .withServerPort(33080)
                                .withTLS(false)
                                .withApiKey("YOUR_API_KEY")
                                .build();
```

Customizing the `Root Holder`:
```java
    FileImmuStateHolder stateHolder = FileImmuStateHolder.newBuilder()
                                        .withStatesFolder("./my_lcapp_states")
                                        .build();

    LedgerComplianceClient lcClient = LedgerComplianceClient.newBuilder()
                                        .withStateHolder(stateHolder)
                                        .build();
```

### Standard Read and Write

Ledger Compliance provides read and write operations that behave as a standard
key-value store i.e. no cryptographic verification is done. These operations
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
        // Data Tampering Detected! That's a call for action!
    }
```

### Multi-key Read

A standard multi-key read operation is also supported by Ledger Compliance and ledger-compliance-java.

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

### Scan

You can `scan()` CNLC database _by prefix_, getting all the keys (and their values) based on a given prefix of the key:

```java
   List<KV> scanResults = client.scan(prefix);
   // or
   List<KV> scanResults = client.scan(prefix, sinceTxId, limit, reverse);
```

The method return a list of key-value pairs whose key names starts with provided `prefix`.
`sinceTxId` and `limit` are used to get only a subset (of potentially a large data set), 
and the boolean `reverse` is used for specifying the sorting.

### History

To get the history of updates that happened to a key, use `history()` method: given a key,
it returns a list of all subsequent modifications. Since each `KV` includes also the transaction id
within which the modification has been performed, further details can be retrieved based on it.

```java
   List<KV> scanResults = client.history(key, limit, offset, reverse);
```

### Closing the Client

To programmatically close the connection with Ledger Compliance instance, use the `shutdown` operation:
 
```java
    lcClient.shutdown();
```

Note: After this call, a new client instance must be created to establish a new connection.

## Contributing

We welcome contributions. Feel free to join the team!

To report bugs or get help, use [GitHub's issues].

[GitHub's issues]: https://github.com/vchain-us/ledger-compliance-java
