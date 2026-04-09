import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/users")
public class ReactiveRepositoryInterfaceExample {

    // ==========================
    // CONFIG
    // ==========================
    private static final String RW_JDBC_URL =
            "jdbc:sqlserver://<server>.database.windows.net:1433;database=<db>;encrypt=true;";

    private static final String RO_JDBC_URL =
            "jdbc:sqlserver://<server>.database.windows.net:1433;database=<db>;encrypt=true;applicationIntent=ReadOnly;";

    private static final int QUERY_TIMEOUT = 30;

    private static final HikariDataSource writeDs = createDataSource(RW_JDBC_URL, false);
    private static final HikariDataSource readDs  = createDataSource(RO_JDBC_URL, true);

    private static final UserService service =
            new UserService(
                    new UserWriteRepositoryImpl(writeDs),
                    new UserReadRepositoryImpl(readDs)
            );

    // ==========================
    // TOKEN PROVIDER
    // ==========================
    static class TokenProvider {
        private static String token;
        private static Instant expiry;

        public synchronized static String getToken() {
            if (token == null || expiry == null ||
                    Instant.now().isAfter(expiry.minusSeconds(300))) {

                var credential = new DefaultAzureCredentialBuilder().build();

                AccessToken t = credential
                        .getToken(new TokenRequestContext()
                                .addScopes("https://database.windows.net/.default"))
                        .block(Duration.ofSeconds(10));

                token = t.getToken();
                expiry = t.getExpiresAt();

                System.out.println("Token refreshed");
            }
            return token;
        }
    }

    // ==========================
    // DATASOURCE
    // ==========================
    static HikariDataSource createDataSource(String jdbcUrl, boolean readOnly) {

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);

        config.setDataSource(new DataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                return getConnection(null, null);
            }

            @Override
            public Connection getConnection(String u, String p) throws SQLException {
                Properties props = new Properties();
                props.put("accessToken", TokenProvider.getToken());

                Connection conn = DriverManager.getConnection(jdbcUrl, props);
                conn.setReadOnly(readOnly);
                return conn;
            }

            public <T> T unwrap(Class<T> iface) { return null; }
            public boolean isWrapperFor(Class<?> iface) { return false; }
            public java.io.PrintWriter getLogWriter() { return null; }
            public void setLogWriter(java.io.PrintWriter out) {}
            public void setLoginTimeout(int seconds) {}
            public int getLoginTimeout() { return 30; }
            public java.util.logging.Logger getParentLogger() { return null; }
        });

        config.setMaximumPoolSize(readOnly ? 15 : 10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(300000);
        config.setMaxLifetime(900000);
        config.setConnectionTimeout(30000);
        config.setConnectionTestQuery("SELECT 1");

        return new HikariDataSource(config);
    }

    // ==========================
    // ENTITY
    // ==========================
    static class User {
        public int id;
        public String name;

        public User() {}
        public User(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    // ==========================
    // REPOSITORY INTERFACES
    // ==========================
    interface UserWriteRepository {
        void saveAll(List<User> users) throws SQLException;
    }

    interface UserReadRepository {
        List<User> findAll() throws SQLException;
    }

    // ==========================
    // IMPLEMENTATIONS
    // ==========================
    static class UserWriteRepositoryImpl implements UserWriteRepository {

        private final HikariDataSource ds;

        UserWriteRepositoryImpl(HikariDataSource ds) {
            this.ds = ds;
        }

        @Override
        public void saveAll(List<User> users) throws SQLException {

            try (Connection conn = ds.getConnection()) {

                conn.setAutoCommit(false);

                try (PreparedStatement stmt =
                             conn.prepareStatement("INSERT INTO users (id, name) VALUES (?, ?)")) {

                    stmt.setQueryTimeout(QUERY_TIMEOUT);

                    for (User u : users) {
                        stmt.setInt(1, u.id);
                        stmt.setString(2, u.name);
                        stmt.addBatch();
                    }

                    stmt.executeBatch();
                    conn.commit();

                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                }
            }
        }
    }

    static class UserReadRepositoryImpl implements UserReadRepository {

        private final HikariDataSource ds;

        UserReadRepositoryImpl(HikariDataSource ds) {
            this.ds = ds;
        }

        @Override
        public List<User> findAll() throws SQLException {

            List<User> list = new ArrayList<>();

            try (Connection conn = ds.getConnection();
                 PreparedStatement stmt =
                         conn.prepareStatement("SELECT id, name FROM users WITH (NOLOCK)")) {

                stmt.setQueryTimeout(QUERY_TIMEOUT);

                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    list.add(new User(
                            rs.getInt("id"),
                            rs.getString("name")));
                }
            }

            return list;
        }
    }

    // ==========================
    // SERVICE (USES INTERFACES ONLY)
    // ==========================
    static class UserService {

        private final UserWriteRepository writeRepo;
        private final UserReadRepository readRepo;

        UserService(UserWriteRepository writeRepo,
                    UserReadRepository readRepo) {
            this.writeRepo = writeRepo;
            this.readRepo = readRepo;
        }

        Mono<Void> createUsers(List<User> users) {
            return Mono.fromRunnable(() -> {
                        try {
                            writeRepo.saveAll(users);
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .retry(3)
                    .then();
        }

        Flux<User> getUsers() {
            return Mono.fromCallable(() -> {
                        try {
                            return readRepo.findAll();
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .flatMapMany(Flux::fromIterable)
                    .subscribeOn(Schedulers.boundedElastic())
                    .retry(3);
        }
    }

    // ==========================
    // CONTROLLER
    // ==========================
    @GetMapping
    public Flux<User> getUsers() {
        return service.getUsers();
    }

    @PostMapping
    public Mono<Void> createUsers(@RequestBody List<User> users) {
        return service.createUsers(users);
    }
      }
