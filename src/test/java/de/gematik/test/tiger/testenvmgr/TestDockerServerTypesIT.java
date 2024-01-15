/*
 * Copyright (c) 2024 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.testenvmgr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.AbstractTigerServer;
import de.gematik.test.tiger.testenvmgr.servers.DockerServer;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.http.conn.HttpHostConnectException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.DockerClientFactory;

/**
 * Tests the docker container and docker compose feature. For the tests to run successfully you need
 * to have a local docker installation set up. The following external resources are used, so make
 * sure your docker installation has access to:
 *
 * <ul>
 *   <li>docker.io/httpd
 *   <li>gematik1/tiger-test-image
 * </ul>
 */
@Slf4j
@Getter
class TestDockerServerTypesIT extends AbstractTigerCloudTest {

  @BeforeEach
  public void listContainers() {
    log.info(
        "Active Docker containers: \n{}",
        DockerClientFactory.instance().client().listContainersCmd().exec().stream()
            .map(
                container ->
                    String.join(", ", container.getNames()) + " -> " + container.toString())
            .collect(Collectors.joining("\n")));
  }

  // -----------------------------------------------------------------------------------------------------------------
  //
  // check missing mandatory props are detected
  //
  @ParameterizedTest
  @CsvSource({
    "testDocker,type",
    "testDocker,source",
    "testDocker,version",
  })
  void testCheckCfgPropertiesMissingParamMandatoryProps_NOK(String cfgFile, String prop) {
    createTestEnvMgrSafelyAndExecute(
        "src/test/resources/de/gematik/test/tiger/testenvmgr/" + cfgFile + ".yaml",
        envMgr -> {
          CfgServer srv = envMgr.getConfiguration().getServers().get(cfgFile);
          ReflectionTestUtils.setField(srv, prop, null);
          assertThatThrownBy(
                  () -> envMgr.createServer("blub", srv).assertThatConfigurationIsCorrect())
              .isInstanceOf(TigerTestEnvException.class)
              .hasMessageContaining(prop);
        });
  }

  // -----------------------------------------------------------------------------------------------------------------
  //
  // check minimum configurations pass the check and MVP configs are started successfully,
  // check hostname is set to key if missing
  // check for docker compose hostname is not allowed
  //

  @ParameterizedTest
  @ValueSource(strings = {"testDocker"})
  void testCheckCfgPropertiesMinimumConfigPasses_OK(String cfgFileName) {
    log.info("Starting testCheckCfgPropertiesMinimumConfigPasses_OK for {}", cfgFileName);
    TigerGlobalConfiguration.initializeWithCliProperties(
        Map.of(
            "TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/" + cfgFileName + ".yaml"));

    createTestEnvMgrSafelyAndExecute(
        envMgr -> {
          CfgServer srv = envMgr.getConfiguration().getServers().get(cfgFileName);
          envMgr.createServer("blub", srv).assertThatConfigurationIsCorrect();
        });
  }

  @ParameterizedTest
  @ValueSource(strings = {"testComposeMVP", "testDockerMVP"})
  void testSetUpEnvironmentNShutDownMinimumConfigPasses_OK(String cfgFileName) throws IOException {
    log.info("Starting testSetUpEnvironmentNShutDownMinimumConfigPasses_OK for {}", cfgFileName);
    FileUtils.deleteDirectory(new File("WinstoneHTTPServer"));
    createTestEnvMgrSafelyAndExecute(
        envMgr -> {
          envMgr.setUpEnvironment();
        },
        "src/test/resources/de/gematik/test/tiger/testenvmgr/" + cfgFileName + ".yaml");
  }

  @Test
  void testHostnameForDockerComposeNotAllowed_NOK() {
    TigerGlobalConfiguration.initializeWithCliProperties(
        Map.of(
            "TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testComposeWithHostname.yaml"));

    assertThatThrownBy(TigerTestEnvMgr::new).isInstanceOf(TigerConfigurationException.class);
  }

  // -----------------------------------------------------------------------------------------------------------------
  //
  // docker details
  //

  @Test
  void testCreateDockerNonExistingVersion() {
    TigerGlobalConfiguration.initializeWithCliProperties(
        Map.of(
            "TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testDockerMVPNonExistingVersion.yaml"));
    createTestEnvMgrSafelyAndExecute(
        envMgr ->
            assertThatThrownBy(envMgr::setUpEnvironment).isInstanceOf(TigerTestEnvException.class));
  }

