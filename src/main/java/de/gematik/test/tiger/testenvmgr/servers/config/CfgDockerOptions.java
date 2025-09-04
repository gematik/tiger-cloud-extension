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


package de.gematik.test.tiger.testenvmgr.servers.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class CfgDockerOptions {
  /**
   * whether to start container with unmodified entrypoint, or whether to modify by adding pki and
   * other stuff, rewriting the entrypoint
   */
  private boolean proxied = true;

  /** for docker type to trigger OneShotStartupStrategy */
  private boolean oneShot = false;

  /**
   * whether to parse all compose files and resolve potential tiger properties within. Attention if
   * compose files are within a hierarchical folder tree, they will be flattened and mounts/volumes
   * may fail to work
   */
  private boolean resolveComposeFiles = true;

  /**
   * whether to start the composition with tesat containers internally or to use the installed local
   * docker composed executable.
   */
  private boolean withLocalDockerCompose = true;

  /** for docker types allows to overwrite the entrypoint cmd configured with in the container */
  private String entryPoint;

  /** used only by docker */
  @JsonIgnore private Map<Integer, Integer> ports = Map.of();

  private List<CopyFilesConfig> copyFiles = List.of();

  /** configuration of files to be copied to the container* */
  @Data
  public static class CopyFilesConfig {
    String sourcePath;
    String destinationPath;
    String fileMode;
  }
}
