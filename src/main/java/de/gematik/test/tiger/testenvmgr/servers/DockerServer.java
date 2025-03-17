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

import static de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr.HTTP;
import static de.gematik.test.tiger.testenvmgr.servers.DockerMgr.DOCKER_HOST;

import de.gematik.test.tiger.common.config.ConfigurationValuePrecedence;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import lombok.Builder;

/**
 * Implementation of the Tiger test environment server type "docker". It starts a given docker image
 * as container using the {@link DockerMgr}.
 */
@TigerServerType("docker")
public class DockerServer extends DockerAbstractServer {

  public static final DockerMgr dockerManager = new DockerMgr();

  @Builder
  public DockerServer(TigerTestEnvMgr tigerTestEnvMgr, String serverId, CfgServer configuration) {
    super(serverId, configuration, tigerTestEnvMgr);
  }

  @Override
  public void assertThatConfigurationIsCorrect() {
    super.assertThatConfigurationIsCorrect();

    assertCfgPropertySet(getConfiguration(), "version");
    assertCfgPropertySet(getConfiguration(), "source");
  }

  @Override
  public void performStartup() {
    statusMessage(
        "Starting docker container for " + getServerId() + " from '" + getDockerSource() + "'");
    dockerManager.startContainer(this);

    // add routes needed for each server to local docker proxy
    // ATTENTION only one route per server!
    if (getDockerOptions().getPorts() != null && !getDockerOptions().getPorts().isEmpty()) {
      addRoute(
          TigerConfigurationRoute.builder()
              .from(HTTP + getHostname())
              .to(
                  HTTP
                      + DOCKER_HOST.getValueOrDefault()
                      + ":"
                      + getDockerOptions().getPorts().values().iterator().next())
              .build());
    }

    statusMessage("Docker container " + getServerId() + " started");
  }

  @Override
  protected void processExports() {
    super.processExports();

    if (getDockerOptions().getPorts() != null && !getDockerOptions().getPorts().isEmpty()) {
      getConfiguration()
          .getExports()
          .forEach(
              exp -> {
                String[] kvp = exp.split("=", 2);
                String origValue = TigerGlobalConfiguration.readString(kvp[0]);
                kvp[1] = origValue;
                // ports substitution are only supported for docker based instances
                if (getDockerOptions().getPorts() != null) {
                  getDockerOptions()
                      .getPorts()
                      .forEach(
                          (localPort, externPort) ->
                              kvp[1] =
                                  kvp[1].replace(
                                      "${PORT:" + localPort + "}", String.valueOf(externPort)));
                }
                if (!origValue.equals(kvp[1])) {
                  log.info("Setting global property {}={}", kvp[0], kvp[1]);
                  TigerGlobalConfiguration.putValue(
                      kvp[0], kvp[1], ConfigurationValuePrecedence.RUNTIME_EXPORT);
                }
              });
    }
  }

  public String getDockerSource() {
    return getConfiguration().getSource().get(0);
  }

  @Override
  public void shutdown() {
    log.info("Stopping docker container {}...", getServerId());
    dockerManager.stopContainer(this);
    setStatus(TigerServerStatus.STOPPED, "Docker container " + getServerId() + " stopped");
  }
}
