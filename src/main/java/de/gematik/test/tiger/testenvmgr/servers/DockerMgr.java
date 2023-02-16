/*
 * Copyright (c) 2023 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.testenvmgr.servers;

import static de.gematik.test.tiger.proxy.TigerProxy.CA_CERT_ALIAS;
import static org.awaitility.Awaitility.await;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.ContainerConfig;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.api.model.ResponseItem;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.util.TigerSerializationUtil;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.cert.Certificate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.utility.Base58;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Bundles all functionality dealing with docker containers start/stop, docker compose scripts, pulling images... It also modifies the
 * container start script and adds the Tiger Proxy certificate to the operating system (assuming LINUX) It's based on the testcontainers
 * library. Used by {@link DockerServer} and {@link DockerComposeServer}
 */
@Slf4j
@Getter
public class DockerMgr {

    private static final String BEGIN_CERT = "-----BEGIN CERTIFICATE-----";
    private static final String END_CERT = "-----END CERTIFICATE-----";
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");
    @SuppressWarnings("OctalInteger")
    private static final int MOD_ALL_EXEC = 0777;

    private static final String CLASSPATH = "classpath:";

    private static final String DOCKER_COMPOSE_PROP_EXPOSE = "expose";
    public static final String TARGET_FOLDER = "target";
    public static final String TIGER_TESTENV_MGR_FOLDER = "tiger-testenv-mgr";


    /**
     * stores a reference for each server id to the related docker container running.
     */
    private final Map<String, GenericContainer<?>> dockerContainers = new HashMap<>();
    /**
     * stores a reference for each server id to the related docker compose container running.
     */
    private final Map<String, DockerComposeContainer<?>> composeContainers = new HashMap<>();

    @SuppressWarnings("unused")
    public void startContainer(final DockerServer server) {
        String imageName = buildImageName(server);
        var testContainersImageName = DockerImageName.parse(imageName);

        pullImage(imageName);

        final var container = new GenericContainer<>(testContainersImageName); //NOSONAR
        try {
            InspectImageResponse iiResponse = container.getDockerClient().inspectImageCmd(imageName).exec();
            final ContainerConfig containerConfig = iiResponse.getConfig();
            if (containerConfig == null) {
                throw new TigerTestEnvException("Docker image '" + imageName + "' has no configuration info!");
            }
            if (server.getDockerOptions().isProxied()) {
                String[] startCmd = containerConfig.getCmd();
                String[] entryPointCmd = containerConfig.getEntrypoint();
                if (StringUtils.isNotEmpty(server.getDockerOptions().getEntryPoint())) {
                    entryPointCmd = new String[]{server.getDockerOptions().getEntryPoint()};
                }
                // erezept hardcoded
                if (entryPointCmd != null && entryPointCmd[0].equals("/bin/sh") && entryPointCmd[1].equals("-c")) {
                    entryPointCmd = new String[]{"su", containerConfig.getUser(), "-c",
                        "'" + entryPointCmd[2] + "'"};
                }
                File tmpScriptFolder = Path.of(TARGET_FOLDER, TIGER_TESTENV_MGR_FOLDER).toFile();
                if (!tmpScriptFolder.exists() && !tmpScriptFolder.mkdirs()) {
                    throw new TigerTestEnvException(
                        "Unable to create temp folder for modified startup script for server "
                            + server.getServerId());
                }
                final String scriptName = createContainerStartupScript(server, iiResponse, startCmd, entryPointCmd);
                String containerScriptPath = containerConfig.getWorkingDir() + "/" + scriptName;
                container.withExtraHost("host.docker.internal", "host-gateway");

                container.withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of(tmpScriptFolder.getAbsolutePath(), scriptName), MOD_ALL_EXEC),
                    containerScriptPath);
                container.withCreateContainerCmdModifier(
                    cmd -> cmd.withUser("root").withEntrypoint(containerScriptPath));
            }

            container.setLogConsumers(List.of(new Slf4jLogConsumer(log)));
            log.info("Passing in environment for {}...", server.getServerId());
            addEnvVarsToContainer(container, server.getEnvironmentProperties());

            if (containerConfig.getExposedPorts() != null) {
                List<Integer> ports = Arrays.stream(containerConfig.getExposedPorts())
                    .map(ExposedPort::getPort)
                    .collect(Collectors.toList());
                log.info("Exposing ports for {}: {}", server.getServerId(), ports);
                container.setExposedPorts(ports);
            }
            if (server.getDockerOptions().isOneShot()) {
                container.withStartupCheckStrategy(new OneShotStartupCheckStrategy());
            }
            container.start();

