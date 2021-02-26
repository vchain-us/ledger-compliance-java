/*
Copyright 2021 CodeNotary, Inc. All rights reserved.

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
import io.codenotary.immudb.ImmudbProto;
import io.codenotary.immudb4j.*;
import io.codenotary.immudb4j.crypto.CryptoUtils;
import io.codenotary.immudb4j.crypto.DualProof;
import io.codenotary.immudb4j.crypto.InclusionProof;
import io.codenotary.immudb4j.exceptions.CorruptedDataException;
import io.codenotary.immudb4j.exceptions.VerificationException;
import io.codenotary.ledgercompliance.client.interceptor.ApiKeyInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import lc.schema.Lc;
import lc.schema.LcServiceGrpc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.NoSuchElementException;

import static io.codenotary.ledgercompliance.client.LedgerComplianceExceptions.CORRUPTED_DATA;


/**
 * The Java client of CodeNotary Ledger Compliance.
 *
 * @author Giacomo Russo
 * @author Marius Ileana
 */
public class LedgerComplianceClient {

    private ManagedChannel channel;
    private final LcServiceGrpc.LcServiceBlockingStub stub;
    private final String serverId;
    private final String apiKey;
    private final ImmuStateHolder stateHolder;

    /**
     * Create a new CNLC Java Client instance based on the provided builder.
     */
    public LedgerComplianceClient(LedgerComplianceClientBuilder builder) {
        this.serverId = Base64.getEncoder()
                .encodeToString((builder.serverUrl + builder.serverPort).getBytes(StandardCharsets.UTF_8));
        this.apiKey = builder.getApiKey();
        this.stateHolder = builder.getStateHolder();
        this.stub = createStubFrom(builder);
    }

    /**
     * Get a new builder instance for creating a CNLC Java Client instance.
     */
    public static LedgerComplianceClient.LedgerComplianceClientBuilder newBuilder() {
        return new LedgerComplianceClient.LedgerComplianceClientBuilder();
    }

    private LcServiceGrpc.LcServiceBlockingStub createStubFrom(LedgerComplianceClientBuilder builder) {

        ManagedChannelBuilder<?> managedChannelBuilder = ManagedChannelBuilder
                .forAddress(builder.getServerUrl(), builder.getServerPort());

        if (!builder.isUseTLS()) {
            managedChannelBuilder = managedChannelBuilder.usePlaintext();
        }

        channel = managedChannelBuilder
                .intercept(new ApiKeyInterceptor(apiKey))
                .build();

        return LcServiceGrpc.newBlockingStub(channel);
    }

    /**
     * Shutdown the client: terminate any server connection and release resources.
     * After this call, a new client instance needs to be created, if needed.
     */
    public synchronized void shutdown() {
        channel.shutdown();
        channel = null;
    }

    /**
     * Tell if the client is shut down.
     */
    public synchronized boolean isShutdown() {
        return channel == null;
    }

    /**
     * The builder used for creating a CNLC Java Client instance.
     */
    public static class LedgerComplianceClientBuilder {

        private String serverUrl;
        private int serverPort;
        private String apiKey;
        private ImmuStateHolder stateHolder;
        private boolean useTLS;

        private LedgerComplianceClientBuilder() {
            this.serverUrl = "localhost";
            this.serverPort = 3322;
            this.stateHolder = new SerializableImmuStateHolder();
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

        public String getApiKey() {
            return apiKey;
        }

        public LedgerComplianceClientBuilder withApiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public boolean isUseTLS() {
            return useTLS;
        }

        public LedgerComplianceClientBuilder withServerUrl(String serverUrl) {
            this.serverUrl = serverUrl;
            return this;
        }

        public LedgerComplianceClientBuilder withServerPort(int serverPort) {
            this.serverPort = serverPort;
            return this;
        }

        public ImmuStateHolder getStateHolder() {
            return stateHolder;
        }

        public LedgerComplianceClientBuilder withStateHolder(ImmuStateHolder stateHolder) {
            this.stateHolder = stateHolder;
            return this;
        }

        public LedgerComplianceClientBuilder withTLS(boolean useTLS) {
            this.useTLS = useTLS;
            return this;
        }
    }

    /**
     * Get the locally saved state of the ledger.
     * If nothing exists already, it is fetched from the server and save it locally.
     */
    public ImmuState state() {
        ImmuState state = stateHolder.getState(serverId, apiKey);
        if (state == null) {
            state = currentState();
            stateHolder.setState(serverId, state);
        }
        return state;
    }

