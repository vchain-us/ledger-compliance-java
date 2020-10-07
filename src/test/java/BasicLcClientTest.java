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

import com.google.common.base.Charsets;
import io.codenotary.immudb4j.KV;
import io.codenotary.immudb4j.KVList;
import io.codenotary.immudb4j.crypto.VerificationException;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class BasicLcClientTest extends LcClientIntegrationTest {

  @Test
  public void testGet() throws VerificationException {
    byte[] v0 = new byte[] {0, 1, 2, 3};
    byte[] v1 = new byte[] {3, 2, 1, 0};

    lcClient.set("k0", v0);
    lcClient.set("k1", v1);

    byte[] rv0 = lcClient.get("k0");
    byte[] rv1 = lcClient.get("k1");

    Assert.assertEquals(v0, rv0);
    Assert.assertEquals(v1, rv1);

    byte[] sv0 = lcClient.safeGet("k0");
    byte[] sv1 = lcClient.safeGet("k1");

    Assert.assertEquals(sv0, v0);
    Assert.assertEquals(sv1, v1);

    byte[] v2 = new byte[] {0, 1, 2, 3};

    lcClient.safeSet("k2", v2);
    byte[] sv2 = lcClient.safeGet("k2");
    Assert.assertEquals(v2, sv2);

  }

  @Test
  public void testRawGetAndSet() throws VerificationException {
    byte[] v0 = new byte[] {0, 1, 2, 3};
    byte[] v1 = new byte[] {3, 2, 1, 0};

    lcClient.rawSet("rawk0", v0);
    lcClient.rawSet("rawk1", v1);

    byte[] rv0 = lcClient.rawGet("rawk0");
    byte[] rv1 = lcClient.rawGet("rawk1");

    Assert.assertEquals(v0, rv0);
    Assert.assertEquals(v1, rv1);

    byte[] sv0 = lcClient.safeRawGet("rawk0");
    byte[] sv1 = lcClient.safeRawGet("rawk1");

    Assert.assertEquals(sv0, v0);
    Assert.assertEquals(sv1, v1);

    byte[] v2 = new byte[] {0, 1, 2, 3};

    lcClient.safeRawSet("rawk2", v2);
    byte[] sv2 = lcClient.safeRawGet("rawk2");
    Assert.assertEquals(v2, sv2);

  }

  /*
  @Test
  public void testGetAllAndSetAll() {

    List<String> keys = new ArrayList<>();
    keys.add("k0");
    keys.add("k1");

    List<byte[]> values = new ArrayList<>();
    values.add(new byte[] {0, 1, 0, 1});
    values.add(new byte[] {1, 0, 1, 0});

    KVList.KVListBuilder kvListBuilder = KVList.newBuilder();

    for (int i = 0; i < keys.size(); i++) {
      kvListBuilder.add(keys.get(i), values.get(i));
    }

    KVList kvList = kvListBuilder.addAll(new LinkedList<>()).build();

    lcClient.setAll(kvList);

    List<KV> getAllResult = lcClient.getAll(keys);

    Assert.assertNotNull(getAllResult);
    Assert.assertTrue(getAllResult.size() == keys.size());

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

   */
}
