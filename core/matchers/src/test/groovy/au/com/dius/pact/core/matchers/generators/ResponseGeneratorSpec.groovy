package au.com.dius.pact.core.matchers.generators

import au.com.dius.pact.core.model.OptionalBody
import au.com.dius.pact.core.model.Response
import au.com.dius.pact.core.model.generators.Category
import au.com.dius.pact.core.model.generators.GeneratorTestMode
import au.com.dius.pact.core.model.generators.Generators
import au.com.dius.pact.core.model.generators.RandomIntGenerator
import au.com.dius.pact.core.model.generators.RandomStringGenerator
import au.com.dius.pact.core.model.generators.UuidGenerator
import au.com.dius.pact.core.support.Json
import au.com.dius.pact.core.support.json.JsonParser
import spock.lang.Specification

class ResponseGeneratorSpec extends Specification {
  private Generators generators
  private Response response

  def setup() {
    generators = new Generators()
    generators.addGenerator(Category.STATUS, new RandomIntGenerator(400, 499))
    generators.addGenerator(Category.HEADER, 'A', new UuidGenerator())
    generators.addGenerator(Category.BODY, '$.a', new RandomStringGenerator())
    response = new Response(generators: generators)
  }

  def 'applies status generator for status to the copy of the response'() {
    given:
    response.status = 200

    when:
    def generated = DefaultResponseGenerator.INSTANCE.generateResponse(response, [:],
      GeneratorTestMode.Provider, [], [:])

    then:
    generated.status >= 400 && generated.status < 500
  }

  def 'applies header generator for headers to the copy of the response'() {
    given:
    response.headers = [A: ['a'], B: ['b']]

    when:
    def generated = DefaultResponseGenerator.INSTANCE.generateResponse(response, [:],
      GeneratorTestMode.Provider, [], [:])

    then:
    generated.headers.A != ['a']
    generated.headers.A.size() == 1
    generated.headers.B == ['b']
  }

  def 'applies a header generator to each value, and an indexed header generator only to that value'() {
    given:
    def generators = new Generators()
    generators.addGenerator(Category.HEADER, 'a', new UuidGenerator())
    generators.addGenerator(Category.HEADER, '$[\'X-Values\'][1]', new UuidGenerator())
    response = new Response(generators: generators, headers: [A: ['a', 'b'], 'x-values': ['a', 'b']])

    when:
    def generated = DefaultResponseGenerator.INSTANCE.generateResponse(response, [:],
      GeneratorTestMode.Provider, [], [:])

    then:
    generated.headers.A.size() == 2
    generated.headers.A.every { it != 'a' && it != 'b' }
    generated.headers['x-values'][0] == 'a'
    generated.headers['x-values'][1] != 'b'
  }

  def 'generates a header that is missing from the response'() {
    given:
    response.headers = [:]

    when:
    def generated = DefaultResponseGenerator.INSTANCE.generateResponse(response, [:],
      GeneratorTestMode.Provider, [], [:])

    then:
    generated.headers.A.size() == 1
  }

  def 'applies body generators for body values to the copy of the response'() {
    given:
    def body = [a: 'A', b: 'B']
    response.body = OptionalBody.body(Json.INSTANCE.prettyPrint(body).bytes)

    when:
    def generated = DefaultResponseGenerator.INSTANCE.generateResponse(response, [:],
      GeneratorTestMode.Provider, [], [:])
    def generatedBody = Json.INSTANCE.toMap(
      JsonParser.INSTANCE.parseString(generated.body.valueAsString()))

    then:
    generatedBody.a != 'A'
    generatedBody.b == 'B'
  }
}
