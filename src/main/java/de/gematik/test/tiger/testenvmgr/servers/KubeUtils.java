package de.gematik.test.tiger.testenvmgr.servers;

import de.gematik.test.tiger.common.data.config.CfgHelmChartOptions;
import de.gematik.test.tiger.testenvmgr.servers.log.TigerStreamLogFeeder;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import lombok.Setter;
import org.apache.commons.collections.ListUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Bundles all functionality about running helm und kubtctl calls directly on OS level (using {@link ProcessBuilder}).
 */

public class KubeUtils {

    public static final String PORT_EXCEPTION_MESSAGE = "Failed to start kubectl port forward for command ";

    /**
     * full path to the helm command found in one of the folders of the PATH env variable on your local system or null.
     */
    private final String helmCommand;
    /**
     * full path to the kubectl command found in one of the folders of the PATH env variable on your local system or null.
     */
    private final String kubeCtlCommand;
    private final AbstractTigerServer tigerServer;
    /**
     * current working directory the kubectl / helm commands should be working out of. Specified in helmChartOption: workingDir
     */
    @Setter
    private String workingDirectory;
    /**
     * Logger of the related tiger server using this helper class to run a helm chart.
     */
    private final Logger log;

    private final Executor executor;
    private final CopyOnWriteArrayList<Process> processes = new CopyOnWriteArrayList<>();


    /**
     * store the status of all pods so that you can log only state changes to console.
     */
    private final Map<String, TigerServerStatus> startupPhaseStatus = new HashMap<>();

    public KubeUtils(AbstractTigerServer server, Executor executor) {
        final String executableExtension = System.getProperty("os.name").startsWith("Win") ? ".exe" : "";
        helmCommand = server.findCommandInPath("helm" + executableExtension);
        kubeCtlCommand = server.findCommandInPath("kubectl" + executableExtension);
        this.tigerServer = server;
        this.executor = executor;
        this.log = tigerServer.getLog();
    }

    public void setKubernetesContext(String context) {
        if (context == null) {
            return;
        }
        log.info("Setting kubernetes context for helm chart {} to {}...", tigerServer.getServerId(), context);

        final ProcessBuilder processBuilder = new ProcessBuilder()
            .command(kubeCtlCommand, "config", "use-context", context)
            .redirectErrorStream(true);
        applyEnvPropertiesToProcess(processBuilder);
        int exitCode = getSafely(spinUpNewExternalProcess(processBuilder),
            "set context " + context + " for server " + tigerServer.getServerId()).exitValue();
        if (exitCode != 0) {
            throw new TigerTestEnvException("Setting context '%s' yielded an error exit code from kubectl"
                + " for server %s!", context, tigerServer.getServerId());
        }
    }

    public CompletableFuture<Process> startupHelmChart() {
        final ProcessBuilder processBuilder = new ProcessBuilder()
            .command(ListUtils.union(List.of(helmCommand), buildStartupCommandOptions()))
            .directory(new File(workingDirectory))
            .redirectErrorStream(true)
            .inheritIO();

        applyEnvPropertiesToProcess(processBuilder);
        return spinUpNewExternalProcess(processBuilder);
    }

    private List<String> buildStartupCommandOptions() {
        List<String> command = new ArrayList<>();
        command.add("upgrade");
        command.add("--install");
        CfgHelmChartOptions options = tigerServer.getConfiguration().getHelmChartOptions();
        if (options.isDebug()) {
            command.add("--debug");
        }
        if (tigerServer.getConfiguration().getVersion() != null) {
            command.add("--version");
            command.add(tigerServer.getConfiguration().getVersion());
        }
        if (options.getNameSpace() != null) {
            command.add("--namespace");
            command.add(options.getNameSpace());
        }
        if (options.getValues() != null) {
            options.getValues().forEach(value -> {
                command.add("--set");
                command.add(value);
            });
        }
        command.add("--timeout");
        command.add(tigerServer.getConfiguration().getStartupTimeoutSec() + "s");
        command.add(options.getPodName());
        command.add(tigerServer.getConfiguration().getSource().get(0));
        return command;
    }

    public void exposePortsViaKubectl(CfgHelmChartOptions options) {

        List<String> exposedPorts = options.getExposedPorts();
        List<String> serviceNames = getKubernetesServices();
        exposedPorts.parallelStream()
                .map(entry -> entry.replaceAll("\\s",""))
                .map(colonSeparatedValues -> Arrays.stream(colonSeparatedValues.split(",")).collect(Collectors.toList()))
                .forEach(values -> {
                    Optional<String> serviceName = serviceNames.stream().filter(svcName -> svcName.equals(values.get(0)) || svcName.matches(values.get(0))).findAny();
                    if (serviceName.isPresent()) {
                        String podName = serviceName.get();
                        log.info("Exposing ports {} of service {} for helm chart {}...", values.subList(1, values.size()), podName, tigerServer.getServerId());
                        List<String> cmd = new ArrayList<>();
                        cmd.add(kubeCtlCommand);
                        cmd.add("--namespace");
                        cmd.add(getNameSpaceOrDefault(options));
                        cmd.add("port-forward");
                        cmd.add("service/"+podName);
                        cmd.addAll(values.subList(1, values.size()));
                         final ProcessBuilder processBuilder = new ProcessBuilder()
                                .command(cmd)
                                .inheritIO()
                                .redirectErrorStream(true);
                        checkKubeCtlPortForwarding(processBuilder, StringUtils.join(cmd, " "));
                    }
                });
    }

