package org.dkf.jed2k;

import lombok.extern.slf4j.Slf4j;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Created by apavlov on 03.03.17.
 */
@Slf4j
public class PGConn {

    public static void main(String[] args) {
        log.info("PG connect started");
        try {
            String url = "jdbc:postgresql://localhost/test";
            Properties props = new Properties();
            props.setProperty("user", "test");
            props.setProperty("password", "test");
            props.setProperty("ssl", "true");
            java.sql.Connection conn = DriverManager.getConnection(url, props);
            if (conn.isClosed()) {
                log.info("connection closed");
            }

            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery("SELECT count(*) FROM test.sources");
            if (rs.next()) {
                log.info("records count {}", rs.getInt(1));
            } else {
                log.warn("no records");
            }

            rs.close();

            conn.close();
        } catch(SQLException e) {
            log.error("sql exception {}", e);
        }
    }
}
