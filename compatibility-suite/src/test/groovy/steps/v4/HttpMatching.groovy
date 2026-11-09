package steps.v4

import au.com.dius.pact.core.matchers.BodyMismatch
import au.com.dius.pact.core.matchers.HeaderMismatch
import au.com.dius.pact.core.matchers.Mismatch
import au.com.dius.pact.core.matchers.RequestMatchResult
import au.com.dius.pact.core.model.Consumer
import au.com.dius.pact.core.model.HttpRequest
import au.com.dius.pact.core.model.HttpResponse
import au.com.dius.pact.core.model.OptionalBody
import au.com.dius.pact.core.model.Provider
import au.com.dius.pact.core.model.V4Interaction
import au.com.dius.pact.core.model.V4Pact
import au.com.dius.pact.core.model.PactReaderKt
import au.com.dius.pact.core.support.json.JsonParser
import au.com.dius.pact.core.support.json.JsonValue
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

import static au.com.dius.pact.core.matchers.RequestMatching.requestMismatches
import static au.com.dius.pact.core.matchers.ResponseMatching.responseMismatches
import static steps.shared.SharedSteps.configureBody
import static steps.shared.SharedSteps.determineContentType

class HttpMatching {
  HttpRequest expectedRequest
  List<HttpRequest> receivedRequests = []
  HttpResponse expectedResponse
  List<HttpResponse> receivedResponses = []
  List<Mismatch> responseResults = []
  List<RequestMatchResult> requestResults = []

  @Given('an expected response configured with the following:')
  void an_expected_response_configured_with_the_following(DataTable dataTable) {
    expectedResponse = new HttpResponse()
    def entry = dataTable.entries().first()

    if (entry['status']) {
      expectedResponse.status = entry['status'].toInteger()
    }

    if (entry['body']) {
      def part = configureBody(entry['body'], determineContentType(entry['body'], expectedResponse.contentTypeHeader()))
      expectedResponse.body = part.body
      expectedResponse.headers.putAll(part.headers)
    }

    if (entry['matching rules']) {
      JsonValue json
      if (entry['matching rules'].startsWith('JSON:')) {
        json = JsonParser.INSTANCE.parseString(entry['matching rules'][5..-1])
      } else {
        File contents = new File("pact-compatibility-suite/fixtures/${entry['matching rules']}")
        contents.withInputStream {
          json = JsonParser.INSTANCE.parseStream(it)
        }
      }
      expectedResponse.matchingRules.fromV3Json(json)
    }
  }

  @Given('a status {int} response is received')
  void a_status_response_is_received(Integer status) {
    receivedResponses << new HttpResponse(status)
  }

  @When('the response is compared to the expected one')
  void the_response_is_compared_to_the_expected_one() {
    def pact = new V4Pact(new Consumer(this.class.name), new Provider(this.class.name))
    def interaction = new V4Interaction.SynchronousHttp(
      null, 'compatibility-suite', [], new HttpRequest(), expectedResponse)
    pact.interactions << interaction
    responseResults.addAll(responseMismatches(pact, interaction, receivedResponses[0]))
  }

  @Then('the response comparison should be OK')
  void the_response_comparison_should_be_ok() {
    assert responseResults.empty
  }

  @Then('the response comparison should NOT be OK')
  void the_response_comparison_should_not_be_ok() {
    assert !responseResults.empty
  }

  @Then('the response mismatches will contain a {string} mismatch with error {string}')
  void the_response_mismatches_will_contain_a_mismatch_with_error(String type, String error) {
    assert responseResults.find {
      it.type() == type && it.description().toLowerCase() == error.toLowerCase()
    }
  }

  @Given('an expected request configured with the following:')
  void an_expected_request_configured_with_the_following(DataTable dataTable) {
    expectedRequest = new HttpRequest()
    def entry = dataTable.entries().first()
    setupQueryAndHeaders(expectedRequest, entry)
    if (entry['body']) {
      def part
      if (entry['content type']) {
        part = configureBody(entry['body'], entry['content type'])
      } else {
        part = configureBody(entry['body'], determineContentType(entry['body'], expectedRequest.contentTypeHeader()))
      }
      expectedRequest.body = part.body
      expectedRequest.headers.putAll(part.headers)
    }

    if (entry['matching rules']) {
      JsonValue json
      if (entry['matching rules'].startsWith('JSON:')) {
        json = JsonParser.INSTANCE.parseString(entry['matching rules'][5..-1])
      } else {
        File contents = new File("pact-compatibility-suite/fixtures/${entry['matching rules']}")
        contents.withInputStream {
          json = JsonParser.INSTANCE.parseStream(it)
        }
      }
      expectedRequest.matchingRules.fromV3Json(json)
    }
  }

  @Given('a request is received with the following:')
  void a_request_is_received_with_the_following(DataTable dataTable) {
    receivedRequests << new HttpRequest()
    def entry = dataTable.entries().first()
    setupQueryAndHeaders(receivedRequests[0], entry)
    if (entry['body']) {
      def part
      if (entry['content type']) {
        part = configureBody(entry['body'], entry['content type'])
      } else {
        part = configureBody(entry['body'], determineContentType(entry['body'],
          receivedRequests[0].contentTypeHeader()))
      }
      receivedRequests[0].body = part.body
      receivedRequests[0].headers.putAll(part.headers)
    }
  }

  @Given('the {string} header is removed from the expected request')
  void the_header_is_removed_from_the_expected_request(String name) {
    expectedRequest.headers.removeAll { key, value -> key.equalsIgnoreCase(name) }
  }

  @Given('the {string} header is removed from the received request')
  void the_header_is_removed_from_the_received_request(String name) {
    def request = receivedRequests[0]
    request.headers.removeAll { key, value -> key.equalsIgnoreCase(name) }
    // A real request carries no body type of its own, only what can be sniffed from the bytes
    if (request.body.present) {
      request.body = OptionalBody.body(request.body.unwrap())
    }
  }

  // The headers column is a JSON object of a name to a list of values, so values can contain commas
  static void setupQueryAndHeaders(HttpRequest request, Map<String, String> entry) {
    if (entry['query']) {
      request.query = PactReaderKt.queryStringToMap(entry['query'])
    }
    if (entry['headers']) {
      def headers = JsonParser.INSTANCE.parseString(entry['headers'])
      headers.entries.each { name, values ->
        request.headers[name] = values.values*.asString()
      }
    }
  }

  @When('the request is compared to the expected one')
  void the_request_is_compared_to_the_expected_one() {
    def pact = new V4Pact(new Consumer(this.class.name), new Provider(this.class.name))
    def interaction = new V4Interaction.SynchronousHttp(null, 'compatibility-suite', [], expectedRequest)
    requestResults << requestMismatches(pact, interaction, receivedRequests[0])
  }

  @Then('the comparison should be OK')
  void the_comparison_should_be_ok() {
    assert requestResults.every { it.mismatches.empty }
  }

  @Then('the comparison should NOT be OK')
  void the_comparison_should_not_be_ok() {
    assert requestResults.any { !it.mismatches.empty }
  }

  @Then('the mismatches will contain a mismatch with error {string} -> {string}')
  @SuppressWarnings('SpaceAfterOpeningBrace')
  void the_mismatches_will_contain_a_mismatch_with_error(String path, String error) {
    assert requestResults.any {
      it.mismatches.find {
        def pathMatches = switch (it) {
          case HeaderMismatch -> it.headerKey == path
          case BodyMismatch -> it.path == path
          default -> false
        }
        def desc = it.description()
        desc.contains(error) && pathMatches
      } != null
    }
  }
}
