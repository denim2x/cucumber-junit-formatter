package io.cucumber.plugin.event

import kotlin.time.TimeMark

internal abstract class TimeStampedEvent(public val instant: TimeMark) : Event