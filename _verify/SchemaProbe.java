import com.usamis.util.DatabaseConnection;
import java.sql.*;

public class SchemaProbe {
    public static void main(String[] a) throws Exception {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            for (String t : new String[]{"grades", "fee_records", "enrollments", "students", "courses"}) {
                System.out.println("=== " + t + " ===");
                try (ResultSet rs = st.executeQuery(
                        "SELECT column_name, data_type, is_nullable, column_default " +
                        "FROM information_schema.columns WHERE table_name='" + t + "' ORDER BY ordinal_position")) {
                    while (rs.next()) {
                        System.out.printf("  %-18s %-12s null=%s def=%s%n",
                            rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4));
                    }
                }
            }
        }
    }
}
