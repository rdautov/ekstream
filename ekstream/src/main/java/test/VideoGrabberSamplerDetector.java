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

            opencv_imgcodecs.cvSaveImage(System.currentTimeMillis() + "-transferred.png",
                    Utils.getInstance().convertToImage(frame));

            byte[] result = Utils.getInstance().convertToByteArray(frame);

            ByteArrayInputStream inputStream = new ByteArrayInputStream(result);

            BufferedImage bufferedImage = ImageIO.read(inputStream);
            IplImage image = Utils.getInstance().convertToImage(bufferedImage);

            opencv_imgcodecs.cvSaveImage(System.currentTimeMillis() + "-received.png", image);

            ArrayList<IplImage> faces = detect(image);

            if (!faces.isEmpty()) {

                //now transfer the cropped images forward
                for (IplImage face : faces) {

                    opencv_imgcodecs.cvSaveImage(System.currentTimeMillis()
                            + "-detected.png", face);
                }

                ArrayList<IplImage> resizedFaces = Utils.getInstance().resizeImages(faces, 92, 112);

                //now transfer the cropped images forward
                for (IplImage face : resizedFaces) {

                    opencv_imgcodecs.cvSaveImage(System.currentTimeMillis()
                            + "-resized.png", face);
                }
            }

            Thread.currentThread();
            Thread.sleep(INTERVAL);

            //grabber.stop();

        }

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
                new CvHaarClassifierCascade(cvLoad(XML_FILE));
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
