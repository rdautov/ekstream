package test;

import static org.bytedeco.javacpp.helper.opencv_objdetect.cvHaarDetectObjects;
import static org.bytedeco.javacpp.opencv_core.cvClearMemStorage;
import static org.bytedeco.javacpp.opencv_core.cvGetSeqElem;
import static org.bytedeco.javacpp.opencv_core.cvLoad;
import static org.bytedeco.javacpp.opencv_objdetect.CV_HAAR_DO_CANNY_PRUNING;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;

import javax.imageio.ImageIO;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_imgcodecs;
import org.bytedeco.javacpp.helper.opencv_core.AbstractCvMemStorage;
import org.bytedeco.javacpp.opencv_core.CvMemStorage;
import org.bytedeco.javacpp.opencv_core.CvRect;
import org.bytedeco.javacpp.opencv_core.CvSeq;
import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacpp.opencv_objdetect.CvHaarClassifierCascade;
import org.bytedeco.javacpp.presets.opencv_objdetect;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;

import utils.Utils;

import org.bytedeco.javacv.FrameGrabber.Exception;

public class VideoGrabberSamplerDetector {

    /** Scale factor for face detection. */
    static final double SCALE_FACTOR = 1.5;

    /** Neighbors for face detection. */
    static final int MIN_NEIGHBOURS = 3;

    /** */
    static final int INTERVAL = 1000;

    /** */
    static final int IMAGE_DIMENSION = 300;

    /** */
    public static final String XML_FILE = "/home/orkes/Desktop/haarcascade_frontalface_default.xml";

    static OpenCVFrameConverter.ToIplImage converter;

    static Java2DFrameConverter flatConverter;

    /**
     * @param aArgs
     * @throws Exception
     * @throws InterruptedException
     * @throws IOException
     */
    public static void main(final String[] aArgs) throws Exception, InterruptedException, IOException {

        Loader.load(opencv_objdetect.class);
        FrameGrabber grabber = FrameGrabber.createDefault(0);
        converter = new OpenCVFrameConverter.ToIplImage();
        flatConverter = new Java2DFrameConverter();

        grabber.start();

        while (true) {

            Frame frame = grabber.grab();
            byte[] result = Utils.getInstance().toByteArray(frame);

            ByteArrayInputStream inputStream = new ByteArrayInputStream(result);

            BufferedImage bufferedImage = ImageIO.read(inputStream);
            IplImage image = Utils.getInstance().toIplImage(bufferedImage);

            opencv_imgcodecs.cvSaveImage(System.currentTimeMillis() + "-received.png", image);

            ArrayList<IplImage> faces = detect(image);

            if (!faces.isEmpty()) {
                ArrayList<IplImage> resizedFaces = Utils.getInstance().resizeImages(faces, 92, 112);

                //now transfer the cropped images forward
                for (IplImage face : resizedFaces) {


                    opencv_imgcodecs.cvSaveImage(System.currentTimeMillis()
                            + "-face.png", face);
                }
            }

            Thread.currentThread();
            Thread.sleep(INTERVAL);

            grabber.stop();

        }

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
    public static IplImage cropImage(final IplImage aImage,
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
        opencv_imgcodecs.cvSaveImage("cropped.png", cropped);
        opencv_imgcodecs.cvSaveImage("data/" + System.currentTimeMillis() + "-crop.png", cropped);

        return cropped;

    }

    /**
     * Crops an image to a given rectangle.
     *
     * @param aImage original image
     * @param aRectangle rectangle
     * @return cropped image
     */
    public static IplImage cropImage(final IplImage aImage, final CvRect aRectangle) {

        // After setting ROI (Region-Of-Interest) all processing will only be
        // done on the ROI
        opencv_core.cvSetImageROI(aImage, aRectangle);
        IplImage cropped = opencv_core.cvCreateImage(opencv_core.cvGetSize(aImage),
                aImage.depth(), aImage.nChannels());
        // Copy original image (only ROI) to the cropped image
        opencv_core.cvCopy(aImage, cropped);
        opencv_imgcodecs.cvSaveImage("data/" + System.currentTimeMillis() + "-crop.png", cropped);

        return cropped;

    }

    /**
     * Detects faces in an input image.
     *
     * @param aImage input image
     * @return an array of detected faces as images
     */
    public static ArrayList<IplImage> detect(final IplImage aImage) {

        ArrayList<IplImage> result = new ArrayList<IplImage>();

        CvHaarClassifierCascade cascade =
                new CvHaarClassifierCascade(cvLoad("haarcascade_frontalface_default.xml"));
        CvMemStorage storage = AbstractCvMemStorage.create();
        CvSeq sign = cvHaarDetectObjects(aImage,
                cascade, storage, SCALE_FACTOR, MIN_NEIGHBOURS, CV_HAAR_DO_CANNY_PRUNING);

        for (int i = 0; i < sign.total(); i++) {
            CvRect r = new CvRect(cvGetSeqElem(sign, i));
            //opencv_imgproc.cvRectangle(aImage, cvPoint(r.x(), r.y()),
            //        cvPoint(r.width() + r.x(), r.height() + r.y()),
            //        AbstractCvScalar.RED, 2, LINE_AA, 0);

            IplImage image = Utils.getInstance().cropImage(aImage, r);
            result.add(image);
        }

        cvClearMemStorage(storage);
        return result;
    }

}