    /**
     * Get the current state that exists on the server.
     */
    public ImmuState currentState() {
        Empty empty = com.google.protobuf.Empty.getDefaultInstance();
        ImmudbProto.ImmutableState state = stub.currentState(empty);
        return new ImmuState(
                apiKey,
                state.getTxId(),
                state.getTxHash().toByteArray(),
                state.getSignature().toByteArray()
        );
    }

    //
    // ========== SET ==========
    //

    /**
     * Set the provided key and value pair into the connected ledger.
     */
    public void set(String key, byte[] value) {
        set(key.getBytes(StandardCharsets.UTF_8), value);
    }

    /**
     * Set the provided key and value pair into the connected ledger.
     */
    public void set(byte[] key, byte[] value) {
        ImmudbProto.KeyValue kv = ImmudbProto.KeyValue
                .newBuilder()
                .setKey(ByteString.copyFrom(key))
                .setValue(ByteString.copyFrom(value))
                .build();
        ImmudbProto.SetRequest req = ImmudbProto.SetRequest.newBuilder().addKVs(kv).build();
        ImmudbProto.TxMetadata txMd;
        try {
            txMd = stub.set(req);
        } catch (StatusRuntimeException e) {
            throw new RuntimeException(e.getMessage());
        }
        if (txMd.getNentries() != 2) {
            throw new RuntimeException(CORRUPTED_DATA);
        }
    }

    /**
     * @deprecated This method is deprecated and it will be removed in the next release. Please use verifiedSet instead.
     */
    public void safeRawSet(String key, byte[] value) throws VerificationException {
        safeRawSet(key.getBytes(StandardCharsets.UTF_8), value);
    }

    /**
     * @deprecated This method is deprecated and it will be removed in the next release. Please use verifiedSet instead.
     */
    public void safeRawSet(byte[] key, byte[] value) throws VerificationException {
        verifiedSet(key, value);
    }

    /**
     * Set the provided key and value pair into the connected ledger in a "safe"
     * (verified, tamper-proof detectable) manner.
     */
    public TxMetadata verifiedSet(String key, byte[] value) throws VerificationException {
        return verifiedSet(key.getBytes(StandardCharsets.UTF_8), value);
    }

    /**
     * Set the provided key and value pair into the connected ledger in a "safe"
     * (verified, tamper-proof detectable) manner.
     */
    public TxMetadata verifiedSet(byte[] key, byte[] value) throws VerificationException {

        ImmuState state = state();
        ImmudbProto.KeyValue kv = ImmudbProto.KeyValue.newBuilder()
                .setKey(ByteString.copyFrom(key)).setValue(ByteString.copyFrom(value)).build();
        ImmudbProto.VerifiableSetRequest vSetReq = ImmudbProto.VerifiableSetRequest.newBuilder()
                .setSetRequest(ImmudbProto.SetRequest.newBuilder().addKVs(kv).build())
                .setProveSinceTx(state.txId)
                .build();
        ImmudbProto.VerifiableTx vtx = stub.verifiableSet(vSetReq);
        int ne = vtx.getTx().getMetadata().getNentries();
        if (ne != 2) {
            throw new VerificationException(
                    String.format("Got back %d entries (in tx metadata) instead of 1.", ne - 1)
            );
        }
        Tx tx;
        InclusionProof inclusionProof;
        try {
            tx = Tx.valueOf(vtx.getTx());
        } catch (Exception e) {
            throw new VerificationException("Failed to extract the transaction.", e);
        }

        try {
            inclusionProof = tx.proof(CryptoUtils.encodeKey(key));
        } catch (NoSuchElementException | IllegalArgumentException e) {
            throw new VerificationException("Failed to create the inclusion proof.", e);
        }

        if (!CryptoUtils.verifyInclusion(inclusionProof, CryptoUtils.encodeKV(key, value), tx.eh())) {
            throw new VerificationException("Data is corrupted (verify inclusion failed)");
        }

        long sourceId = state.txId;
        long targetId = tx.getId();
        byte[] sourceAlh = CryptoUtils.digestFrom(state.txHash);
        byte[] targetAlh = tx.getAlh();

        if (state.txId > 0) {
            if (!CryptoUtils.verifyDualProof(
                    DualProof.valueOf(vtx.getDualProof()),
                    sourceId,
                    targetId,
                    sourceAlh,
                    targetAlh
            )) {
                throw new VerificationException("Data is corrupted (dual proof verification failed).");
            }
        }

        ImmuState newState = new ImmuState(apiKey, targetId, targetAlh, vtx.getSignature().getSignature().toByteArray());

        stateHolder.setState(serverId, newState);

        return tx.metadata();
    }


