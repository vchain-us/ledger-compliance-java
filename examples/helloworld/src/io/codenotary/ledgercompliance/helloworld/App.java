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

package io.codenotary.ledgercompliance.helloworld;

import io.codenotary.immudb4j.FileImmuStateHolder;
import io.codenotary.immudb4j.KV;
import io.codenotary.immudb4j.exceptions.VerificationException;
import io.codenotary.ledgercompliance.client.LedgerComplianceClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class App {

    public static void main(String[] args) {

        LedgerComplianceClient client = null;

        try {
            FileImmuStateHolder stateHolder = FileImmuStateHolder.newBuilder()
                    .withStatesFolder("./helloworld_lc_states")
                    .build();

            client = LedgerComplianceClient.newBuilder()
                    .withStateHolder(stateHolder)
                    .withServerUrl("localhost")
//                    .withServerPort(33080)
                    .withServerPort(3324)
                    .withTLS(false)
//                    .withApiKey("APIKEYHERE")
                    .withApiKey("udfyjxgdqtmiqdekaszhbqjpimolbmnhxlia")
                    .build();

            client.set("hello", "immutable world!".getBytes());

            byte[] helloBytes = client.verifiedGet("hello");

            System.out.format("(%s, %s)\n", "hello", new String(helloBytes));

            client.set("key1", "value1".getBytes(StandardCharsets.UTF_8));
            client.set("key2", "value2".getBytes(StandardCharsets.UTF_8));

            List<String> keyList = new ArrayList<>();
            keyList.add("key1");
            keyList.add("key2");

            // Multi-key Read.
            List<KV> result = client.getAll(keyList);

            for (KV kv : result) {
                byte[] key = kv.getKey();
                byte[] value = kv.getValue();
                System.out.format("(%s, %s)\n", new String(key), new String(value));
            }

            String key = "key3";
            client.verifiedSet(key, new byte[]{1, 2, 3});
            byte[] keyBytes = client.verifiedGet(key);
            System.out.format("(%s, %s)\n", key, Arrays.toString(keyBytes));

        } catch (IOException e) {
            e.printStackTrace();
        } catch (VerificationException e) {
            // This might mean that data tampering has been detected
            // which implies that the history of changes has been modified.
            e.printStackTrace();
            System.exit(1);
        } finally {
            // Graceful shutdown of the client and free up resources.
            if (client != null) {
                client.shutdown();
            }
        }

    }

}