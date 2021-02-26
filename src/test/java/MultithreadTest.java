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

import io.codenotary.immudb4j.exceptions.VerificationException;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;


public class MultithreadTest extends LcClientIntegrationTest {

    @Test(testName = "Multithread without key overlap")
    public void t1() throws InterruptedException, VerificationException {

        final int threadCount = 10;
        final int keyCount = 100;

        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger succeeded = new AtomicInteger(0);

        Function<String, Runnable> workerFactory = (uuid) -> (Runnable) () -> {
            Random rnd = new Random();

            for (int i = 0; i < keyCount; i++) {
                byte[] b = new byte[10];
                rnd.nextBytes(b);

                try {
                    lcClient.verifiedSet(uuid + "k" + i, b);
                    System.out.printf("verifiedSet key %d\n", i);
                } catch (Exception e) {
                    latch.countDown();
                    throw new RuntimeException(e);
                }
            }

            succeeded.incrementAndGet();
            latch.countDown();
        };

        for (int i = 0; i < threadCount; i++) {
            Thread t = new Thread(workerFactory.apply("t" + i));
            t.start();
            System.out.printf("[thread %d] Started\n", i);
        }

        latch.await();

        Assert.assertEquals(succeeded.get(), threadCount);

        for (int i = 0; i < threadCount; i++) {
            for (int k = 0; k < keyCount; k++) {
                lcClient.verifiedGet("t" + i + "k" + i);
                System.out.printf("[thread %d] verifiedGet of key %d\n", i, k);
            }
        }

    }

    @Test(testName = "Multithread with key overlap")
    public void t2() throws InterruptedException, VerificationException {

        final int threadCount = 10;
        final int keyCount = 100;

        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger succeeded = new AtomicInteger(0);

        Runnable runnable = () -> {
            Random rnd = new Random();

            for (int i = 0; i < keyCount; i++) {
                byte[] b = new byte[10];
                rnd.nextBytes(b);

                try {
                    lcClient.verifiedSet("k" + i, b);
                    System.out.printf("verifiedSet key %d\n", i);
                } catch (Exception e) {
                    latch.countDown();
                    throw new RuntimeException(e);
                }
            }

            succeeded.incrementAndGet();
            latch.countDown();
        };

        for (int i = 0; i < threadCount; i++) {
            Thread t = new Thread(runnable);
            t.start();
            System.out.printf("[thread %d] Started\n", i);
        }

        latch.await();

        Assert.assertEquals(succeeded.get(), threadCount);

        for (int i = 0; i < threadCount; i++) {
            for (int k = 0; k < keyCount; k++) {
                lcClient.verifiedGet("k" + i);
                System.out.printf("[thread %d] verifiedGet key %d\n", i, k);
            }
        }

    }
}
