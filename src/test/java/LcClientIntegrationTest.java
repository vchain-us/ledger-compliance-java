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

import io.codenotary.immudb4j.FileImmuStateHolder;
import io.codenotary.ledgercompliance.client.LedgerComplianceClient;
import org.testng.annotations.BeforeClass;

import java.io.IOException;


public abstract class LcClientIntegrationTest {

    private static final String API_KEY = "PUT-YOUR-API-KEY-HERE";

    protected static LedgerComplianceClient lcClient;

    @BeforeClass
    public static void beforeClass() throws IOException {
        FileImmuStateHolder stateHolder = FileImmuStateHolder.newBuilder()
                .setStatesFolder("states")
                .build();

        lcClient = LedgerComplianceClient.newBuilder()
                .setStateHolder(stateHolder)
                .setServerUrl("localhost")
                .setServerPort(33080)
//                .setServerPort(3324)
                .setUseTLS(false)
                .setApiKey(API_KEY)
                .build();
    }

}
