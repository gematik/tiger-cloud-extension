# Changelog tiger-cloud-extension

# Release 1.0.10

* starting a server of type `docker` with a local image no longer tries to retrieve the image from a remote registry.
* with the server type `docker` it is now possible to copy files into the container via the configuration.
* upgrade to Tiger 3.0.2
* the server type `compose` now adds routes to the tiger proxy for every mapped port. The routes are in the format:
  `http://<serverName>-<composeServiceName>-<exposedDockerPortNumber> -> http://localhost:<hostPortNumber>`

# Release 1.0.9

## Features

* upgrade to upcoming Tiger 3.0.0 release
* upgrade spring boot 3.2.1
* upgrade to testcontainers 1.19.3

# Release 1.0.6

## Features

* upgrade to Tiger 2.3.2

# Release 1.0.4

## Features

* upgrade to Tiger 2.2.1

# Release 1.0.3

## Features

* upgrade to java 17 and Tiger 2.0.0
