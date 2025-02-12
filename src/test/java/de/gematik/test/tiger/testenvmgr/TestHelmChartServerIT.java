/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.test.tiger.testenvmgr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.CfgHelmChartOptions;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.HelmChartServer;
import de.gematik.test.tiger.testenvmgr.servers.KubeUtils;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.util.ArrayList;
import java.util.List;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Tests the helm chart feature. To be able to run these tests you need to have a working kubernetes
 * / helm installation (microk8s on Ubuntu is our default set up). All tests are run in the tiger
 * namespace, so amke sure to have this namesapce created on your kubernetes cluster. The following
 * external resources are used, so make sure your helm installation has access to the bitnami repo:
 *
 * <ul>
 *   <li>bitnami/nginx
 * </ul>
 */
@Slf4j
class TestHelmChartServerIT extends AbstractTigerCloudTest {

  // -----------------------------------------------------------------------------------------------------------------
  //
  // check missing mandatory props are detected
  // check for helmchart hostname is not allowed
  //
  @ParameterizedTest
  @CsvSource({
    "testHelmChart,type",
    "testHelmChart,source",
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

  @Test
  void testCheckPodnameNotSetFails_NOK() {
    createTestEnvMgrSafelyAndExecute(
        "src/test/resources/de/gematik/test/tiger/testenvmgr/testHelmChart.yaml",
        envMgr -> {
          CfgServer srv = envMgr.getConfiguration().getServers().get("testHelmChart");
          CfgHelmChartOptions options = srv.getHelmChartOptions();
          ReflectionTestUtils.setField(options, "podName", null);
          assertThatThrownBy(
                  () -> envMgr.createServer("blub", srv).assertThatConfigurationIsCorrect())
              .isInstanceOf(TigerTestEnvException.class)
              .hasMessageContaining("property podName");
        });
  }

  @Test
  void testCheckSourceNotSingleListFails_NOK() {
    createTestEnvMgrSafelyAndExecute(
        "src/test/resources/de/gematik/test/tiger/testenvmgr/testHelmChart.yaml",
        envMgr -> {
          CfgServer srv = envMgr.getConfiguration().getServers().get("testHelmChart");
          CfgHelmChartOptions options = srv.getHelmChartOptions();
          ReflectionTestUtils.setField(
              srv, "source", new ArrayList<>(List.of("./hello-world", "./hello-world-2")));
          assertThatThrownBy(
                  () -> envMgr.createServer("blub", srv).assertThatConfigurationIsCorrect())
              .isInstanceOf(TigerTestEnvException.class)
              .hasMessageContaining("property source set");
        });
  }

  @Test
  void testHostnameForHelmChartNotAllowed_NOK() {
    createTestEnvMgrSafelyAndExecute(
        "src/test/resources/de/gematik/test/tiger/testenvmgr/testHelmChart_WithHostname.yaml",
        envMgr -> {
          CfgServer srv = envMgr.getConfiguration().getServers().get("testHelmChartWithHostname");
          assertThatThrownBy(
                  () -> envMgr.createServer("blub", srv).assertThatConfigurationIsCorrect())
              .isInstanceOf(TigerTestEnvException.class)
              .hasMessageContaining("hostname must not be set");
        });
  }

  // -----------------------------------------------------------------------------------------------------------------
  //
  // check starting simple local hello world and bitnami nginx are started
  //
  @ParameterizedTest
  @ValueSource(strings = {"testHelmChart", "testHelmChart_Nginx"})
  void testSetUpEnvironment_OK(String cfgFileName) {
    log.info("Starting testSetUpEnvironment_OK for {}", cfgFileName);
    createTestEnvMgrSafelyAndExecute(
        envMgr -> envMgr.setUpEnvironment(),
        "src/test/resources/de/gematik/test/tiger/testenvmgr/" + cfgFileName + ".yaml");
  }

  // -----------------------------------------------------------------------------------------------------------------
  //
  // check invalid values are detected / caught correctly
  //

  @ParameterizedTest
  @ValueSource(strings = {"testHelmChart_Nginx_wrongNamespace"})
  void testSetUpEnvironment_NameSpaceUsedButNotCreatedYet_NOK(String cfgFileName) {
    log.info("Starting testSetUpEnvironment_NameSpaceUsedButNotCreatedYet_NOK for {}", cfgFileName);
    assertThatThrownBy(
            () ->
                createTestEnvMgrSafelyAndExecute(
                    TigerTestEnvMgr::setUpEnvironment,
                    "src/test/resources/de/gematik/test/tiger/testenvmgr/" + cfgFileName + ".yaml"))
        .isInstanceOf(TigerEnvironmentStartupException.class)
        .cause()
        .hasMessageContaining(HelmChartServer.FAILED_START_MESSAGE);
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "testHelmChart_Nginx_wrongExposedPort$" + HelmChartServer.EXPOSED_PORT_MESSAGE,
        "testHelmChart_Nginx_wrongExposedPort2$" + HelmChartServer.EXPOSED_PORT_MESSAGE,
        "testHelmChart_Nginx_wrongExposedPort3$" + HelmChartServer.EXPOSED_PORT_MESSAGE,
        "testHelmChart_Nginx_wrongExposedPort4$" + HelmChartServer.EXPOSED_PORT_MESSAGE,
        "testHelmChart_Nginx_wrongExposedPort5$" + HelmChartServer.EXPOSED_PORT_MESSAGE,
      },
      delimiter = '$')
  @DisplayName("Checking wrong exposed port scenario {0}")
  void testSetUpEnvironment_WrongExposedPort_NOK(String cfgFileName, String errorMessage) {
    log.info("Starting testSetUpEnvironment_WrongExposedPort_NOK for {}", cfgFileName);
    createTestEnvMgrSafelyAndExecute(
        tigerTestEnvMgr -> {
          assertThatThrownBy(
                  () -> {
                    tigerTestEnvMgr.setUpEnvironment();
                  })
              .isInstanceOf(TigerEnvironmentStartupException.class)
                  .cause()
              .hasMessageContaining(errorMessage);
          log.info("Test for {} passed", cfgFileName);
        },
        "src/test/resources/de/gematik/test/tiger/testenvmgr/" + cfgFileName + ".yaml");
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "testHelmChart_WrongForwardPort$" + KubeUtils.PORT_EXCEPTION_MESSAGE,
        "testHelmChart_SourceNotExist$" + HelmChartServer.FAILED_START_MESSAGE,
        "testHelmChart_Nginx_wrongVersion$" + HelmChartServer.FAILED_START_MESSAGE,
        "testHelmChart_SourceList$" + HelmChartServer.SOURCE_MESSAGE,
        "testHelmChart_setHostName$" + HelmChartServer.HOST_NAME_MESSAGE,
        "testHelmChart_OptionsNoPodName$" + HelmChartServer.PORT_NAME_MESSAGE,
        "testHelmChart_OptionsNoValuePodName$" + HelmChartServer.PORT_NAME_MESSAGE,
        "testHelmChart_OptionsInvalidPodName$" + HelmChartServer.FAILED_START_MESSAGE,
        "testHelmChart_OptionsInvalidPodName2$" + HelmChartServer.FAILED_START_MESSAGE,
      },
      delimiter = '$')
  @DisplayName("Checking invalid config values scenario {0}")
  void testSetUpEnvironment_InvalidConfigValues_NOK(String cfgFileName, String errorMessage) {
    log.info("Starting testSetUpEnvironment_InvalidConfigValues_NOK for {}", cfgFileName);
    createTestEnvMgrSafelyAndExecute(
        tigerTestEnvMgr -> {
          assertThatThrownBy(
                  () -> {
                    tigerTestEnvMgr.setUpEnvironment();
                  })
              .isInstanceOf(TigerEnvironmentStartupException.class)
              .cause()
              .hasMessageContaining(errorMessage);
          log.info("Test for {} passed", cfgFileName);
        },
        "src/test/resources/de/gematik/test/tiger/testenvmgr/" + cfgFileName + ".yaml");
  }

