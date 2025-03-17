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

import static org.awaitility.Awaitility.await;

import de.gematik.test.tiger.common.data.config.CfgHelmChartOptions;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.env.TigerServerStatusUpdate;
import de.gematik.test.tiger.testenvmgr.servers.log.TigerStreamLogFeeder;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.awaitility.core.ConditionTimeoutException;
import org.slf4j.event.Level;

/**
 * Implementation of the Tiger test environment server type "helmChart". It starts a helm chart on
 * your local / remote kubernetes cluster using the {@link KubeUtils} helper.
 */
@TigerServerType("helmChart")
public class HelmChartServer extends AbstractExternalTigerServer {

  public static final String FAILED_START_MESSAGE = "Failed to start helm chart for server";

  public static final String SOURCE_MESSAGE =
      "Server {} must have property source set and contain exactly one entry!";

  public static final String PORT_NAME_MESSAGE =
      "Server {} must have property podName set for helm chart servers!";
  public static final String HOST_NAME_MESSAGE =
      "hostname must not be set for helm chart servers! Use podName in helmChartOptions instead!";

  public static final String EXPOSED_PORT_MESSAGE =
      "The exposedPorts should look like"
          + " \"<POD_NAME_OR_REGEX>,<LOCAL_PORT>:<FORWARDING_PORT>,<LOCAL_PORT>:<FORWARDING_PORT>*"
          + " \"! Please check your tiger.yml!";

  private final KubeUtils kubeUtils;

  public HelmChartServer(
      TigerTestEnvMgr tigerTestEnvMgr, String serverId, CfgServer configuration) {
    super(serverId, configuration, tigerTestEnvMgr);
    this.kubeUtils = new KubeUtils(this, tigerTestEnvMgr.getExecutor());
  }

  @Override
  public void assertThatConfigurationIsCorrect() {
    super.assertThatConfigurationIsCorrect();

    assertCfgPropertySet(getConfiguration(), "source");
    if (getConfiguration().getSource().size() != 1) {
      throw new TigerTestEnvException(SOURCE_MESSAGE);
    }
    if (getConfiguration().getHostname() != null) {
      throw new TigerTestEnvException(HOST_NAME_MESSAGE);
    }
    CfgHelmChartOptions options = getConfiguration().getHelmChartOptions();
    if (options.getPodName() == null) {
      throw new TigerTestEnvException(PORT_NAME_MESSAGE);
    }
    if (options.getLogPods() == null) {
      log.warn("Detected empty logPods list, adding entry list as default value");
      options.setLogPods(new ArrayList<>());
    }
    if (options.getHealthcheckPods() == null) {
      log.warn(
          "Detected empty healthcheckPod list, adding podName {} as default entry",
          getHelmChartOptions().getPodName());
      options.setHealthcheckPods(List.of(getHelmChartOptions().getPodName() + ".*"));
    }
    if (options.getWorkingDir() == null) {
      options.setWorkingDir(new File(".").getAbsolutePath());
      log.warn(
          "Working folder not specified, defaulting to current working directory {}",
          options.getWorkingDir());
    }
    File f = new File(options.getWorkingDir());
    if (!f.exists() && !f.mkdirs()) {
      throw new TigerTestEnvException("Unable to create working dir folder " + f.getAbsolutePath());
    }
    kubeUtils.setWorkingDirectory(getHelmChartOptions().getWorkingDir());
    checkExposedPorts(getHelmChartOptions().getExposedPorts());
  }

  private void checkExposedPorts(List<String> exposedPorts) {
    if (exposedPorts == null) {
      return;
    }

    exposedPorts.forEach(
        entry -> {
          entry = entry.replaceAll("\\s", "");
          List<String> singleEx = Arrays.stream(entry.split(",")).toList();
          if (singleEx.size() < 2 || !(singleEx.get(0).matches("[a-zA-Z*_.-]{3,}"))) {
            throw new TigerTestEnvException(EXPOSED_PORT_MESSAGE);
          }
          singleEx
              .subList(1, singleEx.size())
              .forEach(
                  port -> {
                    if (!(port.matches("\\d{2,5}") || port.matches("\\d{2,5}:\\d{2,5}"))) {
                      throw new TigerTestEnvException(EXPOSED_PORT_MESSAGE);
                    }
                  });
        });
  }

  @Override
  public void performStartup() {
    publishNewStatusUpdate(TigerServerStatusUpdate.builder().type(getServerTypeToken()).build());

    kubeUtils.setKubernetesContext(getHelmChartOptions().getContext());

    log.info("Checking for left over pods of helm chart {}...", getHelmChartOptions().getPodName());
    try {
      if (kubeUtils.getNumOfPodsOnStatusList(getHelmChartOptions().getNameSpace()) != 0) {
        log.warn(
            "Detected left over helm chart {}\nUninstalling before installing new version",
            getServerId());
        shutdown();
      }
    } catch (TigerTestEnvException exception) {
      log.warn(
          "Exception while checking for left over pods of helm chart {}!\n"
              + "Check your cluster setup!",
          getServerId());
      throw exception;
    }

    setStatus(
        TigerServerStatus.STARTING,
        "Starting helm chart for "
            + getServerId()
            + " from "
            + getConfiguration().getSource().get(0)
            + " as pod "
            + getConfiguration().getHelmChartOptions().getPodName());

    int exitCode;
    try {
      CompletableFuture<Process> startHelmChart = kubeUtils.startupHelmChart();
      startHelmChart.thenAccept(
          process -> {
            new TigerStreamLogFeeder(getServerId(), log, process.getInputStream(), Level.INFO);
            new TigerStreamLogFeeder(getServerId(), log, process.getErrorStream(), Level.ERROR);
            statusMessage(
                "Started helm upgrade for " + getServerId() + " with PID '" + process.pid() + "'");
          });
      exitCode = kubeUtils.getSafely(startHelmChart, "start helm chart").waitFor();
    } catch (InterruptedException e) {
      log.error("Failed to start helm chart - InterruptedException {}", getServerId());
      Thread.currentThread().interrupt();
      throw new TigerTestEnvException("Failed to start helm chart - InterruptedException {}", e);
    }
    if (exitCode != 0) {
      log.error(FAILED_START_MESSAGE + " {}, exitCode was {}", getServerId(), exitCode);
      throw new TigerTestEnvException(
          FAILED_START_MESSAGE + " %s! Please check log!", getServerId());
    } else {
      waitForServerUp();
      logAllPods();
      if (getHelmChartOptions().getExposedPorts() != null) {
        kubeUtils.exposePortsViaKubectl(getHelmChartOptions());
      }
    }
  }

