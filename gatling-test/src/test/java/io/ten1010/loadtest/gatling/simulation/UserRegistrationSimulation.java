package io.ten1010.loadtest.gatling.simulation;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;
import java.util.UUID;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * 사용자 가입 + 상품 조회 시뮬레이션
 *
 * 시나리오:
 *   1. 사용자 가입 (POST /api/users)
 *   2. 사용자 조회 (GET /api/users/{id})
 *   3. 상품 목록 다양한 조건으로 검색 (GET /api/products)
 *   4. 사용자 정보 수정 (PUT /api/users/{id})
 *
 * 부하 패턴:
 *   - 30초에 걸쳐 50명의 사용자 점진적 증가 (ramp-up)
 *   - 이후 50명 유지 30초 (steady state)
 */
public class UserRegistrationSimulation extends Simulation {

    private static final String BASE_URL = "http://localhost:8080";

    HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .contentTypeHeader("application/json")
            .acceptHeader("application/json")
            .shareConnections();

    // ── 시나리오 ──────────────────────────────────────────────────────────────

    ScenarioBuilder userLifecycle = scenario("사용자 생애주기")

            // 1. 세션에 고유 사용자 데이터 생성
            .exec(session -> {
                String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
                return session
                        .set("username", "user_" + uid)
                        .set("email", "user_" + uid + "@loadtest.io")
                        .set("password", "pass_" + uid);
            })

            // 2. 사용자 가입
            .exec(http("사용자 가입")
                    .post("/api/users")
                    .body(StringBody("""
                            {
                              "username": "#{username}",
                              "email": "#{email}",
                              "password": "#{password}"
                            }
                            """))
                    .check(status().is(201))
                    .check(jsonPath("$.id").saveAs("userId"))
                    .check(jsonPath("$.username").is("#{username}")))

            .pause(Duration.ofMillis(200), Duration.ofMillis(500))

            // 3. 가입한 사용자 조회
            .exec(http("사용자 조회")
                    .get("/api/users/#{userId}")
                    .check(status().is(200))
                    .check(jsonPath("$.id").is("#{userId}")))

            .pause(Duration.ofMillis(300), Duration.ofMillis(700))

            // 4. 상품 목록 조회 (필터 없음, 기본 페이징)
            .exec(http("상품 목록 조회")
                    .get("/api/products")
                    .queryParam("page", "0")
                    .queryParam("size", "20")
                    .check(status().is(200))
                    .check(jsonPath("$.totalElements").exists()))

            .pause(Duration.ofMillis(100), Duration.ofMillis(300))

            // 5. 상품 검색 - 카테고리 필터
            .exec(http("상품 검색 - 카테고리")
                    .get("/api/products")
                    .queryParam("category", "electronics")
                    .queryParam("inStock", "true")
                    .queryParam("sort", "price,asc")
                    .check(status().is(200)))

            .pause(Duration.ofMillis(100), Duration.ofMillis(300))

            // 6. 상품 검색 - 가격 범위
            .exec(http("상품 검색 - 가격 범위")
                    .get("/api/products")
                    .queryParam("minPrice", "10000")
                    .queryParam("maxPrice", "500000")
                    .queryParam("sort", "price,desc")
                    .check(status().is(200)))

            .pause(Duration.ofMillis(200), Duration.ofMillis(500))

            // 7. 사용자 정보 수정 (이메일 변경)
            .exec(session -> {
                String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                return session.set("newEmail", "updated_" + uid + "@loadtest.io");
            })
            .exec(http("사용자 정보 수정")
                    .put("/api/users/#{userId}")
                    .body(StringBody("""
                            {
                              "email": "#{newEmail}",
                              "password": "newpass123"
                            }
                            """))
                    .check(status().is(200))
                    .check(jsonPath("$.email").is("#{newEmail}")));

    // ── 부하 설정 ─────────────────────────────────────────────────────────────

    {
        setUp(
                userLifecycle.injectOpen(
                        nothingFor(Duration.ofSeconds(2)),                       // 워밍업 대기
                        rampUsers(50).during(Duration.ofSeconds(30)),            // 30초에 걸쳐 50명 점진적 증가
                        constantUsersPerSec(5).during(Duration.ofSeconds(30))    // 30초간 초당 5명 유지
                )
        )
                .protocols(httpProtocol)
                .assertions(
                        global().responseTime().percentile(95.0).lt(1000),   // p95 응답시간 < 1s
                        global().successfulRequests().percent().gte(95.0)    // 성공률 >= 95%
                );
    }
}