            retrieveExposedPortsAndStoreInServerConfiguration(server, container);
            // make startup time and intervall and url (supporting ${PORT} and regex content configurable
            waitForHealthyStartup(server, container);
            container.getDockerClient().renameContainerCmd(container.getContainerId())
                .withName("tiger." + server.getServerId()).exec();
            dockerContainers.put(server.getServerId(), container);
        } catch (final DockerException de) {
            throw new TigerTestEnvException("Failed to start container for server " + server.getServerId(), de);
        }
    }

    private static void retrieveExposedPortsAndStoreInServerConfiguration(DockerServer server, GenericContainer<?> container) {
        try {
            final Map<Integer, Integer> ports = new HashMap<>();
            container.getContainerInfo().getNetworkSettings().getPorts().getBindings().entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .forEach(entry -> ports.put(entry.getKey().getPort(),
                    Integer.valueOf(entry.getValue()[0].getHostPortSpec())));
            server.getDockerOptions().setPorts(ports);
        } catch (RuntimeException rte) {
            log.warn("Unable to retrieve port bindings! No startup healthcheck can be performed!", rte);
        }
    }

    private String buildImageName(DockerServer server) {
        String result = server.getDockerSource();
        if (server.getTigerTestEnvMgr() != null) {
            result = server.getTigerTestEnvMgr().replaceSysPropsInString(server.getDockerSource());
        }
        if (server.getConfiguration().getVersion() != null) {
            result += ":" + server.getConfiguration().getVersion();
        }
        return result;
    }

    public void startComposition(final DockerComposeServer server) {
        List<String> composeFileContents = new ArrayList<>();
        File[] composeFiles = collectAndProcessComposeYamlFiles(server.getServerId(), server.getSource(),
            composeFileContents);
        String identity = "tiger_" + Base58.randomString(6).toLowerCase();
        DockerComposeContainer composition = new DockerComposeContainer(identity, composeFiles); //NOSONAR

        composeFileContents.stream()
            .filter(content -> !content.isEmpty())
            .map(content -> TigerSerializationUtil.yamlToJsonObject(content).getJSONObject("services"))
            .map(JSONObject::toMap)
            .flatMap(services -> services.entrySet().stream())
            .forEach(serviceEntry -> {
                var map = ((Map<String, ?>) serviceEntry.getValue());
                if (map.containsKey(DOCKER_COMPOSE_PROP_EXPOSE)) {
                    ((List<Integer>) map.get(DOCKER_COMPOSE_PROP_EXPOSE)).forEach(
                        port -> {
                            log.info("Exposing service {} with port {}", serviceEntry.getKey(), port);
                            composition.withExposedService(serviceEntry.getKey(), port);
                        }
                    );
                } else if (map.containsKey("ports")) {
                    ((List<String>) map.get("ports")).forEach(
                        portString -> {
                            if (!portString.contains(":")) {
                                log.warn("Docker compose with ephemeral host ports not supported as of now"
                                    + ", please specify manual host port for '{}'", portString);
                            } else {
                                int port = Integer.parseInt(portString.split(":")[0]);
                                log.info("Exposing service {} with port {}", serviceEntry.getKey(), port);
                                composition.withExposedService(serviceEntry.getKey(), port);
                            }
                        }
                    );
                }
                composition.withLogConsumer(serviceEntry.getKey(), new Slf4jLogConsumer(log));
            });
        // ALERT! Jenkins only works with local docker compose!
        // So do not change this unless you VERIFIED it also works on Jenkins builds
        composition.withLocalCompose(true).withOptions("--compatibility").start(); //NOSONAR

        composeFileContents.stream()
            .filter(content -> !content.isEmpty())
            .map(content -> TigerSerializationUtil.yamlToJsonObject(content).getJSONObject("services"))
            .map(JSONObject::toMap)
            .flatMap(services -> services.entrySet().stream())
            .forEach(serviceEntry -> {
                var map = ((Map<String, ?>) serviceEntry.getValue());
                if (map.containsKey(DOCKER_COMPOSE_PROP_EXPOSE)) {
                    ((List<Integer>) map.get(DOCKER_COMPOSE_PROP_EXPOSE)).forEach(port -> {
                            log.info("Service {} with port {} exposed via {}", serviceEntry.getKey(), port,
                                composition.getServicePort(serviceEntry.getKey(), port));
                            ListContainersCmd cmd = DockerClientFactory.instance().client()
                                .listContainersCmd();
                            log.debug("Inspecting docker container: {}", cmd.exec().toString());
                        }
                    );
                }
                composition.withLogConsumer(serviceEntry.getKey(), new Slf4jLogConsumer(log));
            });
        composeContainers.put(server.getServerId(), composition);

    }

    private File[] collectAndProcessComposeYamlFiles(String serverId, List<String> composeFilePaths,
        List<String> composeFileContents) {
        var folder = Paths.get(TARGET_FOLDER, TIGER_TESTENV_MGR_FOLDER, serverId).toFile();
        return composeFilePaths.stream()
            .map(filePath -> {
                String content = readAndProcessComposeFile(filePath, composeFileContents);
                return saveComposeContentToTempFile(folder, filePath, content);
            }).toArray(File[]::new);
    }

    private String readAndProcessComposeFile(String filePath, List<String> composeFileContents) {
        try {
            String content;
            if (filePath.startsWith(CLASSPATH)) {
                content = readContentFromClassPath(filePath.substring(CLASSPATH.length()));
            } else {
                content = FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8);
            }

            content = TigerGlobalConfiguration.resolvePlaceholders(content);
            composeFileContents.add(content);
            return content;
        } catch (IOException e) {
            throw new TigerTestEnvException("Unable to process compose file " + filePath, e);
        }
    }

    private String readContentFromClassPath(String filePath) {
        String content;
        try (InputStream inputStream = getClass().getResourceAsStream(filePath)) {
            if (inputStream == null) {
                throw new TigerTestEnvException("Missing docker compose file in classpath " + filePath);
            }
            content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new TigerTestEnvException("Unable to read docker compose file from classpath (" + filePath + ")",
                exception);
        }
        return content;
    }

    private File saveComposeContentToTempFile(File folder, String filePath, String content) {
        try {
            if (filePath.startsWith(CLASSPATH)) {
                filePath = filePath.substring(CLASSPATH.length());
            }
            var filename = new File(filePath).getName() + "." + UUID.randomUUID() + ".yml";
            var tmpFile = Paths.get(folder.getAbsolutePath(), filename).toFile();
            if (!tmpFile.getParentFile().exists() && !tmpFile.getParentFile().mkdirs()) {
                throw new TigerTestEnvException(
                    "Unable to create temp folder " + tmpFile.getParentFile().getAbsolutePath());
            }
            FileUtils.writeStringToFile(tmpFile, content, StandardCharsets.UTF_8);
            return tmpFile;
        } catch (IOException e) {
            throw new TigerTestEnvException("Unable to process compose file " + filePath, e);
        }
    }


    private void addEnvVarsToContainer(GenericContainer<?> container, List<String> envVars) {
        envVars.stream()
            .filter(i -> i.contains("="))
            .map(i -> i.split("=", 2))
            .forEach(envvar -> {
                log.info("  * {}={}", envvar[0], envvar[1]);
                container.addEnv(TigerGlobalConfiguration.resolvePlaceholders(envvar[0]),
                    TigerGlobalConfiguration.resolvePlaceholders(envvar[1]));
            });
    }

    public void pullImage(final String imageName) {
        log.info("Pulling docker image {}...", imageName);
        final AtomicBoolean pullComplete = new AtomicBoolean();
        pullComplete.set(false);
        final AtomicReference<Throwable> cbException = new AtomicReference<>();
        final AtomicReference<LocalDateTime> timeOfLastInfoLogMessage = new AtomicReference<>(LocalDateTime.now());
        DockerClientFactory.instance().client().pullImageCmd(imageName)
            .exec(new ResultCallback.Adapter<PullResponseItem>() {
                @Override
                public void onNext(PullResponseItem item) {
                    if (log.isDebugEnabled()) {
                        log.debug("{} {}", item.getStatus(),
                            Optional.ofNullable(item.getProgressDetail())
                                .map(ResponseItem.ProgressDetail::getCurrent)
                                .map(Object::toString)
                                .orElse(""));
                    } else {
                        LocalDateTime now = LocalDateTime.now();
                        if (timeOfLastInfoLogMessage.get().plusSeconds(2).isBefore(now)) {
                            timeOfLastInfoLogMessage.set(now);
                            log.info("{} {}", item.getStatus(),
                                Optional.ofNullable(item.getProgressDetail())
                                    .map(ResponseItem.ProgressDetail::getCurrent)
                                    .map(Object::toString)
                                    .orElse(""));
                        }
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    cbException.set(throwable);
                }

                @Override
                public void onComplete() {
                    pullComplete.set(true);
                }
            });

        await().pollInterval(Duration.ofMillis(1000)).atMost(5, TimeUnit.MINUTES).until(() -> {
            if (cbException.get() != null) {
                throw new TigerTestEnvException("Unable to pull image " + imageName + "!", cbException.get());
            }
            return pullComplete.get();
        });
        log.info("Docker image {} is available locally!", imageName);
    }

    private static final String ECHO_CERT_CMD = "echo \"%s\" >> /etc/ssl/certs/ca-certificates.crt\n";

    private String createContainerStartupScript(AbstractTigerServer server, InspectImageResponse iiResponse,
        String[] startCmd,
        String[] entryPointCmd) {
        final ContainerConfig containerConfig = iiResponse.getConfig();
        if (containerConfig == null) {
            throw new TigerTestEnvException(
                "Docker image of server '" + server.getServerId() + "' has no configuration info!");
        }
        startCmd = startCmd == null ? new String[0] : startCmd;
        entryPointCmd = entryPointCmd == null ? new String[0] : entryPointCmd;
        try {

            File tmpScriptFolder = Path.of(TARGET_FOLDER, TIGER_TESTENV_MGR_FOLDER).toFile();
            if (!tmpScriptFolder.exists() && !tmpScriptFolder.mkdirs()) {
                throw new TigerTestEnvException("Unable to create script folder " + tmpScriptFolder.getAbsolutePath());
            }
            final var scriptName = "__tigerStart_" + server.getServerId() + ".sh";
            // append proxy and other certs (for rise idp)
            var content = "#!/bin/sh -x\nenv\n";

            if (server.getTigerTestEnvMgr().getLocalTigerProxyOptional().isPresent()) {
                content = addCertitifcatesToOsTruststoreOfDockerContainer(server, content);
            }

            // testing ca cert of proxy with openssl
            //+ "echo \"" + proxycert + "\" > /tmp/chain.pem\n"
            //+ "openssl s_client -connect localhost:7000 -showcerts --proxy host.docker.internal:"
            //+ envmgr.getLocalDockerProxy().getPort() + " -CAfile /tmp/chain.pem\n"
            // idp-test.zentral.idp.splitdns.ti-dienste.de:443
            // testing ca cert of proxy with rust client
            // WEBCLIENT + "RUST_LOG=trace /usr/bin/webclient http://tsl \n"

            // change to working dir and execute former entrypoint/startcmd
            content += getContainerWorkingDirectory(containerConfig)
                + String.join(" ", entryPointCmd).replace("\t", " ")
                + " " + String.join(" ", startCmd).replace("\t", " ") + "\n";

            FileUtils.writeStringToFile(Path.of(tmpScriptFolder.getAbsolutePath(), scriptName).toFile(),
                content, StandardCharsets.UTF_8);
            return scriptName;
        } catch (IOException ioe) {
            throw new TigerTestEnvException(
                "Failed to configure start script on container for server " + server.getServerId(), ioe);
        }
    }

    @NotNull
    private String addCertitifcatesToOsTruststoreOfDockerContainer(AbstractTigerServer server, String content) throws IOException {
        final var proxycert = getTigerProxyRootCaCertificate(server);
        final var lecert = IOUtils.toString(
            Objects.requireNonNull(getClass().getResourceAsStream("/letsencrypt.crt")),
            StandardCharsets.UTF_8);
        final var risecert = IOUtils.toString(
            Objects.requireNonNull(getClass().getResourceAsStream("/idp-rise-tu.crt")),
            StandardCharsets.UTF_8);
        content += String.format(ECHO_CERT_CMD + ECHO_CERT_CMD + ECHO_CERT_CMD,
            proxycert, lecert, risecert);
        return content;
    }

    private static String getTigerProxyRootCaCertificate(AbstractTigerServer server) {
        try {
            final Certificate certificate = server.getTigerTestEnvMgr().getLocalTigerProxyOrFail()
                .buildTruststore().getCertificate(CA_CERT_ALIAS);
            final Base64.Encoder encoder = Base64.getMimeEncoder(64, "\r\n".getBytes());

            final byte[] rawCrtText = certificate.getEncoded();
            final String encodedCertText = new String(encoder.encode(rawCrtText));
            return BEGIN_CERT + LINE_SEPARATOR + encodedCertText + LINE_SEPARATOR + END_CERT;
        } catch (GeneralSecurityException e) {
            throw new TigerEnvironmentStartupException("Error while retrieving TigerProxy RootCa", e);
        }
    }

    private String getContainerWorkingDirectory(ContainerConfig containerConfig) {
        final String workingDir = containerConfig.getWorkingDir();
        if (StringUtils.isBlank(workingDir)) {
            return "";
        } else {
            return "cd " + workingDir + "\n";
        }
    }

    private void waitForHealthyStartup(AbstractExternalTigerServer server, GenericContainer<?> container) {
        final long startms = System.currentTimeMillis();
        long endhalfms = server.getStartupTimeoutSec()
            .map(millis -> millis * 500L)
            .orElse(5000L);
        try {
            while (!container.isHealthy()) {
                //noinspection BusyWait
                Thread.sleep(500);
                if (startms + endhalfms * 2L < System.currentTimeMillis()) {
                    throw new TigerTestEnvException("Startup of server %s timed out after %d seconds!",
                        server.getServerId(), (System.currentTimeMillis() - startms) / 1000);
                }
            }
            log.info("HealthCheck OK ({}) for {}", (container.isHealthy() ? 1 : 0), server.getServerId());
        } catch (InterruptedException ie) {
            log.warn("Interruption signaled while waiting for server " + server.getServerId() + " to start up", ie);
            Thread.currentThread().interrupt();
        } catch (TigerTestEnvException ttee) {
            throw ttee;
        } catch (final RuntimeException rte) {
            if (server.getConfiguration().getDockerOptions() == null ||
                server.getConfiguration().getDockerOptions().getPorts() == null ||
                server.getConfiguration().getDockerOptions().getPorts().isEmpty()) {
                log.warn("No healthcheck and no port bindings configured in docker image - waiting {}s", endhalfms / 500L);
                try {
                    Thread.sleep(endhalfms * 2L);
                } catch (final InterruptedException e) {
                    log.warn("Interrupted while waiting for startup of server " + server.getServerId(), e);
                    Thread.currentThread().interrupt();
                }
                log.warn(
                    "Status UNCLEAR for {} as no healthcheck / port bindings were configured in the docker image, we assume it works and continue setup!",
                    server.getServerId());
            } else {
                String dockerHost = TigerGlobalConfiguration.readString("tiger.docker.host", "localhost");
                server.getConfiguration().setHealthcheckUrl("http://" + dockerHost + ":" +
                    server.getConfiguration().getDockerOptions().getPorts().values().iterator().next());
                server.waitForServerUp();
            }
        }
    }

    public void stopContainer(final AbstractTigerServer server) {
        final GenericContainer<?> container = dockerContainers.get(server.getServerId());
        if (container != null && container.getDockerClient() != null) {
            try {
                container.stop();
            } catch (RuntimeException rtex) {
                if (log.isDebugEnabled()) {
                    log.warn("Failed to stop container, relying on test container's implicit stop...", rtex);
                } else {
                    log.warn("Failed to stop container, relying on test container's implicit stop...");
                }
            }
            dockerContainers.remove(server.getServerId());
        }
    }

    public void stopComposeContainer(final AbstractTigerServer server) {
        final DockerComposeContainer<?> container = composeContainers.get(server.getServerId());
        if (container != null) {
            try {
                container.stop();
            } catch (RuntimeException rtex) {
                if (log.isDebugEnabled()) {
                    log.warn("Failed to stop compose container, relying on test container's implicit stop...", rtex);
                } else {
                    log.warn("Failed to stop compose container, relying on test container's implicit stop...");
                }
            }
            composeContainers.remove(server.getServerId());
        }
    }

    @SuppressWarnings("unused")
    public void pauseContainer(final DockerServer srv) {
        final GenericContainer<?> container = dockerContainers.get(srv.getServerId());
        container.getDockerClient().pauseContainerCmd(container.getContainerId()).exec();
    }

    @SuppressWarnings("unused")
    public void unpauseContainer(final DockerServer srv) {
        final GenericContainer<?> container = dockerContainers.get(srv.getServerId());
        container.getDockerClient().unpauseContainerCmd(container.getContainerId()).exec();
    }
}

