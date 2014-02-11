
package org.h2.test.db;

import com.vividsolutions.jts.geom.Envelope;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Random;
import org.h2.test.TestBase;
import org.h2.value.ValueGeoRaster;

/**
 *
 * @author Thomas Crevoisier, Jules Party
 */
public class TestGeoRaster extends TestBase {

    public static void main(String... a) throws Exception {
        TestBase test = TestBase.createCaller().init();
        test.config.big = true;
        test.test();
    }
    
    @Override
    public void test() throws Exception {
        testEmptyGeoRaster();
        testGeoRasterWithBands();
        testReadRaster();
        testWriteRasterFromString();
    }
 
   
    private void testReadRaster() throws Exception {
        deleteDb("georaster");
        Connection conn;
        conn = getConnection("georaster");
        Statement stat = conn.createStatement();
                stat.execute("create table test(id identity, data georaster)");
        
        PreparedStatement prep = conn.prepareStatement(
                "insert into test values(null, ?)");
        byte[] data = new byte[256];
        Random r = new Random(1);
        for (int i = 0; i < 1000; i++) {
            r.nextBytes(data);
            prep.setBinaryStream(1, new ByteArrayInputStream(data), -1);
            prep.execute();
        }

        ResultSet rs = stat.executeQuery("select * from test");
        while (rs.next()) {
            rs.getString(2);
        }
        conn.close();
    }
    
    private void testWriteRasterFromString() throws Exception {
        String bytesString = "01"
                + "0000"
                + "0000"
                + "0000000000000040"
                + "0000000000000840"
                + "000000000000e03F"
                + "000000000000e03F"
                + "0000000000000000"
                + "0000000000000000"
                + "00000000"
                + "0a00"
                + "1400";
        
        byte[] bytes = hexStringToByteArray(bytesString);
        
        InputStream bytesStream = new ByteArrayInputStream(bytes);
        
        deleteDb("georaster");
        Connection conn;
        conn = getConnection("georaster");
        Statement stat = conn.createStatement();
                stat.execute("create table test(id identity, data georaster)");
        
        PreparedStatement prep = conn.prepareStatement(
                "insert into test values(null, ?)");

        prep.setBinaryStream(1, bytesStream, -1);
        prep.execute();

        ResultSet rs = stat.executeQuery("select * from test");
        rs.next();
        assertTrue(bytesString.equalsIgnoreCase(rs.getString(2)));
        conn.close();
    }
    
    public void testEmptyGeoRaster() throws Exception {
        String bytesString = "01"
                + "0000"
                + "0000"
                + "0000000000000040"
                + "0000000000000840"
                + "000000000000e03F"
                + "000000000000e03F"
                + "0000000000000000"
                + "0000000000000000"
                + "00000000"
                + "0a00"
                + "1400";
        
        byte[] bytes = hexStringToByteArray(bytesString);
        
        InputStream bytesStream = new ByteArrayInputStream(bytes);
        long len = bytes.length;
        ValueGeoRaster testRaster = ValueGeoRaster.createGeoRaster(bytesStream, len, null);
        Envelope env = testRaster.getEnvelope();
        assertEquals(env.getMinX(), 0.5);
        assertEquals(env.getMinY(), 0.5);
        assertEquals(env.getMaxX(), 20.5);
        assertEquals(env.getMaxY(), 30.5);
    }

    public void testGeoRasterWithBands() throws Exception {
        String bytesString = "01"
                + "0000"
                + "0300"
                + "9A9999999999A93F"
                + "9A9999999999A9BF"
                + "000000E02B274A41"
                + "0000000077195641"
                + "0000000000000000"
                + "0000000000000000"
                + "FFFFFFFF"
                + "0500"
                + "0500"
                + "04"
                + "00"
                + "FDFEFDFEFE"
                + "FDFEFEFDF9"
                + "FAFEFEFCF9"
                + "FBFDFEFEFD"
                + "FCFAFEFEFE"
                + "04"
                + "00"
                + "4E627AADD1"
                + "6076B4F9FE"
                + "6370A9F5FE"
                + "59637AB0E5"
                + "4F58617087"
                + "04"
                + "00"
                + "46566487A1"
                + "506CA2E3FA"
                + "5A6CAFFBFE"
                + "4D566DA4CB"
                + "3E454C5665";
        
        byte[] bytes = hexStringToByteArray(bytesString);
        
        InputStream bytesStream = new ByteArrayInputStream(bytes);
        long len = bytes.length;
        ValueGeoRaster testRaster = ValueGeoRaster.createGeoRaster(bytesStream, len, null);
        Envelope env = testRaster.getEnvelope();
        assertEquals(env.getMinX(), 3427927.75);
        assertEquals(env.getMinY(), 5793243.75);
        assertEquals(env.getMaxX(), 3427928);
        assertEquals(env.getMaxY(), 5793244);
    }
    
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
}