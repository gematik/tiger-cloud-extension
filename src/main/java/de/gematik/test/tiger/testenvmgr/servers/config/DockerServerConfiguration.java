package de.gematik.test.tiger.testenvmgr.servers.config;

import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class DockerServerConfiguration extends CfgServer {
  private CfgDockerOptions dockerOptions = new CfgDockerOptions();
}
