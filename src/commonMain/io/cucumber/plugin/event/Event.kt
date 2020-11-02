package io.cucumber.plugin.event

import org.apiguardian.api.API
import kotlin.time.TimeMark

@API(status = API.Status.STABLE)
interface Event {
  /**
   * Returns instant from epoch.
   *
   * @return time instant in Instant
   * @see TimeSource.markNow()
   */
  val instant: TimeMark
}