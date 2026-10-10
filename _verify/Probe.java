import java.sql.*;
public class Probe {
  public static void main(String[] a) throws Exception {
    Class.forName("org.postgresql.Driver");
    try(Connection c=DriverManager.getConnection("jdbc:postgresql://localhost:5432/usamis","postgres","postgres");
        Statement s=c.createStatement()){
      ResultSet r=s.executeQuery("SELECT id,username,status,substring(password_hash,1,7) h FROM users ORDER BY id");
      while(r.next()) System.out.println(r.getInt(1)+" | "+r.getString(2)+" | "+r.getString(3)+" | "+r.getString(4));
      ResultSet r2=s.executeQuery("SELECT count(*) FROM students");
      r2.next(); System.out.println("students="+r2.getInt(1));
      ResultSet r3=s.executeQuery("SELECT count(*) FROM ai_prediction");
      r3.next(); System.out.println("ai_prediction="+r3.getInt(1));
    }
  }
}
