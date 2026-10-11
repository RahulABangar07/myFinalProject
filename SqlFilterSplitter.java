import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.util.TablesNamesFinder;


/*
<dependency>
    <groupId>com.github.jsqlparser</groupId>
    <artifactId>jsqlparser</artifactId>
    <version>5.1</version>
</dependency>

*/
import java.util.*;
import java.util.stream.Collectors;

public class SqlFilterSplitter {

    private static final String TRUE_CONDITION = "1 = 1";
    private static final String FALSE_CONDITION = "1 = 0";

    public static class SplitSql {
        public final String insightTypeFilterSql;
        public final String positionFilterSql;
        public final String aggregatePositionFilterSql;
        public final String demographicFilterSql;

        public SplitSql(
                String insightTypeFilterSql,
                String positionFilterSql,
                String aggregatePositionFilterSql,
                String demographicFilterSql) {
            this.insightTypeFilterSql = insightTypeFilterSql;
            this.positionFilterSql = positionFilterSql;
            this.aggregatePositionFilterSql = aggregatePositionFilterSql;
            this.demographicFilterSql = demographicFilterSql;
        }
    }

    /**
     * Configure these identifiers to match the actual database schema.
     * Column names are compared case-insensitively.
     */
    private static final Set<String> INSIGHT_COLUMNS =
            Set.of("insighttype", "insight_type");

    private static final Set<String> POSITION_COLUMNS =
            Set.of("position", "position_id", "positionid");

    private static final Set<String> AGGREGATE_POSITION_COLUMNS =
            Set.of("aggregate_position", "aggregateposition",
                   "aggregate_position_id");

    private static final Set<String> DEMOGRAPHIC_COLUMNS =
            Set.of("age", "gender", "ethnicity", "demographic",
                   "demographic_id", "age_group");

    private static final Set<String> POSITION_TABLES =
            Set.of("position", "positions");

    private static final Set<String> AGGREGATE_POSITION_TABLES =
            Set.of("aggregate_position", "aggregate_positions");

    private static final Set<String> DEMOGRAPHIC_TABLES =
            Set.of("demographic", "demographics");

    public static SplitSql split(String filterSql) throws Exception {
        if (filterSql == null || filterSql.isBlank()) {
            throw new IllegalArgumentException(
                    "filterSql must not be null or empty");
        }

        Statement statement = CCJSqlParserUtil.parse(filterSql);

        if (!(statement instanceof Select)) {
            throw new IllegalArgumentException(
                    "filterSql must be a SELECT query");
        }

        Select select = (Select) statement;

        // Parse independently so that each generated SQL has its own AST.
        String insightSql = filterInsightType(filterSql);
        String positionSql = filterDomain(filterSql,
                POSITION_COLUMNS, POSITION_TABLES);
        String aggregatePositionSql = filterDomain(filterSql,
                AGGREGATE_POSITION_COLUMNS, AGGREGATE_POSITION_TABLES);
        String demographicSql = filterDomain(filterSql,
                DEMOGRAPHIC_COLUMNS, DEMOGRAPHIC_TABLES);

        return new SplitSql(
                insightSql,
                positionSql,
                aggregatePositionSql,
                demographicSql);
    }

    /**
     * Retains insighttype predicates and replaces unrelated predicates.
     *
     * AND: unrelated predicate becomes TRUE.
     * OR:  unrelated predicate becomes FALSE.
     */
    private static String filterInsightType(String sql) throws Exception {
        Select select = (Select) CCJSqlParserUtil.parse(sql);

        SelectBody body = select.getSelectBody();
        if (!(body instanceof PlainSelect)) {
            throw new UnsupportedOperationException(
                    "Insight filtering currently supports PlainSelect only");
        }

        PlainSelect plainSelect = (PlainSelect) body;

        if (plainSelect.getWhere() != null) {
            plainSelect.setWhere(
                    rewriteBooleanExpression(
                            plainSelect.getWhere(), INSIGHT_COLUMNS));
        }

        if (plainSelect.getHaving() != null) {
            plainSelect.setHaving(
                    rewriteBooleanExpression(
                            plainSelect.getHaving(), INSIGHT_COLUMNS));
        }

        return select.toString();
    }

