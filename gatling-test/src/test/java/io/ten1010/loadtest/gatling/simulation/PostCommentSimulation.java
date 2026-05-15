package io.ten1010.loadtest.gatling.simulation;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;
import java.util.UUID;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * ============================================================
 * [이 시뮬레이션이 측정하는 것]
 *
 * 게시판 서비스의 읽기/쓰기 혼합 부하 특성을 측정한다.
 * 실제 서비스는 읽기(조회)가 쓰기(작성)보다 훨씬 많다(Read-heavy).
 *
 * 두 시나리오를 동시에 실행해 현실적인 트래픽을 재현한다:
 *   writerFlow  — 게시글/댓글 CRUD 전체 생애주기 (쓰기 중심)
 *   readerFlow  — 게시글 목록/상세 반복 조회 (읽기 중심)
 *
 * 측정 포인트:
 *   - viewCount++ (단건 조회 시 UPDATE) 의 동시 처리 성능
 *   - 읽기·쓰기가 섞인 상황에서의 DB Lock 경합
 *   - QueryDSL 동적 검색 쿼리의 응답 시간
 * ============================================================
 */
public class PostCommentSimulation extends Simulation {

    private static final String BASE_URL = "http://localhost:8080";

    HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .contentTypeHeader("application/json")
            .acceptHeader("application/json")
            .shareConnections();

    // ============================================================
    // [시나리오 A — 게시글/댓글 CRUD 전체 생애주기]
    //
    // 한 명의 가상 사용자가 수행하는 행동:
    //   가입 → 게시글 작성 → 조회 → 검색 → 수정 → 댓글 작성(2회) → 댓글 목록 조회
    //        → 댓글 수정 → 댓글 삭제 → 게시글 삭제
    //
    // [설계 포인트]
    // 각 VU가 독립적인 게시글·댓글 ID를 갖도록 saveAs() 를 적극 활용한다.
    // 이렇게 하면 VU 간 데이터 간섭 없이 독립적인 시나리오 검증이 가능하다.
    // ============================================================
    ScenarioBuilder writerFlow = scenario("게시글 작성 + 댓글 CRUD")

            .exec(session -> {
                String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
                return session
                        .set("username", "writer_" + uid)
                        .set("email", "writer_" + uid + "@loadtest.io")
                        .set("password", "writepass1")
                        // 게시글 제목/본문에도 uid 를 포함 → 검색 테스트 시 유니크한 결과 보장
                        .set("postTitle", "부하 테스트 게시글 " + uid)
                        .set("postContent", "이 게시글은 Gatling 부하 테스트를 위해 자동 생성되었습니다. ID=" + uid);
            })

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

            // ============================================================
            // [viewCount 초기값 검증]
            //
            // 게시글 작성 직후 응답에서 viewCount 가 0 인지 확인한다.
            // 이 check 로 두 가지를 동시에 검증한다:
            //   1) 서버가 게시글 작성 시 viewCount 를 0 으로 초기화하는지
            //   2) viewCount 필드가 응답 JSON 에 포함되는지
            // ============================================================
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

            // ============================================================
            // [상태 변화 검증 — viewCount 증가 확인]
            //
            // 게시글 단건 조회(GET /api/posts/{id}) 는 서버에서 viewCount++ 를 수행한다.
            // 이전 요청(게시글 작성)에서 viewCount 가 0 임을 확인했으므로,
            // 첫 번째 조회 후 반환된 viewCount 는 1 이어야 한다.
            //
            // 이 체이닝된 검증이 의미 있는 이유:
            //   - 앞 요청에서 saveAs()로 저장한 postId 를 URL에 사용
            //   - 응답의 viewCount 가 정확히 1 인지 확인
            //   → 조회 시 viewCount 증가 로직이 정상 동작함을 보장
            // ============================================================
            .exec(http("게시글 상세 조회")
                    .get("/api/posts/#{postId}")
                    .check(status().is(200))
                    .check(jsonPath("$.id").is("#{postId}"))
                    .check(jsonPath("$.viewCount").is("1"))) // 최초 조회 → 0에서 1로 증가

            .pause(Duration.ofMillis(100), Duration.ofMillis(300))

            // ============================================================
            // [QueryDSL 동적 검색 성능 측정]
            //
            // 제목 + 정렬 조건으로 검색 — BooleanBuilder + PathBuilder 동적 쿼리 발생
            // "부하 테스트" 로 검색하면 이 VU가 만든 게시글이 포함되어야 한다.
            //
            // .check(jsonPath("$.content").exists())
            //   - 결과가 비어있어도 "content" 키 자체는 항상 존재하므로 exists() 가 적절.
            //   - 결과가 있어야 한다면 jsonPath("$.content[0]").exists() 로 변경.
            // ============================================================
            .exec(http("게시글 검색 - 제목")
                    .get("/api/posts")
                    .queryParam("title", "부하 테스트")
                    .queryParam("sort", "viewCount,desc")
                    .queryParam("size", "10")
                    .check(status().is(200))
                    .check(jsonPath("$.content").exists()))

