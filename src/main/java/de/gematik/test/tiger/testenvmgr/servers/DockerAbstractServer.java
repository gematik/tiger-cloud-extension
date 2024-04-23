package de.gematik.test.tiger.testenvmgr.servers;

import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.servers.config.CfgDockerOptions;
import de.gematik.test.tiger.testenvmgr.servers.config.DockerServerConfiguration;

public abstract class DockerAbstractServer extends AbstractExternalTigerServer {

  protected DockerAbstractServer(
      String hostname, String serverId, CfgServer configuration, TigerTestEnvMgr tigerTestEnvMgr) {
    super(hostname, serverId, configuration, tigerTestEnvMgr);
  }

  @Override
  public Class<? extends CfgServer> getConfigurationBeanClass() {
    return DockerServerConfiguration.class;
  }

  public CfgDockerOptions getDockerOptions() {
    return ((DockerServerConfiguration) getConfiguration()).getDockerOptions();
  }
}
