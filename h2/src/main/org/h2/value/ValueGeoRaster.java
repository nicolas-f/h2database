/*
 * Copyright 2004-2013 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value;

import com.vividsolutions.jts.io.ByteOrderValues;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.h2.message.DbException;
import org.h2.store.DataHandler;

/**
 *
 * @author Thomas Crevoisier, Jules Party
 */
public class ValueGeoRaster extends ValueLob {

    static Value get(byte[] bytesNoCopy) {
        InputStream bytesStream = new ByteArrayInputStream(bytesNoCopy);
        long len = bytesNoCopy.length;
        return ValueGeoRaster.createGeoRaster(bytesStream, len, null);
    }

    protected short version;

    /* Number of bands, all share the same dimension
     * and georeference */
    protected short numBands;

    /* Georeference (in projection units) */
    protected double scaleX; /* pixel width */
    protected double scaleY; /* pixel height */
    protected double ipX; /* geo x ordinate of the corner of upper-left pixel */
    protected double ipY; /* geo y ordinate of the corner of bottom-right pixel */
    protected double skewX; /* skew about the X axis*/
    protected double skewY; /* skew about the Y axis */

    protected int srid; /* spatial reference id */
    protected short width; /* pixel columns - max 65535 */
    protected short height; /* pixel rows - max 65535 */

    /*
     * Create a GeoRaster from a value lob
     * 
     */
    private ValueGeoRaster (ValueLob v){
        super(v.type , v.handler, v.fileName, v.tableId, v.objectId, v.linked, v.precision, v.compressed);
        small = v.small;
        hash = v.hash;
    }
    
    /*
     * Create a GeoRaster from a given byte input stream
     * 
     * @param in the InputStream to build the GeoRaster from
     * 
     * @return the ValueGeoRaster created
     */
    public static ValueGeoRaster createGeoRaster(InputStream in, long length, DataHandler handler){
        try{
            ValueGeoRaster geoRaster = new ValueGeoRaster(ValueLob.createBlob(in, length, handler));
            byte[] firstBytesBlob = geoRaster.getBytes();
            int cursor = 1;
            
            int sizeShort = 2;
            int sizeInt = 4;
            int sizeDouble = 8;

            byte[] buffer = new byte[8];

            in.read(buffer, 0, 1);
            int endian = buffer[0]==0 ? ByteOrderValues.LITTLE_ENDIAN  : ByteOrderValues.BIG_ENDIAN;
            buffer = Arrays.copyOfRange(firstBytesBlob, cursor, cursor+sizeShort);
            cursor += sizeShort;
            geoRaster.version = getShort(buffer, endian);
            
            buffer = Arrays.copyOfRange(firstBytesBlob, cursor, cursor+sizeShort);
            cursor += sizeShort;
            geoRaster.numBands = getShort(buffer, endian);
            
            buffer = Arrays.copyOfRange(firstBytesBlob, cursor, cursor+sizeDouble);
            cursor += sizeDouble;
            geoRaster.scaleX = ByteOrderValues.getDouble(buffer, endian);
            
            buffer = Arrays.copyOfRange(firstBytesBlob, cursor, cursor+sizeDouble);
            cursor += sizeDouble;
            geoRaster.scaleY = ByteOrderValues.getDouble(buffer, endian);
            
            buffer = Arrays.copyOfRange(firstBytesBlob, cursor, cursor+sizeDouble);
            cursor += sizeDouble;
            geoRaster.ipX = ByteOrderValues.getDouble(buffer, endian);
            
            buffer = Arrays.copyOfRange(firstBytesBlob, cursor, cursor+sizeDouble);
            cursor += sizeDouble;
            geoRaster.ipY = ByteOrderValues.getDouble(buffer, endian);
            
            buffer = Arrays.copyOfRange(firstBytesBlob, cursor, cursor+sizeDouble);
            cursor += sizeDouble;
            geoRaster.skewX = ByteOrderValues.getDouble(buffer, endian);
            
            buffer = Arrays.copyOfRange(firstBytesBlob, cursor, cursor+sizeDouble);
            cursor += sizeDouble;
            geoRaster.skewY = ByteOrderValues.getDouble(buffer, endian);
            
            buffer = Arrays.copyOfRange(firstBytesBlob, cursor, cursor+sizeInt);
            cursor += sizeInt;
            geoRaster.srid = ByteOrderValues.getInt(buffer, endian);
            
            buffer = Arrays.copyOfRange(firstBytesBlob, cursor, cursor+sizeShort);
            cursor += sizeShort;
            geoRaster.width = getShort(buffer, endian);
            
            buffer = Arrays.copyOfRange(firstBytesBlob, cursor, cursor+sizeShort);
            cursor += sizeShort;
            geoRaster.height = getShort(buffer, endian);
            
            return geoRaster;
        } catch (IOException ex) {
            throw DbException.convertIOException(ex, null);
        }
    }
    