            .pause(Duration.ofMillis(100), Duration.ofMillis(300))

            // 작성자(userId) 기준 필터 — 내 게시글만 조회
            .exec(http("게시글 검색 - 작성자")
                    .get("/api/posts")
                    .queryParam("userId", "#{userId}")
                    .check(status().is(200)))

            .pause(Duration.ofMillis(200), Duration.ofMillis(400))

            // ============================================================
            // [두 번 연속 exec — 세션 업데이트 후 즉시 다음 요청에 사용]
            //
            // 세션 업데이트(exec(session -> ...)) 와
            // HTTP 요청(exec(http(...))) 을 체인으로 연결하는 패턴.
            //
            // 순서 보장:
            //   1) updatedTitle 을 세션에 저장
            //   2) 다음 exec 에서 #{updatedTitle} 로 참조 → 올바른 값 사용
            // ============================================================
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
                    // 응답의 title이 방금 세션에 저장한 updatedTitle 과 일치하는지 교차 검증
                    .check(jsonPath("$.title").is("#{updatedTitle}")))

            .pause(Duration.ofMillis(300), Duration.ofMillis(700))

            // ============================================================
            // [첫 번째 댓글 — commentId 저장]
            //
            // 나중에 수정·삭제에 사용할 commentId 를 첫 번째 댓글에서 저장한다.
            // 두 번째 댓글은 commentId 를 저장하지 않는다.
            // → 댓글 목록에 2개가 있는지 totalElements 로 확인하기 위한 목적.
            // ============================================================
            .exec(http("댓글 작성")
                    .post("/api/posts/#{postId}/comments")
                    .body(StringBody("""
                            {
                              "userId": #{userId},
                              "content": "이 댓글은 Gatling 부하 테스트에서 자동 생성된 댓글입니다."
                            }
                            """))
                    .check(status().is(201))
                    .check(jsonPath("$.id").saveAs("commentId"))) // 수정·삭제에 필요한 ID 저장

            .pause(Duration.ofMillis(100), Duration.ofMillis(300))

            // 두 번째 댓글 (commentId 저장 안 함 — 이후 steps 에서 사용하지 않음)
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

            // ============================================================
            // [페이지 응답 검증 — $.totalElements]
            //
            // 댓글 2개를 작성했으므로 totalElements >= 2 를 확인해야 더 정확하지만,
            // 부하 테스트에서는 exists() 로 충분하다.
            // (정합성 검증은 통합 테스트의 역할, 부하 테스트는 성능이 목적)
            // ============================================================
            .exec(http("댓글 목록 조회")
                    .get("/api/posts/#{postId}/comments")
                    .queryParam("page", "0")
                    .queryParam("size", "20")
                    .check(status().is(200))
                    .check(jsonPath("$.totalElements").exists()))

            .pause(Duration.ofMillis(200), Duration.ofMillis(500))

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

            // DELETE 는 응답 body 없이 204 No Content 반환
            .exec(http("댓글 삭제")
                    .delete("/api/comments/#{commentId}")
                    .check(status().is(204))) // 204: 성공했지만 반환할 body 없음

            .pause(Duration.ofMillis(200), Duration.ofMillis(500))

            .exec(http("게시글 삭제")
                    .delete("/api/posts/#{postId}")
                    .check(status().is(204)));

    // ============================================================
    // [시나리오 B — 읽기 전용 집중 조회]
    //
    // 실제 서비스에서 읽기(조회) 트래픽은 쓰기보다 수십 배 많다.
    // 이 시나리오는 다수의 독자(reader)가 동시에 게시글을 조회하는 상황을 재현한다.
    //
    // [설계 의도]
    // writerFlow 와 readerFlow 를 동시에 실행하면:
    //   - writerFlow: INSERT, UPDATE, DELETE 쿼리 발생
    //   - readerFlow: SELECT 쿼리 집중 발생
    //   → 읽기/쓰기 혼합 부하 상황에서의 성능 측정
    //
    // [repeat() 활용]
    // 각 reader 가 3회 반복해서 조회 → 실제 사용자가 여러 페이지를 탐색하는 패턴 재현
    // ============================================================
    ScenarioBuilder readerFlow = scenario("게시글 반복 조회")

            .exec(session -> {
                String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
                return session
                        .set("username", "reader_" + uid)
                        .set("email", "reader_" + uid + "@loadtest.io")
                        .set("password", "readpass1");
            })

            // 읽기 전용 사용자도 가입 후 userId 를 세션에 저장해 "내 게시글" 조회에 활용
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

            // ============================================================
            // [repeat(N).on(exec(...).exec(...)) — 반복 실행]
            //
            // repeat(3)
            //   - 내부 블록을 3회 반복한다.
            //   - 각 반복은 독립적으로 실행되며 사이에 pause 를 넣으면
            //     더 현실적인 페이지 탐색 패턴이 된다.
            //
            // .on(exec(...))
            //   - 반복할 행동 블록을 지정한다.
            //   - exec 는 체이닝으로 여러 요청을 묶을 수 있다.
            //
            // [repeat vs foreach vs loop]
            //   repeat(N)       : 고정 횟수 반복
            //   foreach(list)   : 컬렉션 순회 (Feeder 없이 리스트 처리)
            //   loop 조건 기반  : asLongAs(), doWhile() 사용
            // ============================================================
            .repeat(3).on(
                    // 기본 정렬: 최신순
                    exec(http("게시글 전체 목록")
                            .get("/api/posts")
                            .queryParam("page", "0")
                            .queryParam("size", "20")
                            .queryParam("sort", "createdAt,desc")
                            .check(status().is(200)))

                    .pause(Duration.ofMillis(100), Duration.ofMillis(300))

                    // 조회수 순 정렬 — viewCount 인덱스 유무에 따라 성능 차이 발생
                    .exec(http("게시글 목록 - 최다 조회수")
                            .get("/api/posts")
                            .queryParam("sort", "viewCount,desc")
                            .queryParam("size", "5")
                            .check(status().is(200)))

                    .pause(Duration.ofMillis(200), Duration.ofMillis(400))

                    // userId 필터 — 작성자별 필터링 쿼리 성능 측정
                    .exec(http("내 게시글 목록")
                            .get("/api/posts")
                            .queryParam("userId", "#{readerId}")
                            .check(status().is(200)))

                    .pause(Duration.ofMillis(100), Duration.ofMillis(300))
            );

    // ============================================================
    // [setUp — 두 시나리오의 부하 주입 설계]
    //
    // writerFlow 타임라인:
    //   0s        nothingFor(2s)      — 워밍업
    //   2s~32s    rampUsers(30) 30s   — 점진 증가 (0.67초마다 1명)
    //   32s~52s   constantUsersPerSec(2) 20s — 초당 2명 steady
    //
    // readerFlow 타임라인:
    //   0s        nothingFor(3s)      — writerFlow 가 먼저 데이터를 만들도록 약간 늦게 시작
    //   3s        atOnceUsers(20)     — 독자 20명 동시 투입 (읽기 스파이크)
    //   3s~23s    rampUsersPerSec(1).to(5).during(20s) — 초당 1명에서 5명으로 점진 증가
    //
    // [rampUsersPerSec(from).to(to).during(duration)]
    //   - 주입 속도를 from ~ to 까지 선형으로 증가시킨다.
    //   - 예) rampUsersPerSec(1).to(5).during(20s)
    //         → 20초 동안 초당 1명 → 초당 5명으로 증가
    //   - constantUsersPerSec 와의 차이:
    //       constantUsersPerSec(N) → 일정 속도 유지
    //       rampUsersPerSec(a).to(b) → 속도 자체를 점진적으로 변화
    // ============================================================
    {
        setUp(
                writerFlow.injectOpen(
                        nothingFor(Duration.ofSeconds(2)),
                        rampUsers(30).during(Duration.ofSeconds(30)),
                        constantUsersPerSec(2).during(Duration.ofSeconds(20))
                ),
                readerFlow.injectOpen(
                        nothingFor(Duration.ofSeconds(3)),
                        // ============================================================
                        // [atOnceUsers + rampUsersPerSec 조합]
                        //
                        // atOnceUsers(20) 으로 초기 읽기 부하를 즉시 생성하고,
                        // 이어서 rampUsersPerSec 로 지속적으로 독자를 추가한다.
                        // 이 조합은 이벤트 시작 직후 트래픽 패턴을 재현한다:
                        //   "처음에 몰리고, 이후에도 꾸준히 유입"
                        // ============================================================
                        atOnceUsers(20),
                        rampUsersPerSec(1).to(5).during(Duration.ofSeconds(20))
                )
        )
                .protocols(httpProtocol)
                .assertions(
                        // p95 응답시간 < 1.5s
                        // (writerFlow 의 댓글 수정·삭제 등 쓰기 작업이 포함되어 1s보다 여유를 둠)
                        global().responseTime().percentile(95.0).lt(1500),
                        // 전체 성공률 >= 95%
                        global().successfulRequests().percent().gte(95.0)
                );
    }
}
