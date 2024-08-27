/*
 * Copyright 2024 gematik GmbH
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
 */

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
