# Tiger cloud extension

The tiger cloud extension allows to embed docker image based containers, docker compose scripts (alas with some
constraints) and even helm charts to local or remote kubernetes clusters.
It is closely coupled to the testcontainers library utilizing its docker feature set (bear this in mind when using
docker compose as testcontainer does not support the newest compose version features).

To include this extension in your project add:

```
    <dependency>
        <groupId>de.gematik</groupId>
        <artifactId>tiger-cloud-extension</artifactId>
        <version>...</version>
    </dependency>
```

To use this extension in your project you at least have to depend upon the tiger testenv mgr or the tiger test lib
module.

```
    <dependency>
        <groupId>de.gematik.test</groupId>
        <artifactId>tiger-testenv-mgr</artifactId>
        <version>${tiger.testenv.version}</version>
    </dependency>
```

For more details please check the Tiger user manual at https://gematik.github.io/app-Tiger/Tiger-User-Manual.html

## Compatibility

The following table shows the recommended combinations of versions you should use to avoid problems using the
tiger-cloud-extension.

| tiger-cloud-extension Version | Tiger Version |
|-------------------------------|---------------|
| 1.10.0                        | 3.0.2         |
| 10.0.18                       | < 3.7.6       |
| 3.7.6                         | 3.7.6         |
| 4.0.9                         | 4.0.9         |

Please note that the highest version (10.0.18) is NOT the latest version.

## Local Testenvironment

You will need microk8s configured correctly to run local tests:

* kubectl create namespace tiger
* microk8s config > ~/.kube/config
* microk8s start
* things that might help
  * gcloud auth login
  * helm repo update

## Docker Image Requirements

When starting a container with the tiger cloud extension, tiger makes some modifications on the entry point script to
add the Tiger Proxy certificate to the container's operating system list of trusted certificates. In order for this to
work, the image of the container must have the following tools available:

* `/bin/sh`- sh shell
* `/bin/env` - env command

## Server Types

### Docker

The server type 'docker' allows to copy files into the container. This is done via the tiger configuration. A full
example can be found in
the [Tiger User Manual](https://gematik.github.io/app-Tiger/Tiger-User-Manual.html#_docker_container_node).

```yaml
dockerServerExample:
  type: docker

  dockerOptions:
    copyFiles:
      # path to the file or the folder to copy inside the container
      - sourcePath: ./example/path/file_to_copy.txt
        # path inside the container where the file should be copied to
        destinationPath: /path/in/container/file_to_copy.txt
        # OPTIONAL the file mode of the copied file as octal representation (see https://en.wikipedia.org/wiki/File-system_permissions#numericNotation
        fileMode: 0633
      # a complete folder can also be copied instead of a single file
      - sourcePath: ./example/copy_folder
        destinationPath: /path/in/container/copy_folder
```

## License

Copyright 2025 gematik GmbH

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.

See the link:./LICENSE[LICENSE] for the specific language governing permissions and limitations under the License.

## Additional Notes and Disclaimer from gematik GmbH

1. Copyright notice: Each published work result is accompanied by an explicit statement of the license conditions for use. These are regularly typical conditions in connection with open source or free software. Programs described/provided/linked here are free software, unless otherwise stated.
2. Permission notice: Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
    1. The copyright notice (Item 1) and the permission notice (Item 2) shall be included in all copies or substantial portions of the Software.
    2. The software is provided "as is" without warranty of any kind, either express or implied, including, but not limited to, the warranties of fitness for a particular purpose, merchantability, and/or non-infringement. The authors or copyright holders shall not be liable in any manner whatsoever for any damages or other claims arising from, out of or in connection with the software or the use or other dealings with the software, whether in an action of contract, tort, or otherwise.
    3. The software is the result of research and development activities, therefore not necessarily quality assured and without the character of a liable product. For this reason, gematik does not provide any support or other user assistance (unless otherwise stated in individual cases and without justification of a legal obligation). Furthermore, there is no claim to further development and adaptation of the results to a more current state of the art.
3. Gematik may remove published results temporarily or permanently from the place of publication at any time without prior notice or justification.
4. Please note: Parts of this code may have been generated using AI-supported technology. Please take this into account, especially when troubleshooting, for security analyses and possible adjustments.

## Contact
This software is currently being tested to ensure its technical quality and legal compliance. Your feedback is highly
valued.
If you find any issues or have any suggestions or comments, or if you see any other ways in which we can improve, please
reach out to: tiger@gematik.de