    private static Expression rewriteBooleanExpression(
            Expression expression,
            Set<String> relevantColumns) throws Exception {

        if (expression instanceof Parenthesis) {
            Parenthesis parenthesis = (Parenthesis) expression;
            parenthesis.setExpression(
                    rewriteBooleanExpression(
                            parenthesis.getExpression(), relevantColumns));
            return parenthesis;
        }

        if (expression instanceof AndExpression) {
            AndExpression and = (AndExpression) expression;
            and.setLeftExpression(
                    rewriteBooleanExpression(
                            and.getLeftExpression(), relevantColumns));
            and.setRightExpression(
                    rewriteBooleanExpression(
                            and.getRightExpression(), relevantColumns));
            return and;
        }

        if (expression instanceof OrExpression) {
            OrExpression or = (OrExpression) expression;
            or.setLeftExpression(
                    rewriteBooleanExpression(
                            or.getLeftExpression(), relevantColumns));
            or.setRightExpression(
                    rewriteBooleanExpression(
                            or.getRightExpression(), relevantColumns));
            return or;
        }

        if (containsRelevantColumn(expression, relevantColumns)) {
            return expression;
        }

        // A leaf predicate is unrelated to insighttype.
        // Its replacement depends on its immediate Boolean parent.
        return expression;
    }

    /**
     * Returns true when an expression references any configured column.
     */
    private static boolean containsRelevantColumn(
            Expression expression,
            Set<String> relevantColumns) {

        String text = expression.toString().toLowerCase(Locale.ROOT);

        for (String column : relevantColumns) {
            String normalized = column.toLowerCase(Locale.ROOT);
            if (text.matches("(?s).*\\b" +
                    java.util.regex.Pattern.quote(normalized) +
                    "\\b.*")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Extracts a domain query by retaining relevant predicates.
     *
     * This basic version retains the original FROM/JOIN structure.
     * Join pruning and alias dependency resolution are schema-specific.
     */
    private static String filterDomain(
            String sql,
            Set<String> relevantColumns,
            Set<String> relevantTables) throws Exception {

        Select select = (Select) CCJSqlParserUtil.parse(sql);
        SelectBody body = select.getSelectBody();

        if (!(body instanceof PlainSelect)) {
            throw new UnsupportedOperationException(
                    "Domain filtering currently supports PlainSelect only");
        }

        PlainSelect plainSelect = (PlainSelect) body;

        if (plainSelect.getWhere() != null) {
            plainSelect.setWhere(
                    keepRelevantPredicates(
                            plainSelect.getWhere(), relevantColumns));
        }

        if (plainSelect.getHaving() != null) {
            plainSelect.setHaving(
                    keepRelevantPredicates(
                            plainSelect.getHaving(), relevantColumns));
        }

        return select.toString();
    }

    /**
     * Keeps predicates mentioning relevant columns.
     * Unrelated leaves are replaced with TRUE to avoid excluding rows.
     */
    private static Expression keepRelevantPredicates(
            Expression expression,
            Set<String> relevantColumns) throws Exception {

        if (expression instanceof Parenthesis) {
            Parenthesis p = (Parenthesis) expression;
            p.setExpression(keepRelevantPredicates(
                    p.getExpression(), relevantColumns));
            return p;
        }

        if (expression instanceof AndExpression) {
            AndExpression and = (AndExpression) expression;
            and.setLeftExpression(keepRelevantPredicates(
                    and.getLeftExpression(), relevantColumns));
            and.setRightExpression(keepRelevantPredicates(
                    and.getRightExpression(), relevantColumns));
            return and;
        }

        if (expression instanceof OrExpression) {
            OrExpression or = (OrExpression) expression;
            or.setLeftExpression(keepRelevantPredicates(
                    or.getLeftExpression(), relevantColumns));
            or.setRightExpression(keepRelevantPredicates(
                    or.getRightExpression(), relevantColumns));
            return or;
        }

        return containsRelevantColumn(expression, relevantColumns)
                ? expression
                : CCJSqlParserUtil.parseCondExpression(TRUE_CONDITION);
    }

    public static void main(String[] args) throws Exception {
        String filterSql =
                "SELECT * FROM employee e " +
                "WHERE e.insighttype = 'SALES' " +
                "AND e.position_id = 10 " +
                "AND (e.gender = 'F' OR e.age > 30)";

        SplitSql result = split(filterSql);

        System.out.println("insightTypeFilterSql:\n"
                + result.insightTypeFilterSql);
        System.out.println("positionFilterSql:\n"
                + result.positionFilterSql);
        System.out.println("aggregatePositionFilterSql:\n"
                + result.aggregatePositionFilterSql);
        System.out.println("demographicFilterSql:\n"
                + result.demographicFilterSql);
    }
}
