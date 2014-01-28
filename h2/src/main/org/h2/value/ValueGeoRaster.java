/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.h2.value;

import com.vividsolutions.jts.io.ByteOrderValues;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.h2.message.DbException;
import org.h2.store.DataHandler;

/**
 *
 * @author The_Artkitekt
 */
public class ValueGeoRaster extends ValueLob {

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
    //rt_band *bands; /* actual bands */
   
    private ValueGeoRaster (ValueLob v){
        super(v.type , v.handler, v.fileName, v.tableId, v.objectId, v.linked, v.precision, v.compressed);
        small = v.small;
        hash = v.hash;
    }
    
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
    
    private static short getShort(byte[] buff, int endian){
        if(endian==1){
            return (short) (((buff[0] & 0xff) << 8)|((buff[1] & 0xff)));
        }else{
            return (short) (((buff[1] & 0xff) << 8)|((buff[0] & 0xff)));
        }
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
