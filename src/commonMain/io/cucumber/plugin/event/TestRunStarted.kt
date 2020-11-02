package io.cucumber.plugin.event

import org.apiguardian.api.API
import kotlin.time.TimeMark

@API(status = API.Status.STABLE)
class TestRunStarted(timeInstant: TimeMark) : TimeStampedEvent(timeInstant)