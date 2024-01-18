# Tiger cloud extension

The tiger cloud extension allows to embed docker image based containers, docker compose scripts (alas with some constraints) and even helm charts to local or remote kubernetes clusters. 
It is closely coupled to the testcontainers library utilizing its docker feature set (bear this in mind when using docker compose as testcontainer does not support the newest compose version features).

To include this extension in your project add:

```
    <dependency>
        <groupId>de.gematik</groupId>
        <artifactId>tiger-cloud-extension</artifactId>
        <version>...</version>
    </dependency>
```

To use this extension in your project you at least have to depend upon the tiger testenv mgr or the tiger test lib module.

```
    <dependency>
        <groupId>de.gematik.test</groupId>
        <artifactId>tiger-testenv-mgr</artifactId>
        <version>${tiger.testenv.version}</version>
    </dependency>
```

For more details please check the Tiger user manual at https://gematik.github.io/app-Tiger/Tiger-User-Manual.html



## Local Testenvironment

You will need microk8s configured correctly to run local tests:

* kubectl create namespace tiger
* microk8s config > ~/.kube/config
