/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.h2.test.db;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.h2.store.DataHandler;
import org.h2.test.TestBase;
import org.h2.value.ValueGeoRaster;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author The_Artkitekt
 */
public class TestGeoRaster extends TestBase {

    public static void main(String... a) throws Exception {
        TestBase test = TestBase.createCaller().init();
        test.config.big = true;
        test.test();
    }
    
    @Override
    public void test(){
        
    }
    
    //@Test
    public void testEmptyGeoRaster() throws Exception {
        String bytesString = "01"
                + "0000"
                + "0000"
                + "0000000000000040"
                + "0000000000000840"
                + "000000000000E03F"
                + "000000000000E03F"
                + "0000000000000000"
                + "0000000000000000"
                + "00000000"
                + "0A00"
                + "1400";
        
        byte[] bytes = hexStringToByteArray(bytesString);
        
        InputStream bytesStream = new ByteArrayInputStream(bytes);
        long len = bytes.length;
        ValueGeoRaster testRaster = ValueGeoRaster.createGeoRaster(bytesStream, len, null);
        Assert.assertTrue(testRaster.getVersion()==0);
        Assert.assertTrue(testRaster.getNumBands()==0);
        Assert.assertTrue(testRaster.getScaleX()==2);
        Assert.assertTrue(testRaster.getScaleY()==3);
        Assert.assertTrue(testRaster.getIpX()==0.5);
        Assert.assertTrue(testRaster.getIpY()==0.5);
        Assert.assertTrue(testRaster.getSkewX()==0);
        Assert.assertTrue(testRaster.getSkewY()==0);
        Assert.assertTrue(testRaster.getSrid()==0);
        Assert.assertTrue(testRaster.getWidth()==10);
        Assert.assertTrue(testRaster.getHeight()==20);
    }

    //@Test
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