    /*
     * Convert an given array of bytes into a short int by precising the value of endian
     * 
     * @param buff the array of bytes to convert
     * @param endian 2 for little endian and 1 for big endian
     * 
     * @return short the result of the conversion
     */
    private static short getShort(byte[] buff, int endian){
        if(endian==1){
            return (short) (((buff[0] & 0xff) << 8)|((buff[1] & 0xff)));
        }else{
            return (short) (((buff[1] & 0xff) << 8)|((buff[0] & 0xff)));
        }
    }
    
    /*
     * Return the index of the first byte of the wanted band given its index
     * 
     * @param numBandQuery the number of the band wanted
     * 
     * @return getIndexBand the index of the wanted band
     */
    public int getIndexBand(int numBandQuery){
        if(numBandQuery<0){
            numBandQuery=0;
        }else if(numBandQuery>(numBands-1)){
            numBandQuery=numBands-1;
        }
        
        
        int indexByte = 61;
        int currBandsRead = 0;
        byte[] buffer = new byte[1];
        InputStream input = getInputStream();
        
        int sizeUnit = 0;
        while(currBandsRead<numBandQuery){
            try{
                input.read(buffer, 0, 1);
                System.out.println(buffer[0]);
                switch(buffer[0]){
                    case 0:
                        // sizeUnit=1 in bit
                    case 1:
                        // sizeUnit=2 in bit
                    case 2:
                        // sizeUnit=4 in bit
                    case 3:
                        sizeUnit=1;
                        break;
                    case 4:
                        sizeUnit=1;
                        break;
                    case 5:
                        sizeUnit=2;
                        break;
                    case 6:
                        sizeUnit=2;
                        break;
                    case 7:
                        sizeUnit=4;
                        break;
                    case 8:
                        sizeUnit=4;
                        break;
                    case 10:
                        sizeUnit=4;
                        break;
                    case 11:
                        sizeUnit=8;
                        break;
                    case 13:
                        // Needs to throw an exception
                }
                System.out.println(sizeUnit);
                input.skip((width*height+1)*sizeUnit);
                indexByte += (width*height+1)*sizeUnit+1;
                currBandsRead++;
            }catch(IOException ex){
                throw DbException.convertIOException(ex, null);
            }
        }
        return indexByte;
    }
    
    public short getVersion() {
        return version;
    }

    public short getNumBands() {
        return numBands;
    }

    public double getScaleX() {
        return scaleX;
    }

    public double getScaleY() {
        return scaleY;
    }

    public double getIpX() {
        return ipX;
    }

    public double getIpY() {
        return ipY;
    }

    public double getSkewX() {
        return skewX;
    }

    public double getSkewY() {
        return skewY;
    }

    public int getSrid() {
        return srid;
    }

    public short getWidth() {
        return width;
    }

    public short getHeight() {
        return height;
    }
}
