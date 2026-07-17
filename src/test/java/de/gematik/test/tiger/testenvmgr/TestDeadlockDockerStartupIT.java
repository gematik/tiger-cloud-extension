/*
 *
 * Copyright 2023-2025 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.testenvmgr;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TestDeadlockDockerStartupIT extends AbstractTigerCloudTest{

    @BeforeAll
    static void pullRequiredImagesBeforeTimeout() {
    pullImageWithDockerCli("kennethreitz/httpbin:latest");
    pullImageWithDockerCli("docker.io/httpd:alpine");
    }

    private static void pullImageWithDockerCli(String imageName) {
    Process process = null;
    try {
        process = new ProcessBuilder("docker", "pull", imageName).start();
        if (!process.waitFor(5, TimeUnit.MINUTES)) {
        process.destroyForcibly();
        throw new IllegalStateException("Timed out while pulling docker image " + imageName);
        }
        if (process.exitValue() != 0) {
        throw new IllegalStateException("docker pull failed for image " + imageName);
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while pulling docker image " + imageName, e);
    } catch (IOException e) {
        throw new IllegalStateException(
            "Unable to execute docker CLI while pulling image " + imageName, e);
    } finally {
        if (process != null) {
        process.destroy();
        }
    }
    }

    @Test
    void testDockerStartsAllServers_shouldNotDeadlock()  {

    // This test starts multiple docker servers. If there is a deadlock it will timeout
    assertTimeoutPreemptively(
        Duration.ofSeconds(30),
        () ->
            createTestEnvMgrSafelyAndExecute(
                TigerTestEnvMgr::setUpEnvironment,
                "src/test/resources/de/gematik/test/tiger/testenvmgr/testDockerComposeMultipleServers.yaml"));
    }
}
