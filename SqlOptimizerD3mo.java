// Import statements
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;

import java.util.*;


/**
 * Entry point class.
 * Demonstrates how to use the SQL optimizer engine.
 */
public class SqlOptimizerDemo {

    public static void main(String[] args) {

        SqlParser parser = new JSqlParserAdapter();

        RuleEngine engine = new RuleEngine();

        // Register optimization rules
        engine.register(new SelectStarRule());
        engine.register(new RemoveTrueConditionRule());
        engine.register(new LimitRule());

        SqlOptimizerEngine optimizer =
                new SqlOptimizerEngine(parser, engine);

        String query = "SELECT * FROM Orders WHERE 1=1";

        String optimized = optimizer.optimize(query);

        System.out.println("Original SQL : " + query);
        System.out.println("Optimized SQL: " + optimized);
    }
}


/**
 * Interface for SQL parsing and serialization.
 */
interface SqlParser {
    Object parse(String sql) throws Exception;
    String toSql(Object ast);
}


/**
 * Adapter implementation using JSQLParser.
 */
class JSqlParserAdapter implements SqlParser {

    @Override
    public Object parse(String sql) throws Exception {
        return CCJSqlParserUtil.parse(sql);
    }

    @Override
    public String toSql(Object ast) {
        return ast.toString();
    }
}


/**
 * Context object to store metadata and logs during optimization.
 */
class OptimizationContext {

    private final Map<String, Object> metadata = new HashMap<>();

    public void put(String key, Object value) {
        metadata.put(key, value);
    }

    public Object get(String key) {
        return metadata.get(key);
    }

    public void log(String message) {
        System.out.println("[OPTIMIZER] " + message);
    }
}


/**
 * Interface for defining optimization rules.
 */
interface OptimizationRule {

    String name();

    int priority();

    boolean matches(Object ast);

    Object apply(Object ast, OptimizationContext context);
}


/**
 * Engine that applies all registered optimization rules in order.
 */
class RuleEngine {

    private final List<OptimizationRule> rules = new ArrayList<>();

    public void register(OptimizationRule rule) {
        rules.add(rule);
        rules.sort(Comparator.comparingInt(OptimizationRule::priority));
    }

    public Object optimize(Object ast, OptimizationContext ctx) {
        for (OptimizationRule rule : rules) {
            if (rule.matches(ast)) {
                ast = rule.apply(ast, ctx);
                ctx.log("Applied rule: " + rule.name());
            }
        }
        return ast;
    }
}


/**
 * Main optimizer engine that orchestrates parsing, rule execution, and SQL generation.
 */
class SqlOptimizerEngine {

    private final SqlParser parser;
    private final RuleEngine engine;

    public SqlOptimizerEngine(SqlParser parser, RuleEngine engine) {
        this.parser = parser;
        this.engine = engine;
    }

    public String optimize(String sql) {
        try {
            Object ast = parser.parse(sql);

            OptimizationContext ctx = new OptimizationContext();

            ast = engine.optimize(ast, ctx);

            return parser.toSql(ast);

        } catch (Exception e) {
            e.printStackTrace();
            return sql; // fallback
        }
    }
}


/**
 * Rule to detect and warn about usage of SELECT *.
 * Best practice is to explicitly specify columns.
 */
class SelectStarRule implements OptimizationRule {

    public String name() { return "SelectStarRule"; }

    public int priority() { return 10; }

    public boolean matches(Object ast) {
        return ast.toString().toLowerCase().contains("select *");
    }

    public Object apply(Object ast, OptimizationContext ctx) {
        ctx.log("Warning: SELECT * detected. Consider specifying columns.");
        return ast;
    }
}


/**
 * Rule to remove redundant WHERE conditions like 'WHERE 1=1'.
 */
class RemoveTrueConditionRule implements OptimizationRule {

    public String name() { return "RemoveTrueConditionRule"; }

    public int priority() { return 20; }

    public boolean matches(Object ast) {
        return ast.toString().contains("1=1");
    }

    public Object apply(Object ast, OptimizationContext ctx) {
        String sql = ast.toString().replaceAll("WHERE 1=1", "");

        try {
            return CCJSqlParserUtil.parse(sql);
        } catch (Exception e) {
            return ast;
        }
    }
}


/**
 * Rule to limit result size using SQL Server TOP clause.
 * Helps prevent large unintended result sets.
 */
class LimitRule implements OptimizationRule {

    public String name() { return "LimitRule"; }

    public int priority() { return 30; }

    public boolean matches(Object ast) {
        return ast.toString().toLowerCase().startsWith("select");
    }

    public Object apply(Object ast, OptimizationContext ctx) {

        String sql = ast.toString();

        if (!sql.toLowerCase().contains("top")) {
            sql = sql.replaceFirst("(?i)select", "SELECT TOP 1000");
            ctx.log("Added TOP 1000 to limit results.");
        }

        try {
            return CCJSqlParserUtil.parse(sql);
        } catch (Exception e) {
            return ast;
        }
    }
}
