package au.com.dius.pact.core.model

import au.com.dius.pact.core.model.generators.Category
import au.com.dius.pact.core.model.generators.GeneratorTestMode
import au.com.dius.pact.core.model.generators.Generators
import au.com.dius.pact.core.model.generators.ProviderStateGenerator
import au.com.dius.pact.core.model.generators.RandomIntGenerator
import au.com.dius.pact.core.model.generators.RandomStringGenerator
import au.com.dius.pact.core.model.generators.UuidGenerator
import au.com.dius.pact.core.support.Json
import au.com.dius.pact.core.support.expressions.DataType
import au.com.dius.pact.core.support.json.JsonParser
import spock.lang.Specification

class GeneratedRequestSpec extends Specification {
  private Generators generators
  private Request request

  def setup() {
    generators = new Generators()
    generators.addGenerator(Category.PATH, new RandomIntGenerator(400, 499))
    generators.addGenerator(Category.HEADER, 'A', new UuidGenerator())
    generators.addGenerator(Category.QUERY, 'A', new UuidGenerator())
    generators.addGenerator(Category.BODY, '$.a', new RandomStringGenerator())
    request = new Request(generators: generators)
  }

  def 'applies path generator for path to the copy of the request'() {
    given:
    request.path = '/path'

    when:
    def generated = request.generatedRequest([:], GeneratorTestMode.Provider)

    then:
    generated.path != request.path
  }

  def 'applies header generator for headers to the copy of the request'() {
    given:
    request.headers = [A: ['a'], B: ['b']]

    when:
    def generated = request.generatedRequest([:], GeneratorTestMode.Provider)

    then:
    generated.headers.A != ['a']
    generated.headers.A.size() == 1
    generated.headers.B == ['b']
  }

  def 'applies a header generator to each value of the header, and finds the header ignoring case'() {
    given:
    request.headers = [a: ['a', 'b'], B: ['b']]

    when:
    def generated = request.generatedRequest([:], GeneratorTestMode.Provider)

    then:
    generated.headers.keySet() == ['a', 'B'] as Set
    generated.headers.a.size() == 2
    generated.headers.a.every { it != 'a' && it != 'b' }
    generated.headers.B == ['b']
  }

  def 'applies an indexed header generator only to that value'() {
    given:
    def generators = new Generators()
    generators.addGenerator(Category.HEADER, '$[\'X-Values\'][1]', new UuidGenerator())
    request = new Request(generators: generators, headers: ['x-values': ['a', 'b', 'c']])

    when:
    def generated = request.generatedRequest([:], GeneratorTestMode.Provider)

    then:
    generated.headers['x-values'][0] == 'a'
    generated.headers['x-values'][1] != 'b'
    generated.headers['x-values'][2] == 'c'
  }

  def 'an indexed header generator runs after the generator for the name'() {
    given:
    def generators = new Generators()
    generators.addGenerator(Category.HEADER, '$.A[0]', new ProviderStateGenerator('b', DataType.STRING))
    generators.addGenerator(Category.HEADER, 'A', new ProviderStateGenerator('a', DataType.STRING))
    request = new Request(generators: generators, headers: [A: ['x', 'y']])

    when:
    def generated = request.generatedRequest([providerState: [a: 'A', b: 'B']], GeneratorTestMode.Provider)

    then:
    generated.headers.A == ['B', 'A']
  }

  def 'an indexed generator for a value that does not exist is ignored'() {
    given:
    def generators = new Generators()
    generators.addGenerator(Category.HEADER, '$.A[2]', new UuidGenerator())
    generators.addGenerator(Category.QUERY, '$.q[1]', new UuidGenerator())
    request = new Request(generators: generators, headers: [A: ['x']], query: [q: ['x']])

    when:
    def generated = request.generatedRequest([:], GeneratorTestMode.Provider)

    then:
    generated.headers == [A: ['x']]
    generated.query == [q: ['x']]
  }

  def 'a query parameter name with brackets is a plain name'() {
    given:
    def generators = new Generators()
    generators.addGenerator(Category.QUERY, 'ids[0]', new UuidGenerator())
    request = new Request(generators: generators, query: ['ids[0]': ['x', 'y']])

    when:
    def generated = request.generatedRequest([:], GeneratorTestMode.Provider)

    then:
    generated.query['ids[0]'].every { it != 'x' && it != 'y' }
  }

  def 'a query generator for a missing parameter generates a single value'() {
    given:
    def generators = new Generators()
    generators.addGenerator(Category.QUERY, 'id', new ProviderStateGenerator('id'))
    request = new Request(generators: generators, query: [a: ['x']])

    when:
    def generated = request.generatedRequest([providerState: [id: 'X']], GeneratorTestMode.Provider)

    then:
    generated.query == [a: ['x'], id: ['X']]
  }

  def 'applies query generator for query parameters to the copy of the request'() {
    given:
    request.query = [A: ['a', 'b'], B: ['b']]

    when:
    def generated = request.generatedRequest([:], GeneratorTestMode.Provider)

    then:
    generated.query.A != ['a', 'b']
    generated.query.A.size() == 2
    generated.query.B == ['b']
  }

  def 'applies an indexed query generator only to that value'() {
    given:
    def generators = new Generators()
    generators.addGenerator(Category.QUERY, '$.a[1]', new UuidGenerator())
    request = new Request(generators: generators, query: [a: ['x', 'y']])

    when:
    def generated = request.generatedRequest([:], GeneratorTestMode.Provider)

    then:
    generated.query.a[0] == 'x'
    generated.query.a[1] != 'y'
  }

  def 'applies body generators for body values to the copy of the request'() {
    given:
    def body = [a: 'A', b: 'B']
    request.body = OptionalBody.body(Json.INSTANCE.prettyPrint(body).bytes)

    when:
    def generated = request.generatedRequest([:], GeneratorTestMode.Provider)
    def generatedBody = Json.INSTANCE.toMap(JsonParser.INSTANCE.parseString(generated.body.valueAsString()))

    then:
    generatedBody.a != 'A'
    generatedBody.b == 'B'
  }
}
