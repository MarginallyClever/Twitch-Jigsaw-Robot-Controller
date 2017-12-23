package com.TwitchJigsawRobotController;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;

import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_videoio.VideoCapture;
import org.junit.Test;
import org.opencv.core.Core;

/**
 * Frame grabbing from examples at http://jcodec.org/
 * @author Dan
 *
 */
public class MapMaker {

	public static BufferedImage mat2Img(Mat matrix) {
	    int cols = matrix.cols();
	    int rows = matrix.rows();
	    //int elemSize = (int)matrix.elemSize();
	    //byte[] data = new byte[cols * rows * elemSize];
	    int type;

	    ByteBuffer buf = matrix.asByteBuffer();
	    byte[] data = buf.array();
	    
	    switch (matrix.channels()) {
	        case 1:
	            type = BufferedImage.TYPE_BYTE_GRAY;
	            break;

	        case 3: 
	            type = BufferedImage.TYPE_3BYTE_BGR;

	            // bgr to rgb
	            byte b;
	            for(int i=0; i<data.length; i=i+3) {
	                b = data[i];
	                data[i] = data[i+2];
	                data[i+2] = b;
	            }
	            break;

	        default:
	            return null;
	    }

	    BufferedImage image = new BufferedImage(cols, rows, type);
	    image.getRaster().setDataElements(0, 0, cols, rows, data);

	    return image;
    } 
	
	public void takePicture(String outputFilename,String outputFileFormat) {
		String STREAM_ADDRESS = "http://192.168.0.100:12345/?action=stream";
		
		System.out.println("Loading "+Core.NATIVE_LIBRARY_NAME);
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		
		try {
		    VideoCapture capture = new VideoCapture();
	    	System.out.println("Opening "+STREAM_ADDRESS+"...");
		    if(capture.open(STREAM_ADDRESS)) {
		    	System.out.println("Reading frame...");
		    	Mat mat=new Mat();
		    	capture.read(mat);
		    	System.out.println("Converting...");
		    	BufferedImage bufferedImage = mat2Img(mat);
		    	if(bufferedImage!=null) {
			    	File outputFile = new File(outputFilename+"."+outputFileFormat);
			    	System.out.println("Saved to "+outputFile.getAbsoluteFile());
		    		ImageIO.write(bufferedImage, outputFileFormat, outputFile);
		    	}
		    }
	    	System.out.println("Closing...");
		    capture.release();

		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void grabOneFrame() {
		takePicture("test","png");
	}
}