    private static String getNameSpaceOrDefault(CfgHelmChartOptions options) {
        return getNameSpaceOrDefault(options.getNameSpace());
    }
    private static String getNameSpaceOrDefault(String nameSpace) {
        return nameSpace != null ? nameSpace : "default";
    }

    private List<String> getKubernetesStatusLines(String nameSpace) {
        final ProcessBuilder processBuilder = new ProcessBuilder()
            .command(kubeCtlCommand, "get", "pods", "-o", "wide", "-n", getNameSpaceOrDefault(nameSpace))
            .redirectErrorStream(true);
        return getSafely(spinUpNewExternalProcess(processBuilder)
            .thenApplyAsync(process -> {
                InputStream input = process.getInputStream();
                try {
                    return Arrays.stream(IOUtils.toString(input, StandardCharsets.UTF_8).split("\n"))
                        .skip(1)
                        .collect(Collectors.toList());
                } catch (IOException e) {
                    throw new TigerTestEnvException("Unable to retrieve list of pods from kubernetes cluster!", e);
                }
            }), "get list of pods for server " + tigerServer.getServerId());
    }

    private List<String> getKubernetesServices() {
        final String nameSpace =  getNameSpaceOrDefault(tigerServer.getConfiguration().getHelmChartOptions());
        final ProcessBuilder processBuilder = new ProcessBuilder()
            .command(kubeCtlCommand, "get", "services", "-n", nameSpace)
            .redirectErrorStream(true);
        return getSafely(spinUpNewExternalProcess(processBuilder)
            .thenApplyAsync(process -> {
                InputStream input = process.getInputStream();
                try {
                    return Arrays.stream(IOUtils.toString(input, StandardCharsets.UTF_8).split("\n"))
                        .skip(1)
                        .map(line -> line.substring(0, line.indexOf(" ")))
                        .collect(Collectors.toList());
                } catch (IOException e) {
                    throw new TigerTestEnvException("Unable to retrieve list of services from kubernetes cluster!", e);
                }
            }), "get list of services in cluster for server " + tigerServer.getServerId());
    }

