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
