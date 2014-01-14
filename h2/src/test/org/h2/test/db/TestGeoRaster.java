
package org.h2.test.db;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Random;
import org.h2.store.DataHandler;
import org.h2.test.TestBase;
import org.h2.value.ValueGeoRaster;
import org.junit.Assert;
import org.junit.Test;

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
    
    @Test
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
        assertTrue(testRaster.getVersion()==0);
        assertTrue(testRaster.getNumBands()==0);
        assertTrue(testRaster.getScaleX()==2);
        assertTrue(testRaster.getScaleY()==3);
        assertTrue(testRaster.getIpX()==0.5);
        assertTrue(testRaster.getIpY()==0.5);
        assertTrue(testRaster.getSkewX()==0);
        assertTrue(testRaster.getSkewY()==0);
        assertTrue(testRaster.getSrid()==0);
        assertTrue(testRaster.getWidth()==10);
        assertTrue(testRaster.getHeight()==20);
    }

    @Test
    public void testGeoRasterWithBands() throws Exception {
        String bytesString = "01000003009A9999999999A93F9A9999999999A9BF000000E02B274A" +
"41000000007719564100000000000000000000000000000000FFFFFFFF050005000400FDFEFDFEFEFDFEFEFDF9FAFEF" +
"EFCF9FBFDFEFEFDFCFAFEFEFE04004E627AADD16076B4F9FE6370A9F5FE59637AB0E54F58617087040046566487A1506CA2E3FA5A6CAFFBFE4D566DA4CB3E454C5665";
        
        byte[] bytes = hexStringToByteArray(bytesString);
        
        InputStream bytesStream = new ByteArrayInputStream(bytes);
        long len = bytes.length;
        ValueGeoRaster testRaster = ValueGeoRaster.createGeoRaster(bytesStream, len, null);
        // Check only if we get the right value for numBands
        Assert.assertTrue(testRaster.getVersion()==0);
        Assert.assertTrue(testRaster.getNumBands()==3);
        Assert.assertTrue(testRaster.getWidth()==5);
        Assert.assertTrue(testRaster.getHeight()==5);
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
    
    @Test
    public void testQueryIndexBand() throws IOException{
        String bytesString = "01000003009A9999999999A93F9A9999999999A9BF000000E02B274A" +
"41000000007719564100000000000000000000000000000000FFFFFFFF050005000400FDFEFDFEFEFDFEFEFDF9FAFEF" +
"EFCF9FBFDFEFEFDFCFAFEFEFE04004E627AADD16076B4F9FE6370A9F5FE59637AB0E54F58617087040046566487A1506CA2E3FA5A6CAFFBFE4D566DA4CB3E454C5665";
        
        byte[] bytes = hexStringToByteArray(bytesString);
        
        InputStream bytesStream = new ByteArrayInputStream(bytes);
        long len = bytes.length;
        ValueGeoRaster testRaster = ValueGeoRaster.createGeoRaster(bytesStream, len, null);
        System.out.println("Result :"+testRaster.getIndexBand(1));
    }
}