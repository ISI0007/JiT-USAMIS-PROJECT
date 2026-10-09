import com.usamis.util.DatabaseConnection;
import java.nio.file.*;
import java.sql.*;

/** One-off seed runner: executes _verify/seed_real.sql via the app's JDBC config. */
public class SeedRunner {
    public static void main(String[] args) throws Exception {
        String sql = new String(Files.readAllBytes(Paths.get(args[0])), "UTF-8");
        try (Connection c = DatabaseConnection.getConnection();
             Statement st = c.createStatement()) {
            // Split on ';' at end of line (our seed has no procedural blocks).
            StringBuilder buf = new StringBuilder();
            int executed = 0, failed = 0;
            for (String line : sql.split("\n")) {
                String t = line.trim();
                if (t.startsWith("--") || t.isEmpty()) continue;
                buf.append(line).append('\n');
                if (t.endsWith(";")) {
                    String stmt = buf.toString().trim();
                    buf.setLength(0);
                    String head = stmt.replaceAll("\\s+", " ").substring(0, Math.min(70, stmt.length()));
                    try {
                        boolean isRs = st.execute(stmt);
                        if (isRs) {
                            try (ResultSet rs = st.getResultSet()) {
                                ResultSetMetaData m = rs.getMetaData();
                                while (rs.next()) {
                                    StringBuilder row = new StringBuilder();
                                    for (int i = 1; i <= m.getColumnCount(); i++) {
                                        row.append(m.getColumnLabel(i)).append('=').append(rs.getString(i)).append("  ");
                                    }
                                    System.out.println("    " + row);
                                }
                            }
                        }
                        executed++;
                        System.out.println("OK  " + head);
                    } catch (SQLException e) {
                        failed++;
                        System.out.println("ERR " + head + "  -> " + e.getMessage());
                    }
                }
            }
            System.out.println("\nDone: " + executed + " statements OK, " + failed + " failed.");
        }
    }
}
