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

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.testenvmgr.servers.DockerServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TestDockerManagerInitialization extends AbstractTigerCloudTest {

  @ParameterizedTest
  @ValueSource(strings = {"testNoDockerServers.yaml", "testInactiveDockerServer.yaml"})
  void testDockerManagerIsNotInitializedWhenNoActiveDockerServerStarts(String cfgFileName) {
    DockerServer.setDockerManager(null);

    createTestEnvMgrSafelyAndExecute(
        envMgr -> {
          envMgr.setUpEnvironment();
          assertThat(envMgr.getServers()).isEmpty();
          assertThat(DockerServer.isDockerManagerInitialized()).isFalse();
        },
        "src/test/resources/de/gematik/test/tiger/testenvmgr/" + cfgFileName);
  }

  @Test
  void testDockerManagerIsInitializedWhenDockerServerStarts() {
    DockerServer.setDockerManager(null);

    createTestEnvMgrSafelyAndExecute(
        envMgr -> {
          envMgr.setUpEnvironment();
          assertThat(DockerServer.isDockerManagerInitialized()).isTrue();
        },
        "src/test/resources/de/gematik/test/tiger/testenvmgr/testDockerHttpd.yaml");
  }
}
