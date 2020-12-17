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

import io.codenotary.immudb4j.KV;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class ScanHistoryTest extends LcClientIntegrationTest {

    @Test(priority = 2)
    public void testHistory() {
        byte[] value1 = {0, 1, 2, 3};
        byte[] value2 = {4, 5, 6, 7};
        byte[] value3 = {8, 9, 10, 11};

        lcClient.set("history1", value1);
        lcClient.set("history1", value2);
        lcClient.set("history2", value1);
        lcClient.set("history2", value2);
        lcClient.set("history2", value3);

        List<KV> historyResponse1 = lcClient.history("history1", 10, 0, false);

        Assert.assertEquals(historyResponse1.size(), 2);

        Assert.assertEquals(historyResponse1.get(0).getKey(), "history1".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(historyResponse1.get(0).getValue(), value2);

        Assert.assertEquals(historyResponse1.get(1).getKey(), "history1".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(historyResponse1.get(1).getValue(), value1);

        List<KV> historyResponse2 = lcClient.history("history2", 10, 0, false);

        Assert.assertEquals(historyResponse2.size(), 3);

        Assert.assertEquals(historyResponse2.get(0).getKey(), "history2".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(historyResponse2.get(0).getValue(), value3);

        Assert.assertEquals(historyResponse2.get(1).getKey(), "history2".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(historyResponse2.get(1).getValue(), value2);

        Assert.assertEquals(historyResponse2.get(2).getKey(), "history2".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(historyResponse2.get(2).getValue(), value1);

        List<KV> nonExisting = lcClient.history("nonExisting", 10, 0, false);
        Assert.assertTrue(nonExisting.isEmpty());
    }

    @Test(priority = 2)
    public void testScan() {
        byte[] value1 = {0, 1, 2, 3};
        byte[] value2 = {4, 5, 6, 7};

        lcClient.set("scan1", value1);
        lcClient.set("scan2", value2);

        List<KV> scan = lcClient.scan("scan", "", 5, false, false);

        Assert.assertEquals(scan.size(), 2);
        Assert.assertEquals(scan.get(0).getKey(), "scan1".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(scan.get(0).getValue(), value1);
        Assert.assertEquals(scan.get(1).getKey(), "scan2".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(scan.get(1).getValue(), value2);
    }

    @Test(priority = 2)
    public void testZScan() {
        byte[] value1 = {0, 1, 2, 3};
        byte[] value2 = {4, 5, 6, 7};

        lcClient.set("zadd1", value1);
        lcClient.set("zadd2", value2);

        lcClient.zAdd("set1", "zadd1", 1);
        lcClient.zAdd("set1", "zadd2", 2);

        lcClient.zAdd("set2", "zadd1", 2);
        lcClient.zAdd("set2", "zadd2", 1);

        List<KV> zScan1 = lcClient.zScan("set1", "", 5, false);

        Assert.assertEquals(zScan1.size(), 2);
        Assert.assertEquals(zScan1.get(0).getKey(), "zadd1".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(zScan1.get(0).getValue(), value1);
        Assert.assertEquals(zScan1.get(1).getKey(), "zadd2".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(zScan1.get(1).getValue(), value2);

        List<KV> zScan2 = lcClient.zScan("set2", "", 5, false);

        Assert.assertEquals(zScan2.size(), 2);
        Assert.assertEquals(zScan2.get(0).getKey(), "zadd2".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(zScan2.get(0).getValue(), value2);
        Assert.assertEquals(zScan2.get(1).getKey(), "zadd1".getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(zScan2.get(1).getValue(), value1);
    }
}
