package de.gematik.test.tiger.testenvmgr.servers;

import java.util.Arrays;
import java.util.List;

/** Represents a port mapping from host to docker container */
public record PortMapping(Integer hostPort, Integer dockerPort) {

  public static PortMapping fromPortMappingString(String dockerComposePortMapping) {
    List<Integer> ports =
        Arrays.stream(dockerComposePortMapping.split(":")).map(Integer::valueOf).toList();
    return new PortMapping(ports.get(0), ports.get(1));
  }
}
