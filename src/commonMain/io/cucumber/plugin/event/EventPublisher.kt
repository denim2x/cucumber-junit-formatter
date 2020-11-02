package io.cucumber.plugin.event

import org.apiguardian.api.API

@API(status = API.Status.STABLE)
interface EventPublisher {
  /**
   * Registers an event handler for a specific event.
   *
   *
   * The available events types are:
   *
   *  * [Event] - all events.
   *  * [TestRunStarted] - the first event sent.
   *  * [TestSourceRead] - sent for each feature file read, contains
   * the feature file source.
   *  * [SnippetsSuggestedEvent] - sent for each step that could not be
   * matched to a step definition, contains the raw snippets for the step.
   *  * [StepDefinedEvent] - sent for each step definition as it is
   * loaded, contains the StepDefinition
   *  * [TestCaseStarted] - sent before starting the execution of a
   * Test Case(/Pickle/Scenario), contains the Test Case
   *  * [TestStepStarted] - sent before starting the execution of a
   * Test Step, contains the Test Step
   *  * [EmbedEvent] - calling scenario.embed in a hook triggers this
   * event.
   *  * [WriteEvent] - calling scenario.write in a hook triggers this
   * event.
   *  * [TestStepFinished] - sent after the execution of a Test Step,
   * contains the Test Step and its Result.
   *  * [TestCaseFinished] - sent after the execution of a Test
   * Case(/Pickle/Scenario), contains the Test Case and its Result.
   *  * [TestRunFinished] - the last event sent.
   *
   *
   * @param eventType the event type for which the handler is being registered
   * @param handler   the event handler
   * @param <T>       the event type
   * @see Event
  </T> */
  fun <T> registerHandlerFor(eventType: Class<T>?, handler: EventHandler<T>?)
  
  /**
   * Unregister an event handler for a specific event
   *
   * @param eventType the event type for which the handler is being registered
   * @param handler   the event handler
   * @param <T>       the event type
  </T> */
  fun <T> removeHandlerFor(eventType: Class<T>?, handler: EventHandler<T>?)
}