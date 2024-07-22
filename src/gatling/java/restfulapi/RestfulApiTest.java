package restfulapi;

import java.time.Duration;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class RestfulApiTest extends Simulation {

    // Define target Url
    String baseUrl = System.getProperty("baseUrl", "https://api.restful-api.dev");

    // Define httpProtocol to send raw/json data
    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(baseUrl)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    // Define feeder to run tests with data
    FeederBuilder.FileBased<Object> feeder = jsonFile("data/devices.json").circular();

    // Create the scenario and passing the feeder
    ScenarioBuilder scn = scenario("Restful API Test")
            .feed(feeder)

            // POST request to create device
            .exec(
                    http("POST - Create device")
                            .post("/objects")
                            .body(StringBody(
                                    """
                                        {
                                            "name":  "#{name}",
                                            "data": {
                                                "year": #{data.year},
                                                "price": #{data.price},
                                                "CPU model": "#{data.cpu_model}",
                                                "Hard disk size": "#{data.storage}"
                                            }
                                        }
                                    """
                            )).asJson()
                            .check(
                                    status().is(200),
                                    bodyString().saveAs("BODY"),
                                    jsonPath("$.id").saveAs("deviceId")
                            )
            )
            .exec(session -> {
                String deviceId = session.getString("deviceId");
                if (deviceId == null || deviceId.isEmpty()) {
                    System.err.println("Error: 'deviceId' not found in session");
                }
                System.out.println( "Created: " + session.getString("BODY"));
                return session;
            })

            .pause(3)

            // PUT request to update data.status device info
            .exec(
                    http("PUT - Update device")
                            .put("/objects/#{deviceId}")
                            .body(StringBody(
                                    """
                                        {
                                            "name":  "#{name}",
                                            "data": {
                                                "year": #{data.year},
                                                "price": #{data.price},
                                                "CPU model": "#{data.cpu_model}",
                                                "Hard disk size": "#{data.storage}",
                                                "status": "#{data.status}"
                                            }
                                        }
                                    """
                            )).asJson()
                            .check(
                                    status().is(200),
                                    bodyString().saveAs("BODY"),
                                    jsonPath("$.data.status").saveAs("status")
                            )
            )
            .exec(session -> {
                String status = session.getString("status");
                if (status == null || status.isEmpty()) {
                    System.err.println("Error: updated 'status' is not in session object");
                }
                System.out.println( "Updated: " + session.getString("BODY"));
                return session;
            })

            .pause(3)

            // GET request to validate device info
            .exec(
                    http("GET - Check device")
                            .get("/objects/#{deviceId}")
                            .check(
                                    status().is(200),
                                    bodyString().saveAs("BODY"),
                                    jmesPath("name").isEL("#{name}"),
                                    jmesPath("data.year").isEL("#{data.year}"),
                                    jmesPath("data.price").isEL("#{data.price}"),
                                    jmesPath("data.status").isEL("#{data.status}")
                            )
            )
            .exec(
                    session -> {
                        System.out.println("Final object: " + session.getString("BODY"));
                        return session;
                    }
            );

    {
        // Set up the scenario
        setUp(
                scn.injectClosed(
                        rampConcurrentUsers(5).to(15)
                                .during(Duration.ofSeconds(15)),
                        constantConcurrentUsers(10)
                                .during(Duration.ofSeconds(10))
                        //rampConcurrentUsers(1).to(2).during(Duration.ofSeconds(2))
                )
        ).protocols(httpProtocol);
    }

}