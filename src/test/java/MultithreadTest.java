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

import io.codenotary.immudb4j.crypto.VerificationException;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class MultithreadTest extends LcClientIntegrationTest {

  @Test
  public void testMultithredWithoutKeyOverlap() throws InterruptedException, VerificationException {
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
          lcClient.safeSet(uuid + "k" + i, b);
          System.out.printf("safe set key %d\n", i);
        } catch (Exception e) {
          latch.countDown();
          throw new RuntimeException(e);
        }
      }

      succeeded.incrementAndGet();
      latch.countDown();
    };

    for(int i=0;i<threadCount;i++) {
      Thread t = new Thread(workerFactory.apply("t"+i));
      t.start();
      System.out.println("Started thread");
    }

    latch.await();

    Assert.assertEquals(succeeded.get(), threadCount);

    for(int i=0;i<threadCount;i++) {
      for (int k = 0; k < keyCount; k++) {
        lcClient.safeGet("t" + i + "k" + i);
        System.out.printf("Safe get key %d by thread %d\n", k, i);
      }
    }

  }

  @Test
  public void testMultithredWithKeyOverlap() throws InterruptedException, VerificationException {
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
          lcClient.safeSet("k" + i, b);
          System.out.printf("safe set key %d\n", i);
        } catch (Exception e) {
          latch.countDown();
          throw new RuntimeException(e);
        }
      }

      succeeded.incrementAndGet();
      latch.countDown();
    };

    for(int i=0;i<threadCount;i++) {
      Thread t = new Thread(runnable);
      t.start();
      System.out.println("Started thread");
    }

    latch.await();

    Assert.assertEquals(succeeded.get(), threadCount);

    for(int i=0;i<threadCount;i++) {
      for (int k = 0; k < keyCount; k++) {
        lcClient.safeGet("k" + i);
        System.out.printf("Safe get key %d by thread %d\n", k, i);
      }
    }

  }
}
