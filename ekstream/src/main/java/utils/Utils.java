package utils;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import javax.imageio.ImageIO;

import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_imgcodecs;
import org.bytedeco.javacpp.opencv_imgproc;
import org.bytedeco.javacpp.helper.opencv_core.AbstractIplImage;
import org.bytedeco.javacpp.opencv_core.CvRect;
import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;

/**
 * Utility class.
 */
public final class Utils {

    /** Converter for Frames and IplImages. */
    private static OpenCVFrameConverter.ToIplImage converter;

    /** Converter for byte arrays and images. */
    private static Java2DFrameConverter flatConverter;


    /** Singleton instance of the Utils class. */
    private static Utils instance;

    /**
     * Constructor.
     */
    private Utils() {
        converter = new OpenCVFrameConverter.ToIplImage();
        flatConverter = new Java2DFrameConverter();
    }

    /**
     * Return the singleton instance of Utils.
     *
     * @return singleton instance
     */
    public static synchronized Utils getInstance() {
        if (instance == null) {
            instance = new Utils();
        }
        return instance;
    }

    /**
     * Crops an image to a given rectangle.
     *
     * @param aImage t
     * @param aX t
     * @param aY t
     * @param aW t
     * @param aH t
     * @return t
     */
    public IplImage cropImage(final IplImage aImage,
            final int aX, final int aY, final int aW, final int aH) {

        // IplImage orig = cvLoadImage("orig.png");
        // Creating rectangle by which bounds image will be cropped
        CvRect r = new CvRect(aX, aY, aW, aH);
        // After setting ROI (Region-Of-Interest) all processing will only be
        // done on the ROI
        opencv_core.cvSetImageROI(aImage, r);
        IplImage cropped = opencv_core.cvCreateImage(opencv_core.cvGetSize(aImage),
                aImage.depth(), aImage.nChannels());
        // Copy original image (only ROI) to the cropped image
        opencv_core.cvCopy(aImage, cropped);

        return cropped;

    }

    /**
     * Crops an image to a given rectangle.
     *
     * @param aImage original image
     * @param aRectangle rectangle
     * @return cropped image
     */
    public IplImage cropImage(final IplImage aImage, final CvRect aRectangle) {

        // After setting ROI (Region-Of-Interest) all processing will only be
        // done on the ROI
        opencv_core.cvSetImageROI(aImage, aRectangle);
        IplImage cropped = opencv_core.cvCreateImage(opencv_core.cvGetSize(aImage),
                aImage.depth(), aImage.nChannels());
        // Copy original image (only ROI) to the cropped image
        opencv_core.cvCopy(aImage, cropped);

        return cropped;

    }

    /**
     * Converts an image into grayscale.
     *
     * @param aImage original image
     * @return grayscale image
     */
    public IplImage grayImage(final IplImage aImage) {

        IplImage result = new IplImage();
        opencv_imgproc.cvCvtColor(aImage, result, opencv_imgproc.CV_BGR2GRAY);
        return result;
    }

    /**
     * Resizes and returns a single image.
     *
     * @param aImage original image
     * @param aWidth image width
     * @param aHeight image height
     * @return resized image
     */
    public IplImage resizeImage(final IplImage aImage, final int aWidth, final int aHeight) {

        IplImage result = AbstractIplImage.create(aWidth, aHeight,
                aImage.depth(), aImage.nChannels());
        opencv_imgproc.cvResize(aImage, result, opencv_imgproc.CV_INTER_CUBIC);

        return result;
    }

    /**
     * Resizes and returns resized images.
     *
     * @param aImages original images
     * @param aWidth image width
     * @param aHeight image height
     * @return an array of resized images
     */
    public ArrayList<IplImage> resizeImages(final ArrayList<IplImage> aImages,
            final int aWidth, final int aHeight) {

        ArrayList<IplImage> result = new ArrayList<IplImage>();

        for (IplImage image : aImages) {
            result.add(resizeImage(image, aWidth, aHeight));
        }

        return result;
    }

    /**
     * Converts a JavaCV frame into a JavaCV image.
     *
     * @param aFrame JavaCV frame
     * @return JavaCV image
     */
    public IplImage convertToImage(final Frame aFrame) {

        return converter.convert(aFrame);
    }

    /**
     * Converts a JavaCV image into a JavaCV frame.
     *
     * @param aImage JavaCV image
     * @return JavaCV frame
     */
    public Frame convertToFrame(final IplImage aImage) {

        return converter.convert(aImage);
    }

    /**
     * Converts an IplImage into a byte array.
     *
     * @param aImage input image
     * @return byte array
     * @throws IOException exception
     */
    public byte[] convertToByteArray(final IplImage aImage) throws IOException {

        BufferedImage result = flatConverter.convert(converter.convert(aImage));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(result, "png", baos);
        baos.flush();
        byte[] byteImage = baos.toByteArray();
        baos.close();

        return byteImage;
    }

    /**
     * Converts a frame into a byte array.
     *
     * @param aFrame input frame
     * @return byte array
     * @throws IOException exception
     */
    public byte[] convertToByteArray(final Frame aFrame) throws IOException {

        BufferedImage result = flatConverter.convert(aFrame);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(result, "png", baos);
        baos.flush();
        byte[] byteImage = baos.toByteArray();
        baos.close();

        return byteImage;
    }

    /**
     * Converts a buffered image into a JavaCV frame.
     *
     * @param aImage buffered image
     * @return frame
     * @throws IOException exception
     */
    public Frame convertToFrame(final BufferedImage aImage) throws IOException {

        return flatConverter.convert(aImage);
    }

    /**
     * Converts a buffered image into a JavaCV image.
     *
     * @param aImage buffered image
     * @return image
     * @throws IOException exception
     */
    public IplImage convertToImage(final BufferedImage aImage) throws IOException {

        return converter.convertToIplImage(flatConverter.convert(aImage));
    }

    /**
     * Converts a buffered image into a JavaCV image.
     *
     * @param aFrame JavaCV frame
     * @return JavaCV mat
     * @throws IOException exception
     */
    public Mat convertToMat(final Frame aFrame) throws IOException {

        return converter.convertToMat(aFrame);
    }

    /**
     * Saves image.
     *
     * @param aName image name
     * @param aImage image
     */
    public void saveImage(final String aName, final IplImage aImage) {
        opencv_imgcodecs.cvSaveImage(aName, aImage);
    }

    /**
     * Saves image.
     *
     * @param aName image name
     * @param aFrame image
     */
    public void saveImage(final String aName, final Frame aFrame) {
        opencv_imgcodecs.cvSaveImage(aName, convertToImage(aFrame));
    }
}
