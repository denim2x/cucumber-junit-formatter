package io.cucumber.plugin.event

import org.apiguardian.api.API

/**
 * A node in a source file.
 *
 *
 * A node has a location, a keyword and name. The keyword and name are both
 * optional (e.g. [Example] and blank scenario names).
 *
 *
 * Nodes are organized in a tree like structure where [Container] nodes
 * contain yet more nodes.
 *
 *
 * A node can be linked to a [TestCase] by [.getLocation]. The
 * [Node.findPathTo] method can be used to find a path from the
 * root node to a node with the same location as a test case. `<pre>
 *
 * `Location location = testCase.getLocation();`
 * `Predicate<Node> withLocation = candidate -> location.equals(candidate.getLocation());`
 * `Optional<List<Node>> path = node.findPathTo(withLocation);`
</pre> *
` *
 */
@API(status = API.Status.EXPERIMENTAL)
interface Node {
  val location: Location?
  val keyword: Optional<String?>?
  val name: Optional<String?>?
  
  /**
   * Recursively maps a node into another tree-like structure.
   *
   * @param  parent             the parent node of the target structure
   * @param  mapFeature         a function that takes a feature and a parent
   * node and returns a mapped feature
   * @param  mapRule            a function that takes a rule and a parent node
   * and returns a mapped rule
   * @param  mapScenario        a function that takes a scenario and a parent
   * node and returns a mapped scenario
   * @param  mapScenarioOutline a function that takes a scenario outline and a
   * parent node and returns a mapped scenario
   * outline
   * @param  mapExamples        a function that takes an examples and a parent
   * node and returns a mapped examples
   * @param  mapExample         a function that takes an example and a parent
   * node and returns a mapped example
   * @param  <T>                the type of the target structure
   * @return                    the mapped version of this instance
  </T> */
  fun <T> map(
      parent: T,
      mapFeature: BiFunction<Feature?, T, T>,
      mapRule: BiFunction<Rule?, T, T>, mapScenario: BiFunction<Scenario?, T, T>,
      mapScenarioOutline: BiFunction<ScenarioOutline?, T, T>,
      mapExamples: BiFunction<Examples?, T, T>,
      mapExample: BiFunction<Example?, T, T>
  ): T {
    return if (this is Scenario) {
      mapScenario.apply(this, parent)
    } else if (this is Example) {
      mapExample.apply(this, parent)
    } else if (this is Container<*>) {
      val mapped: T
      mapped = if (this is Feature) {
        mapFeature.apply(this, parent)
      } else if (this is Rule) {
        mapRule.apply(this, parent)
      } else if (this is ScenarioOutline) {
        mapScenarioOutline.apply(this, parent)
      } else if (this is Examples) {
        mapExamples.apply(this, parent)
      } else {
        throw IllegalArgumentException(this.getClass().getName())
      }
      val container = this as Container<*>
      container.elements().forEach { node ->
        node.map(mapped, mapFeature, mapRule, mapScenario, mapScenarioOutline,
            mapExamples, mapExample)
      }
      mapped
    } else {
      throw IllegalArgumentException(this.getClass().getName())
    }
  }
  
  /**
   * Finds a path down tree starting at this node to the first node that
   * matches the predicate using depth first search.
   *
   * @param  predicate to match the target node.
   * @return           a path to the first node or an empty optional if none
   * was found.
   */
  fun findPathTo(predicate: Predicate<Node?>): List<Node> {
    val path = ArrayList<Node>()
    if (predicate.test(this)) {
      path.add(this)
    }
    return path
  }
  
  interface Container<T : Node?> : Node {
    @Override
    override fun findPathTo(predicate: Predicate<Node?>): Optional<List<Node>> {
      val path: List<Node> = ArrayList()
      val toSearch: Deque<Deque<Node>> = ArrayDeque()
      toSearch.addLast(ArrayDeque(singletonList(this)))
      while (!toSearch.isEmpty()) {
        val candidates: Deque<Node> = toSearch.peekLast()
        if (candidates.isEmpty()) {
          if (!path.isEmpty()) {
            path.remove(path.size() - 1)
          }
          toSearch.removeLast()
          continue
        }
        val candidate: Node = candidates.pop()
        if (predicate.test(candidate)) {
          path.add(candidate)
          return Optional.of(path)
        }
        if (candidate is Container<*>) {
          path.add(candidate)
          toSearch.addLast(ArrayDeque(candidate.elements()))
        }
      }
      return Optional.empty()
    }
    
    fun elements(): Collection<T>
  }
  
  /**
   * A feature has a keyword and optionally a name.
   */
  interface Feature : Node, Container<Node?>
  
  /**
   * A rule has a keyword and optionally a name.
   */
  interface Rule : Node, Container<Node?>
  
  /**
   * A scenario has a keyword and optionally a name.
   */
  interface Scenario : Node
  
  /**
   * A scenario outline has a keyword and optionally a name.
   */
  interface ScenarioOutline : Node, Container<Examples?>
  
  /**
   * An examples section has a keyword and optionally a name.
   */
  interface Examples : Node, Container<Example?>
  
  /**
   * An example has no keyword but always a name.
   */
  interface Example : Node
}