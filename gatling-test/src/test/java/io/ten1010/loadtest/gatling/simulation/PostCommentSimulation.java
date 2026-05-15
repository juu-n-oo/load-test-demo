package io.ten1010.loadtest.gatling.simulation;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;
import java.util.UUID;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * 게시판 + 댓글 부하 테스트 시뮬레이션
 *
 * 시나리오 A — 게시글 작성자:
 *   1. 사용자 가입
 *   2. 게시글 작성 (POST /api/posts)
 *   3. 게시글 단건 조회 — viewCount++ (GET /api/posts/{id})
 *   4. 게시글 목록 검색 (GET /api/posts?title=&userId=)
 *   5. 게시글 수정 (PUT /api/posts/{id})
 *   6. 게시글에 댓글 작성 (POST /api/posts/{postId}/comments)
 *   7. 댓글 목록 조회 (GET /api/posts/{postId}/comments)
 *   8. 댓글 수정 (PUT /api/comments/{id})
 *   9. 댓글 삭제 (DELETE /api/comments/{id})
 *  10. 게시글 삭제 (DELETE /api/posts/{id})
 *
 * 시나리오 B — 읽기 전용 조회:
 *   1. 게시글 목록 반복 조회 (여러 조건 조합)
 *   2. 고정 게시글 상세 조회 (viewCount 증가 집중 테스트)
 *
 * 부하 패턴:
 *   - 작성자 시나리오: 30초에 걸쳐 30명 점진적 증가
 *   - 읽기 시나리오:  동시 20명 즉시 투입 후 20초간 초당 3명 추가
 */
public class PostCommentSimulation extends Simulation {

    private static final String BASE_URL = "http://localhost:8080";

    HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .contentTypeHeader("application/json")
            .acceptHeader("application/json")
            .shareConnections();

    // ── 시나리오 A: 게시글/댓글 CRUD ─────────────────────────────────────────

    ScenarioBuilder writerFlow = scenario("게시글 작성 + 댓글 CRUD")

