package io.cucumber.core.plugin

import io.cucumber.core.exception.CucumberException
import io.cucumber.plugin.EventListener
import io.cucumber.plugin.event.EventPublisher
import io.cucumber.plugin.event.Location
import io.cucumber.plugin.event.Node
import io.cucumber.plugin.event.PickleStepTestStep
import io.cucumber.plugin.event.Result
import io.cucumber.plugin.event.Status
import io.cucumber.plugin.event.TestCaseFinished
import io.cucumber.plugin.event.TestCaseStarted
import io.cucumber.plugin.event.TestRunFinished
import io.cucumber.plugin.event.TestRunStarted
import io.cucumber.plugin.event.TestSourceParsed
import io.cucumber.plugin.event.TestStepFinished
import org.w3c.dom.Document
import org.w3c.dom.Element
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.OutputKeys
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import java.io.Closeable
import java.io.IOException
import java.io.OutputStream
import java.io.Writer
import java.net.URI
import java.text.DecimalFormat
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.function.Predicate
import io.cucumber.core.exception.ExceptionUtils.printStackTrace
import java.util.Locale.ROOT
import java.util.concurrent.TimeUnit.SECONDS

class JUnitFormatter(out: OutputStream?) : EventListener {
  private val writer: Writer
  private var document: Document? = null
  private var rootElement: Element? = null
  private val parsedTestSources: Map<URI, Collection<Node>> = hashMapOf()
  private var root: Element? = null
  private var testCase: TestCase? = null
  private var currentFeatureFile: URI? = null
  private var previousTestCaseName: String? = null
  private var exampleNumber = 0
  private var started: Instant? = null
  
  @Override
  fun setEventPublisher(publisher: EventPublisher) {
    publisher.registerHandlerFor(TestRunStarted::class.java) { event: TestRunStarted -> handleTestRunStarted(event) }
    publisher.registerHandlerFor(TestSourceParsed::class.java) { event: TestSourceParsed -> handleTestSourceParsed(event) }
    publisher.registerHandlerFor(TestCaseStarted::class.java) { event: TestCaseStarted -> handleTestCaseStarted(event) }
    publisher.registerHandlerFor(TestCaseFinished::class.java) { event: TestCaseFinished -> handleTestCaseFinished(event) }
    publisher.registerHandlerFor(TestStepFinished::class.java) { event: TestStepFinished -> handleTestStepFinished(event) }
    publisher.registerHandlerFor(TestRunFinished::class.java) { event: TestRunFinished -> handleTestRunFinished(event) }
  }
  
  private fun handleTestRunStarted(event: TestRunStarted) {
    started = event.getInstant()
  }
  
  private fun handleTestSourceParsed(event: TestSourceParsed) {
    parsedTestSources.put(event.getUri(), event.getNodes())
  }
  
  private fun handleTestCaseStarted(event: TestCaseStarted) {
    if (currentFeatureFile == null || !currentFeatureFile.equals(event.getTestCase().getUri())) {
      currentFeatureFile = event.getTestCase().getUri()
      previousTestCaseName = ""
      exampleNumber = 1
    }
    testCase = TestCase(event.getTestCase())
    root = testCase!!.createElement(document)
    testCase!!.writeElement(root)
    rootElement.appendChild(root)
    increaseTestCount(rootElement)
  }
  
  private fun handleTestCaseFinished(event: TestCaseFinished) {
    if (testCase!!.steps.isEmpty()) {
      testCase!!.handleEmptyTestCase(document, root, event.getResult())
    } else {
      testCase!!.addTestCaseElement(document, root, event.getResult())
    }
  }
  
  private fun handleTestStepFinished(event: TestStepFinished) {
    if (event.getTestStep() is PickleStepTestStep) {
      testCase!!.steps.add(event.getTestStep() as PickleStepTestStep)
      testCase!!.results.add(event.getResult())
    }
  }
  
  private fun handleTestRunFinished(event: TestRunFinished) {
    try {
      val finished: Instant = event.getInstant()
      // set up a transformer
      rootElement.setAttribute("name", JUnitFormatter::class.java.getName())
      rootElement.setAttribute("failures",
          String.valueOf(rootElement.getElementsByTagName("failure").getLength()))
      rootElement.setAttribute("skipped",
          String.valueOf(rootElement.getElementsByTagName("skipped").getLength()))
      rootElement.setAttribute("errors", "0")
      rootElement.setAttribute("time", calculateTotalDurationString(Duration.between(started, finished)))
      val factory: TransformerFactory = TransformerFactory.newInstance()
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
      val transformer: Transformer = factory.newTransformer()
      transformer.setOutputProperty(OutputKeys.INDENT, "yes")
      val result = StreamResult(writer)
      val source = DOMSource(document)
      transformer.transform(source, result)
      closeQuietly(writer)
    } catch (e: TransformerException) {
      throw CucumberException("Error while transforming.", e)
    }
  }
  
  private fun increaseTestCount(element: Element?) {
    var value = 0
    if (element.hasAttribute("tests")) {
      value = Integer.parseInt(element.getAttribute("tests"))
    }
    element.setAttribute("tests", String.valueOf(++value))
  }
  
  private fun closeQuietly(out: Closeable) {
    try {
      out.close()
    } catch (ignored: IOException) {
      // go gentle into that good night
    }
  }
  
