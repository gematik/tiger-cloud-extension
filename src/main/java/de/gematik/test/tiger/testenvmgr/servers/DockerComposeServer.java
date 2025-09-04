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


package de.gematik.test.tiger.testenvmgr.servers;

import static de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr.HTTP;
import static de.gematik.test.tiger.testenvmgr.servers.DockerMgr.DOCKER_HOST;

import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import org.apache.commons.lang3.StringUtils;

/**
 * Implementation of the Tiger test environment server type "compose". It starts a set of docker
 * compose files using the {@link DockerMgr} provided as static member from the {@link
 * DockerServer}.
 */
@TigerServerType("compose")
public class DockerComposeServer extends DockerAbstractServer {

  private final Map<String, List<PortMapping>> serviceNameToPortMapping = new HashMap<>();

  @Builder
  public DockerComposeServer(
      String serverId, CfgServer configuration, TigerTestEnvMgr tigerTestEnvMgr) {
    super(serverId, configuration, tigerTestEnvMgr);
  }

  @Override
  public void assertThatConfigurationIsCorrect() {
    super.assertThatConfigurationIsCorrect();
    assertCfgPropertySet(getConfiguration(), "source");
    if (!StringUtils.isBlank(getConfiguration().getHostname())) {
      throw new TigerConfigurationException(
          "Hostname property is not supported for docker compose nodes!");
    }
  }

  @Override
  public void performStartup() {
    statusMessage("Starting docker compose for " + getServerId() + " from " + getDockerSource());
    DockerServer.dockerManager.startComposition(this);

    addRoutesToTigerProxy();
    statusMessage("Docker compose " + getServerId() + " started");
  }

  private void addRoutesToTigerProxy() {
    log.info("Adding routes for docker compose {} to tiger proxy", getServerId());

    serviceNameToPortMapping.forEach(
        (serviceName, portMappings) ->
            portMappings.forEach(
                portMapping ->
                    addRoute(
                        TigerConfigurationRoute.builder()
                            .from(
                                HTTP
                                    + getServerId()
                                    + "-"
                                    + serviceName
                                    + "-"
                                    + portMapping.dockerPort())
                            .to(
                                HTTP
                                    + DOCKER_HOST.getValueOrDefault()
                                    + ":"
                                    + portMapping.hostPort())
                            .build())));
  }

  public String getDockerSource() {
    return getConfiguration().getSource().get(0);
  }

  public List<String> getSource() {
    if (getConfiguration().getSource() == null) {
      return List.of();
    }
    return Collections.unmodifiableList(getConfiguration().getSource());
  }

  public void addPortMapping(String serviceName, PortMapping portMapping) {
    serviceNameToPortMapping.computeIfAbsent(serviceName, k -> new ArrayList<>()).add(portMapping);
  }

  @Override
  public void shutdown() {
    log.info("Stopping docker compose {}...", getServerId());
    DockerServer.dockerManager.stopComposeContainer(this);
    setStatus(TigerServerStatus.STOPPED, "Docker compose " + getServerId() + " stopped");
  }
}