  // -----------------------------------------------------------------------------------------------------------------
  //
  // check nginx is started correctly and is reachable via the exposed port
  //

  @Test
  @TigerTest(
      tigerYaml =
          """
        localProxyActive: false
        servers:
          tigerNginxLiveness:
            type: helmChart
            startupTimeoutSec: 180
            source:
              - bitnami/nginx
            version: 16.0.3
            helmChartOptions:
              debug: true
              podName: tiger-nginx-liveness
              nameSpace: tiger
              logPods:
                - tiger-nginx-liveness.*
              exposedPorts:
                - tiger-nginx-liveness.*, 8080:80 , 8081:80""")
  void testSetUpEnvironment_CheckLiveness_OK(TigerTestEnvMgr tigerTestEnvMgr) {
    try {
      log.info("Starting testSetUpEnvironment_CheckLiveness_OK");
      final UnirestInstance unirestInstance = Unirest.spawnInstance();
      assertThat(unirestInstance.get("http://127.0.0.1:8080").asString().getStatus())
          .isEqualTo(200);
      assertThat(unirestInstance.get("http://127.0.0.1:8081").asString().getStatus())
          .isEqualTo(200);
    } catch (Throwable t) {
      log.error("Exception occurred:", t);
      fail();
    } finally {
      if (tigerTestEnvMgr != null) {
        tigerTestEnvMgr.shutDown();
        TigerGlobalConfiguration.reset();
      }
    }
  }

  @Test
  @Disabled("This test is only run locally as it needs locally deployed genua ZT poc images")
  @ValueSource(strings = {""})
  void testSetUpEnvironment_Genua_ZT_OK() {
    String cfgFileName = "testHelmChart_Genua_ZT";
    log.info("Starting testSetUpEnvironment_Genua_ZT_OK for {}", cfgFileName);
    createTestEnvMgrSafelyAndExecute(
        TigerTestEnvMgr::setUpEnvironment,
        "src/test/resources/de/gematik/test/tiger/testenvmgr/" + cfgFileName + ".yaml");
  }
}