    //
    // ========== GET ==========
    //


    /**
     * Get the value of the provided key.
     */
    public byte[] get(String key) {
        return get(key.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Get the value of the provided key.
     */
    public byte[] get(byte[] key) {
        ImmudbProto.KeyRequest req = ImmudbProto.KeyRequest.newBuilder().setKey(ByteString.copyFrom(key)).build();
        ImmudbProto.Entry entry;
        try {
            entry = stub.get(req);
        } catch (StatusRuntimeException e) {
            throw new RuntimeException(e.getMessage());
        }
        return entry.getValue().toByteArray();
    }

    /**
     * @deprecated This method is deprecated and it will be removed in the next release. Please use verifiedGet instead.
     */
    public byte[] safeGet(String key) throws VerificationException {
        return safeGet(key.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @deprecated This method is deprecated and it will be removed in the next release. Please use verifiedGet instead.
     */
    public byte[] safeGet(byte[] key) throws VerificationException {
        return verifiedGet(key);
    }

    /**
     * Get the value of the provided key in a "safe" manner
     * (verified that no data tampering happened on the server).
     */
    public byte[] verifiedGet(String key) throws VerificationException {
        return verifiedGet(key.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Get the value of the provided key in a "safe" manner
     * (verified that no data tampering happened on the server).
     */
    public byte[] verifiedGet(byte[] key) throws VerificationException {

        ImmuState state = state();
        ImmudbProto.KeyRequest keyReq = ImmudbProto.KeyRequest.newBuilder()
                .setKey(ByteString.copyFrom(key))
                .build();
        return verifiedGet(keyReq, state).kv.getValue();
    }

    /**
     * Get the value of the provided key in a "safe" manner
     * (verified that no data tampering happened on the server).
     * This retrieval is considering a specific transaction id within which the KV pair has been previously set.
     */
    public byte[] verifiedGetAt(String key, long atTxId) throws VerificationException {
        return verifiedGetAt(key.getBytes(StandardCharsets.UTF_8), atTxId);
    }

    /**
     * Get the value of the provided key in a "safe" manner
     * (verified that no data tampering happened on the server).
     * This retrieval is considering a specific transaction id within which the KV pair has been previously set.
     */
    public byte[] verifiedGetAt(byte[] key, long atTxId) throws VerificationException {

        ImmuState state = state();
        ImmudbProto.KeyRequest keyReq = ImmudbProto.KeyRequest.newBuilder()
                .setKey(ByteString.copyFrom(key))
                .setAtTx(atTxId)
                .build();
        return verifiedGet(keyReq, state).kv.getValue();
    }

    /**
     * Get the value of the provided key in a "safe" manner
     * (verified that no data tampering happened on the server).
     * This retrieval is considering a specific transaction id since such a KV has been set.
     */
    public byte[] verifiedGetSince(String key, long txId) throws VerificationException {
        return verifiedGetSince(key.getBytes(StandardCharsets.UTF_8), txId);
    }

    /**
     * Get the value of the provided key in a "safe" manner
     * (verified that no data tampering happened on the server).
     * This retrieval is considering a specific transaction id since such a KV has been set.
     */
    public byte[] verifiedGetSince(byte[] key, long txId) throws VerificationException {

        ImmuState state = state();
        ImmudbProto.KeyRequest keyReq = ImmudbProto.KeyRequest.newBuilder()
                .setKey(ByteString.copyFrom(key))
                .setSinceTx(txId)
                .build();
        return verifiedGet(keyReq, state).kv.getValue();
    }

    private Entry verifiedGet(ImmudbProto.KeyRequest keyReq, ImmuState state) throws VerificationException {

        ImmudbProto.VerifiableGetRequest vGetReq = ImmudbProto.VerifiableGetRequest.newBuilder()
                .setKeyRequest(keyReq)
                .setProveSinceTx(state.txId)
                .build();
        ImmudbProto.VerifiableEntry vEntry = stub.verifiableGet(vGetReq);
        InclusionProof inclusionProof = InclusionProof.valueOf(vEntry.getInclusionProof());
        DualProof dualProof = DualProof.valueOf(vEntry.getVerifiableTx().getDualProof());

        byte[] eh;
        long sourceId, targetId;
        byte[] sourceAlh;
        byte[] targetAlh;
        long vTx;
        KV kv;

        ImmudbProto.Entry entry = vEntry.getEntry();

        if (!entry.hasReferencedBy()) {
            vTx = entry.getTx();
            kv = CryptoUtils.encodeKV(vGetReq.getKeyRequest().getKey().toByteArray(), entry.getValue().toByteArray());
        } else {
            ImmudbProto.Reference entryRefBy = entry.getReferencedBy();
            vTx = entryRefBy.getTx();
            kv = CryptoUtils.encodeReference(
                    entryRefBy.getKey().toByteArray(),
                    entry.getKey().toByteArray(),
                    entryRefBy.getAtTx());
        }

        if (state.txId <= vTx) {
            byte[] digest = vEntry.getVerifiableTx().getDualProof().getTargetTxMetadata().getEH().toByteArray();
            eh = CryptoUtils.digestFrom(digest);

            sourceId = state.txId;
            sourceAlh = CryptoUtils.digestFrom(state.txHash);
            targetId = vTx;
            targetAlh = dualProof.targetTxMetadata.alh();
        } else {
            byte[] digest = vEntry.getVerifiableTx().getDualProof().getSourceTxMetadata().getEH().toByteArray();
            eh = CryptoUtils.digestFrom(digest);

            sourceId = vTx;
            sourceAlh = dualProof.sourceTxMetadata.alh();
            targetId = state.txId;
            targetAlh = CryptoUtils.digestFrom(state.txHash);
        }

        if (!CryptoUtils.verifyInclusion(inclusionProof, kv, eh)) {
            throw new VerificationException("Inclusion verification failed.");
        }

        if (state.txId > 0) {
            if (!CryptoUtils.verifyDualProof(
                    dualProof,
                    sourceId,
                    targetId,
                    sourceAlh,
                    targetAlh
            )) {
                throw new VerificationException("Dual proof verification failed.");
            }
        }

        ImmuState newState = new ImmuState(
                apiKey,
                targetId,
                targetAlh,
                vEntry.getVerifiableTx().getSignature().toByteArray());

        stateHolder.setState(serverId, newState);

        return Entry.valueOf(vEntry.getEntry());
    }

    /**
     * Get multiple key-value pairs.
     */
    public List<KV> getAll(List<String> keys) {
        List<ByteString> keysBS = new ArrayList<>(keys.size());
        for (String key : keys) {
            keysBS.add(ByteString.copyFrom(key, StandardCharsets.UTF_8));
        }
        return getAllBS(keysBS);
    }

    private List<KV> getAllBS(List<ByteString> keys) {
        ImmudbProto.KeyListRequest req = ImmudbProto.KeyListRequest.newBuilder().addAllKeys(keys).build();
        ImmudbProto.Entries entries = stub.getAll(req);
        List<KV> result = new ArrayList<>(entries.getEntriesCount());
        for (ImmudbProto.Entry entry : entries.getEntriesList()) {
            result.add(KVPair.from(entry));
        }
        return result;
    }


    //
    // ========== HEALTH ==========
    //


    /**
     * Get the heath state of the server.
     *
     * @return true, if all good;<br/>
     * false, otherwise.
     */
    public boolean health() {
        Empty empty = com.google.protobuf.Empty.getDefaultInstance();
        return stub.health(empty).getStatus();
    }


    //
    // ========== HISTORY ==========
    //


    /**
     * Get the history of a key: values that were been set over time.
     */
    public List<KV> history(String key, int limit, long offset, boolean reverse) {
        return history(key.getBytes(StandardCharsets.UTF_8), limit, offset, reverse);
    }

    /**
     * Get the history of a key: values that were been set over time.
     */
    public List<KV> history(byte[] key, int limit, long offset, boolean reverse) {

        ImmudbProto.Entries entries;
        try {
            entries = stub.history(ImmudbProto.HistoryRequest.newBuilder()
                    .setKey(ByteString.copyFrom(key))
                    .setLimit(limit)
                    .setOffset(offset)
                    .setDesc(reverse)
                    .build()
            );
        } catch (StatusRuntimeException e) {
            return new ArrayList<>(0);
        }
        return buildList(entries);
    }


    //
    // ========== SCAN ==========
    //


    /**
     * Scan all entries (KVs) that exist for a key.
     * The provided "key" can just be a prefix, not just the whole key name.
     */
    public List<KV> scan(String key) {
        return scan(ByteString.copyFrom(key, StandardCharsets.UTF_8).toByteArray());
    }

    /**
     * Scan all entries (KVs) that exist for a key.
     * The provided "key" can just be a prefix, not just the whole key name.
     */
    public List<KV> scan(String key, long sinceTxId, long limit, boolean reverse) {
        return scan(ByteString.copyFrom(key, StandardCharsets.UTF_8).toByteArray(), sinceTxId, limit, reverse);
    }

    /**
     * Scan all entries (KVs) that exist for a key.
     * The provided "key" can just be a prefix, not just the whole key name.
     */
    public List<KV> scan(byte[] key) {
        ImmudbProto.ScanRequest req = ImmudbProto.ScanRequest.newBuilder().setPrefix(ByteString.copyFrom(key)).build();
        ImmudbProto.Entries entries = stub.scan(req);
        return buildList(entries);
    }

    /**
     * Scan all entries (KVs) that exist for a key.
     * The provided "key" can just be a prefix, not just the whole key name.
     */
    public List<KV> scan(byte[] key, long sinceTxId, long limit, boolean reverse) {
        ImmudbProto.ScanRequest req = ImmudbProto.ScanRequest.newBuilder()
                .setPrefix(ByteString.copyFrom(key))
                .setLimit(limit)
                .setSinceTx(sinceTxId)
                .setDesc(reverse)
                .build();
        ImmudbProto.Entries entries = stub.scan(req);
        return buildList(entries);
    }


    //
    // ========== Z ==========
    //

    public TxMetadata zAdd(String set, double score, String key) throws CorruptedDataException {
        return zAddAt(set, score, key, 0);
    }

    public TxMetadata zAddAt(String set, double score, String key, long atTxId)
            throws CorruptedDataException {
        ImmudbProto.TxMetadata txMd = stub.zAdd(
                ImmudbProto.ZAddRequest.newBuilder()
                        .setSet(ByteString.copyFrom(set, StandardCharsets.UTF_8))
                        .setKey(ByteString.copyFrom(key, StandardCharsets.UTF_8))
                        .setScore(score)
                        .setAtTx(atTxId)
                        .setBoundRef(atTxId > 0)
                        .build()
        );
        if (txMd.getNentries() != 1) {
            throw new CorruptedDataException();
        }
        return TxMetadata.valueOf(txMd);
    }


    public List<KV> zScan(String set, long limit, boolean reverse) {
        return zScan(set.getBytes(StandardCharsets.UTF_8), 1, limit, reverse);
    }

    public List<KV> zScan(String set, long sinceTxId, long limit, boolean reverse) {
        return zScan(set.getBytes(StandardCharsets.UTF_8), sinceTxId, limit, reverse);
    }

    public List<KV> zScan(byte[] set, long limit, boolean reverse) {
        return zScan(set, 1, limit, reverse);
    }

    public List<KV> zScan(byte[] set, long sinceTxId, long limit, boolean reverse) {
        ImmudbProto.ZScanRequest req = ImmudbProto.ZScanRequest
                .newBuilder()
                .setSet(ByteString.copyFrom(set))
                .setLimit(limit)
                .setSinceTx(sinceTxId)
                .setDesc(reverse)
                .build();
        ImmudbProto.ZEntries zEntries = stub.zScan(req);
        return buildList(zEntries);
    }


    //
    // ========== REPORT TAMPER ==========
    //

    public void reportTamper(byte[] key, long index, ImmudbProto.Signature signature) {

        Lc.ReportOptions options = Lc.ReportOptions.newBuilder()
                .setPayload(Lc.TamperReport.newBuilder()
                        .setKey(ByteString.copyFrom(key))
                        .setIndex(index)
                        .setRoot(ByteString.copyFrom(state().txHash))
                        .build()
                )
                .setSignature(signature)
                .build();
        //noinspection ResultOfMethodCallIgnored
        stub.reportTamper(options);
    }


    //
    // ========== INTERNAL UTILS ==========
    //


    private List<KV> buildList(ImmudbProto.Entries entries) {
        List<KV> result = new ArrayList<>(entries.getEntriesCount());
        entries.getEntriesList()
                .forEach(entry -> result.add(KVPair.from(entry)));
        return result;
    }

    private List<KV> buildList(ImmudbProto.ZEntries entries) {
        List<KV> result = new ArrayList<>(entries.getEntriesCount());
        entries.getEntriesList()
                .forEach(entry -> result.add(KVPair.from(entry)));
        return result;
    }

}