            // 1. 고유 사용자 데이터 준비
            .exec(session -> {
                String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
                return session
                        .set("username", "writer_" + uid)
                        .set("email", "writer_" + uid + "@loadtest.io")
                        .set("password", "writepass1")
                        .set("postTitle", "부하 테스트 게시글 " + uid)
                        .set("postContent", "이 게시글은 Gatling 부하 테스트를 위해 자동 생성되었습니다. ID=" + uid);
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

            .pause(Duration.ofMillis(200), Duration.ofMillis(500))

            // 3. 게시글 작성
            .exec(http("게시글 작성")
                    .post("/api/posts")
                    .body(StringBody("""
                            {
                              "userId": #{userId},
                              "title": "#{postTitle}",
                              "content": "#{postContent}"
                            }
                            """))
                    .check(status().is(201))
                    .check(jsonPath("$.id").saveAs("postId"))
                    .check(jsonPath("$.viewCount").is("0")))

            .pause(Duration.ofMillis(300), Duration.ofMillis(600))

            // 4. 게시글 단건 조회 (viewCount 증가)
            .exec(http("게시글 상세 조회")
                    .get("/api/posts/#{postId}")
                    .check(status().is(200))
                    .check(jsonPath("$.id").is("#{postId}"))
                    .check(jsonPath("$.viewCount").is("1")))

            .pause(Duration.ofMillis(100), Duration.ofMillis(300))

            // 5. 게시글 목록 검색 - 제목 검색
            .exec(http("게시글 검색 - 제목")
                    .get("/api/posts")
                    .queryParam("title", "부하 테스트")
                    .queryParam("sort", "viewCount,desc")
                    .queryParam("size", "10")
                    .check(status().is(200))
                    .check(jsonPath("$.content").exists()))

            .pause(Duration.ofMillis(100), Duration.ofMillis(300))

            // 6. 게시글 목록 검색 - 작성자 필터
            .exec(http("게시글 검색 - 작성자")
                    .get("/api/posts")
                    .queryParam("userId", "#{userId}")
                    .check(status().is(200)))

            .pause(Duration.ofMillis(200), Duration.ofMillis(400))

            // 7. 게시글 수정
            .exec(session -> {
                String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
                return session.set("updatedTitle", "수정된 게시글 " + uid);
            })
            .exec(http("게시글 수정")
                    .put("/api/posts/#{postId}")
                    .body(StringBody("""
                            {
                              "title": "#{updatedTitle}",
                              "content": "수정된 본문 내용입니다. 부하 테스트 검증용."
                            }
                            """))
                    .check(status().is(200))
                    .check(jsonPath("$.title").is("#{updatedTitle}")))

            .pause(Duration.ofMillis(300), Duration.ofMillis(700))

            // 8. 댓글 작성
            .exec(http("댓글 작성")
                    .post("/api/posts/#{postId}/comments")
                    .body(StringBody("""
                            {
                              "userId": #{userId},
                              "content": "이 댓글은 Gatling 부하 테스트에서 자동 생성된 댓글입니다."
                            }
                            """))
                    .check(status().is(201))
                    .check(jsonPath("$.id").saveAs("commentId")))

            .pause(Duration.ofMillis(100), Duration.ofMillis(300))

            // 9. 두 번째 댓글 추가
            .exec(http("댓글 추가 작성")
                    .post("/api/posts/#{postId}/comments")
                    .body(StringBody("""
                            {
                              "userId": #{userId},
                              "content": "두 번째 댓글입니다."
                            }
                            """))
                    .check(status().is(201)))

            .pause(Duration.ofMillis(200), Duration.ofMillis(400))

            // 10. 댓글 목록 조회
            .exec(http("댓글 목록 조회")
                    .get("/api/posts/#{postId}/comments")
                    .queryParam("page", "0")
                    .queryParam("size", "20")
                    .check(status().is(200))
                    .check(jsonPath("$.totalElements").exists()))

            .pause(Duration.ofMillis(200), Duration.ofMillis(500))

            // 11. 댓글 수정
            .exec(http("댓글 수정")
                    .put("/api/comments/#{commentId}")
                    .body(StringBody("""
                            {
                              "content": "수정된 댓글입니다. 부하 테스트 확인 완료."
                            }
                            """))
                    .check(status().is(200))
                    .check(jsonPath("$.content").is("수정된 댓글입니다. 부하 테스트 확인 완료.")))

            .pause(Duration.ofMillis(200), Duration.ofMillis(400))

            // 12. 댓글 삭제
            .exec(http("댓글 삭제")
                    .delete("/api/comments/#{commentId}")
                    .check(status().is(204)))

            .pause(Duration.ofMillis(200), Duration.ofMillis(500))

            // 13. 게시글 삭제
            .exec(http("게시글 삭제")
                    .delete("/api/posts/#{postId}")
                    .check(status().is(204)));

    // ── 시나리오 B: 읽기 전용 집중 조회 ─────────────────────────────────────

    ScenarioBuilder readerFlow = scenario("게시글 반복 조회")

            .exec(session -> {
                String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
                return session
                        .set("username", "reader_" + uid)
                        .set("email", "reader_" + uid + "@loadtest.io")
                        .set("password", "readpass1");
            })

            // 읽기 전용 사용자 가입 (ID 저장용)
            .exec(http("읽기 사용자 가입")
                    .post("/api/users")
                    .body(StringBody("""
                            {
                              "username": "#{username}",
                              "email": "#{email}",
                              "password": "#{password}"
                            }
                            """))
                    .check(status().is(201))
                    .check(jsonPath("$.id").saveAs("readerId")))

            // 다양한 조건으로 게시글 목록 반복 조회
            .repeat(3).on(
                    exec(http("게시글 전체 목록")
                            .get("/api/posts")
                            .queryParam("page", "0")
                            .queryParam("size", "20")
                            .queryParam("sort", "createdAt,desc")
                            .check(status().is(200)))

                    .pause(Duration.ofMillis(100), Duration.ofMillis(300))

                    .exec(http("게시글 목록 - 최다 조회수")
                            .get("/api/posts")
                            .queryParam("sort", "viewCount,desc")
                            .queryParam("size", "5")
                            .check(status().is(200)))

                    .pause(Duration.ofMillis(200), Duration.ofMillis(400))

                    .exec(http("내 게시글 목록")
                            .get("/api/posts")
                            .queryParam("userId", "#{readerId}")
                            .check(status().is(200)))

                    .pause(Duration.ofMillis(100), Duration.ofMillis(300))
            );

    // ── 부하 설정 ─────────────────────────────────────────────────────────────

    {
        setUp(
                writerFlow.injectOpen(
                        nothingFor(Duration.ofSeconds(2)),
                        rampUsers(30).during(Duration.ofSeconds(30)),
                        constantUsersPerSec(2).during(Duration.ofSeconds(20))
                ),
                readerFlow.injectOpen(
                        nothingFor(Duration.ofSeconds(3)),
                        atOnceUsers(20),
                        rampUsersPerSec(1).to(5).during(Duration.ofSeconds(20))
                )
        )
                .protocols(httpProtocol)
                .assertions(
                        global().responseTime().percentile(95.0).lt(1500),  // p95 < 1.5s
                        global().successfulRequests().percent().gte(95.0)   // 성공률 >= 95%
                );
    }
}
