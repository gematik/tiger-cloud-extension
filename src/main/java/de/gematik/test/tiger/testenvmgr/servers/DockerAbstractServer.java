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
