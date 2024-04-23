package org.testcontainers.images;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.PullResponseItem;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

/**
 * This class only exists to make the LoggedPullImageResultCallback visible. In this way we can use
 * it in DockerMgr and get fancy logging for the pull image command.
 */
@Slf4j
public class TigerDelegatePullImageResultCallback implements ResultCallback<PullResponseItem> {
  @Delegate
  private final LoggedPullImageResultCallback loggedPullImageResultCallback =
      new LoggedPullImageResultCallback(log);
}
