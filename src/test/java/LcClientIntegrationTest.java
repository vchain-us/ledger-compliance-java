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

import io.codenotary.immudb4j.FileRootHolder;
import io.codenotary.immudb4j.ImmuClient;
import io.codenotary.ledgercompliance.client.LedgerComplianceClient;
import org.testng.annotations.BeforeClass;

import java.io.IOException;

public abstract class LcClientIntegrationTest {

  protected static LedgerComplianceClient lcClient;

  @BeforeClass
  public static void beforeClass() throws IOException {
    FileRootHolder rootHolder = FileRootHolder.newBuilder().setRootsFolder("roots").build();

    lcClient = LedgerComplianceClient.newBuilder()
            .setRootHolder(rootHolder)
            .setServerUrl("ppp.immudb.io")
            .setServerPort(3324)
            .setApiKey("oyshkxyipyvprtqgrwyczlhdnvyrwbchhxxj")
            .build();
  }
}