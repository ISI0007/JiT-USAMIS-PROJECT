import com.usamis.util.DatabaseConnection;
import java.sql.*;

public class DbProbe {
    public static void main(String[] a) throws Exception {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            for (String q : new String[]{
                "SELECT * FROM instructors ORDER BY id",
                "SELECT id, username, first_name, last_name, role_id FROM users WHERE role_id=3 ORDER BY id",
                "SELECT id, name FROM departments ORDER BY id",
                "SELECT id, name, department_id FROM programs ORDER BY id",
            }) {
                System.out.println("=== " + q);
                try (ResultSet rs = st.executeQuery(q)) {
                    ResultSetMetaData m = rs.getMetaData();
                    while (rs.next()) {
                        StringBuilder sb = new StringBuilder("  ");
                        for (int i = 1; i <= m.getColumnCount(); i++)
                            sb.append(m.getColumnLabel(i)).append('=').append(rs.getString(i)).append("  ");
                        System.out.println(sb);
                    }
                } catch (SQLException e) { System.out.println("  ERR " + e.getMessage()); }
            }
        }
    }
}
