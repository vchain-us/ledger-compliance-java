/*
Copyright 2019-2020 vChain, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

	http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package io.codenotary.ledgercompliance.client;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.InvalidProtocolBufferException;
import io.codenotary.immudb.ImmudbProto;
import io.codenotary.immudb4j.RootHolder;
import io.codenotary.immudb4j.SerializableRootHolder;
import io.codenotary.immudb4j.crypto.CryptoUtils;
import io.codenotary.immudb4j.crypto.Root;
import io.codenotary.immudb4j.crypto.VerificationException;
import io.codenotary.ledgercompliance.client.interceptor.ApiKeyInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lc.schema.LcServiceGrpc;

import java.nio.charset.StandardCharsets;

/**
 * CodeNotary Ledger Compliance client using grpc.
 *
 * @author Giacomo Russo
 */
public class LedgerComplianceClient {
    private ManagedChannel channel;
    private final LcServiceGrpc.LcServiceBlockingStub stub;
    private final String apiKey;
    private final RootHolder rootHolder;

    public LedgerComplianceClient(LedgerComplianceClientBuilder builder) {
        this.apiKey = builder.getApiKey();
        this.stub = createStubFrom(builder);
        this.rootHolder = builder.getRootHolder();
    }

    public static LedgerComplianceClient.LedgerComplianceClientBuilder newBuilder() {
        return new LedgerComplianceClient.LedgerComplianceClientBuilder();
    }

    private LcServiceGrpc.LcServiceBlockingStub createStubFrom(LedgerComplianceClientBuilder builder) {
        channel =
                ManagedChannelBuilder.forAddress(builder.getServerUrl(), builder.getServerPort())
                        .usePlaintext()
                        .intercept(new ApiKeyInterceptor(apiKey))
                        .build();
        return LcServiceGrpc.newBlockingStub(channel);
    }

    public synchronized void shutdown() {
        channel.shutdown();
        channel = null;
    }

    public synchronized boolean isShutdown() {
        return channel == null;
    }

    public static class LedgerComplianceClientBuilder {

        private String serverUrl;

        private int serverPort;

        private String apiKey;

        private RootHolder rootHolder;

        private LedgerComplianceClientBuilder() {
            this.serverUrl = "localhost";
            this.serverPort = 3322;
            this.rootHolder = new SerializableRootHolder();
        }

        public LedgerComplianceClient build() {
            return new LedgerComplianceClient(this);
        }

        public String getServerUrl() {
            return this.serverUrl;
        }

        public int getServerPort() {
            return serverPort;
        }

        public RootHolder getRootHolder() {
            return rootHolder;
        }

        public String getApiKey() {
            return apiKey;
        }

        public LedgerComplianceClientBuilder setServerUrl(String serverUrl) {
            this.serverUrl = serverUrl;
            return this;
        }

        public LedgerComplianceClientBuilder setServerPort(int serverPort) {
            this.serverPort = serverPort;
            return this;
        }

        public LedgerComplianceClientBuilder setRootHolder(RootHolder rootHolder) {
            this.rootHolder = rootHolder;
            return this;
        }

