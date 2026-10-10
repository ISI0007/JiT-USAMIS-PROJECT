import java.sql.*;
import java.util.*;

/**
 * Non-destructive seeder: grows USAMIS to a realistic scale for demo/report.
 * - Adds students STU2024009..STU2024060 (52 new; existing 001-008 untouched)
 * - Enrolls each in 3-5 courses across semesters
 * - Adds grades (varied, incl. some at-risk)
 * - Adds fee records (Paid/Partial/Unpaid)
 * Idempotent-ish: skips a student_id that already exists.
 */
public class SeedScale {
    static final Random R = new Random(20261010L);
    static String[] FN={"Wei","Mei","Jun","Yaseen","Li","Hao","Xin","Kai","Yan","Fang","Jing","Lei","Na","Qiang","Rui","Tao","Xue","Yang","Zhen","Bo","Chao","Dan","Feng","Gang","Hui","Juan","Kun","Ming","Ning","Peng","Qing","Shan","Ting","Wen","Xiao","Yong","Zhe","Bing","Cai","Dong","Fei","Guo","Hong","Lin","Min","Nan","Ping","Shu","Tian","Xia"};
    static String[] LN={"Zhang","Liu","Chen","Al-Rashid","Wang","Zhao","Sun","Zhou","Wu","Xu","Yang","Li","Guo","He","Gao","Lin","Luo","Song","Tang","Deng","Feng","Han","Jiang","Kong","Lu","Ma","Nie","Pei","Qin","Ren"};

