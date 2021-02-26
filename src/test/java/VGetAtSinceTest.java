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

import io.codenotary.immudb4j.TxMetadata;
import io.codenotary.immudb4j.exceptions.VerificationException;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;

public class VGetAtSinceTest extends LcClientIntegrationTest {

    @Test(testName = "verifiedSet, verifiedGetAt, verifiedGetSince")
    public void t1() throws VerificationException {

        String prefix = "VGetAtSinceTest_t1__";

        String k1 = prefix + "k1";
        byte[] k2 = (prefix + "k2").getBytes(StandardCharsets.UTF_8);

        byte[] v1 = (prefix + "v1").getBytes(StandardCharsets.UTF_8);
        byte[] v2 = (prefix + "v2").getBytes(StandardCharsets.UTF_8);

        TxMetadata k1TxMd = lcClient.verifiedSet(k1, v1);
        TxMetadata k2TxMd = lcClient.verifiedSet(k2, v2);

        byte[] vgv1 = lcClient.verifiedGetAt(k1, k1TxMd.id);
        byte[] vgv2 = lcClient.verifiedGetAt(k2, k2TxMd.id);

        Assert.assertEquals(vgv1, v1);
        Assert.assertEquals(vgv2, v2);

        vgv1 = lcClient.verifiedGetSince(k1, k1TxMd.id);
        vgv2 = lcClient.verifiedGetSince(k2, k2TxMd.id);

        Assert.assertEquals(vgv1, v1);
        Assert.assertEquals(vgv2, v2);

    }

}