  @Test
  @TigerTest(
      cfgFilePath = "src/test/resources/de/gematik/test/tiger/testenvmgr/testDockerHttpd.yaml")
  void testDockerExportingPortsNHostname_OK(TigerTestEnvMgr envMgr) {
    final String cfgFileName = "testDockerHttpd";
    log.info("Starting testDockerExportingPortsNHostname_OK for {}", cfgFileName);
    AbstractTigerServer server = envMgr.getServers().get(cfgFileName);
    assertThat(server.getConfiguration().getDockerOptions().getPorts())
        .as("Checking dockers ports data")
        .hasSize(1)
        .containsKey(80);
    assertThat(Unirest.get(server.getConfiguration().getHealthcheckUrl()).asString().getStatus())
        .as("Request to httpd is working")
        .isEqualTo(200);
    assertThat(TigerGlobalConfiguration.readStringOptional("external.http.port"))
        .as("Check exposed ports are parsed")
        .isPresent()
        .get()
        .isEqualTo(String.valueOf(server.getConfiguration().getDockerOptions().getPorts().get(80)));
    assertThat(TigerGlobalConfiguration.readStringOptional("external.hostname"))
        .as("Check hostname is parsed")
        .isPresent()
        .get()
        .isEqualTo(cfgFileName);
  }

  @Test
  @TigerTest(
      cfgFilePath = "src/test/resources/de/gematik/test/tiger/testenvmgr/testDockerHttpd.yaml")
  void testDockerPauseUnpuaseStop_OK(TigerTestEnvMgr envMgr) {
    final String cfgFileName = "testDockerHttpd";
    log.info("Starting testDockerPauseUnpuase_OK for {}", cfgFileName);

    DockerServer server = (DockerServer) envMgr.getServers().get(cfgFileName);
    Unirest.config().reset();
    Unirest.config().socketTimeout(1000).connectTimeout(1000);
    assertThat(Unirest.get(server.getConfiguration().getHealthcheckUrl()).asString().getStatus())
        .as("Request to httpd is working")
        .isEqualTo(200);
    DockerServer.dockerManager.pauseContainer(server);
    assertThatThrownBy(() -> Unirest.get(server.getConfiguration().getHealthcheckUrl()).asString())
        .isInstanceOf(UnirestException.class)
        .hasCauseInstanceOf(SocketTimeoutException.class);
    DockerServer.dockerManager.unpauseContainer(server);
    assertThat(Unirest.get(server.getConfiguration().getHealthcheckUrl()).asString().getStatus())
        .as("Request to httpd after resuming is working")
        .isEqualTo(200);
    DockerServer.dockerManager.stopContainer(server);
    assertThatThrownBy(() -> Unirest.get(server.getConfiguration().getHealthcheckUrl()).asString())
        .isInstanceOf(UnirestException.class)
        .hasCauseInstanceOf(HttpHostConnectException.class);
  }

  @Test
  void testCreateDockerComposeAndCheckPortIsAvailable() {
    createTestEnvMgrSafelyAndExecute(
        envMgr -> {
          envMgr.setUpEnvironment();
          String host = System.getenv("DOCKER_HOST");
          if (host == null) {
            host = "localhost";
          } else {
            host = new URI(host).getHost();
          }
          log.info(
              "Web server expected to serve at {}",
              TigerGlobalConfiguration.resolvePlaceholders("http://" + host + ":${free.port.1}"));
          try {
            log.info(
                "Web server responds with: "
                    + Unirest.spawnInstance()
                        .get(
                            TigerGlobalConfiguration.resolvePlaceholders(
                                "http://" + host + ":${free.port.1}"))
                        .asString()
                        .getBody());
          } catch (Exception e) {
            log.error("Unable to retrieve document from docker compose webserver...", e);
          }
          assertThat(
                  Unirest.spawnInstance()
                      .get(
                          TigerGlobalConfiguration.resolvePlaceholders(
                              "http://" + host + ":${free.port.1}"))
                      .asString()
                      .getStatus())
              .isEqualTo(200);
        },
        "src/test/resources/de/gematik/test/tiger/testenvmgr/testComposeMVP.yaml");
  }
}
