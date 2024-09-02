package rest.uam;

import java.sql.Connection;
import java.sql.DriverManager;

public class Mydb {
	
	public static Connection connect() throws Exception {
		String driver="com.mysql.cj.jdbc.Driver",url="jdbc:mysql://localhost:3306/uamDb",userName="root",password="root224";
	Class.forName(driver);
	Connection c=DriverManager.getConnection(url,userName,password);
	return c;
	
	
	}
	
	
	

}
