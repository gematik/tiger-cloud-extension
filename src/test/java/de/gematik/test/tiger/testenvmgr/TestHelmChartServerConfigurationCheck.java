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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import org.junit.jupiter.api.Test;

class TestHelmChartServerConfigurationCheck {
  @TigerTest(
      tigerYaml =
          """
          servers:
            myHelmServer:
              type: helmChart
              helmChartOptions:
                podName: test-helm-chart
          localProxyActive: false
          """,
      skipEnvironmentSetup = true)
  @Test
  void testCheckCfgPropertiesMissingParamMandatoryProps_NOK(TigerTestEnvMgr envMgr) {
    assertThatThrownBy(envMgr::setUpEnvironment)
        .cause()
        .isInstanceOf(TigerTestEnvException.class)
        .hasMessageContaining("source");
  }

  @TigerTest(
      tigerYaml =
          """
          servers:
            myHelmServer:
              type: helmChart
              source:
                - "./hello-world"
          localProxyActive: false
          """,
      skipEnvironmentSetup = true)
  @Test
  void testCheckPodnameNotSetFails_NOK(TigerTestEnvMgr envMgr) {
    assertThatThrownBy(envMgr::setUpEnvironment)
        .cause()
        .isInstanceOf(TigerTestEnvException.class)
        .hasMessageContaining("property podName");
  }

  @TigerTest(
      tigerYaml =
          """
          servers:
            myHelmServer:
              type: helmChart
              source:
                - "./hello-world"
                - "./hello-world-2"
              helmChartOptions:
                podName: test-helm-chart
          localProxyActive: false
          """,
      skipEnvironmentSetup = true)
  @Test
  void testCheckSourceNotSingleListFails_NOK(TigerTestEnvMgr envMgr) {
    assertThatThrownBy(envMgr::setUpEnvironment)
        .cause()
        .isInstanceOf(TigerTestEnvException.class)
        .hasMessageContaining("property source set");
  }

  @TigerTest(
      tigerYaml =
          """
          servers:
            myHelmServer:
              hostname: testHelmChartWithHostname
              type: helmChart
              source:
                - ./hello-world
              helmChartOptions:
                podName: test-helm-chart
          localProxyActive: false
          """,
      skipEnvironmentSetup = true)
  @Test
  void testHostnameForHelmChartNotAllowed_NOK(TigerTestEnvMgr envMgr) {
    assertThatThrownBy(envMgr::setUpEnvironment)
        .cause()
        .isInstanceOf(TigerTestEnvException.class)
        .hasMessageContaining("hostname must not be set");
  }
}
