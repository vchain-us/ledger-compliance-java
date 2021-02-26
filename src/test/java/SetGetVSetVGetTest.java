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

import com.google.common.base.Charsets;
import io.codenotary.immudb4j.KV;
import io.codenotary.immudb4j.exceptions.VerificationException;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SetGetVSetVGetTest extends LcClientIntegrationTest {

    @Test(testName = "set, get")
    public void t1() throws VerificationException {

        String k1 = "sgvsvgTest_t1__k1";
        byte[] k2 = "sgvsvgTest_t1__k2".getBytes(StandardCharsets.UTF_8);
        byte[] v1 = "sgvsvgTest_t1__v0".getBytes(StandardCharsets.UTF_8);
        byte[] v2 = "sgvsvgTest_t1__v1".getBytes(StandardCharsets.UTF_8);


        lcClient.set(k1, v1);
        lcClient.set(k2, v2);

        byte[] gv0 = lcClient.get(k1);
        byte[] gv1 = lcClient.get(k2);

        Assert.assertEquals(v1, gv0);
        Assert.assertEquals(v2, gv1);

    }

    @Test(testName = "verifiedSet & verifiedGet")
    public void t2() throws VerificationException {

        String k1 = "sgvsvgTest_t2__k1";
        byte[] k2 = "sgvsvgTest_t2__k2".getBytes(StandardCharsets.UTF_8);
        byte[] v1 = "sgvsvgTest_t2__v1".getBytes(StandardCharsets.UTF_8);
        byte[] v2 = "sgvsvgTest_t2__v2".getBytes(StandardCharsets.UTF_8);

        lcClient.verifiedSet(k1, v1);
        lcClient.verifiedSet(k2, v2);

        byte[] vgv1 = lcClient.verifiedGet(k1);
        byte[] vgv2 = lcClient.verifiedGet(k2);

        Assert.assertEquals(vgv1, v1);
        Assert.assertEquals(vgv2, v2);

    }

    @Test(testName = "set, getAll")
    public void t3() {

        List<String> keys = new ArrayList<>();
        keys.add("sgvsvgTest_t3__k1");
        keys.add("sgvsvgTest_t3__k2");

        List<byte[]> values = new ArrayList<>();
        values.add("sgvsvgTest_t3__v1".getBytes(StandardCharsets.UTF_8));
        values.add(new byte[]{1, 0, 1, 0});

        for (int i = 0; i < keys.size(); i++) {
            lcClient.set(keys.get(i), values.get(i));
        }

        List<KV> getAllResult = lcClient.getAll(keys);

        Assert.assertNotNull(getAllResult);
        Assert.assertEquals(keys.size(), getAllResult.size());

        for (int i = 0; i < getAllResult.size(); i++) {
            KV kv = getAllResult.get(i);
            Assert.assertEquals(kv.getKey(), keys.get(i).getBytes(Charsets.UTF_8));
            Assert.assertEquals(kv.getValue(), values.get(i));
        }

        for (int i = 0; i < keys.size(); i++) {
            byte[] v = lcClient.get(keys.get(i));
            Assert.assertEquals(v, values.get(i));
        }

    }

}
