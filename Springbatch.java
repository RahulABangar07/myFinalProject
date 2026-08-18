spring.datasource.url=jdbc:sqlserver://your-server:1433;databaseName=your-db;useBulkCopyForBatchInsert=true;encrypt=true;trustServerCertificate=true;
package com.example.batch;

import com.microsoft.sqlserver.jdbc.SQLServerDriver;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// ============================================================================
// 1. ANNOTATION DEFINITION
// ============================================================================
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@interface DbColumn {
    String value();
}

// ============================================================================
// 2. DOMAIN DATA MODEL
// ============================================================================
class User {
    @DbColumn("id")
    private Long id;

    @DbColumn("first_name")
    private String firstName;

    @DbColumn("last_name")
    private String lastName;

    private String email; // Defaults to field name since no annotation is present

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}

// ============================================================================
// 3. TASKLET IMPLEMENTATIONS (PRE & POST WORKFLOWS)
// ============================================================================
class PreStepProcedureTasklet implements Tasklet {
    private final JdbcTemplate jdbcTemplate;

    public PreStepProcedureTasklet(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        // Executes 1 setup procedure before the data copy step starts
        jdbcTemplate.execute("EXEC sp_PreBatchSetup");
        return RepeatStatus.FINISHED;
    }
}

class PostStepProcedureTasklet implements Tasklet {
    private final JdbcTemplate jdbcTemplate;

    public PostStepProcedureTasklet(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        // Executes 3 procedures sequentially inside a single unified Spring Transaction boundary
        jdbcTemplate.execute("EXEC sp_PostBatchProcessOne");
        jdbcTemplate.execute("EXEC sp_PostBatchProcessTwo");
        jdbcTemplate.execute("EXEC sp_PostBatchProcessThree");
        return RepeatStatus.FINISHED;
    }
}

// ============================================================================
// 4. BULK ITEM WRITER IMPLEMENTATION (OPTION 2 STRUCTURE)
// ============================================================================
class AutomatedBulkWriter implements ItemWriter<User> {
    private final SimpleJdbcInsert jdbcInsert;
    private final BeanPropertyItemSqlParameterSourceProvider<User> paramProvider;

    public AutomatedBulkWriter(DataSource dataSource) {
        // SimpleJdbcInsert queries MS SQL metadata automatically to map database columns
        this.jdbcInsert = new SimpleJdbcInsert(dataSource).withTableName("target_users");
        this.paramProvider = new BeanPropertyItemSqlParameterSourceProvider<>();
    }

    @Override
    public void write(Chunk<? extends User> chunk) throws Exception {
        var batchParams = chunk.getItems().stream()
                .map(paramProvider::createParameterSource)
                .toArray(org.springframework.jdbc.core.namedparam.SqlParameterSource[]::new);

        // Executes batch natively. 
        // Note: Ensure your application.properties string includes: "useBulkCopyForBatchInsert=true"
        jdbcInsert.executeBatch(batchParams);
    }
}

// ============================================================================
// 5. SPRING BATCH CONFIGURATION ORCHESTRATION
// ============================================================================
@Configuration
public class UnifiedBatchJobConfig {

    @Bean
    public ItemWriter<User> automatedBulkWriter(DataSource dataSource) {
        return new AutomatedBulkWriter(dataSource);
    }

    @Bean
    public Step preStep(JobRepository jobRepository, PlatformTransactionManager transactionManager, DataSource dataSource) {
        return new StepBuilder("preStep", jobRepository)
                .tasklet(new PreStepProcedureTasklet(dataSource), transactionManager)
                .build();
    }

    @Bean
    public Step copyDataStep(JobRepository jobRepository, 
                             PlatformTransactionManager transactionManager,
                             ItemReader<User> userReader, 
                             ItemWriter<User> automatedBulkWriter) {
        return new StepBuilder("copyDataStep", jobRepository)
                .<User, User>chunk(1000, transactionManager) // Controls structural commit sizes
                .reader(userReader)
                .writer(automatedBulkWriter)
                .build();
    }

    @Bean
    public Step postStep(JobRepository jobRepository, PlatformTransactionManager transactionManager, DataSource dataSource) {
        return new StepBuilder("postStep", jobRepository)
                // Transaction manager guarantees all 3 stored procedures execute atomically
                .tasklet(new PostStepProcedureTasklet(dataSource), transactionManager)
                .build();
    }

    @Bean
    public Job dataMigrationJob(JobRepository jobRepository, Step preStep, Step copyDataStep, Step postStep) {
        return new JobBuilder("dataMigrationJob", jobRepository)
                .start(preStep)           // 1. Run initialization procedure
                .next(copyDataStep)       // 2. Stream chunked data via native driver bulk copying
                .next(postStep)           // 3. Complete structural tasks with 3 transactional procedures
                .build();
    }
}
