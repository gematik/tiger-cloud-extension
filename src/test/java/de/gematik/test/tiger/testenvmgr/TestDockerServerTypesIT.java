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

import static de.gematik.test.tiger.testenvmgr.servers.DockerMgr.DOCKER_HOST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.proxy.data.TigerProxyRoute;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.DockerAbstractServer;
import de.gematik.test.tiger.testenvmgr.servers.DockerComposeServer;
import de.gematik.test.tiger.testenvmgr.servers.DockerServer;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerStatus;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kong.unirest.core.GetRequest;
import kong.unirest.core.Unirest;
import kong.unirest.core.UnirestException;
import kong.unirest.core.UnirestInstance;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
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
            .map(container -> String.join(", ", container.getNames()) + " -> " + container)
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
          srv.setHostname("testblub");
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
          srv.setHostname("testblub");
          envMgr.createServer("blub", srv).assertThatConfigurationIsCorrect();
        });
  }

  @ParameterizedTest
  @ValueSource(strings = {"testComposeMVP", "testDockerMVP"})
  void testSetUpEnvironmentNShutDownMinimumConfigPasses_OK(String cfgFileName) throws IOException {
    log.info("Starting testSetUpEnvironmentNShutDownMinimumConfigPasses_OK for {}", cfgFileName);
    FileUtils.deleteDirectory(new File("WinstoneHTTPServer"));
    createTestEnvMgrSafelyAndExecute(
        TigerTestEnvMgr::setUpEnvironment,
        "src/test/resources/de/gematik/test/tiger/testenvmgr/" + cfgFileName + ".yaml");
  }

  @Test
  void testHostnameForDockerComposeNotAllowed_NOK() {
    createTestEnvMgrSafelyAndExecute(
        "src/test/resources/de/gematik/test/tiger/testenvmgr/testComposeWithHostname.yaml",
        envMgr -> {
          assertThatThrownBy(() -> envMgr.findServer("testDemis").get().start(envMgr))
              .isInstanceOf(TigerConfigurationException.class)
              .hasMessageContaining("Hostname property is not supported for docker compose nodes!");
        });
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
            assertThatThrownBy(envMgr::setUpEnvironment)
                .isInstanceOf(TigerEnvironmentStartupException.class));
  }

  @Test
  @TigerTest(
      cfgFilePath = "src/test/resources/de/gematik/test/tiger/testenvmgr/testDockerHttpd.yaml")
  void testDockerExportingPortsNHostname_OK(TigerTestEnvMgr envMgr) {
    final String cfgFileName = "testDockerHttpd";
    log.info("Starting testDockerExportingPortsNHostname_OK for {}", cfgFileName);
    DockerAbstractServer server = (DockerAbstractServer) envMgr.getServers().get(cfgFileName);
    assertThat(server.getDockerOptions().getPorts())
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
        .isEqualTo(String.valueOf(server.getDockerOptions().getPorts().get(80)));
    assertThat(TigerGlobalConfiguration.readStringOptional("external.hostname"))
        .as("Check hostname is parsed")
        .isPresent()
        .get()
        .isEqualTo(cfgFileName);
  }

  @Test
  @TigerTest(
      cfgFilePath = "src/test/resources/de/gematik/test/tiger/testenvmgr/testDockerHttpd.yaml")
  void testDockerPauseUnpauseStop_OK(TigerTestEnvMgr envMgr) {
    final String cfgFileName = "testDockerHttpd";
    log.info("Starting testDockerPauseUnpauseStop_OK for {}", cfgFileName);

    DockerServer server = (DockerServer) envMgr.getServers().get(cfgFileName);
    String healthcheckUrl = server.getConfiguration().getHealthcheckUrl();
    Unirest.config().reset();
    Unirest.config().requestTimeout(1000).connectTimeout(1000);

    assertThat(Unirest.get(healthcheckUrl).asString().getStatus())
        .as("Request to httpd is working")
        .isEqualTo(200);

    DockerServer.dockerManager.pauseContainer(server);
    GetRequest requestAfterPause = Unirest.get(healthcheckUrl);
    assertThatThrownBy(requestAfterPause::asString)
        .isInstanceOf(UnirestException.class)
        .hasCauseInstanceOf(HttpTimeoutException.class);

    DockerServer.dockerManager.unpauseContainer(server);
    assertThat(Unirest.get(healthcheckUrl).asString().getStatus())
        .as("Request to httpd after resuming is working")
        .isEqualTo(200);

    DockerServer.dockerManager.stopContainer(server);
    GetRequest requestAfterStop = Unirest.get(healthcheckUrl);
    assertThatThrownBy(requestAfterStop::asString)
        .isInstanceOf(UnirestException.class)
        .hasCauseInstanceOf(ConnectException.class);
  }

  @Test
  @TigerTest(
      cfgFilePath = "src/test/resources/de/gematik/test/tiger/testenvmgr/testComposeMVP.yaml")
  void testDockerComposeStop_OK(TigerTestEnvMgr envMgr) {
    final String cfgFileName = "testComposeMVP";
    log.info("Starting testDockerComposeStop_OK for {}", cfgFileName);

    DockerComposeServer server = (DockerComposeServer) envMgr.getServers().get(cfgFileName);
    var baseUrl =
        TigerGlobalConfiguration.resolvePlaceholders(
            "http://" + DOCKER_HOST.getValueOrDefault() + ":${free.port.1}");
    Unirest.config().reset();
    Unirest.config().requestTimeout(1000).connectTimeout(1000);

    assertThat(Unirest.get(baseUrl).asString().getStatus())
        .as("Request to httpd is working")
        .isEqualTo(200);
    server.shutdown();
    GetRequest requestAfterStop = Unirest.get(baseUrl);
    assertThatThrownBy(requestAfterStop::asString)
        .isInstanceOf(UnirestException.class)
        .hasCauseInstanceOf(ConnectException.class);
  }

  @Test
  void testCreateDockerComposeAndCheckPortIsAvailable() {
    createTestEnvMgrSafelyAndExecute(
        envMgr -> {
          envMgr.setUpEnvironment();
          log.info(
              "Web server expected to serve at {}",
              TigerGlobalConfiguration.resolvePlaceholders(
                  "http://" + DOCKER_HOST.getValueOrDefault() + ":${free.port.1}"));
          try {
            log.info(
                "Web server responds with: "
                    + Unirest.spawnInstance()
                        .get(
                            TigerGlobalConfiguration.resolvePlaceholders(
                                "http://" + DOCKER_HOST.getValueOrDefault() + ":${free.port.1}"))
                        .asString()
                        .getBody());
          } catch (Exception e) {
            log.error("Unable to retrieve document from docker compose webserver...", e);
          }
          assertThat(
                  Unirest.spawnInstance()
                      .get(
                          TigerGlobalConfiguration.resolvePlaceholders(
                              "http://" + DOCKER_HOST.getValueOrDefault() + ":${free.port.1}"))
                      .asString()
                      .getStatus())
              .isEqualTo(200);
        },
        "src/test/resources/de/gematik/test/tiger/testenvmgr/testComposeMVP.yaml");
  }

  @Test
  void testCreateDockerComposeWithoutLocalComposeAndCheckPortIsAvailable() {
    createTestEnvMgrSafelyAndExecute(
        envMgr -> {
          envMgr.setUpEnvironment();
          log.info(
              "Web server expected to serve at {}",
              TigerGlobalConfiguration.resolvePlaceholders(
                  "http://" + DOCKER_HOST.getValueOrDefault() + ":${free.port.1}"));
          try {
            log.info(
                "Web server responds with: "
                    + Unirest.spawnInstance()
                        .get(
                            TigerGlobalConfiguration.resolvePlaceholders(
                                "http://" + DOCKER_HOST.getValueOrDefault() + ":${free.port.1}"))
                        .asString()
                        .getBody());
          } catch (Exception e) {
            log.error("Unable to retrieve document from docker compose webserver...", e);
          }
          assertThat(
                  Unirest.spawnInstance()
                      .get(
                          TigerGlobalConfiguration.resolvePlaceholders(
                              "http://" + DOCKER_HOST.getValueOrDefault() + ":${free.port.1}"))
                      .asString()
                      .getStatus())
              .isEqualTo(200);
        },
        "src/test/resources/de/gematik/test/tiger/testenvmgr/testComposeMVPWithNoLocalCompose.yaml");
  }

  /** test that docker compose adds routes to tiger proxy */
  @Test
  void testCreateDockerComposeAndCheckRoutesAreAddedToTigerProxy() {
    createTestEnvMgrSafelyAndExecute(
        (envMgr) -> {
          envMgr.setUpEnvironment();
          List<TigerProxyRoute> routes = envMgr.getLocalTigerProxyOrFail().getRoutes();
          assertThat(routes)
              .as("Checking routes are added to tiger proxy")
              .extracting(route -> Pair.of(route.getFrom(), route.getTo()))
              .contains(
                  Pair.of(
                      "http://testComposeTestPortMapping-webserver-80",
                      TigerGlobalConfiguration.resolvePlaceholders(
                          "http://" + DOCKER_HOST.getValueOrDefault() + ":${free.port.1}")),
                  Pair.of(
                      "http://testComposeTestPortMapping-httpbin-80",
                      TigerGlobalConfiguration.resolvePlaceholders(
                          "http://" + DOCKER_HOST.getValueOrDefault() + ":${free.port.2}")));

          // check that servers are reachable over the direct uri
          try (UnirestInstance instanceForDirectAccess = Unirest.spawnInstance()) {
            GetRequest requestWebServerDirectly =
                instanceForDirectAccess.get(
                    TigerGlobalConfiguration.resolvePlaceholders(
                        "http://" + DOCKER_HOST.getValueOrDefault() + ":${free.port.1}"));
            assertThat(requestWebServerDirectly.asString().getStatus())
                .as("Request to webserver directly is working")
                .isEqualTo(200);
            GetRequest requestHttpBinDirectly =
                instanceForDirectAccess.get(
                    TigerGlobalConfiguration.resolvePlaceholders(
                        "http://"
                            + DOCKER_HOST.getValueOrDefault()
                            + ":${free.port.2}/status/200"));
            assertThat(requestHttpBinDirectly.asString().getStatus())
                .as("Request to httpbin directly is working")
                .isEqualTo(200);
          }

          // check that servers are reachable over the tiger proxy route
          try (UnirestInstance instanceViaTigerProxy = Unirest.spawnInstance()) {
            instanceViaTigerProxy
                .config()
                .proxy("localhost", envMgr.getLocalTigerProxyOrFail().getProxyPort());

            GetRequest requestHttpBinViaTigerProxy =
                instanceViaTigerProxy.get(
                    "http://testComposeTestPortMapping-httpbin-80/status/200");
            assertThat(requestHttpBinViaTigerProxy.asString().getStatus())
                .as("Request to httpbin via tiger proxy is working")
                .isEqualTo(200);
            GetRequest requestWebServerViaTigerProxy =
                instanceViaTigerProxy.get("http://testComposeTestPortMapping-webserver-80");

            assertThat(requestWebServerViaTigerProxy.asString().getStatus())
                .as("Request to webserver via tiger proxy is working")
                .isEqualTo(200);
          }
        },
        "src/test/resources/de/gematik/test/tiger/testenvmgr/testComposeTestPortMapping.yaml");
  }

  @Test
  void testCreateDockerWithCopyFiles_filesShouldBeInContainer() {
    var cfgFile = "testDocker_CopyFiles";
    createTestEnvMgrSafelyAndExecute(
        "src/test/resources/de/gematik/test/tiger/testenvmgr/" + cfgFile + ".yaml",
        envMgr -> {
          envMgr.setUpEnvironment();
          DockerAbstractServer server = (DockerAbstractServer) envMgr.getServers().get(cfgFile);
          var mappedPort = server.getDockerOptions().getPorts().get(80);
          var baseUrl = "http://" + DOCKER_HOST.getValueOrDefault() + ":" + mappedPort;
          assertThat(Unirest.get(baseUrl + "/test_file_inside_container.txt").asString().getBody())
              .isEqualTo("this is the content of test_file_to_copy.txt");
          assertThat(
                  Unirest.get(baseUrl + "/test_folder_inside_container_to_copy/a_file.txt")
                      .asString()
                      .getBody())
              .isEqualTo("this is the content of test_folder_to_copy/a_file.txt");
        });
  }

  @Test
  @TigerTest(
      cfgFilePath =
          "src/test/resources/de/gematik/test/tiger/testenvmgr/testDockerHttpdHealth.yaml")
  void testDockerHealth_OK(TigerTestEnvMgr envMgr) {
    final String cfgFileName = "testDockerHttpdHealth";
    log.info("Starting testDockerHealth_OK for {}", cfgFileName);
    DockerAbstractServer server = (DockerAbstractServer) envMgr.getServers().get(cfgFileName);
    assertThat(server.getDockerOptions().getPorts())
        .as("Checking dockers ports data")
        .hasSize(1)
        .containsKey(80);
    assertThat(Unirest.get(server.getConfiguration().getHealthcheckUrl()).asString().getStatus())
        .as("Request to httpd is working")
        .isEqualTo(200);
    assertThat(server.getConfiguration().getHealthcheckUrl())
        .as("RequestURL is taken from healthcheckUrl")
        .isEqualTo("http://www.example.com");
    assertThat(server.getStatus())
        .as("Server Status is Running")
        .isEqualTo(TigerServerStatus.RUNNING);
  }
}
