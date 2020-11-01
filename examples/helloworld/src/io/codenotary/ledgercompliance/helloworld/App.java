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

package io.codenotary.ledgercompliance.helloworld;

import io.codenotary.immudb4j.FileRootHolder;
import io.codenotary.immudb4j.KV;
import io.codenotary.immudb4j.KVList;
import io.codenotary.immudb4j.crypto.VerificationException;
import io.codenotary.ledgercompliance.client.LedgerComplianceClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class App {

    public static void main(String[] args) {

        LedgerComplianceClient client = null;

        try {

            FileRootHolder rootHolder = FileRootHolder.newBuilder().setRootsFolder("./helloworld_lc_roots").build();
            client = LedgerComplianceClient.newBuilder()
                    .setServerUrl("localhost")
                    .setServerPort(33080)
                    .setApiKey("APIKEYHERE")
                    .setRootHolder(rootHolder)
                    .build();

            client.set("hello", "immutable world!".getBytes());
            byte[] v = client.safeGet("hello");

            System.out.format("(%s, %s)", "hello", new String(v));

            // Multi-key operations
            KVList.KVListBuilder builder = KVList.newBuilder();

            builder.add("k123", new byte[]{1, 2, 3});
            builder.add("k321", new byte[]{3, 2, 1});

            KVList kvList = builder.build();

            client.setAll(kvList);

            List<String> keyList = new ArrayList<>();
            keyList.add("k123");
            keyList.add("k321");
            keyList.add("k231");

            List<KV> result = client.getAll(keyList);

            for (KV kv : result) {
                byte[] key = kv.getKey();
                byte[] value = kv.getValue();

                System.out.format("(%s, %s)", new String(key), Arrays.toString(value));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (VerificationException e) {
            // Tampering detected!
            // This means the history of changes has been tampered
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (client != null) {
                client.shutdown();
            }
        }

    }

}