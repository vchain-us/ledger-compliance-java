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

import io.codenotary.immudb4j.KV;
import io.codenotary.immudb4j.TxMetadata;
import io.codenotary.immudb4j.exceptions.CorruptedDataException;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class ScanHistoryTest extends LcClientIntegrationTest {

    @Test(testName = "history", priority = 2)
    public void t1() {

        String key1 = "ScanHistoryTest_t1__history1";
        String key2 = "ScanHistoryTest_t1__history2";
        byte[] value1 = {0, 1, 2, 3};
        byte[] value2 = {4, 5, 6, 7};
        byte[] value3 = {8, 9, 10, 11};


        lcClient.set(key1, value1);
        lcClient.set(key1, value2);

        lcClient.set(key2, value1);
        lcClient.set(key2, value2);
        lcClient.set(key2, value3);

        List<KV> history1Response = lcClient.history(key1, 10, 0, false);

        Assert.assertEquals(history1Response.size(), 2);

        Assert.assertEquals(history1Response.get(0).getKey(), key1.getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(history1Response.get(0).getValue(), value1);

        Assert.assertEquals(history1Response.get(1).getKey(), key1.getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(history1Response.get(1).getValue(), value2);


        List<KV> history2Response = lcClient.history(key2, 10, 0, false);

        Assert.assertEquals(history2Response.size(), 3);

        Assert.assertEquals(history2Response.get(0).getKey(), key2.getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(history2Response.get(0).getValue(), value1);

        Assert.assertEquals(history2Response.get(1).getKey(), key2.getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(history2Response.get(1).getValue(), value2);

        Assert.assertEquals(history2Response.get(2).getKey(), key2.getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(history2Response.get(2).getValue(), value3);


        List<KV> nonExisting = lcClient.history("nonExisting", 10, 0, false);
        Assert.assertTrue(nonExisting.isEmpty());
    }

    @Test(testName = "scan", priority = 2)
    public void t2() {

        byte[] value1 = {0, 1, 2, 3};
        byte[] value2 = {4, 5, 6, 7};

        lcClient.set("scan1", value1);
        lcClient.set("scan2", value2);

        List<KV> scan = lcClient.scan("scan", 1, 5, false);

        Assert.assertEquals(scan.size(), 2);
        Assert.assertEquals(scan.get(0).getKey(), "scan1".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(scan.get(0).getValue(), value1);
        Assert.assertEquals(scan.get(1).getKey(), "scan2".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(scan.get(1).getValue(), value2);
    }

    @Test(testName = "set, zAdd, zScan", priority = 2)
    public void t3() {

        byte[] value1 = {0, 1, 2, 3};
        byte[] value2 = {4, 5, 6, 7};

        lcClient.set("zadd1", value1);
        lcClient.set("zadd2", value2);

        TxMetadata set1TxMd = null;
        TxMetadata set2TxMd = null;
        try {
            lcClient.zAdd("set1", 1, "zadd1");
            set1TxMd = lcClient.zAdd("set1", 2, "zadd2");

            lcClient.zAdd("set2", 2, "zadd1");
            set2TxMd = lcClient.zAdd("set2", 1, "zadd2");
        } catch (CorruptedDataException e) {
            Assert.fail("Failed to zAdd", e);
        }


        List<KV> zScan1 = lcClient.zScan("set1", set1TxMd.id, 5, false);

        Assert.assertEquals(zScan1.size(), 2);
        Assert.assertEquals(zScan1.get(0).getKey(), "zadd1".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(zScan1.get(0).getValue(), value1);
        Assert.assertEquals(zScan1.get(1).getKey(), "zadd2".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(zScan1.get(1).getValue(), value2);

        List<KV> zScan2 = lcClient.zScan("set2", set2TxMd.id, 5, false);

        Assert.assertEquals(zScan2.size(), 2);
        Assert.assertEquals(zScan2.get(0).getKey(), "zadd2".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(zScan2.get(0).getValue(), value2);
        Assert.assertEquals(zScan2.get(1).getKey(), "zadd1".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(zScan2.get(1).getValue(), value1);
    }

}
