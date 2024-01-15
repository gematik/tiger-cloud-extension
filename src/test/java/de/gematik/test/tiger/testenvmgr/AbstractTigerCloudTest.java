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
