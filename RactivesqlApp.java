import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.core.credential.*;
import io.r2dbc.spi.*;
import io.r2dbc.pool.*;
import io.r2dbc.mssql.*;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

public class ReactiveSqlApp {

    // =========================
    // ENTITY
    // =========================
    static class User {
        Long id;
        String name;
        Integer age;

        public String toString() {
            return id + " " + name + " " + age;
        }
    }

    // =========================
    // TOKEN PROVIDER (RBAC)
    // =========================
    static class AzureTokenProvider {

        private final TokenCredential credential =
                new DefaultAzureCredentialBuilder().build();

        private static final String SCOPE = "https://database.windows.net/.default";

        private Mono<String> cachedToken;

        public Mono<String> getToken() {
            if (cachedToken == null) {
                cachedToken = Mono.fromFuture(
                        credential.getToken(new TokenRequestContext().addScopes(SCOPE)).toFuture()
                )
                .map(AccessToken::getToken)
                .cache(Duration.ofMinutes(50));
            }
            return cachedToken;
        }
    }

    // =========================
    // CONNECTION FACTORY (POOL + TOKEN)
    // =========================
    static class TokenAwareConnectionFactory {

        private final ConnectionPool pool;
        private final AzureTokenProvider tokenProvider;

        public TokenAwareConnectionFactory(String host, String db) {

            ConnectionFactory factory = new MssqlConnectionFactory(
                MssqlConnectionConfiguration.builder()
                    .host(host)
                    .port(1433)
                    .database(db)
                    .username("dummy")
                    .password("dummy")
                    .build()
            );

            this.pool = new ConnectionPool(
                    ConnectionPoolConfiguration.builder(factory)
                            .maxSize(20)
                            .initialSize(5)
                            .maxIdleTime(Duration.ofMinutes(20))
                            .build()
            );

            this.tokenProvider = new AzureTokenProvider();
        }

        public Mono<Connection> create() {
            return tokenProvider.getToken()
                    .flatMap(token ->
                        Mono.from(pool.create())
                            .flatMap(conn ->
                                Mono.from(conn.createStatement(
                                    "EXEC sp_set_session_context @key=N'access_token', @value=@token")
                                    .bind("token", token)
                                    .execute())
                                .thenReturn(conn)
                            )
                    );
        }
    }

    // =========================
    // QUERY DSL
    // =========================
    static class Criteria {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();

        Criteria eq(String field, Object value) {
            if (value != null) {
                conditions.add(field + " = @" + field);
                params.put(field, value);
            }
            return this;
        }

        Criteria gte(String field, Object value) {
            if (value != null) {
                conditions.add(field + " >= @" + field);
                params.put(field, value);
            }
            return this;
        }

        Criteria lte(String field, Object value) {
            if (value != null) {
                conditions.add(field + " <= @" + field);
                params.put(field, value);
            }
            return this;
        }

        String where() {
            return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        }
    }

    // =========================
    // PAGINATION
    // =========================
    static class PageRequest {
        int page = 0;
        int size = 50;
        String sortBy = "id";
        String direction = "ASC";

        int offset() {
            return page * size;
        }
    }

    // =========================
    // REPOSITORY
    // =========================
    static class UserRepository {

        private final TokenAwareConnectionFactory factory;

        public UserRepository(TokenAwareConnectionFactory factory) {
            this.factory = factory;
        }

        public Flux<User> search(Criteria c, PageRequest p) {

            String sql = "SELECT id, name, age FROM users"
                    + c.where()
                    + " ORDER BY " + p.sortBy + " " + p.direction
                    + " OFFSET @offset ROWS FETCH NEXT @limit ROWS ONLY";

            return Flux.usingWhen(
                factory.create(),
                conn -> {
                    Statement stmt = conn.createStatement(sql);

                    c.params.forEach(stmt::bind);
                    stmt.bind("offset", p.offset());
                    stmt.bind("limit", p.size);

                    return Flux.from(stmt.execute())
                            .flatMap(res ->
                                res.map((row, meta) -> {
                                    User u = new User();
                                    u.id = row.get("id", Long.class);
                                    u.name = row.get("name", String.class);
                                    u.age = row.get("age", Integer.class);
                                    return u;
                                })
                            );
                },
                Connection::close
            )
            .timeout(Duration.ofSeconds(5))
            .limitRate(100);
        }
    }

    // =========================
    // MAIN (TEST)
    // =========================
    public static void main(String[] args) {

        TokenAwareConnectionFactory factory =
                new TokenAwareConnectionFactory(
                        "your-server.database.windows.net",
                        "yourdb"
                );

        UserRepository repo = new UserRepository(factory);

        Criteria c = new Criteria()
                .eq("name", "John")
                .gte("age", 20)
                .lte("age", 50);

        PageRequest p = new PageRequest();
        p.page = 0;
        p.size = 20;

        repo.search(c, p)
                .doOnNext(System.out::println)
                .doOnError(Throwable::printStackTrace)
                .blockLast(); // ONLY for demo/testing
    }
}
