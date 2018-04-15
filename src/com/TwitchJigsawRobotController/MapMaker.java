package com.TwitchJigsawRobotController;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;

import javax.imageio.ImageIO;

import org.junit.Test;

/**
 * http://thistleshrub.net/www/index.php?controller=posts&action=show&id=2012-05-13DisplayingStreamedMJPEGinJava.txt
 * was a good start, but it assumed things about the shape of the header format that were not true for raspi.
 * @author Dan
 *
 */
public class MapMaker {
	private static final String STREAM_ADDRESS = "http://192.168.0.100:12345/?action=stream";
	private static final String CONTENT_LENGTH = "content-length: ";
	private static final String CONTENT_TYPE = "content-type: image/jpeg";

	public void takeMJPEGFrameCapture(String outputFilename,String outputFileFormat) throws Exception {
		//System.out.println("takeMJPEGFrameCapture");
		//System.out.println("connecting");
		URL url = new URL(STREAM_ADDRESS);
		URLConnection urlConn = url.openConnection();
		urlConn.setReadTimeout(1000);
		urlConn.connect();

		//System.out.println("get stream");
		InputStream urlStream = urlConn.getInputStream();

		//System.out.println("retrieve image");
		byte [] imageBytes = retrieveNextImage(urlStream);
		//System.out.println("get byte array");
		ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
		BufferedImage bufferedImage = ImageIO.read(bais);
    	if(bufferedImage!=null) {
			//System.out.println("saving");
	    	File outputFile = new File(outputFilename+"."+outputFileFormat);
	    	//System.out.println("Saved to "+outputFile.getAbsoluteFile());
    		ImageIO.write(bufferedImage, outputFileFormat, outputFile);
    	}
		//System.out.println("closing");
    	urlStream.close();
		//System.out.println("done");
	}
	
	/**
	 * Using the urlStream get the next JPEG image as a byte[]
	 * @return byte[] of the JPEG
	 * @throws IOException
	 */
	private byte[] retrieveNextImage(InputStream urlStream) throws IOException {
		StringWriter stringWriter = new StringWriter(256);
		
		boolean haveHeader = false; 
		int currByte = -1;

		String header = null;
		// collect headers
		while((currByte = urlStream.read()) > -1 && !haveHeader) {
			stringWriter.write(currByte);
			//System.out.print((char)currByte);
			
			String tempString = stringWriter.toString().toLowerCase(); 
			if(currByte=='\n') {
				int indexOfType = tempString.indexOf(CONTENT_TYPE);
				int indexOfLength = tempString.indexOf(CONTENT_LENGTH);
				if(indexOfType > 0 && indexOfLength > 0) {
					haveHeader = true;
					header = tempString;
				}
			}
		}		
		
		// 255 indicates the start of the jpeg image
		while((urlStream.read()) != 255) {
			// just skip extras
			//System.out.print((char)i);
		}

		// rest is the buffer
		int contentLength = contentLength(header);
		byte[] imageBytes = new byte[contentLength + 1];
		// since we ate the original 255 , shove it back in
		imageBytes[0] = (byte)255;
		int offset = 1;
		int numRead = 0;
		while (offset < imageBytes.length
			&& (numRead=urlStream.read(imageBytes, offset, imageBytes.length-offset)) >= 0) {
			offset += numRead;
		}       
		
		return imageBytes;
	}

	// dirty but it works content-length parsing
	private static int contentLength(String header)
	{
		int indexOfContentLength = header.indexOf(CONTENT_LENGTH);
		int valueStartPos = indexOfContentLength + CONTENT_LENGTH.length();
		int indexOfEOL = header.indexOf('\n', indexOfContentLength);
		
		String lengthValStr = header.substring(valueStartPos, indexOfEOL).trim();
		
		int retValue = Integer.parseInt(lengthValStr);
		
		return retValue;
	}
/*
	@Test
	public void testVideoCapture() {
		takeVideoCapture("test1","png");
	}
	
	@Test
	public void testFFmegFrameGrabber() {
		takeFFmpegFrame("test2","png");
	}
*/
	@Test
	public void testMJPEGCapture() throws Exception {
		takeMJPEGFrameCapture("test3","png");
	}
}