  private void logAllPods() {
    getHelmChartOptions().getLogPods().parallelStream()
        .forEach(podName -> kubeUtils.addLogForPod(podName, getHelmChartOptions().getNameSpace()));
  }

  @Override
  public TigerServerStatus updateStatus(boolean quiet) {
    try {
      log.debug("Getting status of helm chart {}...", getServerId());
      setStatus(
          kubeUtils.getNumOfRunningPods(getHelmChartOptions().getNameSpace())
                  == getHelmChartOptions().getHealthcheckPods().size()
              ? TigerServerStatus.RUNNING
              : TigerServerStatus.STARTING);
      return getStatus();
    } catch (TigerTestEnvException ttException) {
      RuntimeException ex =
          new TigerTestEnvException(
              "Unable to look up kubernetes pods for helm chart " + getServerId(), ttException);
      if (!quiet) {
        setStatus(TigerServerStatus.STOPPED, ex.getMessage());
        throw ex;
      } else {
        return getStatus();
      }
    }
  }

  @Override
  boolean isHealthCheckNone() {
    return false;
  }

  @Override
  public Optional<String> getHealthcheckUrl() {
    if (getHelmChartOptions().getContext() != null) {
      return Optional.of("kubernetes cluster context '" + getHelmChartOptions().getContext() + "'");
    } else {
      return Optional.of("kubernetes cluster");
    }
  }

  @Override
  public void shutdown() {
    log.info("Stopping helm chart {}...", getServerId());
    if (getConfiguration().getHelmChartOptions().getPodName() == null) {
      log.warn(
          "Helm chart pod name not specified in tiger.yaml under helmChartOptions -> podName. "
              + "No helm chart could have been started!");
      kubeUtils.stopAllProcesses();
      return;
    }
    kubeUtils.setKubernetesContext(getHelmChartOptions().getContext());
    kubeUtils.stopAllProcesses();

    final Optional<CompletableFuture<Process>> shutdownFuture =
        kubeUtils.shutdownHelm(getHelmChartOptions().getNameSpace());
    shutdownFuture.ifPresent(
        future -> {
          try {
            if (kubeUtils.getSafely(future, "shutdown helm chart").waitFor() != 0) {
              log.error("Failed to uninstall helm chart {}", getServerId());
              setStatus(
                  TigerServerStatus.STOPPED,
                  "Failed to stop helm chart " + getServerId() + ". Please clean up manually!");
              return;
            }
            waitForShutdownToComplete();
            setStatus(TigerServerStatus.STOPPED, "Helm chart " + getServerId() + " deleted");
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            String msg =
                "Timeout while stopping helm chart "
                    + getServerId()
                    + ".\nPlease clean up manually!";
            setStatus(TigerServerStatus.STOPPED, msg);
            throw new TigerTestEnvException(msg, ie);
          } catch (TigerTestEnvException tteException) {
            String msg =
                "Failed to stop helm chart "
                    + getServerId()
                    + ".\n"
                    + tteException.getMessage()
                    + "\nPlease clean up manually!";
            setStatus(TigerServerStatus.STOPPED, msg);
            throw new TigerTestEnvException(msg, tteException);
          }
        });
  }

  private void waitForShutdownToComplete() {
    if (getHelmChartOptions().getHealthcheckPods().isEmpty()) {
      log.warn("No HealthcheckPods defined, assuming shutdown went well...");
    } else {
      try {
        long timems = System.currentTimeMillis();
        await()
            .atMost(getConfiguration().getStartupTimeoutSec(), TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .until(
                () -> {
                  try {
                    long runningPods =
                        kubeUtils.getNumOfPodsOnStatusList(getHelmChartOptions().getNameSpace());
                    if (getHelmChartOptions().isDebug() && runningPods != 0) {
                      log.info(
                          "{} pods of helm chart {} still present, waiting {}s ",
                          runningPods,
                          getServerId(),
                          (System.currentTimeMillis() - timems) / 1000);
                    }
                    return runningPods == 0;
                  } catch (TigerTestEnvException e) {
                    return false;
                  }
                });
      } catch (ConditionTimeoutException cte) {
        throw new TigerTestEnvException(
            "Timeout waiting for helm chart server %s shutdown!", getServerId());
      }
    }
  }

  public CfgHelmChartOptions getHelmChartOptions() {
    return getConfiguration().getHelmChartOptions();
  }
}