    private void checkKubeCtlPortForwarding(ProcessBuilder processBuilder, String command) {
        boolean hasExited;
        try {
            hasExited = getSafely(spinUpNewExternalProcess(processBuilder), "start kubectl port forward").waitFor(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("Failed to start kubectl port forward - InterruptedException {}", command);
            throw new TigerTestEnvException("Failed to start kubectl port forward - InterruptedException {}", e);
        }
        if (hasExited) {
            log.error(PORT_EXCEPTION_MESSAGE +" {}", command);
            throw new TigerTestEnvException(PORT_EXCEPTION_MESSAGE + "\"%s\"! Please check exposedPorts in your tiger.yaml!",
                command);
        }
    }

    private Optional<String> getStatusLineForPod(List<String> statusLines, String podName) {
        return statusLines.stream()
            .filter(line -> {
                String podnm = line.split(" +")[0];
                return podnm.matches(podName) || podnm.equals(podName);
            }).findFirst();
    }

    public long getNumOfPodsOnStatusList(String nameSapce) {
        return getKubernetesStatusLines(nameSapce).stream()
            .map(line -> line.split(" +"))
            .filter(columns -> tigerServer.getConfiguration().getHelmChartOptions().getHealthcheckPods().stream()
                .anyMatch(podName -> columns[0].equals(podName) || columns[0].matches(podName)))
            .count();
    }

    public long getNumOfRunningPods(String nameSpace) {
        List<String> statusLines = getKubernetesStatusLines(nameSpace);

        return tigerServer.getConfiguration().getHelmChartOptions().getHealthcheckPods().stream()
            .filter(podName -> isPodRunning(podName, statusLines))
            .count();
    }


    private boolean isPodRunning(String podName, List<String> statusLines) {
        return statusLines.stream()
            .map(line -> line.split(" +"))
            .filter(columns -> columns[0].equals(podName) || columns[0].matches(podName))
            .anyMatch(columns -> {
                TigerServerStatus newStatus = getTigerServerStatusFromKubeCtlStatus(columns);
                if (startupPhaseStatus.getOrDefault(podName, TigerServerStatus.NEW) != newStatus) {
                    startupPhaseStatus.put(podName, newStatus);
                    if (newStatus == TigerServerStatus.STOPPED) {
                        log.warn("Status of pod {} STOPPED ({}) unexpectedly", columns[0], columns[2]);
                    } else {
                        if (tigerServer.getConfiguration().getHelmChartOptions().isDebug()) {
                            log.info("Status of pod {} switched to {}", columns[0], newStatus);
                        }
                    }
                }
                return newStatus == TigerServerStatus.RUNNING;
            });
    }

    private static TigerServerStatus getTigerServerStatusFromKubeCtlStatus(String[] columns) {
        TigerServerStatus newStatus;
        switch (columns[2]) {
            case "ContainerCreating":
            case "Pending":
                newStatus = TigerServerStatus.STARTING;
                break;
            case "Running":
                String[] ready = columns[1].split("/");
                if (ready.length == 2  &&  ready[0].equals(ready[1])) {
                    newStatus = TigerServerStatus.RUNNING;
                } else {
                    newStatus = TigerServerStatus.STARTING;
                }
                break;
            case "Terminating":
            case "ImagePullBackOff":
            case "ErrImagePull":
            case "Error":
            default:
                newStatus = TigerServerStatus.STOPPED;
                break;
        }
        return newStatus;
    }

    public Optional<CompletableFuture<Process>> shutdownHelm(String nameSpace) {
        ProcessBuilder processBuilder = new ProcessBuilder()
            .command(helmCommand, "list", "-n", getNameSpaceOrDefault(nameSpace));
        applyEnvPropertiesToProcess(processBuilder);
        final CompletableFuture<Process> shutdownFuture = spinUpNewExternalProcess(processBuilder);
        try {
            String list = IOUtils.toString(getSafely(shutdownFuture, "list helm charts").getInputStream(), StandardCharsets.UTF_8);
            if (Arrays.stream(list.split("\n")).noneMatch(chartName -> chartName.startsWith(tigerServer.getConfiguration().getHelmChartOptions().getPodName()))) {
                log.warn("Helm chart {} not listed, no need to issue shutdown command!",
                    tigerServer.getConfiguration().getHelmChartOptions().getPodName());
                return Optional.empty();
            }
        } catch (IOException e) {
            throw new TigerTestEnvException("Unable to obtain list of helm charts! Trying to shutdown nevertheless.", e);
        }

        processBuilder = new ProcessBuilder()
            .command(helmCommand, "uninstall", "-n", getNameSpaceOrDefault(nameSpace), "--wait", tigerServer.getConfiguration().getHelmChartOptions().getPodName());
        applyEnvPropertiesToProcess(processBuilder);
        return Optional.of(spinUpNewExternalProcess(processBuilder));
    }

    private CompletableFuture<Process> spinUpNewExternalProcess(ProcessBuilder processBuilder) {
        return CompletableFuture.supplyAsync(() -> {
                try {
                    log.debug("Starting process {}", processBuilder.command());
                    return processBuilder.start();
                } catch (IOException e) {
                    throw new TigerTestEnvException(e, "Unable to start helm chart '%s'!", tigerServer.getServerId());
                }
            }, executor)
            .thenApplyAsync(process -> {
                process.onExit().thenApply(processes::remove);
                processes.add(process);
                return process;
            });
    }

    public <T> T getSafely(CompletableFuture<T> future, String cmdText) {
        try {
            return future.get();
        } catch (ExecutionException e) {
            throw new TigerTestEnvException(e, "Error while executing command %s", cmdText);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TigerTestEnvException(e, "Interruption received while executing command %s", cmdText);
        }
    }

    public void addLogForPod(String podName, String nameSpace) {
        List<String> statusLines = getKubernetesStatusLines(nameSpace);
        Optional<String> optPod = getStatusLineForPod(statusLines, podName);
        optPod.ifPresent(s -> startLog(s.substring(0, s.indexOf(" "))));
    }

    private void startLog (String podName) {
        log.info("Starting log for pod {}", podName);
        final ProcessBuilder processBuilder = new ProcessBuilder()
                .command(kubeCtlCommand, "logs", podName, "-n", tigerServer.getConfiguration().getHelmChartOptions().getNameSpace(), "-f")
                .redirectErrorStream(true);
        spinUpNewExternalProcess(processBuilder).thenAccept(process -> {
            new TigerStreamLogFeeder(log, process.getInputStream(), Level.INFO);
            new TigerStreamLogFeeder(log, process.getErrorStream(), Level.ERROR);
        });
    }

    private void applyEnvPropertiesToProcess(ProcessBuilder processBuilder) {
        processBuilder.environment().putAll(tigerServer.getEnvironmentProperties().stream()
            .map(str -> str.split("=", 2))
            .filter(ar -> ar.length == 2)
            .collect(Collectors.toMap(
                ar -> ar[0].trim(),
                ar -> ar[1].trim()
            )));
    }

    public void stopAllProcesses() {
        for (Process process : processes) {
            if (process.isAlive()) {
                log.info("Destroying process calling kubernetes/helm {} ({})",
                    process.pid(),
                    process.info().commandLine().orElse(""));
                process.destroy();
            }
        }
    }
}
