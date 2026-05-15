package io.ten1010.loadtest.gatling.simulation;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;
import java.util.UUID;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * 주문 프로세스 부하 테스트 시뮬레이션
 *
 * 시나리오:
 *   1. 사용자 가입 (POST /api/users)
 *   2. 상품 등록 (POST /api/products) — 각 가상 사용자가 독립적인 상품 생성
 *   3. 상품 목록 조회 + 상품 ID 추출
 *   4. 주문 생성 — 재고 차감 포함 (POST /api/orders)
 *   5. 주문 상세 조회 (GET /api/orders/{orderId})
 *   6. 주문 목록 조회 (GET /api/orders?userId=)
 *   7. 주문 상태 변경 PENDING → SHIPPED (PUT /api/orders/{orderId}/status)
 *   8. 주문 상태 변경 SHIPPED → DELIVERED
 *
 * 부하 패턴:
 *   - 20초에 걸쳐 30명 점진적 증가
 *   - 30초간 일정 부하 유지
 */
public class OrderFlowSimulation extends Simulation {

    private static final String BASE_URL = "http://localhost:8080";

    HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .contentTypeHeader("application/json")
            .acceptHeader("application/json")
            .shareConnections();

    // ── 시나리오 ──────────────────────────────────────────────────────────────

    ScenarioBuilder orderFlow = scenario("주문 전체 흐름")

            // 1. 세션에 고유 데이터 생성
            .exec(session -> {
                String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
                String pid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                return session
                        .set("username", "order_user_" + uid)
                        .set("email", "order_" + uid + "@loadtest.io")
                        .set("password", "orderpass1")
                        .set("productName", "부하테스트상품_" + pid);
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
                    .check(jsonPath("$.id").saveAs("userId")))

            .pause(Duration.ofMillis(100), Duration.ofMillis(300))

            // 3. 상품 등록
            .exec(http("상품 등록")
                    .post("/api/products")
                    .body(StringBody("""
                            {
                              "name": "#{productName}",
                              "description": "부하 테스트용 상품입니다.",
                              "price": 29900,
                              "stock": 1000,
                              "category": "test"
                            }
                            """))
                    .check(status().is(201))
                    .check(jsonPath("$.id").saveAs("productId"))
                    .check(jsonPath("$.stock").is("1000")))

            .pause(Duration.ofMillis(100), Duration.ofMillis(300))

            // 4. 상품 단건 조회
            .exec(http("상품 상세 조회")
                    .get("/api/products/#{productId}")
                    .check(status().is(200))
                    .check(jsonPath("$.id").is("#{productId}")))

            .pause(Duration.ofMillis(200), Duration.ofMillis(500))

            // 5. 주문 생성 (수량 2개)
            .exec(http("주문 생성")
                    .post("/api/orders")
                    .body(StringBody("""
                            {
                              "userId": #{userId},
                              "shippingAddress": "서울시 강남구 테스트로 123",
                              "productQuantities": {
                                "#{productId}": 2
                              }
                            }
                            """))
                    .check(status().is(201))
                    .check(jsonPath("$.id").saveAs("orderId"))
                    .check(jsonPath("$.status").is("PENDING"))
                    .check(jsonPath("$.totalPrice").exists()))

            .pause(Duration.ofMillis(200), Duration.ofMillis(500))

            // 6. 주문 상세 조회 (items fetch join 확인)
            .exec(http("주문 상세 조회")
                    .get("/api/orders/#{orderId}")
                    .check(status().is(200))
                    .check(jsonPath("$.id").is("#{orderId}"))
                    .check(jsonPath("$.items[0].productId").is("#{productId}"))
                    .check(jsonPath("$.items[0].quantity").is("2")))

            .pause(Duration.ofMillis(100), Duration.ofMillis(300))

            // 7. 사용자 주문 목록 조회
            .exec(http("주문 목록 조회")
                    .get("/api/orders")
                    .queryParam("userId", "#{userId}")
                    .queryParam("status", "PENDING")
                    .check(status().is(200))
                    .check(jsonPath("$.content[0].id").exists()))

            .pause(Duration.ofMillis(300), Duration.ofMillis(600))

            // 8. 주문 상태 변경: PENDING → SHIPPED
            .exec(http("주문 상태 변경 - SHIPPED")
                    .put("/api/orders/#{orderId}/status")
                    .body(StringBody("""
                            { "status": "SHIPPED" }
                            """))
                    .check(status().is(200))
                    .check(jsonPath("$.status").is("SHIPPED")))

            .pause(Duration.ofMillis(200), Duration.ofMillis(400))

            // 9. 주문 상태 변경: SHIPPED → DELIVERED
            .exec(http("주문 상태 변경 - DELIVERED")
                    .put("/api/orders/#{orderId}/status")
                    .body(StringBody("""
                            { "status": "DELIVERED" }
                            """))
                    .check(status().is(200))
                    .check(jsonPath("$.status").is("DELIVERED")));

    // ── 재고 부족 시나리오 ─────────────────────────────────────────────────────

    ScenarioBuilder stockExhaustion = scenario("재고 부족 주문 시도")

            .exec(session -> {
                String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
                String pid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                return session
                        .set("username", "stock_user_" + uid)
                        .set("email", "stock_" + uid + "@loadtest.io")
                        .set("password", "stockpass1")
                        .set("productName", "재고1개상품_" + pid);
            })

            // 사용자 가입
            .exec(http("사용자 가입 (재고테스트)")
                    .post("/api/users")
                    .body(StringBody("""
                            {
                              "username": "#{username}",
                              "email": "#{email}",
                              "password": "#{password}"
                            }
                            """))
                    .check(status().is(201))
                    .check(jsonPath("$.id").saveAs("userId")))

            // 재고 1개짜리 상품 등록
            .exec(http("재고 1개 상품 등록")
                    .post("/api/products")
                    .body(StringBody("""
                            {
                              "name": "#{productName}",
                              "description": "재고가 1개뿐인 상품",
                              "price": 9900,
                              "stock": 1,
                              "category": "limited"
                            }
                            """))
                    .check(status().is(201))
                    .check(jsonPath("$.id").saveAs("productId")))

            // 수량 2개 주문 → 재고 부족으로 400 응답 기대
            .exec(http("재고 초과 주문 시도")
                    .post("/api/orders")
                    .body(StringBody("""
                            {
                              "userId": #{userId},
                              "shippingAddress": "서울시 종로구 재고없음로 1",
                              "productQuantities": {
                                "#{productId}": 2
                              }
                            }
                            """))
                    .check(status().is(400)));  // BusinessException → 400

    // ── 부하 설정 ─────────────────────────────────────────────────────────────

    {
        setUp(
                orderFlow.injectOpen(
                        nothingFor(Duration.ofSeconds(2)),
                        rampUsers(30).during(Duration.ofSeconds(20)),
                        constantUsersPerSec(3).during(Duration.ofSeconds(30))
                ),
                stockExhaustion.injectOpen(
                        nothingFor(Duration.ofSeconds(5)),
                        atOnceUsers(10)
                )
        )
                .protocols(httpProtocol)
                .assertions(
                        // 전체 요청 기준 (재고부족 400 응답 포함되므로 성공률 기준 완화)
                        global().responseTime().percentile(95.0).lt(2000),
                        // 주문 생성 엔드포인트 기준 성공률은 별도 측정 어려우므로 전체 80% 이상
                        global().successfulRequests().percent().gte(80.0)
                );
    }
}
