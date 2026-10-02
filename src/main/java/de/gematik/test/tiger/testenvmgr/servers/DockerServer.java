/*
 *
 * Copyright 2023-2026 gematik GmbH
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

import com.google.common.annotations.VisibleForTesting;
import de.gematik.test.tiger.common.config.ConfigurationValuePrecedence;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.events.BeforeContainerStartEvent;
import de.gematik.test.tiger.testenvmgr.servers.config.CfgDockerOptions;
import de.gematik.test.tiger.testenvmgr.servers.config.DockerServerConfiguration;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Builder;
import lombok.Getter;
import lombok.val;
import org.testcontainers.containers.GenericContainer;

/**
 * Implementation of the Tiger test environment server type "docker". It starts a given docker image
 * as container using the {@link DockerMgr}.
 */
@TigerServerType("docker")
public class DockerServer extends DockerAbstractServer {

  private static DockerMgr dockerManager;

  @Getter private final List<String> injectedDnsServers = new ArrayList<>();

  @Getter private final Map<String, String> injectedEnv = new LinkedHashMap<>();

  @Builder
  public DockerServer(String serverId, CfgServer configuration, TigerTestEnvMgr tigerTestEnvMgr) {
    super(serverId, configuration, tigerTestEnvMgr);
  }

  public static synchronized DockerMgr getDockerManager() {
    if (dockerManager == null) {
      dockerManager = new DockerMgr();
    }
    return dockerManager;
  }

  public static synchronized boolean isDockerManagerInitialized() {
    return dockerManager != null;
  }

  @VisibleForTesting
  public static synchronized void setDockerManager(DockerMgr dockerManager) {
    DockerServer.dockerManager = dockerManager;
  }

  @Override
  protected void doPrepareDependencies() {
    super.doPrepareDependencies();

    if (!isInjectDnsEnabled()) {
      return;
    }
    // Implicitly add dependency on Canopy if exactly one Canopy server exists in the environment
    // so that Canopy is RUNNING and its DNS address is available when this container starts.
    findUniqueCanopyServerId().ifPresent(id -> getConfiguration().addDependsUpon(id));
  }

  private boolean isInjectDnsEnabled() {

    if (getConfiguration() instanceof DockerServerConfiguration dockerCfg) {
      return dockerCfg.getDockerOptions().isInjectDns();
    }
    // The decision to inject DNS is done before servers are started, so we do not have yet casted
    // the configuration
    // into the the DockerServerConfiguration object. Therefore we read it over the
    // getTypeSpecificConfig.
    // We can't cast it immediately here because there may be unresolved placeholders that
    // TigerGlobalConfiguration will resolve later.
    var dockerOptions = getConfiguration().getTypeSpecificConfig().get("dockerOptions");
    if (dockerOptions == null || dockerOptions.get("injectDns") == null) {
      return new CfgDockerOptions().isInjectDns(); //  to use whatever the default value is.
    }
    return dockerOptions.get("injectDns").asBoolean();
  }

  private Optional<String> findUniqueCanopyServerId() {
    var candidates =
        getTigerTestEnvMgr().getServers().values().stream()
            .filter(s -> s != this && "canopy".equals(s.getConfiguration().getType()))
            .toList();
    if (candidates.size() != 1) {
      return Optional.empty();
    }
    return Optional.of(candidates.get(0).getServerId());
  }

  @Override
  public void assertThatConfigurationIsCorrect() {
    super.assertThatConfigurationIsCorrect();

    assertCfgPropertySet(getConfiguration(), "version");
    assertCfgPropertySet(getConfiguration(), "source");
    getDockerOptions()
        .getPorts()
        .forEach(
            portExportString -> {
              String[] kvp = portExportString.split(":", 2);
              if (kvp.length != 2) {
                throw new TigerTestEnvException(
                    "Docker port mapping in server '"
                        + getServerId()
                        + "' with value '"
                        + portExportString
                        + "' is invalid. Use the format '<host-port>:<container-port>'");
              }
            });
  }

  @Override
  public void performStartup() {
    statusMessage(
        "Starting docker container for " + getServerId() + " from '" + getDockerSource() + "'");

    // Publish BeforeContainerStartEvent for subscribers (e.g. CanopyDnsAutoInjector), unless this
    // server has opted out of DNS injection via docker.injectDns=false.
    BeforeContainerStartEvent event =
        new BeforeContainerStartEvent(
            this,
            new ArrayList<>(getInitialDnsServers()),
            new ArrayList<>(),
            new LinkedHashMap<>(getInitialEnvironment()),
            getDockerOptions() != null && getDockerOptions().isInjectDns());
    getTigerTestEnvMgr().getLifecycleEventBus().publish(event);
    // Apply the mutated DNS servers and environment variables to the state
    injectedDnsServers.clear();
    injectedDnsServers.addAll(event.getDnsServers());
    injectedEnv.clear();
    injectedEnv.putAll(event.getExtraEnv());

    syncInjectedEnvToConfiguration(event);

    getDockerManager().startContainer(this);

    // add routes needed for each server to local docker proxy
    // ATTENTION only one route per server!
    if (getDockerOptions().getPorts() != null && !getDockerOptions().getPorts().isEmpty()) {
      final String targetHostPort = getDockerOptions().getPorts().get(0).split(":")[0];
      log.info(
          "Adding route for docker server {}: TO={}:{}",
          getServerId(),
          DOCKER_HOST.getValueOrDefault(),
          targetHostPort);
      addRoute(
          TigerConfigurationRoute.builder()
              .from(HTTP + getHostname())
              .to(HTTP + DOCKER_HOST.getValueOrDefault() + ":" + targetHostPort)
              .build());
    }

    statusMessage("Docker container " + getServerId() + " started");
  }

  private List<String> getInitialDnsServers() {
    return List.of();
  }

  private Map<String, String> getInitialEnvironment() {
    Map<String, String> envMap = new LinkedHashMap<>();
    if (getConfiguration().getEnvironment() != null) {
      for (String envStr : getConfiguration().getEnvironment()) {
        if (envStr != null && envStr.contains("=")) {
          String[] parts = envStr.split("=", 2);
          envMap.put(parts[0], parts[1]);
        }
      }
    }
    return envMap;
  }

  private void syncInjectedEnvToConfiguration(BeforeContainerStartEvent event) {
    if (!event.getExtraEnv().isEmpty()) {
      event
          .getExtraEnv()
          .forEach(
              (k, v) -> {
                getConfiguration().getEnvironment().removeIf(e -> e.startsWith(k + "="));
                getConfiguration().getEnvironment().add(k + "=" + v);
              });
    }
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
                          (entry) -> {
                            val pairs = entry.split(":");
                            kvp[1] =
                                kvp[1].replace(
                                    "${PORT:" + pairs[0] + "}", String.valueOf(pairs[1]));
                          });
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

  @VisibleForTesting
  public GenericContainer<?> getDockerContainer() {
    return dockerManager.getDockerContainers().get(getServerId());
  }

  @Override
  public void shutdown() {
    log.info("Stopping docker container {}...", getServerId());
    if (isDockerManagerInitialized()) {
      getDockerManager().stopContainer(this);
    }
    setStatus(TigerServerStatus.STOPPED, "Docker container " + getServerId() + " stopped");
  }
}
