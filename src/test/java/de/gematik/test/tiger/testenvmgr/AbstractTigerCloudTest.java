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

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.ThrowingConsumer;

/**
 * Helper test class providing methods to start the test env mgr with a given tiger yaml config
 * file.
 */
public class AbstractTigerCloudTest {

  public void createTestEnvMgrSafelyAndExecute(
      String configurationFilePath, ThrowingConsumer<TigerTestEnvMgr> testEnvMgrConsumer) {
    TigerTestEnvMgr envMgr = null;
    try {
      if (StringUtils.isEmpty(configurationFilePath)) {
        TigerGlobalConfiguration.initialize();
      } else {
        // The reset() is important to make sure the props tree is really reset between tests as TGC
        // is a globally available
        // class / data storage provider.
        TigerGlobalConfiguration.reset();
        TigerGlobalConfiguration.initializeWithCliProperties(
            Map.of("TIGER_TESTENV_CFGFILE", configurationFilePath));
      }
      envMgr = new TigerTestEnvMgr();
      testEnvMgrConsumer.accept(envMgr);
    } finally {
      if (envMgr != null) {
        envMgr.shutDown();
      }
      TigerGlobalConfiguration.reset();
    }
  }

  public void createTestEnvMgrSafelyAndExecute(
      ThrowingConsumer<TigerTestEnvMgr> testEnvMgrConsumer, String configurationFilePath) {
    createTestEnvMgrSafelyAndExecute(configurationFilePath, testEnvMgrConsumer);
  }

  public void createTestEnvMgrSafelyAndExecute(
      ThrowingConsumer<TigerTestEnvMgr> testEnvMgrConsumer) {
    createTestEnvMgrSafelyAndExecute("", testEnvMgrConsumer);
  }
}
