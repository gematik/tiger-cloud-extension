# Changelog tiger-cloud-extension

# Release 3.7.6

## Features

* upgrade to tiger 3.7.6


# Release 10.0.19

## Features

* upgrade to
  * testcontainers 1.20.6
  * tiger 3.7.0

# Release 10.0.18

## Features

* upgrade to 
  * testcontainers 1.20.4
  * tiger 3.6.1
  * junit 5.11.4

## Bugfixes
* TCLE-17 - a rootCA is not necessarily present at Tiger Proxy keystore
* TGR-1660: Jenkins notification for release pipeline adjusted

# Release 1.0.11

## Features

* upgrade to testcontainers 1.20.1

# Release 1.0.10

* starting a server of type `docker` with a local image no longer tries to retrieve the image from a remote registry.
* with the server type `docker` it is now possible to copy files into the container via the configuration.
* upgrade to Tiger 3.1.2
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
