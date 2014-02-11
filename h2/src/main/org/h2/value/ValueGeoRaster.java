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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.h2.store.DataHandler;

import com.vividsolutions.jts.geom.Envelope;

/**
 *
 * @author Thomas Crevoisier, Jules Party
 */
public class ValueGeoRaster extends ValueLob implements ValueSpatial {

    static Value get(byte[] bytesNoCopy) {
        InputStream bytesStream = new ByteArrayInputStream(bytesNoCopy);
        long len = bytesNoCopy.length;
        return ValueGeoRaster.createGeoRaster(bytesStream, len, null);
    }


    /**
     * Create a GeoRaster from a value lob
     * 
     */
    private ValueGeoRaster (ValueLob v){
        super(v.type , v.handler, v.fileName, v.tableId, v.objectId, v.linked, v.precision, v.compressed);
        small = v.small;
        hash = v.hash;
    }
    
    /**
     * Create a GeoRaster from a given byte input stream
     * 
     * @param in the InputStream to build the GeoRaster from
     * 
     * @return the ValueGeoRaster created
     */
    public static ValueGeoRaster createGeoRaster(InputStream in, long length, DataHandler handler){
        ValueGeoRaster geoRaster = new ValueGeoRaster(ValueLob.createBlob(in, length, handler));
        return geoRaster;
    }

    /**
     * Create an envelope based on the inputstream of the georaster
     *
     * @return the envelope of the georaster
     */
    @Override
    public Envelope getEnvelope(){
        InputStream input = getInputStream();

        try {
            byte[] buffer = new byte[8];

            // Retrieve the endian value
            input.read(buffer, 0, 1);
            int endian = buffer[0]==1 ? ByteOrderValues.LITTLE_ENDIAN  : ByteOrderValues.BIG_ENDIAN;
            
            // Skip the bytes related to the version and the number of bands
            input.skip(4);
            
            // Retrieve scale values
            input.read(buffer,0,8);
            double scaleX = ByteOrderValues.getDouble(buffer, endian);
            
            input.read(buffer,0,8);
            double scaleY = ByteOrderValues.getDouble(buffer, endian);
            
            // Retrieve ip values
            input.read(buffer,0,8);
            double ipX = ByteOrderValues.getDouble(buffer, endian);
            
            input.read(buffer,0,8);
            double ipY = ByteOrderValues.getDouble(buffer, endian);
            
            // Retrieve skew values
            input.read(buffer,0,8);
            double skewX = ByteOrderValues.getDouble(buffer, endian);
            
            input.read(buffer,0,8);
            double skewY = ByteOrderValues.getDouble(buffer, endian);
            
            // Retrieve the srid value
            input.read(buffer,0,4);
            int srid = ByteOrderValues.getInt(buffer, endian);
            
            // Retrieve width and height values
            input.read(buffer,0,2);
            short width = getShort(buffer, endian);
            
            input.read(buffer,0,2);
            short height = getShort(buffer, endian);

            // Calculate the four points of the envelope and keep max and min values for x and y
            double xMax = ipX;
            double yMax = ipY;
            double xMin = ipX;
            double yMin = ipY;

            xMax = Math.max(xMax,ipX + width*scaleX);
            xMin = Math.min(xMin,ipX + width*scaleX);
            yMax = Math.max(yMax,ipY + width*scaleY);
            yMin = Math.min(yMin,ipY + width*scaleY);

            xMax = Math.max(xMax,ipX + height*skewX);
            xMin = Math.min(xMin,ipX + height*skewX);
            yMax = Math.max(yMax,ipY + height*skewY);
            yMin = Math.min(yMin,ipY + height*skewY);

            xMax = Math.max(xMax,ipX + width*scaleX + height*skewX);
            xMin = Math.min(xMin,ipX + width*scaleX + height*skewX);
            yMax = Math.max(yMax,ipY + width*scaleY + height*skewY);
            yMin = Math.min(yMin,ipY + width*scaleY + height*skewY);

            return new Envelope(xMax, xMin, yMax, yMin);

        } catch (IOException ex) {
            Logger.getLogger(ValueGeoRaster.class.getName()).log(Level.SEVERE, "H2 is unable to read the raster.", ex);
        }

        return null;
    }

    /**
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
}
