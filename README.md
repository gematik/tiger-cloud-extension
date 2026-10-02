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

## Documentation

The complete, detailed documentation of this extension - including the full configuration reference and examples
for all supported server types (`docker`, `compose`, `helmChart`) - is maintained in the user manual in
[`doc/user_manual`](doc/user_manual/user_manual.adoc):

* [Overview](doc/user_manual/01_overview.adoc) - what the extension does, architecture, common server properties
* [Getting started](doc/user_manual/02_getting_started.adoc) - dependency setup, compatibility matrix, docker image
  requirements, breaking changes
* [Server type `docker`](doc/user_manual/03_docker_server.adoc) - single docker containers, `dockerOptions`
  reference, port mapping, copying files, network modes, extra hosts
* [Server type `compose`](doc/user_manual/04_compose_server.adoc) - docker compose based servers
* [Server type `helmChart`](doc/user_manual/05_helm_chart_server.adoc) - helm charts on local/remote kubernetes
  clusters
* [Local test environment](doc/user_manual/06_local_test_environment.adoc) - setting up microk8s, required CLI
  tools

For general Tiger concepts not specific to this extension, please also check the Tiger user manual at
https://gematik.github.io/app-Tiger/Tiger-User-Manual.html

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