    public static void main(String[] a) throws Exception {
        Class.forName("org.postgresql.Driver");
        try(Connection c=DriverManager.getConnection("jdbc:postgresql://localhost:5432/usamis","postgres","postgres")){
            c.setAutoCommit(false);
            int deptMax = q1(c,"SELECT max(id) FROM departments");
            int progFirst = q1(c,"SELECT min(id) FROM programs");
            List<Integer> courseIds = qList(c,"SELECT id FROM courses WHERE status='Active' ORDER BY id");
            List<Integer> credits = qList(c,"SELECT credits FROM courses WHERE status='Active' ORDER BY id");
            if(courseIds.isEmpty()){ System.out.println("no courses; abort"); return; }

            int added=0, enr=0, gr=0, fee=0;
            for(int n=9;n<=60;n++){
                String sid = String.format("STU2024%03d", n);
                if(exists(c,"SELECT 1 FROM students WHERE student_id=?",sid)) continue;
                int dept = 1 + R.nextInt(deptMax);
                int prog = progFirst + R.nextInt(10);
                int yr = 1 + R.nextInt(4);
                String fn=FN[R.nextInt(FN.length)], ln=LN[R.nextInt(LN.length)];
                String email = sid.toLowerCase()+"@stu.jit.edu.cn";
                String phone = "138"+String.format("%08d", R.nextInt(100000000));
                int stuId;
                try(PreparedStatement ps=c.prepareStatement(
                    "INSERT INTO students(student_id,first_name,last_name,date_of_birth,gender,email,phone,department_id,program_id,year_of_study,status,nationality) VALUES(?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id")){
                    ps.setString(1,sid); ps.setString(2,fn); ps.setString(3,ln);
                    ps.setDate(4, java.sql.Date.valueOf(2000+R.nextInt(6)+"-0"+(1+R.nextInt(9))+"-1"+(R.nextInt(9))));
                    ps.setString(5, R.nextBoolean()?"Male":"Female");
                    ps.setString(6,email); ps.setString(7,phone);
                    ps.setInt(8,dept); ps.setInt(9,prog); ps.setInt(10,yr);
                    ps.setString(11,"Active"); ps.setString(12,"Chinese");
                    try(ResultSet rs=ps.executeQuery()){ rs.next(); stuId=rs.getInt(1); }
                }
                added++;
                // pick 3-5 distinct courses
                Set<Integer> idx = new TreeSet<>();
                int k = 3 + R.nextInt(3);
                while(idx.size()<k) idx.add(R.nextInt(courseIds.size()));
                for(int ci : idx){
                    int cid = courseIds.get(ci);
                    String sem = "Semester "+(1+R.nextInt(2))+" 2024/25";
                    if(exists(c,"SELECT 1 FROM enrollments WHERE student_id=? AND course_id=? AND semester=?",stuId,cid,sem)) continue;
                    int eid;
                    try(PreparedStatement ps=c.prepareStatement("INSERT INTO enrollments(student_id,course_id,semester,status) VALUES(?,?,?,?) RETURNING id")){
                        ps.setInt(1,stuId); ps.setInt(2,cid); ps.setString(3,sem); ps.setString(4,"Active");
                        try(ResultSet rs=ps.executeQuery()){ rs.next(); eid=rs.getInt(1); }
                    }
                    enr++;
                    double score = mkScore();
                    String letter = letter(score); double pts = points(score);
                    try(PreparedStatement ps=c.prepareStatement("INSERT INTO grades(enrollment_id,score,letter_grade,gpa_points,entered_by) VALUES(?,?,?,?,?)")){
                        ps.setInt(1,eid); ps.setDouble(2,score); ps.setString(3,letter); ps.setDouble(4,pts); ps.setInt(5,3);
                        ps.executeUpdate();
                    }
                    gr++;
                }
                // 1-2 fee records
                for(int f=0; f<1+R.nextInt(2); f++){
                    String[] types={"Tuition","Accommodation","Laboratory","Library","Registration"};
                    String ft=types[R.nextInt(types.length)];
                    double total = 2000 + R.nextInt(12000);
                    double paid; String status;
                    int roll=R.nextInt(100);
                    if(roll<55){ paid=total; status="Paid"; }
                    else if(roll<80){ paid=Math.round(total*0.5); status="Partial"; }
                    else { paid=0; status="Unpaid"; }
                    String recpt="RCP2024"+String.format("%05d", 10000+added*3+f);
                    if(exists(c,"SELECT 1 FROM fee_records WHERE receipt_no=?",recpt)) continue;
                    try(PreparedStatement ps=c.prepareStatement(
                        "INSERT INTO fee_records(receipt_no,student_id,fee_type,semester,total_amount,paid_amount,payment_method,payment_date,status,created_by) VALUES(?,?,?,?,?,?,?,?,?,?)")){
                        ps.setString(1,recpt); ps.setInt(2,stuId); ps.setString(3,ft);
                        ps.setString(4,"Semester "+(1+R.nextInt(2))+" 2024/25");
                        ps.setDouble(5,total); ps.setDouble(6,paid);
                        if("Paid".equals(status)){ ps.setString(7,"WeChat Pay"); ps.setDate(8,java.sql.Date.valueOf("2024-9-15")); }
                        else if("Partial".equals(status)){ ps.setString(7,"Alipay"); ps.setDate(8,java.sql.Date.valueOf("2024-9-20")); }
                        else { ps.setString(7,null); ps.setDate(8,null); }
                        ps.setString(9,status); ps.setInt(10,5);
                        ps.executeUpdate();
                    }
                    fee++;
                }
            }
            c.commit();
            System.out.println("students_added="+added+" enrollments="+enr+" grades="+gr+" fees="+fee);
            try(Statement s=c.createStatement()){
                for(String tb: new String[]{"students","enrollments","grades","fee_records"}){
                    ResultSet r=s.executeQuery("SELECT count(*) FROM "+tb); r.next();
                    System.out.println(tb+"="+r.getInt(1));
                }
            }
        }
    }
    static double mkScore(){ int r=R.nextInt(100); if(r<12) return 30+R.nextInt(28); if(r<30) return 58+R.nextInt(12); return 70+R.nextInt(31); }
    static String letter(double s){ if(s>=95)return "A+"; if(s>=90)return "A"; if(s>=85)return "B+"; if(s>=80)return "B"; if(s>=75)return "B-"; if(s>=70)return "C+"; if(s>=65)return "C"; if(s>=60)return "C-"; if(s>=55)return "D"; return "F"; }
    static double points(double s){ if(s>=95)return 4.0; if(s>=90)return 4.0; if(s>=85)return 3.5; if(s>=80)return 3.0; if(s>=75)return 2.7; if(s>=70)return 2.3; if(s>=65)return 2.0; if(s>=60)return 1.7; if(s>=55)return 1.0; return 0.0; }
    static int q1(Connection c,String sql) throws SQLException { try(Statement s=c.createStatement(); ResultSet r=s.executeQuery(sql)){ r.next(); return r.getInt(1);} }
    static List<Integer> qList(Connection c,String sql) throws SQLException { List<Integer> l=new ArrayList<>(); try(Statement s=c.createStatement(); ResultSet r=s.executeQuery(sql)){ while(r.next()) l.add(r.getInt(1)); } return l; }
    static boolean exists(Connection c,String sql,Object...args) throws SQLException { try(PreparedStatement ps=c.prepareStatement(sql)){ for(int i=0;i<args.length;i++) ps.setObject(i+1,args[i]); try(ResultSet r=ps.executeQuery()){ return r.next(); } } }
}