        public LedgerComplianceClientBuilder setApiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }
    }

    public Root getRoot() {
        if (rootHolder.getRoot(apiKey) == null) {
            Empty empty = Empty.getDefaultInstance();
            ImmudbProto.Root r = stub.currentRoot(empty);
            Root root = new Root(apiKey, r.getPayload().getIndex(), r.getPayload().getRoot().toByteArray());
            rootHolder.setRoot(root);
        }
        return rootHolder.getRoot(apiKey);
    }

    public void set(String key, byte[] value) {
        set(key.getBytes(StandardCharsets.UTF_8), value);
    }

    public void set(byte[] key, byte[] value) {
        ImmudbProto.Content content = ImmudbProto.Content.newBuilder()
                .setTimestamp(System.currentTimeMillis() / 1000L)
                .setPayload(ByteString.copyFrom(value))
                .build();
        this.rawSet(key, content.toByteArray());
    }

    public byte[] get(String key) {
        return get(key.getBytes(StandardCharsets.UTF_8));
    }

    public byte[] get(byte[] key) {
        try {
            ImmudbProto.Content content = ImmudbProto.Content.parseFrom(rawGet(key));
            return content.getPayload().toByteArray();
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] safeGet(String key) throws VerificationException {
        return safeGet(key.getBytes(StandardCharsets.UTF_8));
    }

    public byte[] safeGet(byte[] key) throws VerificationException {
        try {
            ImmudbProto.Content content = ImmudbProto.Content.parseFrom(safeRawGet(key, this.getRoot()));
            return content.getPayload().toByteArray();
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    public void safeSet(String key, byte[] value) throws VerificationException {
        safeSet(key.getBytes(StandardCharsets.UTF_8), value);
    }

    public void safeSet(byte[] key, byte[] value) throws VerificationException {
        ImmudbProto.Content content = ImmudbProto.Content.newBuilder()
                .setTimestamp(System.currentTimeMillis() / 1000L)
                .setPayload(ByteString.copyFrom(value))
                .build();

        safeRawSet(key, content.toByteArray(), this.getRoot());
    }

    public void rawSet(String key, byte[] value) {
        rawSet(key.getBytes(StandardCharsets.UTF_8), value);
    }

    public void rawSet(byte[] key, byte[] value) {
        ImmudbProto.KeyValue kv =
                ImmudbProto.KeyValue.newBuilder()
                        .setKey(ByteString.copyFrom(key))
                        .setValue(ByteString.copyFrom(value))
                        .build();

        stub.set(kv);
    }

    public byte[] rawGet(String key) {
        return rawGet(key.getBytes(StandardCharsets.UTF_8));
    }

    public byte[] rawGet(byte[] key) {
        ImmudbProto.Key k = ImmudbProto.Key.newBuilder().setKey(ByteString.copyFrom(key)).build();

        ImmudbProto.Item item = stub.get(k);
        return item.getValue().toByteArray();
    }

    public byte[] safeRawGet(String key) throws VerificationException {
        return safeRawGet(key.getBytes(StandardCharsets.UTF_8));
    }

    public byte[] safeRawGet(byte[] key) throws VerificationException {
        return safeRawGet(key, this.getRoot());
    }

    public byte[] safeRawGet(byte[] key, Root root) throws VerificationException {
        ImmudbProto.Index index = ImmudbProto.Index.newBuilder().setIndex(root.getIndex()).build();

        ImmudbProto.SafeGetOptions sOpts =
                ImmudbProto.SafeGetOptions.newBuilder()
                        .setKey(ByteString.copyFrom(key))
                        .setRootIndex(index)
                        .build();

        ImmudbProto.SafeItem safeItem = stub.safeGet(sOpts);

        ImmudbProto.Proof proof = safeItem.getProof();

        CryptoUtils.verify(proof, safeItem.getItem(), root);

        rootHolder.setRoot(new Root(apiKey, proof.getAt(), proof.getRoot().toByteArray()));

        return safeItem.getItem().getValue().toByteArray();
    }

    public void safeRawSet(String key, byte[] value) throws VerificationException {
        safeRawSet(key.getBytes(StandardCharsets.UTF_8), value);
    }

    public void safeRawSet(byte[] key, byte[] value) throws VerificationException {
        safeRawSet(key, value, this.getRoot());
    }

    public void safeRawSet(byte[] key, byte[] value, Root root) throws VerificationException {
        ImmudbProto.KeyValue kv =
                ImmudbProto.KeyValue.newBuilder()
                        .setKey(ByteString.copyFrom(key))
                        .setValue(ByteString.copyFrom(value))
                        .build();

        ImmudbProto.SafeSetOptions sOpts =
                ImmudbProto.SafeSetOptions.newBuilder()
                        .setKv(kv)
                        .setRootIndex(ImmudbProto.Index.newBuilder().setIndex(root.getIndex()).build())
                        .build();

        ImmudbProto.Proof proof = stub.safeSet(sOpts);

        ImmudbProto.Item item =
                ImmudbProto.Item.newBuilder()
                        .setIndex(proof.getIndex())
                        .setKey(ByteString.copyFrom(key))
                        .setValue(ByteString.copyFrom(value))
                        .build();

        CryptoUtils.verify(proof, item, root);

        rootHolder.setRoot(new Root(apiKey, proof.getAt(), proof.getRoot().toByteArray()));
    }

}