  internal inner class TestCase(testCase: io.cucumber.plugin.event.TestCase) {
    val steps: List<PickleStepTestStep> = ArrayList()
    val results: List<Result> = ArrayList()
    private val testCase: io.cucumber.plugin.event.TestCase
    fun createElement(doc: Document): Element {
      return doc.createElement("testcase")
    }
    
    fun writeElement(tc: Element?) {
      tc.setAttribute("classname", findRootNodeName(testCase))
      tc.setAttribute("name", calculateElementName(testCase))
    }
    
    private fun findRootNodeName(testCase: io.cucumber.plugin.event.TestCase): String {
      val location: Location = testCase.getLocation()
      val withLocation: Predicate<Node> = Predicate<Node> { candidate -> location.equals(candidate.getLocation()) }
      return parsedTestSources[testCase.getUri()]
          .map { node -> node.findPathTo(withLocation) }
          .firstOrNull()
          ?.map { nodes -> nodes[0] }
          ?.flatMap { it.name }
          ?: "Unknown"
    }
    
    private fun calculateElementName(testCase: io.cucumber.plugin.event.TestCase): String {
      val testCaseName: String = testCase.getName()
      return if (testCaseName.equals(previousTestCaseName)) {
        getUniqueTestNameForScenarioExample(testCaseName, ++exampleNumber)
      } else {
        previousTestCaseName = testCase.getName()
        exampleNumber = 1
        testCaseName
      }
    }
    
    fun addTestCaseElement(doc: Document?, tc: Element?, result: Result) {
      tc.setAttribute("time", calculateTotalDurationString(result.getDuration()))
      val sb = StringBuilder()
      addStepAndResultListing(sb)
      val child: Element
      val status: Status = result.getStatus()
      child = if (status.`is`(Status.FAILED) || status.`is`(Status.AMBIGUOUS)) {
        addStackTrace(sb, result)
        createFailure(doc, sb, result.getError().getMessage(), result.getError().getClass())
      } else if (status.`is`(Status.PENDING) || status.`is`(Status.UNDEFINED)) {
        val error: Throwable = result.getError()
        createFailure(doc, sb, "The scenario has pending or undefined step(s)",
            if (error == null) Exception::class.java else error.getClass())
      } else if (status.`is`(Status.SKIPPED) && result.getError() != null) {
        addStackTrace(sb, result)
        createSkipped(doc, sb, printStackTrace(result.getError()))
      } else {
        createElement(doc, sb, "system-out")
      }
      tc.appendChild(child)
    }
    
    private fun addStepAndResultListing(sb: StringBuilder) {
      for (i in 0 until steps.size()) {
        val length: Int = sb.length()
        var resultStatus = "not executed"
        if (i < results.size()) {
          resultStatus = results[i].getStatus().name().toLowerCase(ROOT)
        }
        sb.append(steps[i].getStep().getKeyword())
        sb.append(steps[i].getStepText())
        do {
          sb.append(".")
        } while (sb.length() - length < 76)
        sb.append(resultStatus)
        sb.append("\n")
      }
    }
    
    private fun addStackTrace(sb: StringBuilder, failed: Result) {
      sb.append("\nStackTrace:\n")
      sb.append(printStackTrace(failed.getError()))
    }
    
    private fun createFailure(doc: Document?, sb: StringBuilder, message: String, type: Class<out Throwable?>): Element {
      val child: Element = createElement(doc, sb, "failure")
      child.setAttribute("message", message)
      child.setAttribute("type", type.getName())
      return child
    }
    
    private fun createSkipped(doc: Document, sb: StringBuilder, message: String): Element {
      val child: Element = createElement(doc, sb, "skipped")
      child.setAttribute("message", message)
      return child
    }
    
    private fun createElement(doc: Document?, sb: StringBuilder, elementType: String): Element {
      val child: Element = doc.createElement(elementType)
      // the createCDATASection method seems to convert "\n" to "\r\n" on
      // Windows, in case
      // data originally contains "\r\n" line separators the result
      // becomes "\r\r\n", which
      // are displayed as double line breaks.
      val normalizedLineEndings: String = sb.toString().replace(System.lineSeparator(), "\n")
      child.appendChild(doc.createCDATASection(normalizedLineEndings))
      return child
    }
    
    fun handleEmptyTestCase(doc: Document?, tc: Element?, result: Result) {
      tc.setAttribute("time", calculateTotalDurationString(result.getDuration()))
      val child: Element = createFailure(doc, StringBuilder(), "The scenario has no steps", Exception::class.java)
      tc.appendChild(child)
    }
    
    init {
      this.testCase = testCase
    }
  }
  
  companion object {
    private val MILLIS_PER_SECOND: Long = SECONDS.toMillis(1L)
    private fun getUniqueTestNameForScenarioExample(testCaseName: String, exampleNumber: Int): String {
      return "$testCaseName${if (testCaseName.contains(" ")) " " else "_"}$exampleNumber"
    }
    
    private fun calculateTotalDurationString(result: Duration): String {
      val numberFormat: DecimalFormat = NumberFormat.getNumberInstance(Locale.US) as DecimalFormat
      val duration = result.toMillis() as Double / MILLIS_PER_SECOND
      return numberFormat.format(duration)
    }
  }
  
  init {
    writer = UTF8OutputStreamWriter(out)
    try {
      document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
      rootElement = document.createElement("testsuite")
      document.appendChild(rootElement)
    } catch (e: ParserConfigurationException) {
      throw CucumberException("Error while processing unit report", e)
    }
  }
}