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
import io.codenotary.immudb4j.*;
import io.codenotary.immudb4j.crypto.CryptoUtils;
import io.codenotary.immudb4j.crypto.Root;
import io.codenotary.immudb4j.crypto.VerificationException;
import io.codenotary.ledgercompliance.client.interceptor.ApiKeyInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lc.schema.Lc;
import lc.schema.LcServiceGrpc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
        ManagedChannelBuilder<?> managedChannelBuilder = ManagedChannelBuilder.forAddress(builder.getServerUrl(),
                builder.getServerPort());

        if (!builder.isUseTLS()) {
            managedChannelBuilder = managedChannelBuilder.usePlaintext();
        }

        channel = managedChannelBuilder
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

        private boolean useTLS;

        private LedgerComplianceClientBuilder() {
            this.serverUrl = "localhost";
            this.serverPort = 3322;
            this.rootHolder = new SerializableRootHolder();
            this.useTLS = true;
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

        public boolean isUseTLS() {
            return useTLS;
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

        public LedgerComplianceClientBuilder setUseTLS(boolean useTLS) {
            this.useTLS = useTLS;
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

        //noinspection ResultOfMethodCallIgnored
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
    public void setAll(KVList kvList) {
        KVList.KVListBuilder svListBuilder = KVList.newBuilder();

        for (KV kv : kvList.entries()) {
            ImmudbProto.Content content = ImmudbProto.Content.newBuilder()
                    .setTimestamp(System.currentTimeMillis() / 1000L)
                    .setPayload(ByteString.copyFrom(kv.getValue()))
                    .build();
            svListBuilder.add(kv.getKey(), content.toByteArray());
        }

        rawSetAll(svListBuilder.build());
    }

    public void rawSetAll(KVList kvList) {
        ImmudbProto.KVList.Builder builder = ImmudbProto.KVList.newBuilder();

        for (KV kv : kvList.entries()) {
            ImmudbProto.KeyValue skv =
                    ImmudbProto.KeyValue.newBuilder()
                            .setKey(ByteString.copyFrom(kv.getKey()))
                            .setValue(ByteString.copyFrom(kv.getValue()))
                            .build();

            builder.addKVs(skv);
        }

        //noinspection ResultOfMethodCallIgnored
        stub.setBatch(builder.build());
    }

    public List<KV> getAll(List<?> keyList) {
        List<KV> rawKVs = rawGetAll(keyList);
        return getKvs(rawKVs);
    }

    public List<KV> rawGetAll(List<?> keyList) {
        if (keyList == null) {
            throw new RuntimeException("Illegal argument");
        }

        if (keyList.isEmpty()) {
            return new ArrayList<>();
        }

        if (keyList.get(0) instanceof String) {
            List<byte[]> kList = new ArrayList<>(keyList.size());

            for (Object key : keyList) {
                kList.add(((String)key).getBytes(StandardCharsets.UTF_8));
            }

            return rawGetAllFrom(kList);
        }

        if (keyList.get(0) instanceof byte[]) {
            //noinspection unchecked
            return rawGetAllFrom((List<byte[]>)keyList);
        }

        throw new RuntimeException("Illegal argument");
    }

    private List<KV> rawGetAllFrom(List<byte[]> keyList) {

        ImmudbProto.KeyList.Builder builder = ImmudbProto.KeyList.newBuilder();

        for (byte[] key : keyList) {
            ImmudbProto.Key k = ImmudbProto.Key.newBuilder().setKey(ByteString.copyFrom(key)).build();
            builder.addKeys(k);
        }

        ImmudbProto.ItemList res = stub.getBatch(builder.build());

        return getKvs(res);
    }

    public List<KV> history(String key, long limit, long offset, boolean reverse) {
        return history(key.getBytes(StandardCharsets.UTF_8), limit, offset, reverse);
    }

    public List<KV> history(byte[] key, long limit, long offset, boolean reverse) {
        return convertToStructuredKVList(rawHistory(key, limit, offset, reverse));
    }


    private List<KV> convertToStructuredKVList(List<KV> rawKVs) {
        assert rawKVs != null;
        return getKvs(rawKVs);
    }

    private List<KV> getKvs(List<KV> rawKVs) {
        List<KV> kvs = new ArrayList<>(rawKVs.size());

        for (KV rawKV : rawKVs) {
            try {
                ImmudbProto.Content content = ImmudbProto.Content.parseFrom(rawKV.getValue());
                kvs.add(new KVPair(rawKV.getKey(), content.getPayload().toByteArray()));
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
            }
        }
        return kvs;
    }

    public List<KV> rawHistory(byte[] key, long limit, long offset, boolean reverse) {
        ImmudbProto.HistoryOptions h = ImmudbProto.HistoryOptions.newBuilder()
                .setKey(ByteString.copyFrom(key))
                .setLimit(limit)
                .setOffset(offset)
                .setReverse(reverse)
                .build();
        ImmudbProto.ItemList res = stub.history(h);

        return buildKVList(res);
    }

    private List<KV> buildKVList(ImmudbProto.ItemList itemList) {
        return getKvs(itemList);
    }

    private List<KV> getKvs(ImmudbProto.ItemList itemList) {
        List<KV> result = new ArrayList<>(itemList.getItemsCount());

        for (ImmudbProto.Item item : itemList.getItemsList()) {
            KV kv = new KVPair(item.getKey().toByteArray(), item.getValue().toByteArray());
            result.add(kv);
        }

        return result;
    }

    public List<KV> scan(String prefix, String offset, long limit, boolean reverse, boolean deep) {
        return convertToStructuredKVList(rawScan(prefix, offset, limit, reverse, deep));
    }

    public List<KV> scan(byte[] prefix, byte[] offset, long limit, boolean reverse, boolean deep) {
        return convertToStructuredKVList(rawScan(prefix, offset, limit, reverse, deep));
    }

    public List<KV> rawScan(String prefix, String offset, long limit, boolean reverse, boolean deep) {
        return rawScan(prefix.getBytes(StandardCharsets.UTF_8), offset.getBytes(StandardCharsets.UTF_8), limit, reverse, deep);
    }

    public List<KV> rawScan(byte[] prefix, byte[] offset, long limit, boolean reverse, boolean deep) {
        ImmudbProto.ScanOptions request = ImmudbProto.ScanOptions.newBuilder()
                .setPrefix(ByteString.copyFrom(prefix))
                .setOffset(ByteString.copyFrom(offset))
                .setLimit(limit)
                .setReverse(reverse)
                .setDeep(deep)
                .build();

        ImmudbProto.ItemList res = stub.scan(request);
        return buildKVList(res);
    }

    public List<KV> zScan(String set, String offset, long limit, boolean reverse) {
        return zScan(set.getBytes(StandardCharsets.UTF_8), offset.getBytes(StandardCharsets.UTF_8), limit, reverse);
    }

    public List<KV> zScan(byte[] set, byte[] offset, long limit, boolean reverse) {
        return convertToStructuredKVList(rawZScan(set, offset, limit, reverse));
    }

    public List<KV> rawZScan(byte[] set, byte[] offset, long limit, boolean reverse) {
        ImmudbProto.ZScanOptions request = ImmudbProto.ZScanOptions.newBuilder()
                .setSet(ByteString.copyFrom(set))
                .setOffset(ByteString.copyFrom(offset))
                .setLimit(limit)
                .setReverse(reverse)
                .build();

        ImmudbProto.ZItemList res = stub.zScan(request);

        return buildKVList(res);
    }

    private List<KV> buildKVList(ImmudbProto.ZItemList zItemList) {
        List<KV> result = new ArrayList<>(zItemList.getItemsCount());

        for (ImmudbProto.ZItem zItem : zItemList.getItemsList()) {
            KV kv = new KVPair(zItem.getItem().getKey().toByteArray(), zItem.getItem().getValue().toByteArray());
            result.add(kv);
        }

        return result;
    }

    public void zAdd(String set, String key, double score) {
        ImmudbProto.Score scoreObject = ImmudbProto.Score.newBuilder()
                .setScore(score)
                .build();

        ImmudbProto.ZAddOptions options = ImmudbProto.ZAddOptions.newBuilder()
                .setSet(ByteString.copyFrom(set, StandardCharsets.UTF_8))
                .setScore(scoreObject)
                .setKey(ByteString.copyFrom(key, StandardCharsets.UTF_8))
                .build();
        //noinspection ResultOfMethodCallIgnored
        stub.zAdd(options);
    }

    public void zAdd(String set, String key, double score, long index) {
        ImmudbProto.Score scoreObject = ImmudbProto.Score.newBuilder()
                .setScore(score)
                .build();

        ImmudbProto.ZAddOptions options = ImmudbProto.ZAddOptions.newBuilder()
                .setSet(ByteString.copyFrom(set, StandardCharsets.UTF_8))
                .setScore(scoreObject)
                .setKey(ByteString.copyFrom(key, StandardCharsets.UTF_8))
                .setIndex(ImmudbProto.Index.newBuilder()
                        .setIndex(index)
                        .build())
                .build();
        //noinspection ResultOfMethodCallIgnored
        stub.zAdd(options);
    }

    public void reportTamper(byte[] key, long index, ImmudbProto.Signature signature) {
        Lc.ReportOptions options = Lc.ReportOptions.newBuilder()
                .setPayload(Lc.TamperReport.newBuilder()
                .setKey(ByteString.copyFrom(key))
                .setIndex(index)
                .setRoot(ByteString.copyFrom(getRoot().getDigest()))
                .build()
                )
                .setSignature(signature)
                .build();
        //noinspection ResultOfMethodCallIgnored
        stub.reportTamper(options);
    }
}
