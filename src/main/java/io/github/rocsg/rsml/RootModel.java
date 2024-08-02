/*
 *
 */
package io.github.rocsg.rsml;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.RGBStackMerge;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import io.github.rocsg.fijiyama.common.Timer;
import io.github.rocsg.fijiyama.common.VitimageUtils;
import io.github.rocsg.fijiyama.registration.ItkTransform;
import io.github.rocsg.fijiyama.registration.TransformUtils;
import io.github.rocsg.rsmlparser.*;
import io.github.rocsg.rsmlparser.RSML2D.Root4Parser;
import io.github.rocsg.rsmlparser.RSML2D.RootModel4Parser;
import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.Clusterer;
import org.apache.commons.math3.ml.clustering.DoublePoint;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import org.apache.commons.math3.ml.distance.DistanceMeasure;
import org.scijava.vecmath.Point3d;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.*;

import static io.github.rocsg.rsmlparser.RSML2D.Root4Parser.getTotalChildrenList;

/**
 * Taken from Xavier Draye and Guillaume Lobet - Université catholique de Louvain
 *
 * @author Romain Fernandez and Loaï gandeel
 */

//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

class DataFileFilterRSML extends FileFilter
        implements java.io.FileFilter {

    public DataFileFilterRSML() {
    }

    public boolean accept(File f) {
        return f.getName().toLowerCase().endsWith("rsml");
    }

    public String getDescription() {
        return "Root System Markup Language";
    }
}


// %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

/**
 * The Class RootModel.
 */
public class RootModel extends WindowAdapter implements Comparable<RootModel>, IRootModelParser {
    /**
     * The datafile filter RSML.
     */
    static private final DataFileFilterRSML datafileFilterRSML = new DataFileFilterRSML();
    /**
     * The version.
     */
//	static int nextRootKey;
    static String version = "4.1";
    public Metadata metadata;

    /**
     * The datafile key.
     */
    static String datafileKey = "default";
    /**
     * The root list.
     */
    public ArrayList<Root> rootList = new ArrayList<Root>();
    /**
     * The next auto root ID.
     */
    public int nextAutoRootID;
    /**
     * The img name.
     */
    public String imgName;
    /**
     * The previous magnification.
     */
    public float previousMagnification = 0.0f;
    /**
     * The pixel size.
     */
    public float pixelSize;

    /**  autoBuildFromNode() estimates the putative location for a new node in the direction of the         line joining the previous and current nodes, using a distance which is the minimum of         putative distances 1 & 2 (see AUTOBUILD_STEP_FACTOR_BORDER AUTOBUILD_STEP_FACTOR_DIAMETER)        but which is at least equal to AUTOBUILD_MIN_STEP. */
    /** Putative distance 1 (from the current node) is equal to the
     distance to the root border (along the predicted direction) multiplied by the AUTOBUILD_STEP_FACTOR_BORDER */
    /** Putative distance 2 (from the current node) is equal to the
     root diameter at the current node multiplied by the AUTOBUILD_STEP_FACTOR_DIAMETER */
    /**
     * Minimum angle step for the automatic recentering of nodes in autoBuildFromNode()
     */
    public double[] hoursCorrespondingToTimePoints;
    /**
     * The img.
     */
    ImagePlus img;
    /**
     * The ip.
     */
    ImageProcessor ip;
    /**
     * The angle step.
     */
    double angleStep;
    /**
     * The threshold.
     */
    float threshold = 100;
    /**
     * The d M.
     */
    float dM = 0;
    /**
     * Angle step for the automatic recentering of nodes in autoBuildFromNode(): the angle step
     * is equal to AUTOBUILD_THETA_STEP_FACTOR divided by the root diameter
     */
    float AUTOBUILD_MIN_STEP = 3.0f;
    /**
     * The autobuild step factor border.
     */
    float AUTOBUILD_STEP_FACTOR_BORDER = 0.5f;
    /**
     * The autobuild step factor diameter.
     */
    float AUTOBUILD_STEP_FACTOR_DIAMETER = 2.0f;
    /**
     * The autobuild min theta step.
     */
    float AUTOBUILD_MIN_THETA_STEP = 0.02f;
    /**
     * The autobuild theta step factor.
     */
    float AUTOBUILD_THETA_STEP_FACTOR = (float) Math.PI / 2.0f;
    Map<Root, List<Node>> insertionPointsMap;
    /**
     * The directory.
     */
    private String directory;
    /**
     * The dpi.
     */
    private float dpi;
    /**
     * The n plants.
     */
    private int nPlants;
    private int maxRootOrder = 0;

    /**
     * Constructors.
     */
    public RootModel() {
        dpi = 1;
        pixelSize = 2.54f / dpi;
    }

    /**
     * Constructors.
     *
     * @param dataFName the data F name
     */
    public RootModel(String dataFName) {
        //dpi = (FSR.prefs != null ? FSR.prefs.getFloat("DPI_default", dpi) : 1);
        if (dpi == 0) { // TODO fix
            dpi = 1;
        }
        pixelSize = 2.54f / dpi;
        //readRSML(dataFName);
        readRSMLNew(dataFName);
    }

    /**
     * Constructors.
     *
     * @param dataFName      the data F name
     * @param timeLapseModel the time lapse model
     */
    public RootModel(String dataFName, boolean timeLapseModel) {
        //dpi = (FSR.prefs != null ? FSR.prefs.getFloat("DPI_default", dpi) : 1);
        if (dpi == 0) { // TODO fix
            dpi = 1;
        }
        pixelSize = 2.54f / dpi;
        readRSML(dataFName, timeLapseModel);
    }

    public RootModel(RootModel rm) {
        this.dpi = rm.dpi;
        this.pixelSize = rm.pixelSize;
        this.directory = rm.directory;
        this.nPlants = rm.nPlants;
        this.imgName = rm.imgName;
        this.previousMagnification = rm.previousMagnification;
        this.nextAutoRootID = rm.nextAutoRootID;
        this.img = rm.img;
        this.ip = rm.ip;
        this.angleStep = rm.angleStep;
        this.threshold = rm.threshold;
        this.dM = rm.dM;
        this.AUTOBUILD_MIN_STEP = rm.AUTOBUILD_MIN_STEP;
        this.AUTOBUILD_STEP_FACTOR_BORDER = rm.AUTOBUILD_STEP_FACTOR_BORDER;
        this.AUTOBUILD_STEP_FACTOR_DIAMETER = rm.AUTOBUILD_STEP_FACTOR_DIAMETER;
        this.AUTOBUILD_MIN_THETA_STEP = rm.AUTOBUILD_MIN_THETA_STEP;
        this.AUTOBUILD_THETA_STEP_FACTOR = rm.AUTOBUILD_THETA_STEP_FACTOR;
        this.rootList = new ArrayList<Root>();
        List<Float> birthTimes = new ArrayList<>();
        for (Root r : rm.rootList) {
            Node n = r.firstNode;
            while (n != null) {
                if (!birthTimes.contains(n.birthTime)) {
                    birthTimes.add(n.birthTime);
                }
                n = n.child;
            }
        }
        birthTimes.sort(Float::compareTo);
        this.hoursCorrespondingToTimePoints = rm.hoursCorrespondingToTimePoints;
    }

    /**
     * Here is a test function for the function above, standardOrderingOfRoots()
     * It takes as an argument a rsml, read it by using the function RootModelWildReadFromRsml, then standardize ordering, and then write it back to a new rsml file
     */
    public static void testStandardOrderingOfRoots(String rsmlFile) {
        RootModel rm = RootModel.RootModelWildReadFromRsml(rsmlFile);
        rm.standardOrderingOfRoots();
        rm.writeRSML3D(rsmlFile + "_standardized.rsml", "", true, false);
    }

    /**
     * Root model wild read from rsml.
     *
     * @param rsmlFile the rsml file
     * @return the root model
     */
    public static RootModel RootModelWildReadFromRsml(String rsmlFile) {
        //Wild read model for Fijiyama did Root model with time, diameter, vx and vy information
        //FSR sr = (new FSR());
        //sr.initialize();
        boolean debug = false;
        //String lineSep= System.getProperty("line.separator");

        int Nobs = 100000; //N max of observations

        String[] str = null;
        if (VitimageUtils.isWindowsOS()) str = VitimageUtils.readStringFromFile(rsmlFile).split("\\n");
        else str = VitimageUtils.readStringFromFile(rsmlFile).split("\n");
        RootModel rm = new RootModel();
        rm.imgName = "";
        rm.pixelSize = (float) Double.parseDouble(str[4].split(">")[1].split("<")[0]);

        double[] hours = new double[Nobs];
        boolean hasHours = true;
        hasHours = (str[9].contains("observation"));
        double[] tabD = new double[0];
        if (hasHours) {
            String[] tab = (str[9].split(">")[1].split("<")[0]).split(",");
            tabD = new double[tab.length + 1];
            for (int i = 1; i < tab.length + 1; i++) tabD[i] = Double.parseDouble(tab[i - 1]);
        }
        tabD[0] = tabD[1];
        rm.hoursCorrespondingToTimePoints = tabD;

        int ind = hasHours ? 16 : 15;
        boolean first;
        if (debug) IJ.log("Pl" + str[ind]);
        while (str[ind].contains("<plant")) {
            ind = ind + 1 + 3;//<root then <point
            Root rPrim = new Root(null, rm, "", 1);
            first = true;
            if (debug) IJ.log("Poiprim" + str[ind]);
            while (str[ind].contains("<point")) {
                String[] vals = str[ind].replace("<point ", "").replace("/>", "").replace("\"", "").split(" ");
                if (hasHours)
                    rPrim.addNode(Double.parseDouble(vals[2].split("=")[1]), Double.parseDouble(vals[3].split("=")[1]), Double.parseDouble(vals[0].split("=")[1]), Double.parseDouble(vals[1].split("=")[1]), Double.parseDouble(vals[4].split("=")[1]), Double.parseDouble(vals[5].split("=")[1]), Double.parseDouble(vals[6].split("=")[1]), first);
                else
                    rPrim.addNode(Double.parseDouble(vals[1].split("=")[1]), Double.parseDouble(vals[2].split("=")[1]), Double.parseDouble(vals[0].split("=")[1]), Double.parseDouble(vals[0].split("=")[1]), Double.parseDouble(vals[3].split("=")[1]), Double.parseDouble(vals[4].split("=")[1]), Double.parseDouble(vals[5].split("=")[1]), first);
                if (first) first = false;
                ind++;
                if (debug) IJ.log("-Poiprim" + str[ind]);
            }
            rPrim.computeDistances();
            rm.rootList.add(rPrim);
            ind = ind + 2;//<root or </root
            if (debug) IJ.log("root lat" + str[ind]);
            while (str[ind].contains("<root")) {//lateral
                ind = ind + 3;//point
                Root rLat = new Root(null, rm, "", 2);
                first = true;
                if (debug) IJ.log("PoiLat" + str[ind]);
                while (str[ind].contains("<point")) {
                    String[] vals = str[ind].replace("<point ", "").replace("/>", "").replace("\"", "").split(" ");
                    if (hasHours)
                        rLat.addNode(Double.parseDouble(vals[2].split("=")[1]), Double.parseDouble(vals[3].split("=")[1]), Double.parseDouble(vals[0].split("=")[1]), Double.parseDouble(vals[1].split("=")[1]), Double.parseDouble(vals[4].split("=")[1]), Double.parseDouble(vals[5].split("=")[1]), Double.parseDouble(vals[6].split("=")[1]), first);
                    else
                        rLat.addNode(Double.parseDouble(vals[1].split("=")[1]), Double.parseDouble(vals[2].split("=")[1]), Double.parseDouble(vals[0].split("=")[1]), Double.parseDouble(vals[0].split("=")[1]), Double.parseDouble(vals[3].split("=")[1]), Double.parseDouble(vals[4].split("=")[1]), Double.parseDouble(vals[5].split("=")[1]), first);
                    if (first) first = false;
                    ind++;
                    if (debug) IJ.log("-PoiLat?" + str[ind]);
                }
                rLat.computeDistances();
                rPrim.attachChild(rLat);
                rLat.attachParent(rPrim);
                rm.rootList.add(rLat);
                ind = ind + 3;//<root or </root
                if (debug) IJ.log("-root lat?" + str[ind]);
            }
            ind = ind + 2;//<plant or nothing
            if (debug) IJ.log("-plant?" + str[ind]);
        }
        rm.standardOrderingOfRoots();
        return rm;
    }

    /**
     * Creates the superposition time lapse from path.
     *
     * @param imgPath  the img path
     * @param rsmlPath the rsml path
     * @return the image plus
     */
    public static ImagePlus createSuperpositionTimeLapseFromPath(String imgPath, String rsmlPath) {
        ImagePlus imgReg = IJ.openImage(imgPath);
        int Nt = imgReg.getStackSize();
        ImagePlus[] tabReg = VitimageUtils.stackToSlices(imgReg);
        ImagePlus[] tabRes = new ImagePlus[Nt];
        RootModel rm = RootModel.RootModelWildReadFromRsml(rsmlPath);
        for (int i = 0; i < Nt; i++) {
            ImagePlus imgRSML = rm.createGrayScaleImageWithTime(imgReg, 1, false, (i + 1), true, new boolean[]{true, true, true, false, true}, new double[]{2, 2});
            imgRSML.setDisplayRange(0, Nt + 3);
            tabRes[i] = RGBStackMerge.mergeChannels(new ImagePlus[]{tabReg[i], imgRSML}, true);
            IJ.run(tabRes[i], "RGB Color", "");
        }
        return VitimageUtils.slicesToStack(tabRes);
    }

    /**
     * Project rsml on image.
     *
     * @param rm               the rm
     * @param registeredStack  the registered stack
     * @param zoomFactor       the zoom factor
     * @param primaryRadius    the primary radius
     * @param secondaryRadius  the secondary radius
     * @param isOldRsmlVersion the is old rsml version
     * @param binaryColor      the binary color
     * @param projectOnStack   the project on stack
     * @return the image plus
     */
    public static ImagePlus projectRsmlOnImage(RootModel rm, ImagePlus registeredStack, int zoomFactor, int primaryRadius, int secondaryRadius, boolean isOldRsmlVersion, boolean binaryColor, boolean projectOnStack) {
        if (!isOldRsmlVersion) {
            IJ.showMessage("Not yet coded");
            System.exit(0);
        }
        Timer t = new Timer();
        int Nt = registeredStack.getStackSize();
        ImagePlus[] tabRes = new ImagePlus[Nt];
        ImagePlus[] tabReg = VitimageUtils.stackToSlices(registeredStack);
        for (int i = 0; i < Nt; i++) {
            ImagePlus imgRSML = rm.createGrayScaleImageWithTime(
                    registeredStack, zoomFactor, binaryColor, (i + 1), true,
                    new boolean[]{true, true, true, false, true}, new double[]{primaryRadius, secondaryRadius});
            imgRSML.setDisplayRange(0, binaryColor ? 255 : (Nt + 3));
            tabRes[i] = RGBStackMerge.mergeChannels(new ImagePlus[]{projectOnStack ? tabReg[i] : VitimageUtils.nullImage(tabReg[i]), imgRSML}, true);
            IJ.run(tabRes[i], "RGB Color", "");
        }
        t.print("Updating root model took : ");
        return VitimageUtils.slicesToStack(tabRes);
    }

    /**
     * Adapted from ImageProcessor in IJ v1.53i
     *
     * @param img           the img
     * @param x1            the x 1
     * @param y1            the y 1
     * @param x2            the x 2
     * @param y2            the y 2
     * @param drawOneEveryX the draw one every X
     * @param lineWidth     the line width
     * @param valueToSet    the value to set
     */
    public static void drawDotline(ImagePlus img, int x1, int y1, int x2, int y2, int drawOneEveryX, double lineWidth, double valueToSet) {
        ImageProcessor ip = img.getProcessor();
        int type = img.getType();
        drawDotline(ip, type, x1, y1, x2, y2, drawOneEveryX, lineWidth, valueToSet);
    }

    /**
     * Adapted from ImageProcessor in IJ v1.53i
     *
     * @param ip            the ip
     * @param type          the type
     * @param x1            the x 1
     * @param y1            the y 1
     * @param x2            the x 2
     * @param y2            the y 2
     * @param drawOneEveryX the draw one every X
     * @param lineWidth     the line width
     * @param valueToSet    the value to set
     */
    public static void drawDotline(ImageProcessor ip, int type, int x1, int y1, int x2, int y2, int drawOneEveryX, double lineWidth, double valueToSet) {
        double val = Math.max(valueToSet, 1);
        int valueI = (int) Math.round(val);
        int valueF = Float.floatToIntBits((float) val);
        int dx = x2 - x1;
        int dy = y2 - y1;
        int absdx = dx >= 0 ? dx : -dx;
        int absdy = dy >= 0 ? dy : -dy;
        int n = Math.max(absdy, absdx);
        double xinc = dx != 0 ? (double) dx / n : 0; //single point (dx=dy=n=0): avoid division by zero
        double yinc = dy != 0 ? (double) dy / n : 0;
        double x = x1;
        double y = y1;
        double rCar = (lineWidth / 2) * (lineWidth / 2);
        for (int i = 0; i <= n; i++) {
            if (lineWidth <= 1) {
                if ((drawOneEveryX > 0) && ((i) % drawOneEveryX) != 0) {
                } else
                    ip.putPixel((int) Math.round(x), (int) Math.round(y), (type == ImagePlus.GRAY8 ? valueI : valueF));
            } else {
                if ((drawOneEveryX > 0) && ((i) % (drawOneEveryX * lineWidth) != 0)) {
                } else {
                    for (int ddx = (int) -lineWidth; ddx <= lineWidth; ddx++)
                        for (int ddy = (int) -lineWidth; ddy <= lineWidth; ddy++)
                            if ((ddx * ddx + ddy * ddy) <= (rCar)) {
                                ip.putPixel((int) Math.round(x + ddx), (int) Math.round(y + ddy), (type == ImagePlus.GRAY8 ? valueI : valueF));
                            }
                }
            }
            x += xinc;
            y += yinc;
        }
    }

    /**
     * View rsml and image sequence superposition.
     *
     * @param rm            the rm
     * @param imageSequence the image sequence
     * @param zoomFactor    the zoom factor
     * @return the insert angl
     */
    static ImagePlus viewRsmlAndImageSequenceSuperposition(RootModel rm, ImagePlus imageSequence, int zoomFactor) {
        int Nt = imageSequence.getStackSize();
        ImagePlus[] tabRes = new ImagePlus[Nt];
        ImagePlus[] tabReg = VitimageUtils.stackToSlices(imageSequence);
        for (int i = 0; i < Nt; i++) {
            ImagePlus imgRSML = rm.createGrayScaleImageWithTime(imageSequence, zoomFactor, false, (i + 1), true, new boolean[]{true, true, true, false, true}, new double[]{2, 2});
            imgRSML.setDisplayRange(0, Nt + 3);
            tabRes[i] = RGBStackMerge.mergeChannels(new ImagePlus[]{tabReg[i], imgRSML}, true);
            IJ.run(tabRes[i], "RGB Color", "");
        }
        return VitimageUtils.slicesToStack(tabRes);
    }

    public void increaseNbPlants() {
        nPlants++;
    }


    public Root getPrimaryRootOfPlant(int plant) {
        for (Root r : rootList) {
            if (r.order == 1 && r.plantNumber == plant) return r;
        }
        return null;
    }

    /*  TODO : reactivate if needed or delete after release
           public Root[] getLateralRootsOfPlant(int plant) {
           ArrayList<Root> ar = new ArrayList<Root>();
           for (Root r : rootList) {
               if (r.order == 2 && r.plantNumber == plant) ar.add(r);
           }
           return (ar.toArray(new Root[ar.size()]));
       }
   */
    public double[][] getTipDepthAndRootLengthOverTimesteps(Root r) {
        Node n = r.firstNode;
        Node nPar = n;
        double[][] ret = new double[2][hoursCorrespondingToTimePoints.length];
        double incrDist = 0;
        double totalDist = 0;
        double curHours = -1;
        while (n.child != null) {
            nPar = n;
            n = n.child;
            totalDist += distance(nPar, n);
            int valInt = Math.round(n.birthTime);
            double deltaPos = Math.abs(n.birthTime - valInt);
            if (deltaPos < VitimageUtils.EPSILON) {
                ret[0][valInt] = n.y;
                ret[1][valInt] = totalDist;
            }
        }
        ret[0][0] = 0;
        ret[1][0] = 0;
        return ret;
    }

    public double[][] getTipDepthSpeedAndRootLengthOverTime(Root r) {
        Node n = r.firstNode;
        Node nPar = n;
        double[][] ret = new double[3][hoursCorrespondingToTimePoints.length];
        ret[1][0] = 0;
        int incrIndex = -1;
        double incrTime = 0;
        double incrDist = 0;
        double totalDist = 0;
        double curHours = n.birthTimeHours;
        while (n.child != null) {
            incrDist = 0;
            incrTime = 0;
            while (curHours < hoursCorrespondingToTimePoints[incrIndex + 1]) {
                nPar = n;
                n = n.child;
                curHours = n.birthTimeHours;

                incrDist += distance(nPar, n);
                totalDist += distance(nPar, n);
                incrTime += (n.birthTimeHours - nPar.birthTimeHours);
            }
            incrIndex++;
            ret[0][incrIndex] = n.y;
            ret[1][incrIndex] = incrDist / incrTime;
            ret[2][incrIndex] = totalDist;
        }
        return ret;
    }

    /**
     * Gets the lateral speeds and depth over time.
     *
     * @param tMax  the t max
     * @param plant the plant
     * @return the lateral speeds and depth over time
     */
    public double[][][] getLateralSpeedsAndDepthOverTime(int tMax, int plant) {
        System.out.println("Working for plant " + plant + " at tmax=" + tMax);
        int nbLat = nbLatsPerPlant()[plant];
        double primInitDepth = 0;
        for (Root r : rootList) {
            if (r.order == 1) {
                primInitDepth = r.firstNode.y;
            }
        }
        double[][][] tab = new double[2][nbLat][tMax + 1];
        int incr = -1;
        for (Root r : rootList) {
            if (r.plantNumber != plant || r.order == 1) continue;
            incr++;
            Node n = r.firstNode;
            double dh = n.child.birthTimeHours - n.birthTimeHours;
            if (dh <= 0) dh = VitimageUtils.EPSILON;
            double spe = 0;
            while (n != null) {
                double ti = n.birthTime;
                if (n.child != null) spe = VitimageUtils.distance(n.x, n.y, n.child.x, n.child.y) / dh;
                tab[0][incr][(int) Math.round(ti)] = -n.y + primInitDepth;
                tab[1][incr][(int) Math.round(ti)] = spe;
                n = n.child;
            }
        }
        return tab;
    }

    /**
     * Write RSML 3 D.
     *
     * @param f                   the f
     * @param imgExt              the img ext
     * @param shortValues         the short values
     * @param respectStandardRSML the respect standard RSML
     */
    public void writeRSML3D(String f, String imgExt, boolean shortValues, boolean respectStandardRSML) {
        this.standardOrderingOfRoots();
        String fileName = VitimageUtils.withoutExtension(new File(f).getName());
        nextAutoRootID = 0;
        Document dom = null;
        Element re, me, met, mett, sce, plant, rootPrim, rootLat, geomPrim, polyPrim, geomLat, polyLat, pt;

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            dom = builder.newDocument();
        } catch (ParserConfigurationException pce) {
            logReadError();
            return;
        }

        // add <?xml version='1.0`' encoding='UTF-8'?> at the beginning of the file for formatting purposes
        //ProcessingInstruction xmlDecl = dom.createProcessingInstruction("xml", "version=\"1.0\" encoding=\"UTF-8\"");
        //dom.insertBefore(xmlDecl, dom.getFirstChild());

        re = dom.createElement("rsml");

        // create a time list
        StringBuilder hours = new StringBuilder();
        for (int i = 1; i < hoursCorrespondingToTimePoints.length - 1; i++)
            hours.append(VitimageUtils.dou(hoursCorrespondingToTimePoints[i])).append(",");
        hours.append(VitimageUtils.dou(hoursCorrespondingToTimePoints[hoursCorrespondingToTimePoints.length - 1]));

        //Build and add metadata
        me = dom.createElement("metadata");
        met = dom.createElement("version");
        met.setTextContent("1.4");
        me.appendChild(met);
        met = dom.createElement("unit");
        met.setTextContent("pixel(um)");
        me.appendChild(met);
        met = dom.createElement("size");
        met.setTextContent("" + pixelSize);
        me.appendChild(met);
        met = dom.createElement("last-modified");
        met.setTextContent(new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date()));
        me.appendChild(met);
        met = dom.createElement("software");
        met.setTextContent("RootSystemTracker");
        me.appendChild(met);
        met = dom.createElement("user");
        met.setTextContent("Unknown");
        me.appendChild(met);
        met = dom.createElement("file-key");
        met.setTextContent(fileName);
        me.appendChild(met);
        met = dom.createElement("observation-hours");
        met.setTextContent(hours.toString());
        me.appendChild(met);
        met = dom.createElement("image");
        mett = dom.createElement("label");
        mett.setTextContent(fileName + imgExt);
        met.appendChild(mett);
        mett = dom.createElement("sha256");
        mett.setTextContent("Nothing there");
        met.appendChild(mett);
        me.appendChild(met);
        re.appendChild(me);

        //Build and add the scene
        sce = dom.createElement("scene");

        //Build and add plant 1
        int incrPlant = 0;
        for (Root r : rootList) {
            if (r.isChild == 1) continue;
            ++incrPlant;
            plant = dom.createElement("plant");
            plant.setAttribute("ID", "" + incrPlant);
            plant.setAttribute("label", "Plant " + incrPlant);
            rootPrim = dom.createElement("root");
            rootPrim.setAttribute("ID", (incrPlant) + ".1");
            rootPrim.setAttribute("label", r.label);
            geomPrim = dom.createElement("geometry");
            polyPrim = dom.createElement("polyline");
            double[][] coord = getRootCoordinates(r);
            for (double[] value : coord) {
                pt = dom.createElement("point");
                pt.setAttribute(respectStandardRSML ? "x" : "coord_x", "" + (shortValues ? VitimageUtils.dou(value[0]) : value[0]));
                pt.setAttribute(respectStandardRSML ? "y" : "coord_y", "" + (shortValues ? VitimageUtils.dou(value[1]) : value[1]));
                if (!respectStandardRSML) {
                    pt.setAttribute("vx", "" + (shortValues ? VitimageUtils.dou(value[2]) : value[2]));
                    pt.setAttribute("vy", "" + (shortValues ? VitimageUtils.dou(value[3]) : value[3]));
                    pt.setAttribute("diameter", "" + (shortValues ? VitimageUtils.dou(value[4]) : value[4]));
                    pt.setAttribute("coord_t", "" + (shortValues ? VitimageUtils.dou(value[5]) : value[5]));
                    pt.setAttribute("coord_th", "" + (shortValues ? VitimageUtils.dou(value[6]) : value[6]));
                }
                polyPrim.appendChild(pt);
            }
            geomPrim.appendChild(polyPrim);
            rootPrim.appendChild(geomPrim);
            if (respectStandardRSML) {
                Element functionPrim = dom.createElement("functions");
                Element funcPrim = dom.createElement("function");
                funcPrim.setAttribute("name", "timepoint");
                funcPrim.setAttribute("domain", "polyline");
                for (double[] doubles : coord) {
                    pt = dom.createElement("sample");
                    pt.setTextContent("" + (shortValues ? VitimageUtils.dou(doubles[5]) : doubles[5]));
                    funcPrim.appendChild(pt);
                }
                functionPrim.appendChild(funcPrim);
                Element funcPrim2 = dom.createElement("function");
                funcPrim2.setAttribute("name", "hours");
                funcPrim2.setAttribute("domain", "polyline");
                for (double[] doubles : coord) {
                    pt = dom.createElement("sample");
                    pt.setTextContent("" + (shortValues ? VitimageUtils.dou(doubles[6]) : doubles[6]));
                    funcPrim2.appendChild(pt);
                }
                functionPrim.appendChild(funcPrim2);
                rootPrim.appendChild(functionPrim);
            }


            // Ajouter les enfants
            int incrLat = 0;
            for (Root rLat : r.childList) {
                incrLat++;
                rootLat = dom.createElement("root");
                rootLat.setAttribute("ID", (incrPlant) + ".1." + incrLat);
                rootLat.setAttribute("label", rLat.label);
                geomLat = dom.createElement("geometry");
                polyLat = dom.createElement("polyline");
                coord = getRootCoordinates(rLat);
                for (double[] doubles : coord) {
                    pt = dom.createElement("point");
                    pt.setAttribute(respectStandardRSML ? "x" : "coord_x", "" + (shortValues ? VitimageUtils.dou(doubles[0]) : doubles[0]));
                    pt.setAttribute(respectStandardRSML ? "y" : "coord_y", "" + (shortValues ? VitimageUtils.dou(doubles[1]) : doubles[1]));
                    if (!respectStandardRSML) {
                        pt.setAttribute("vx", "" + (shortValues ? VitimageUtils.dou(doubles[2]) : doubles[2]));
                        pt.setAttribute("vy", "" + (shortValues ? VitimageUtils.dou(doubles[3]) : doubles[3]));
                        pt.setAttribute("diameter", "" + (shortValues ? VitimageUtils.dou(doubles[4]) : doubles[4]));
                        pt.setAttribute("coord_t", "" + (shortValues ? VitimageUtils.dou(doubles[5]) : doubles[5]));
                        pt.setAttribute("coord_th", "" + (shortValues ? VitimageUtils.dou(doubles[6]) : doubles[6]));
                    }
                    polyLat.appendChild(pt);
                }
                geomLat.appendChild(polyLat);
                rootLat.appendChild(geomLat);
                if (respectStandardRSML) {
                    Element functionLat = dom.createElement("functions");
                    Element funcLat = dom.createElement("function");
                    funcLat.setAttribute("name", "timepoint");
                    funcLat.setAttribute("domain", "polyline");
                    for (double[] doubles : coord) {
                        pt = dom.createElement("sample");
                        pt.setTextContent("" + (shortValues ? VitimageUtils.dou(doubles[5]) : doubles[5]));
                        funcLat.appendChild(pt);
                    }
                    functionLat.appendChild(funcLat);
                    Element funcLat2 = dom.createElement("function");
                    funcLat2.setAttribute("name", "hours");
                    funcLat2.setAttribute("domain", "polyline");
                    for (double[] doubles : coord) {
                        pt = dom.createElement("sample");
                        pt.setTextContent("" + (shortValues ? VitimageUtils.dou(doubles[6]) : doubles[6]));
                        funcLat2.appendChild(pt);
                    }
                    functionLat.appendChild(funcLat2);
                    rootLat.appendChild(functionLat);
                }
                rootPrim.appendChild(rootLat);
            }
            plant.appendChild(rootPrim);
            sce.appendChild(plant);
        }
        re.appendChild(sce);
        dom.appendChild(re);


        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer;
        try {
            transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            FileOutputStream outStream = new FileOutputStream(new File(f));
            transformer.transform(new DOMSource(dom), new StreamResult(outStream));
            outStream.close();
        } catch (Exception ee) {
            ee.printStackTrace();
        }
    }

    /**
     * This function modify the current object in order to respect the standard description of roots.
     * To that end, the primary roots are ordered according to the x coordinate of their first node (see Root.java)
     */
    public void standardOrderingOfRoots() {
        //First, we order the tab rootList with a specific sorting function taking in consideration their first node x coordinate
        //to that end we first define a comparator of roots
        Comparator<Root> comparatorPrimaries = new Comparator<Root>() {
            @Override
            public int compare(Root r1, Root r2) {
                return Double.compare(r1.firstNode.x, r2.firstNode.x);
            }
        };
        //Then we use comparator to sort rootList
        this.rootList.sort(comparatorPrimaries);
        int incr = 0;
        int maxOrder = 1;
        for (Root root : this.rootList) {
            maxOrder = Math.max(maxOrder, root.order);
            if (root.order == 1) root.plantNumber = (incr++);
        }

        //Then, we order the lateral roots
        //to that end we first define a comparator of roots. Its behaviour is to take in consideration their first node y coordinate
        Comparator<Root> comparatorLateral = new Comparator<Root>() {
            @Override
            public int compare(Root r1, Root r2) {
                return Double.compare(r1.firstNode.y, r2.firstNode.y);
            }
        };
        for (Root r : this.rootList) {
            r.childList.sort(comparatorLateral);
        }

        for (int i = 2; i <= maxOrder; i++) {
            for (Root root : this.rootList) {
                if (root.order == i)
                    root.plantNumber = root.getParent().plantNumber;
            }
        }

    }

    /**
     * Computed the scale base on an unit and a resolutiono.
     *
     * @param unit       the unit
     * @param resolution the resolution
     * @return the dpi
     */
    public float getDPI(String unit, float resolution) {
        if (unit.startsWith("cm") || unit.startsWith("cen")) {
            return resolution * 2.54f;
        } else if (unit.startsWith("mm") || unit.startsWith("mill")) {
            return (resolution / 10) * 2.54f;
        } else if (unit.startsWith("m") || unit.startsWith("me")) {
            return (resolution * 100) * 2.54f;
        } else if (unit.startsWith("IN") || unit.startsWith("in")) {
            return resolution;
        } else {
            return 0.0f;
        }
    }

    /**
     * Distance between point and segment or ten times vitimage utils large value if the projection does not fall into the segment.
     *
     * @param ptC the pt C
     * @param ptA the pt A
     * @param ptB the pt B
     * @return the ${e.g(1).rsfl()}
     */
    public double distanceBetweenPointAndSegmentOrTenTimesVitimageUtilsLargeValueIfTheProjectionDoesNotFallIntoTheSegment
    (double[] ptC, double[] ptA, double[] ptB) {
        double[] AB = TransformUtils.vectorialSubstraction(ptB, ptA);
        double[] BA = TransformUtils.vectorialSubstraction(ptA, ptB);
        double[] AC = TransformUtils.vectorialSubstraction(ptC, ptA);
        double[] BC = TransformUtils.vectorialSubstraction(ptC, ptB);
        double ABscalAC = TransformUtils.scalarProduct(AB, AC);
        double BAscalBC = TransformUtils.scalarProduct(BA, BC);
        if (ABscalAC < 0 || BAscalBC < 0) return 10 * VitimageUtils.LARGE_VALUE;
        double[] AD = TransformUtils.proj_u_of_v(AB, AC);
        double[] ptD = TransformUtils.vectorialAddition(ptA, AD);
        double[] DC = TransformUtils.vectorialSubstraction(ptC, ptD);
        return TransformUtils.norm(DC);
    }

    /**
     * Gets the nearest root segment.
     *
     * @param pt                 the pt
     * @param maxGuessedDistance the max guessed distance
     * @return the nearest root segment
     */
    public Object[] getNearestRootSegment(Point3d pt, double maxGuessedDistance) {
        Node nearestParent = null;
        Root nearestRoot = null;
        double distMin = maxGuessedDistance;
        for (Root r : rootList) {
            Node n = r.firstNode;
            while (n != null && n.child != null && n.child.birthTime <= pt.z) {
                double dist = distanceBetweenPointAndSegmentOrTenTimesVitimageUtilsLargeValueIfTheProjectionDoesNotFallIntoTheSegment(
                        new double[]{pt.x, pt.y, 0}, new double[]{n.x, n.y, 0}, new double[]{n.child.x, n.child.y, 0});
                if (dist < distMin) {
                    distMin = dist;
                    nearestParent = n;
                    nearestRoot = r;
                }
                n = n.child;
            }
        }
        return new Object[]{nearestParent, nearestRoot};
    }

    /**
     * Csv send marks.
     *
     * @param pw     the pw
     * @param header the header
     * @param name   the name
     * @param last   the last
     */
    public void csvSendMarks(PrintWriter pw, boolean header, String name, boolean last) {

        if (header)
            pw.println("image, source, root, root_name, mark_type, position_from_base, diameter, angle, x, y, root_order, root_ontology, value");
        String stmt;
        for (Root r : rootList) {
            // Root origin information
            stmt = name + ", ";
            stmt = stmt.concat(imgName + ", ");  // XD 20110629
            stmt = stmt.concat(r.getRootKey() + ", ");
            stmt = stmt.concat(r.getRootID() + ", ");
            stmt = stmt.concat("Origin, ");
            stmt = stmt.concat("0.0, 0.0, 0.0, ");
            stmt = stmt.concat(r.firstNode.x * pixelSize + ", ");
            stmt = stmt.concat(r.firstNode.y * pixelSize + ", ");
            stmt = stmt.concat(r.isChild() + ", ");
            stmt = stmt.concat(r.getPoAccession() + ", ");
            stmt = stmt.concat(imgName);
            pw.println(stmt);
            pw.flush();
            // Marks information
            for (int j = 0; j < r.markList.size(); j++) {
                Mark m = r.markList.get(j);
                //Point p = r.getLocation(m.lPos * pixelSize);
                stmt = imgName + ", ";
                stmt = stmt.concat((m.isForeign ? m.foreignImgName : imgName) + ", ");  // XD 20110629
                stmt = stmt.concat(r.getRootKey() + ", ");
                stmt = stmt.concat(r.getRootID() + ", ");
                stmt = stmt.concat(Mark.getName(m.type) + ", ");
                stmt = stmt.concat(r.lPosPixelsToCm(m.lPos) + ", ");
                stmt = stmt.concat(m.diameter * pixelSize + ", ");
                stmt = stmt.concat(m.angle + ", ");
//              if (p != null) {
//                 stmt = stmt.concat(p.x * pixelSize + ", ");
//                 stmt = stmt.concat(p.y * pixelSize + ", ");
//              }
//              else {
//                 SR.write("[WARNING] " + Mark.getName(m.type) + " mark '" + m.value + "' on root '"+ r.getRootID() + "' is past the end of root.");
//                 stmt = stmt.concat(" 0.0, 0.0, ");
//              }
                stmt = stmt.concat(r.isChild() + ", ");
                stmt = stmt.concat(r.getPoAccession() + ", ");
                if (m.needsTwinPosition())
                    stmt = stmt.concat(((m.twinLPos - m.lPos) * pixelSize) + "");
                else stmt = stmt.concat(m.value);
                pw.println(stmt);
                pw.flush();
            }
            // Root end information
            stmt = imgName + ", ";
            stmt = stmt.concat(imgName + ", ");  // XD 20110629
            stmt = stmt.concat(r.getRootKey() + ", ");
            stmt = stmt.concat(r.getRootID() + ", ");
            stmt = stmt.concat("Length, ");
            stmt = stmt.concat(r.lPosPixelsToCm(r.getRootLength()) + ", ");
            stmt = stmt.concat("0.0, 0.0, ");
            stmt = stmt.concat(r.lastNode.x * pixelSize + ", ");
            stmt = stmt.concat(r.lastNode.y * pixelSize + ", ");
            stmt = stmt.concat(r.isChild() + ", ");
            stmt = stmt.concat(r.getPoAccession() + ", ");
            stmt = stmt.concat(imgName);
            pw.println(stmt);
            if (last) pw.flush();
        }
        IJ.log("CSV data transfer completed for 'Marks'.");
    }

    /**
     * Clean wild rsml.
     *
     * @return the int
     */
    public int cleanWildRsml() {//TODO : currently, does not even verify  if primary was there before the lateral !
        int stamp = 0;
        ArrayList<Root> prim = new ArrayList<Root>();
        ArrayList<Root> lat = new ArrayList<Root>();
        for (Root r : rootList) {
            if (r.order > 1) lat.add(r);
            else prim.add(r);
            //		   if(r.childList!=null && r.childList.size()>0) { prim.add(r);stamp+=1000000;}
//		   else { lat.add(r);stamp+=1000;}
        }
        for (Root rLat : lat) {
            Node nL = rLat.firstNode;

            Root bestPrim = null;
            double distBest = 1E10;
            for (Root rPrim : prim) {
                Node nP = rPrim.firstNode;
                while (nP != null) {
                    double dist = Node.distanceBetween(nP, nL);
                    if (dist < distBest) {
                        distBest = dist;
                        bestPrim = rPrim;
                    }
                    nP = nP.child;
                }
            }
            if (!(bestPrim == rLat.parent)) {//Proceed to good attachment
                stamp++;
                ArrayList<Root> newChi = new ArrayList<Root>();
                //String s = rLat.toString();
                IJ.log("Process.ing rLat=" + rLat);

                //s = rLat.parent.toString();
                IJ.log("ChildList=" + rLat.parent.childList);
                IJ.log("Processing rLat.parent=" + rLat.parent);
                for (Root rOld : rLat.parent.childList) {
                    IJ.log(" - Child : " + rOld);
                    if (rOld != rLat) newChi.add(rOld);
                }
                rLat.parent.childList = newChi;
                Objects.requireNonNull(bestPrim).attachChild(rLat);
                rLat.attachParent(bestPrim);
            }
        }
        return stamp;
    }

    public void setHoursFromPph(double[] hours) {
        hoursCorrespondingToTimePoints = hours;
    }

    /**
     * Send the root data to the ResulsTable rt.
     *
     * @param rt   the rt
     * @param name the name
     */
    public void sendRootData(ResultsTable rt, String name) {

        for (Root r : rootList) {
            if (!r.validate()) continue; // corrupted Root instance
            rt.incrementCounter();
            rt.addValue("image", name);
            rt.addValue("root_name", r.getRootID());
            rt.addValue("root", r.getRootKey());
            rt.addValue("length", r.lPosPixelsToCm(r.getRootLength()));
            rt.addValue("surface", r.getRootSurface());
            rt.addValue("volume", r.getRootVolume());
            rt.addValue("convexhull_area", r.getConvexHullArea());
            rt.addValue("diameter", r.getAVGDiameter());
            rt.addValue("root_order", r.order); // old isChild
            rt.addValue("root_ontology", r.getPoAccession());
            rt.addValue("parent_name", r.getParentName());
            if (r.getParent() != null) rt.addValue("parent", r.getParent().getRootKey());
            else rt.addValue("parent", "-1");
            rt.addValue("insertion_position", r.lPosPixelsToCm(r.getDistanceFromBase()));
            rt.addValue("insertion_angle", r.getInsertAngl() * (180 / Math.PI));
            rt.addValue("n_child", r.childList.size());
            rt.addValue("child_density", r.getChildDensity());
            if (r.firstChild != null) {
                rt.addValue("first_child", r.getFirstChild().getRootKey());
                rt.addValue("insertion_first_child", r.lPosPixelsToCm(r.getFirstChild().getDistanceFromBase()));
            } else {
                rt.addValue("first_child", "null");
                rt.addValue("insertion_first_child", "null");

            }
            if (r.lastChild != null) {
                rt.addValue("last_child", r.getLastChild().getRootKey());
                rt.addValue("insertion_last_child", r.lPosPixelsToCm(r.getLastChild().getDistanceFromBase()));
            } else {
                rt.addValue("last_child", "null");
                rt.addValue("insertion_last_child", "null");
            }
        }
    }

    /**
     * Compute speed vectors.
     *
     * @param deltaBefAfter the delta bef after
     */
    public void computeSpeedVectors(double deltaBefAfter) {
        for (Root r : rootList) r.computeSpeedVectors(deltaBefAfter, deltaBefAfter, false);
    }

    /**
     * Send the node data to the Result Table.
     *
     * @param rt   the rt
     * @param name the name
     */
    public void sendNodeData(ResultsTable rt, String name) {

        for (Root r : rootList) {
            if (!r.validate()) continue; // corrupted Root instance
            Node n = r.firstNode;
            do {
                rt.incrementCounter();
                rt.addValue("image", name);
                rt.addValue("root", r.getRootKey());
                rt.addValue("root_name", r.getRootID());
                rt.addValue("x", n.x * pixelSize);
                rt.addValue("y", n.y * pixelSize);
                rt.addValue("theta", n.theta);
                rt.addValue("diameter", n.diameter * pixelSize);
                rt.addValue("distance_from_base", n.cLength * pixelSize);
                rt.addValue("distance_from_apex", (r.getRootLength() - n.cLength) * pixelSize);
                rt.addValue("root_order", r.order); // old isChild
                rt.addValue("root_ontology", r.getPoAccession());
            } while ((n = n.child) != null);
        }
    }

    /**
     * Send the image data to the Result Table.
     *
     * @param rt   the rt
     * @param name the name
     */
    public void sendImageData(ResultsTable rt, String name) {

        rt.incrementCounter();
        rt.addValue("image", name);
        rt.addValue("tot_root_length", getTotalRLength());
        //rt.addValue("convexhull_area",getConvexHullArea());
        // Primary roots
        rt.addValue("n_primary", getNPRoot());
        rt.addValue("tot_prim_length", getTotalPRLength());
        rt.addValue("mean_prim_length", getAvgPRLength());
        rt.addValue("mean_prim_diameter", getAvgPRDiam());
        rt.addValue("mean_lat_density", getAvgChildDens());
        // Secondary roots
        rt.addValue("n_laterals", getNSRoot());
        rt.addValue("tot_lat_length", getTotalSRLength());
        rt.addValue("mean_lat_length", getAvgSRLength());
        rt.addValue("mean_lat_diameter", getAvgSRDiam());
        rt.addValue("mean_lat_angle", this.getAvgSRInsAng());
    }

    /**
     * Get a given root in the root list.
     *
     * @param i the i
     * @return the root
     */
    public Root getRoot(int i) {
        if (i < getNRoot()) return rootList.get(i);
        else return null;
    }

    /**
     * Get the total number of roots.
     *
     * @return the n root
     */
    public int getNRoot() {
        return rootList.size();
    }

    /**
     * Ge tthe DPI value for the image.
     *
     * @return the dpi
     */
    public float getDPI() {
        return dpi;
    }

    /**
     * Set the DPI avlue for the image.
     *
     * @param dpi the new dpi
     */
    public void setDPI(float dpi) {
        this.dpi = dpi;
        pixelSize = (float) (2.54 / dpi);
        for (Root root : rootList) root.setDPI(dpi);
    }

    /**
     * Remove all the roots from the root list.
     */
    public void clearDatafile() {
        rootList.clear();
    }

    /**
     * Log read error.
     */
    private void logReadError() {
        IJ.log("An I/O error occured while attemping to read the datafile.");
        IJ.log("A new empty datafile will be created.");
        IJ.log("Backup versions of the datafile, if any, can be loaded");
        IJ.log("using the File -> Use backup datafile menu item.");
    }

    /**
     * Read RSML.
     *
     * @param f the f
     */
    // still in use ? Yes, peviously called when making Blockmatching registration setup
    public void readRSML(String f) {

        // Choose the datafile
        String fPath = f;

        if (f == null) {
            clearDatafile();
            JFileChooser fc = new JFileChooser(new File(directory));
            fc.setFileFilter(datafileFilterRSML);
            if (fc.showDialog(null, "Select Root System Markup Datafile") == JFileChooser.CANCEL_OPTION) return;
            fPath = fc.getSelectedFile().getAbsolutePath();
        }

        nextAutoRootID = 0;


        Document documentDOM = null;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            File file = new File(fPath);
            documentDOM = builder.parse(file);
        } catch (SAXException | ParserConfigurationException | IOException sxe) {
            logReadError();
            return;
        }

        documentDOM.normalize();

        org.w3c.dom.Node nodeDOM = documentDOM.getFirstChild();

        if (!nodeDOM.getNodeName().equals("rsml")) {
            logReadError();
            return;
        }

        String origin = "smartroot";
        // Navigate the whole document
        nodeDOM = nodeDOM.getFirstChild();
        while (nodeDOM != null) {

            String nName = nodeDOM.getNodeName();

            // Get and process the metadata
            if (nName.equals("metadata")) {
                org.w3c.dom.Node nodeMeta = nodeDOM.getFirstChild();
                String unit = "cm";
                float res = 1.0f;
                while (nodeMeta != null) {
                    String metaName = nodeMeta.getNodeName();
                    // Get the image resolution
                    if (metaName.equals("unit")) unit = nodeMeta.getFirstChild().getNodeValue();
                    if (metaName.equals("resolution")) res = Float.parseFloat(nodeMeta.getFirstChild().getNodeValue());
                    if (metaName.equals("file-key")) datafileKey = nodeMeta.getFirstChild().getNodeValue();
                    if (metaName.equals("software")) origin = nodeMeta.getFirstChild().getNodeValue();
                    nodeMeta = nodeMeta.getNextSibling();
                }
                dpi = getDPI(unit, res);
                IJ.log("resolution = " + dpi);
                pixelSize = 2.54f / dpi;
            }

            // Get the plant
            if (nName.equals("scene")) {
                org.w3c.dom.Node nodeScene = nodeDOM.getFirstChild();
                while (nodeScene != null) {
                    String sceneName = nodeScene.getNodeName();

                    if (sceneName.equals("plant")) {
                        org.w3c.dom.Node nodeRoot = nodeScene.getFirstChild();
                        while (nodeRoot != null) {
                            String rootName = nodeRoot.getNodeName();

                            // Get the Roots
                            if (rootName.equals("root")) {
                                String childRootID = nodeDOM.getAttributes().getNamedItem("label").getNodeValue();
                                new Root(dpi, childRootID, nodeRoot, true, null, this, origin);
                            }
                            nodeRoot = nodeRoot.getNextSibling();
                        }
                    }
                    nodeScene = nodeScene.getNextSibling();
                }
            }
            nodeDOM = nodeDOM.getNextSibling();
        }
        IJ.log(rootList.size() + " root(s) were created");
        setDPI(dpi);
    }

    /**
     * Read common datafile structure.
     * RSML 2D
     *
     * @param f the f
     */
    public void readRSMLNew(String f) {
        // Choose the datafile
        String fPath = f;

        if (f == null) {
            clearDatafile();
            JFileChooser fc = new JFileChooser(new File(directory));
            fc.setFileFilter(datafileFilterRSML);
            if (fc.showDialog(null, "Select Root System Markup Datafile") == JFileChooser.CANCEL_OPTION) return;
            fPath = fc.getSelectedFile().getAbsolutePath();
        }

        nextAutoRootID = 0;


        Document documentDOM = null;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        try {
            documentDOM = factory.newDocumentBuilder().parse(new File(fPath));
        } catch (SAXException | ParserConfigurationException | IOException sxe) {
            logReadError();
            return;
        }

        documentDOM.normalize();

        org.w3c.dom.Node nodeDOM = documentDOM.getFirstChild();

        if (!nodeDOM.getNodeName().equals("rsml")) {
            logReadError();
            return;
        }

        String origin = "smartroot";
        // Navigate the whole document
        nodeDOM = nodeDOM.getFirstChild();
        while (nodeDOM != null) {

            String nName = nodeDOM.getNodeName();

            // Get and process the metadata
            if (nName.equals("metadata")) {
                org.w3c.dom.Node nodeMeta = nodeDOM.getFirstChild();
                String unit = "cm";
                float res = 1.0f;
                while (nodeMeta != null) {
                    String metaName = nodeMeta.getNodeName();
                    // Get the image resolution
                    if (metaName.equals("unit")) unit = nodeMeta.getFirstChild().getNodeValue();
                    if (metaName.equals("resolution")) res = Float.parseFloat(nodeMeta.getFirstChild().getNodeValue());
                    if (metaName.equals("file-key")) datafileKey = nodeMeta.getFirstChild().getNodeValue();
                    if (metaName.equals("software")) origin = nodeMeta.getFirstChild().getNodeValue();
                    nodeMeta = nodeMeta.getNextSibling();
                }
                dpi = getDPI(unit, res);
                //SR.write("resolution = "+dpi);
                pixelSize = 2.54f / dpi;
            }

            // Get the plant
            if (nName.equals("scene")) {
                org.w3c.dom.Node nodeScene = nodeDOM.getFirstChild();
                while (nodeScene != null) {
                    String sceneName = nodeScene.getNodeName();

                    if (sceneName.equals("plant")) {
                        org.w3c.dom.Node nodeRoot = nodeScene.getFirstChild();
                        while (nodeRoot != null) {
                            String rootName = nodeRoot.getNodeName();

                            // Get the Roots
                            if (rootName.equals("root")) {
                                String childRootID = nodeDOM.getAttributes().getNamedItem("label").getNodeValue();
                                System.out.println("nodeRoot=" + nodeRoot);
                                System.out.println("origin=" + origin);
                                System.out.println("dpi=" + dpi);
                                new Root(dpi, childRootID, nodeRoot, true, null, this, origin);
                            }
                            nodeRoot = nodeRoot.getNextSibling();
                        }
                    }
                    nodeScene = nodeScene.getNextSibling();
                }
            }
            nodeDOM = nodeDOM.getNextSibling();
        }
        IJ.log(rootList.size() + " root(s) were created");
        setDPI(dpi);
    }

    /**
     * Read RSML.
     *
     * @param f              the f
     * @param timeLapseModel the time lapse model
     */
    public void readRSML(String f, boolean timeLapseModel) {

        // Choose the datafile
        String fPath = f;

        if (f == null) {
            clearDatafile();
            JFileChooser fc = new JFileChooser(new File(directory));
            fc.setFileFilter(datafileFilterRSML);
            if (fc.showDialog(null, "Select Root System Markup Datafile") == JFileChooser.CANCEL_OPTION) return;
            fPath = fc.getSelectedFile().getAbsolutePath();
        }

        nextAutoRootID = 0;


        Document documentDOM = null;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            documentDOM = builder.parse(new File(fPath));
        } catch (SAXException | ParserConfigurationException | IOException sxe) {
            logReadError();
            return;
        }

        documentDOM.normalize();

        org.w3c.dom.Node nodeDOM = documentDOM.getFirstChild();

        if (!nodeDOM.getNodeName().equals("rsml")) {
            logReadError();
            return;
        }

        String origin = "smartroot";
        // Navigate the whole document
        nodeDOM = nodeDOM.getFirstChild();
        while (nodeDOM != null) {

            String nName = nodeDOM.getNodeName();

            // Get and process the metadata
            if (nName.equals("metadata")) {
                org.w3c.dom.Node nodeMeta = nodeDOM.getFirstChild();
                String unit = "cm";
                float res = 1.0f;
                while (nodeMeta != null) {
                    String metaName = nodeMeta.getNodeName();
                    // Get the image resolution
                    if (metaName.equals("unit")) unit = nodeMeta.getFirstChild().getNodeValue();
                    if (metaName.equals("resolution")) res = Float.valueOf(nodeMeta.getFirstChild().getNodeValue());
                    if (metaName.equals("file-key")) datafileKey = nodeMeta.getFirstChild().getNodeValue();
                    if (metaName.equals("software")) origin = nodeMeta.getFirstChild().getNodeValue();
                    nodeMeta = nodeMeta.getNextSibling();
                }
                dpi = getDPI(unit, res);
                //SR.write("resolution = "+dpi);
                pixelSize = 2.54f / dpi;
            }

            // Get the plant
            if (nName.equals("scene")) {
                org.w3c.dom.Node nodeScene = nodeDOM.getFirstChild();
                while (nodeScene != null) {
                    String sceneName = nodeScene.getNodeName();

                    if (sceneName.equals("plant")) {
                        org.w3c.dom.Node nodeRoot = nodeScene.getFirstChild();
                        while (nodeRoot != null) {
                            String rootName = nodeRoot.getNodeName();

                            // Get the Roots
                            if (rootName.equals("root")) {
                                new Root(dpi, nodeRoot, true, null, this, origin, timeLapseModel);
                            }
                            nodeRoot = nodeRoot.getNextSibling();
                        }
                    }
                    nodeScene = nodeScene.getNextSibling();
                }
            }
            nodeDOM = nodeDOM.getNextSibling();
        }
        IJ.log(rootList.size() + " root(s) were created");
        setDPI(dpi);
    }

    /**
     * Write RSML.
     *
     * @param f      the f
     * @param imgExt the img ext
     */
    public void writeRSML(String f, String imgExt) {
        String fileName = VitimageUtils.withoutExtension(new File(f).getName());
        // String fPath = f;
        nextAutoRootID = 0;
        Document dom = null;
        Element re, me, met, mett, sce, plant, rootPrim, rootLat, geomPrim, polyPrim, geomLat, polyLat, pt;

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            dom = builder.newDocument();
        } catch (ParserConfigurationException pce) {
            logReadError();
            return;
        }

        re = dom.createElement("rsml");

        //Build and add metadata
        me = dom.createElement("metadata");
        met = dom.createElement("version");
        met.setTextContent("1");
        me.appendChild(met);
        met = dom.createElement("unit");
        met.setTextContent("pixel");
        me.appendChild(met);
        met = dom.createElement("resolution");
        met.setTextContent("1");
        me.appendChild(met);
        met = dom.createElement("last-modified");
        met.setTextContent(new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date()));
        me.appendChild(met);
        met = dom.createElement("software");
        met.setTextContent("Fijiyama ");
        me.appendChild(met);
        met = dom.createElement("user");
        met.setTextContent("John Doe ");
        me.appendChild(met);
        met = dom.createElement("file-key");
        met.setTextContent(fileName);
        me.appendChild(met);
        met = dom.createElement("image");
        mett = dom.createElement("label");
        mett.setTextContent(fileName + imgExt);
        met.appendChild(mett);
        mett = dom.createElement("sha256");
        mett.setTextContent("Nothing there");
        met.appendChild(mett);
        me.appendChild(met);
        re.appendChild(me);

        //Build and add scene
        sce = dom.createElement("scene");

        //Build and add plant 1
        int incrPlant = 0;
        for (int indexPrim = 0; indexPrim < rootList.size(); indexPrim++) {
            Root r = rootList.get(indexPrim);
            if (r.isChild == 1) continue;
            ++incrPlant;
            plant = dom.createElement("plant");
            plant.setAttribute("ID", "" + (incrPlant));
            plant.setAttribute("label", "");
            rootPrim = dom.createElement("root");
            rootPrim.setAttribute("ID", (incrPlant) + ".1");
            rootPrim.setAttribute("label", "");
            geomPrim = dom.createElement("geometry");
            polyPrim = dom.createElement("polyline");
            double[][] coord = getRootCoordinates(r);
            for (int i = 0; i < coord.length; i++) {
                pt = dom.createElement("point");
                pt.setAttribute("x", "" + (coord[i][0]));
                pt.setAttribute("y", "" + (coord[i][1]));
                polyPrim.appendChild(pt);
            }
            geomPrim.appendChild(polyPrim);
            rootPrim.appendChild(geomPrim);

            //Ajouter les enfants
            int incrLat = 0;
            for (Root rLat : r.childList) {
                incrLat++;
                rootLat = dom.createElement("root");
                rootLat.setAttribute("ID", (incrPlant) + ".1." + incrLat);
                rootLat.setAttribute("label", "");
                geomLat = dom.createElement("geometry");
                polyLat = dom.createElement("polyline");
                coord = getRootCoordinates(rLat);
                for (int i = 0; i < coord.length; i++) {
                    pt = dom.createElement("point");
                    pt.setAttribute("x", "" + (coord[i][0]));
                    pt.setAttribute("y", "" + (coord[i][1]));
                    polyLat.appendChild(pt);
                }
                geomLat.appendChild(polyLat);
                rootLat.appendChild(geomLat);
                rootPrim.appendChild(rootLat);
            }
            plant.appendChild(rootPrim);
            sce.appendChild(plant);
        }
        re.appendChild(sce);
        dom.appendChild(re);


        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer;
        try {
            transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            FileOutputStream outStream = new FileOutputStream(new File(f));
            transformer.transform(new DOMSource(dom), new StreamResult(outStream));
            outStream.close();
        } catch (TransformerException eee) {
            eee.printStackTrace();
        } catch (Exception ee) {
            ee.printStackTrace();
        }
    }

    /**
     * Gets the root coordinates.
     *
     * @param r the r
     * @return the root coordinates
     */
    public double[][] getRootCoordinates(Root r) {
        Node n = r.firstNode;
        int incr = 1;
        while (n.child != null) {
            n = n.child;
            incr++;
        }
        double[][] ret = new double[incr][2];
        incr = 0;
        n = r.firstNode;
        ret[0] = new double[]{n.x, n.y, n.vx, n.vy, n.diameter, n.birthTime, n.birthTimeHours};
        while (n.child != null) {
            n = n.child;
            incr++;
            ret[incr] = new double[]{n.x, n.y, n.vx, n.vy, n.diameter, n.birthTime, n.birthTimeHours};
        }
        return ret;
    }

    /**
     * Ge the directory containing the image.
     *
     * @return the directory
     */
    public String getDirectory() {
        return directory;
    }

    /**
     * Get the closest root from the base of a given root .
     */

    public void cleanNegativeTh() {
        for (Root r : rootList) {
            r.cleanNegativeTh();
        }
    }

    public int resampleFlyingRoots() {
        int stamp = 0;
        for (Root r : rootList) {
            try {
                stamp += r.resampleFlyingPoints(hoursCorrespondingToTimePoints);
            } catch (Exception e) {
                IJ.log("Error in resampling root " + r);
            }
        }
        return stamp;
    }

    public void removeInterpolatedNodes() {
        IJ.log("Removing interpolated nodes");
        int tot = 0;
        int rem = 0;
        for (Root r : rootList) {
            IJ.log("Processing root " + r);
            tot++;
            boolean rootkept = r.removeInterpolatedNodes();
            if (!rootkept) {
                rem++;
                rootList.remove(r);
            }
            IJ.log("Ok");
        }
        IJ.log(" after removing, " + rem + " / " + tot + " pathological roots have been wiped");
    }

    public void changeTimeBasis(double timestep, int N) {
        hoursCorrespondingToTimePoints = new double[N];
        for (int i = 0; i < N; i++) {
            hoursCorrespondingToTimePoints[i] = timestep * i;
        }
        for (Root r : rootList) {
            r.changeTimeBasis(timestep, N);
        }
    }

    /**
     * Gets the closest node.
     *
     * @param pt the pt
     * @return the closest node
     */
    public Object[] getClosestNode(Point3d pt) {
        double x = pt.x;
        double y = pt.y;
        double distMin = 1E18;
        Node nodeMin = null;
        Root rootMin = null;
        for (Root r : rootList) {
            Node n = r.firstNode;
            while (n != null) {
                double dist = Math.sqrt((x - n.x) * (x - n.x) + (y - n.y) * (y - n.y));
                if (dist < distMin && n.birthTime <= pt.z) {
                    distMin = dist;
                    rootMin = r;
                    nodeMin = n;
                }
                n = n.child;
            }
        }
        return new Object[]{nodeMin, rootMin};
    }

    /**
     * Gets the closest node in primary.
     *
     * @param pt the pt
     * @return the closest node in primary
     * <p>
     * public Object[] getClosestNodeInPrimary(Point3d pt) {
     * <p>
     * double x = pt.x;
     * double y = pt.y;
     * double distMin = 1E18;
     * Node nodeMin = null;
     * Root rootMin = null;
     * for (Root r : rootList) {
     * //if (r.childList == null || r.childList.isEmpty()) continue;
     * if (r.order > 1) continue;
     * Node n = r.firstNode;
     * while (n != null) {
     * double dist = Math.sqrt((x - n.x) * (x - n.x) + (y - n.y) * (y - n.y));
     * if (dist < distMin && n.birthTime <= pt.z) {
     * distMin = dist;
     * rootMin = r;
     * nodeMin = n;
     * }
     * n = n.child;
     * }
     * }
     * return new Object[]{nodeMin, rootMin};
     * }
     */
    public Object[] getClosestNodeInPrimary(Point3d pt) {
        double x = pt.x;
        double y = pt.y;
        double distMin = Double.MAX_VALUE;
        Node nodeMin = null;
        Root rootMin = null;
        for (Root r : rootList) {
            Node n = r.firstNode;
            while (n != null) {
                double dist = Math.sqrt((x - n.x) * (x - n.x) + (y - n.y) * (y - n.y));
                if (dist < distMin && n.birthTime <= pt.z && r.order == 1) {
                    distMin = dist;
                    rootMin = r;
                    nodeMin = n;
                }
                n = n.child;
            }
        }
        return new Object[]{nodeMin, rootMin};
    }

    public Object[] getClosesNodeParentOrder(Point3d pt, Root currentRoot) {
        double x = pt.x;
        double y = pt.y;
        double distMin = Double.MAX_VALUE;
        if (currentRoot.order <= 2) return getClosestNodeInPrimary(pt);
        int parentOrder = currentRoot.order - 1;
        Node nodeMin = null;
        Root rootMin = null;
        for (Root r : rootList) {
            if (r.order != parentOrder) continue;
            Node n = r.firstNode;
            while (n != null) {
                double dist = Math.sqrt((x - n.x) * (x - n.x) + (y - n.y) * (y - n.y));
                if (dist < distMin && n.birthTime <= pt.z) {
                    distMin = dist;
                    rootMin = r;
                    nodeMin = n;
                }
                n = n.child;
            }
        }
        return new Object[]{nodeMin, rootMin};
    }

    public Object[] getClosestNodeChildOrder(Point3d pt, Root currentRoot) {
        double x = pt.x;
        double y = pt.y;
        double distMin = Double.MAX_VALUE;
        if (currentRoot.order < 1) return null;
        int childOrder = currentRoot.order + 1;
        Node nodeMin = null;
        Root rootMin = null;
        for (Root r : rootList) {
            if (r.order != childOrder) continue;
            Node n = r.firstNode;
            while (n != null) {
                double dist = Math.sqrt((x - n.x) * (x - n.x) + (y - n.y) * (y - n.y));
                if (dist < distMin && n.birthTime <= pt.z) {
                    distMin = dist;
                    rootMin = r;
                    nodeMin = n;
                }
                n = n.child;
            }
        }
        return new Object[]{nodeMin, rootMin};
    }

    public Object[] getClosesNodeInCurrentRoot(Point3d pt, Root currentRoot) {
        double x = pt.x;
        double y = pt.y;
        double distMin = 1E18;
        Node nodeMin = null;
        Root rootMin = null;
        Node n = currentRoot.firstNode;
        while (n != null) {
            double dist = Math.sqrt((x - n.x) * (x - n.x) + (y - n.y) * (y - n.y));
            if (dist < distMin && n.birthTime <= pt.z) {
                distMin = dist;
                rootMin = currentRoot;
                nodeMin = n;
            }
            n = n.child;
        }
        return new Object[]{nodeMin, rootMin};
    }

    public Object[] getClosesNodeInCurrentRoot(Point3d pt, Root currentRoot, List<Node> notTheseNode) {
        double x = pt.x;
        double y = pt.y;
        double distMin = 1E18;
        Node nodeMin = null;
        Root rootMin = null;
        Node n = currentRoot.firstNode;
        while (n != null) {
            double dist = Math.sqrt((x - n.x) * (x - n.x) + (y - n.y) * (y - n.y));
            if (dist < distMin && n.birthTime <= pt.z && !notTheseNode.contains(n)) {
                distMin = dist;
                rootMin = currentRoot;
                nodeMin = n;
            }
            n = n.child;
        }
        return new Object[]{nodeMin, rootMin};
    }

    /**
     * Gets the closest root.
     *
     * @param r the r
     * @return the closest root
     */
    public Root getClosestRoot(Root r) {
        Node n = r.firstNode;
        int ls = rootList.size();
        if (ls == 1) return null;
        Root rp;
        Root rpFinal = null;
        float dist;
        float distMin = 1000000.0f;

        for (Root root : rootList) {
            rp = root;
            if (rp.getRootKey().equals(r.getRootKey())) continue;
            Node np = rp.firstNode;
            dist = (float) Point2D.distance(n.x, n.y, np.x, np.y);
            if (dist < distMin) {
                distMin = dist;
                rpFinal = rp;
            }
            while (np.child != null) {
                np = np.child;
                dist = (float) Point2D.distance(n.x, n.y, np.x, np.y);
                if (dist < distMin) {
                    distMin = dist;
                    rpFinal = rp;
                }
            }
        }
        return rpFinal;
    }

    /**
     * Attach c to p.
     *
     * @param p the parent root
     * @param c the child root
     */
    public void setParent(Root p, Root c) {
        c.attachParent(p);
        p.attachChild(c);
    }

    /**
     * Get the number of primary roots.
     *
     * @return the number of primary roots
     */
    public int getNPRoot() {
        int n = 0;
        Root r;
        for (Root root : rootList) {
            r = root;
            if (r.isChild() == 0) n++;
        }
        return n;
    }

    /**
     * Gets the NP root.
     *
     * @param t the t
     * @return the NP root
     */
    public int getNPRoot(double t) {
        int n = 0;
        Root r;
        for (Root root : rootList) {
            r = root;
            if (r.isChild() == 0 && r.firstNode.birthTime <= t) n++;
        }
        return n;
    }

    /**
     * Get the number of secondary roots.
     *
     * @return the number of secondary roots
     */
    public int getNSRoot() {
        int n = 0;
        Root r;
        for (Root root : rootList) {
            r = root;
            if (r.isChild() != 0) n++;
        }
        return n;
    }

    /**
     * Gets the NS root.
     *
     * @param t the t
     * @return the NS root
     */
    public int getNSRoot(double t) {
        int n = 0;
        Root r;
        for (Root root : rootList) {
            r = root;
            if (r.isChild() != 0 && r.firstNode.birthTime <= t) n++;
        }
        return n;
    }

    /**
     * Get the average length of secondary roots.
     *
     * @return the average length of secondary roots
     */
    public float getAvgSRLength() {
        float n = 0;
        int m = 0;
        Root r;
        for (Root root : rootList) {
            r = root;
            if (r.isChild() != 0) {
                n += r.getRootLength();
                m++;
            }
        }
        return n / m * pixelSize;
    }

    /**
     * Get average length of primary roots.
     *
     * @return the average length of primary roots
     */
    public float getAvgPRLength() {
        float n = 0;
        int m = 0;
        Root r;
        for (Root root : rootList) {
            r = root;
            if (r.isChild() == 0) {
                n += r.getRootLength();
                m++;
            }
        }
        return n / m * pixelSize;
    }

    /**
     * Get the total root length.
     *
     * @return the totla root length
     */
    public float getTotalRLength() {
        float n = 0;
        Root r;
        for (Root root : rootList) {
            r = root;
            n += r.getRootLength();
        }
        return n * pixelSize;

    }

    /**
     * Get the average length of all roots.
     *
     * @return the average length of all roots
     */
    public float getAvgRLength() {
        float n = 0;
        int m = 0;
        Root r;
        for (Root root : rootList) {
            r = root;
            n += r.getRootLength();
            m++;
        }
        return n / m * pixelSize;
    }

    /**
     * Get the average diameter of secondary roots.
     *
     * @return the average diameter of secondary roots
     */
    public float getAvgSRDiam() {
        float n = 0;
        int m = 0;
        Root r;
        for (Root root : rootList) {
            r = root;
            if (r.isChild() != 0) {
                Node node = r.firstNode;
                n += node.diameter;
                m++;
                while (node.child != null) {
                    node = node.child;
                    n += node.diameter;
                    m++;
                }
            }
        }
        return n / m * pixelSize;
    }

    /**
     * Get the average insertion angle of secondary roots.
     *
     * @return the average insertion angle of secondary roots
     */
    public float getAvgSRInsAng() {
        float n = 0;
        int m = 0;
        Root r;
        for (Root root : rootList) {
            r = root;
            if (r.isChild() != 0) {
                n += (float) (r.getInsertAngl() * (180 / Math.PI));
                m++;
            }
        }
        return n / m;
    }

    /**
     * Get the total length of the primary roots.
     *
     * @return the total length of the primary roots
     */
    public float getTotalPRLength() {
        int l = 0;
        Root r;
        for (Root root : rootList) {
            r = root;
            if (r.isChild() == 0) {
                l += (int) r.lPosPixelsToCm(r.getRootLength());
            }
        }
        return l;
    }

    /**
     * Gets the total PR length.
     *
     * @param t the t
     * @return the total PR length
     */
    public float getTotalPRLength(double t) {
        int l = 0;
        Root r;
        for (Root root : rootList) {
            r = root;
            if (r.isChild() == 0) {
                l += (int) r.computeRootLength(t);
            }
        }
        return l;
    }

    /**
     * Sets the plant numbers.
     */
    public void setPlantNumbers() {
        ArrayList<Root> listTemp = new ArrayList<Root>();
        //Set stamp to primaries
        for (Root r : rootList) if (r.isChild() <= 0) listTemp.add(r);
        Collections.sort(listTemp);
        for (int i = 0; i < listTemp.size(); i++) listTemp.get(i).plantNumber = i;
        this.nPlants = listTemp.size();

        //Set stamp to laterals
        for (Root r : rootList) if (r.isChild() > 0) r.plantNumber = r.parent.plantNumber;
    }

    /**
     * Gets the PR length.
     *
     * @param t the t
     * @return the PR length
     */
    public float getPRLength(double t) {
        int l = 0;
        Root r;
        for (Root root : rootList) {
            r = root;
            System.out.println("New : " + r);
            System.out.println("Got :");
            System.out.println(r.parentKey);
            System.out.println(r.rootID);
        }
        return l;
    }

    /**
     * Gets the lengths.
     *
     * @param t the t
     * @return the lengths
     */
    public double[][] getLengths(double t) {
        if (this.nPlants == 0) return null;
        double[][] lengs = new double[this.nPlants][2];
        for (Root r : rootList) {
            int index = r.plantNumber;
            lengs[index][r.order - 1] += r.computeRootLength(t);
        }
        return lengs;
    }

    /**
     * Gets the number and lenght over time serie.
     *
     * @param tMax the t max
     * @return the number and lenght over time serie
     */
    //Compute a series of statistics over root system present in image [plant number][time][stat : 0=LenPrim, 1=Nlat, 2=Llat]
    public double[][][] getNumberAndLenghtOverTimeSerie(int tMax) {
        if (this.nPlants == 0) return null;
        double[][][] lengs = new double[this.nPlants][tMax][3];
        for (int t = 1; t <= tMax; t++) {
            for (Root r : rootList) {
                int index = r.plantNumber;
                if (r.order == 1) lengs[index][t - 1][0] += r.computeRootLength(t);
                else {
                    lengs[index][t - 1][2] += r.computeRootLength(t);
                    lengs[index][t - 1][1]++;
                }
            }
        }
        return lengs;
    }

    /**
     * Nb lats per plant.
     *
     * @return the int[]
     */
    public int[] nbLatsPerPlant() {
        int[] count = new int[5];
        int maxOrder = 1;
        for (Root r : rootList) {
            maxOrder = Math.max(maxOrder, r.order);
        }
        for (int i = 2; i <= maxOrder; i++) {
            for (Root r : rootList) {
                if (r.order == i) count[r.plantNumber]++;
            }
        }
        return count;
    }

    /**
     * Gets the prim depth over time.
     *
     * @param tMax  the t max
     * @param plant the plant
     * @return the prim depth over time
     */
    public double[] getPrimDepthOverTime(int tMax, int plant) {
        double[] ret = new double[tMax];
        Arrays.fill(ret, -1000000);
        for (Root r : rootList) {
            if (r.plantNumber != plant || r.order != 1) continue;
            Node n = r.firstNode;
            float y0 = n.y;
            int curTime = 0;
            while (n.child != null) {
                n = n.child;
                curTime = (int) Math.floor(n.birthTime) - 1;
                if (curTime < 0) curTime = 0;
                ret[curTime] = n.y - y0;
            }
            for (int i = 0; i < ret.length; i++) {
                if (ret[i] <= -1000000) {
                    double lastVit = -10;
                    int j = i;
                    while (lastVit < 0 && (--j) >= 0) {
                        if (ret[j] > -1000000) {
                            lastVit = ret[j];
                        }
                    }
                    j = i;
                    while (lastVit < 0 && (++j) < ret.length) {
                        if (ret[j] > -1000000) {
                            lastVit = ret[j];
                        }
                    }
                    ret[i] = lastVit;
                }
            }
        }
        return ret;
    }

    /**
     * Gets the prim length over time.
     *
     * @param tMax  the t max
     * @param plant the plant
     * @return the ${e.g(1).rsfl()}
     */
    public double[][] getPrimLengthOverTime(int tMax, int plant) {
        double[][] ret = new double[tMax][2];
        for (Root r : rootList) {
            if (r.plantNumber != plant || r.order != 1) continue;
            Node n = r.firstNode;
            float dist = 0;
            int curTime = 0;
            Node ntmp;
            while (n.child != null) {
                ntmp = n;
                n = n.child;
                curTime = (int) Math.floor(n.birthTime) - 1;
                if (curTime < 0) curTime = 0;
                dist += Node.distanceBetween(ntmp, n);
                ret[curTime][0] = dist;
            }
            if (curTime < tMax - 1) for (int t = Math.max(curTime, 1); t < tMax; t++) ret[t][0] = ret[t - 1][0];

        }
        for (int j = 1; j < ret.length; j++) ret[j][1] = ret[j][0] - ret[j - 1][0];
        return ret;
    }

    /**
     * Gets the prim length over time.
     *
     * @param tMax  the t max
     * @param plant the plant
     * @return the ${e.g(1).rsfl()}
     */
    public double[][] getPrimLengthAndSpeedOverTime(int tMax, int plant) {
        double[][] ret = new double[tMax][3];
        for (Root r : rootList) {
            if (r.plantNumber != plant || r.order != 1) continue;
            Node n = r.firstNode;
            float dist = 0;
            int curTime = 0;
            Node ntmp;
            while (n.child != null) {
                ntmp = n;
                n = n.child;
                curTime = (int) Math.floor(n.birthTime) - 1;
                if (curTime < 0) curTime = 0;
                dist += Node.distanceBetween(ntmp, n);
                ret[curTime][0] = dist;
            }
            if (curTime < tMax - 1) for (int t = Math.max(curTime, 1); t < tMax; t++) ret[t][0] = ret[t - 1][0];

        }
        for (int j = 1; j < ret.length; j++) ret[j][1] = ret[j][0] - ret[j - 1][0];
        for (int j = 1; j < ret.length; j++) ret[j][2] = ret[j][1];
        return ret;
    }

    /**
     * Get the total lengthd of the lateral roots.
     *
     * @return the total lenght of the lateral roots
     */
    public float getTotalSRLength() {
        int l = 0;
        Root r;
        for (Root root : rootList) {
            r = root;
            if (r.isChild() > 0) {
                l += (int) r.lPosPixelsToCm(r.getRootLength());
            }
        }
        return l;
    }

    /**
     * Gets the total SR length.
     *
     * @param t the t
     * @return the total SR length
     */
    public float getTotalSRLength(double t) {
        int l = 0;
        Root r;
        for (Root root : rootList) {
            r = root;
            if (r.isChild() > 0) {
                l += (int) r.computeRootLength(t);
            }
        }
        return l;
    }

    /**
     * Get the average diameter of primary roots.
     *
     * @return the average diameter of primary roots
     */
    public float getAvgPRDiam() {
        float n = 0;
        int m = 0;
        Root r;
        for (Root root : rootList) {
            r = root;
            if (r.isChild() == 0) {
                Node node = r.firstNode;
                n += node.diameter;
                m++;
                while (node.child != null) {
                    node = node.child;
                    n += node.diameter;
                    m++;
                }
            }
        }
        return n / m * pixelSize;
    }

    /**
     * Get average interbranch distance.
     *
     * @return the average interbranch distance
     */
    public float getAvgInterBranch() {
        float iB = 0;
        Root r;
        int n = 0;
        for (Root root : rootList) {
            r = root;
            if (r.getInterBranch() != 0) {
                iB += r.getInterBranch();
                n++;
            }
        }
        return (iB / n) * pixelSize;
    }

    /**
     * Get the number of nodes of the primary roots.
     *
     * @return the number of nodes of the primary roots
     */
    public int getNPNode() {
        int m = 0;
        Root r;
        for (Root root : rootList) {
            r = root;
            if (r.isChild() == 0) {
                Node node = r.firstNode;
                m++;
                while (node.child != null) {
                    node = node.child;
                    m++;
                }
            }
        }
        return m;
    }

    /**
     * Get the number of node of the secondary roots.
     *
     * @return the number of node of the secondary roots
     */

    public int getNSNode() {
        int m = 0;
        Root r;
        for (Root root : rootList) {
            r = root;
            if (r.isChild() != 0) {
                Node node = r.firstNode;
                m++;
                while (node.child != null) {
                    node = node.child;
                    m++;
                }
            }
        }
        return m;
    }

    /**
     * Get a list of strings containing all the name of roots having children.
     *
     * @return a array of strings containing all the name of roots having children
     */
    public String[] getParentRootNameList() {
        int ind = 0;
        int c = 0;
        Root r;
        for (int i = 0; i < rootList.size(); i++) {
            r = rootList.get(i);
            if (!r.childList.isEmpty()) {
                ind++;
            }
        }
        String[] n = new String[ind];
        for (int i = 0; i < rootList.size(); i++) {
            r = rootList.get(i);
            if (!r.childList.isEmpty()) {
                n[i - c] = r.getRootID();
            } else c++;
        }
        return n;
    }

    /**
     * Get a list of strings containing all the name of primary.
     *
     * @return an array of strings containing all the name of primary
     */
    public String[] getPrimaryRootNameList() {
        int ind = 0;
        int c = 0;
        Root r;
        for (int i = 0; i < rootList.size(); i++) {
            r = rootList.get(i);
            if (r.isChild() == 0) {
                ind++;
            }
        }
        String[] n = new String[ind];
        for (int i = 0; i < rootList.size(); i++) {
            r = rootList.get(i);
            if (r.isChild() == 0) {
                n[i - c] = r.getRootID();
            } else c++;
        }
        return n;
    }

    /**
     * Gets the primary roots.
     *
     * @return the primary roots
     */
    public Root[] getPrimaryRoots() {
        int ind = 0;
        int incr = 0;
        Root r;
        for (int i = 0; i < rootList.size(); i++) {
            r = rootList.get(i);
            if (r.isChild() == 0) {
                ind++;
            }
        }
        Root[] n = new Root[ind];
        for (int i = 0; i < rootList.size(); i++) {
            r = rootList.get(i);
            if (r.isChild() == 0) {
                n[incr++] = r;
            }
        }
        return n;

    }

    /**
     * Get the average child density of all the parent roots of the image.
     *
     * @return the average child density of all the parent roots of the image
     */
    public float getAvgChildDens() {
        float cd = 0;
        int n = 0;
        Root r;
        for (int i = 0; i < rootList.size(); i++) {
            r = rootList.get(i);
            if (r.getChildDensity() != 0) {
                cd += r.getChildDensity();
                n++;
            }
        }
        return cd / n;
    }

    /**
     * Return the image name.
     *
     * @return the string
     */
    public String toString() {
        return this.imgName;
    }

    /**
     * Get the center of the tracing.
     *
     * @return the center
     */
    public float[] getCenter() {
        float[] coord = new float[2];

        // Get x
        float min = 1e5f, max = 0;
        Root r;
        Node n;
        for (int i = 0; i < rootList.size(); i++) {
            r = rootList.get(i);
            n = r.firstNode;
            while (n.child != null) {
                if (n.x < min) min = n.x;
                if (n.x > max) max = n.x;
                n = n.child;
            }
        }
        coord[0] = min + ((max - min) / 2);


        // Get y
        min = 1e5f;
        max = 0;
        for (int i = 0; i < rootList.size(); i++) {
            r = rootList.get(i);
            n = r.firstNode;
            while (n.child != null) {
                if (n.y < min) min = n.y;
                if (n.y > max) max = n.y;
                n = n.child;
            }
        }
        coord[1] = min + ((max - min) / 2);


        return coord;
    }

    /**
     * Get the widht of the tracing.
     *
     * @return the min Y
     */
    public int getMinY() {
        float min = 1e5f;
        Root r;
        Node n;
        for (Root root : rootList) {
            r = root;
            n = r.firstNode;
            while (n.child != null) {
                if (n.y < min) min = n.y;
                n = n.child;
            }
        }
        return (int) min;
    }

    /**
     * Get the widht of the tracing.
     *
     * @return the min X
     */
    public int getMinX() {
        float min = 1e5f;
        Root r;
        Node n;
        for (Root root : rootList) {
            r = root;
            n = r.firstNode;
            while (n.child != null) {
                if (n.x < min) min = n.x;
                n = n.child;
            }
        }
        return (int) min;
    }

    /**
     * Get the widht of the tracing.
     *
     * @param add the add
     * @return the width
     */
    public int getWidth(boolean add) {
        float min = 1e5f, max = 0;
        Root r;
        Node n;
        for (Root root : rootList) {
            r = root;
            n = r.firstNode;
            while (n.child != null) {
                if (n.x < min) min = n.x;
                if (n.x > max) max = n.x;
                n = n.child;
            }
        }
        if (add) return (int) (max + min);
        else return (int) (max - min);
    }

    /**
     * Get the height of the tracing.
     *
     * @param add the add
     * @return the height
     */
    public int getHeight(boolean add) {
        float min = 1e5f, max = 0;
        Root r;
        Node n;
        for (Root root : rootList) {
            r = root;
            n = r.firstNode;
            while (n.child != null) {
                if (n.y < min) min = n.y;
                if (n.y > max) max = n.y;
                n = n.child;
            }
        }
        if (add) return (int) (max + min);
        else return (int) (max - min);
    }

    /**
     * Refine description.
     *
     * @param maxRangeBetweenNodes the max range between nodes
     */
    public void refineDescription(int maxRangeBetweenNodes) {
        Root r;
        Node n, n1, n0;
        for (int i = 0; i < rootList.size(); i++) {
            r = rootList.get(i);
            n = r.firstNode;
            n0 = r.firstNode;
            while (n.child != null) {
                n1 = n;
                n = n.child;
                if (VitimageUtils.distance(n.x, n.y, n1.x, n1.y) > maxRangeBetweenNodes) {
                    Node nPlus = new Node((float) (n.x * 0.5 + n1.x * 0.5), (float) (n.y * 0.5 + n1.y * 0.5), n1, true);
                    nPlus.child = n;
                    n = n0;
                }
            }
        }

    }

    /**
     * Distance.
     *
     * @param n1 the n 1
     * @param n2 the n 2
     * @return the double
     */
    public double distance(Node n1, Node n2) {
        return VitimageUtils.distance(n1.x, n1.y, n2.x, n2.y);
    }

    /**
     * Attach lat to prime.
     */
    public void attachLatToPrime() {
        Root r, rPar;
        Node nPrim, nLat, newNode = null;
        for (int i = 0; i < rootList.size(); i++) {
            r = rootList.get(i);
            if (r.isChild() == 0) continue;
            rPar = r.parent;
            nLat = r.firstNode;
            nPrim = rPar.firstNode;

            double distMin = Double.MAX_VALUE;
            while (nPrim.child != null) {
                nPrim = nPrim.child;
                if (distance(nPrim, nLat) < distMin) {
                    distMin = distance(nPrim, nLat);
                    newNode = new Node(nPrim.x, nPrim.y, nLat.diameter, nLat, false);
                }
            }
            r.firstNode = newNode;
        }
    }

    /**
     * Apply transform to geometry.
     *
     * @param tr the tr
     */
    public void applyTransformToGeometry(ItkTransform tr) {
        Root r;
        Node n, n1;
        double[] coords;
        for (Root root : rootList) {
            r = root;
            n = r.firstNode;
            boolean transformed = false;

            coords = tr.transformPoint(new double[]{n.x, n.y, 0});
            n.x += (n.x - (float) coords[0]);
            n.y += (n.y - (float) coords[1]);

            while (n.child != null) {
                n = n.child;
                coords = tr.transformPoint(new double[]{n.x, n.y, 0});
                n.x += (n.x - (float) coords[0]);
                n.y += (n.y - (float) coords[1]);
            }
        }
    }

    /**
     * Apply transform to geometry. for a specific time of appearance
     *
     * @param tr             the transformation
     * @param timeAppearance the time of appearance
     */
    public void applyTransformToGeometry(ItkTransform tr, int timeAppearance) {
        Root r;
        Node n;
        double[] coords;
        for (Root root : rootList) {
            r = root;
            n = r.firstNode;
            coords = tr.transformPoint(new double[]{n.x, n.y, 0});
            n.x += (n.birthTime == timeAppearance ? (n.x - (float) coords[0]) : 0);
            n.y += (n.birthTime == timeAppearance ? (n.y - (float) coords[1]) : 0);
            while (n.child != null) {
                n = n.child;
                coords = tr.transformPoint(new double[]{n.x, n.y, 0});
                n.x += (n.birthTime == timeAppearance ? (n.x - (float) coords[0]) : 0);
                n.y += (n.birthTime == timeAppearance ? (n.y - (float) coords[1]) : 0);
            }
        }
        System.out.println("Transformation applied");
    }

    /**
     * Creates the gray scale image with time.
     *
     * @param imgInitSize      the img init size
     * @param SIZE_FACTOR      the size factor
     * @param binaryColor      the binary color
     * @param observationTime  the observation time
     * @param dotLineForHidden the dot line for hidden
     * @param symbolOptions    the symbol options
     * @param lineWidths       the line widths
     * @return the image plus
     */
    public ImagePlus createGrayScaleImageWithTime(ImagePlus imgInitSize, int SIZE_FACTOR, boolean binaryColor, double observationTime, boolean dotLineForHidden, boolean[] symbolOptions, double[] lineWidths) {
        int[] initDims = new int[]{imgInitSize.getWidth(), imgInitSize.getHeight()};
        return createGrayScaleImageWithTime(initDims, SIZE_FACTOR, binaryColor, observationTime, dotLineForHidden, symbolOptions, lineWidths, false);
    }

    public ImagePlus createGrayScaleImageWithHours(ImagePlus imgInitSize, int SIZE_FACTOR, boolean binaryColor, double observationTime, boolean dotLineForHidden, boolean[] symbolOptions, double[] lineWidths) {
        int[] initDims = new int[]{imgInitSize.getWidth(), imgInitSize.getHeight()};
        return createGrayScaleImageWithTime(initDims, SIZE_FACTOR, binaryColor, observationTime, dotLineForHidden, symbolOptions, lineWidths, true);
    }

    /**
     * Creates the gray scale image with time.
     *
     * @param initDims         the init dims
     * @param SIZE_FACTOR      the size factor
     * @param binaryColor      the binary color
     * @param observationTime  the observation time
     * @param dotLineForHidden the dot line for hidden
     * @param symbolOptions    the symbol options
     * @param lineWidths       the line widths
     * @return the image plus
     */
    public ImagePlus createGrayScaleImageWithTime(int[] initDims, int SIZE_FACTOR, boolean binaryColor, double observationTime, boolean dotLineForHidden, boolean[] symbolOptions, double[] lineWidths, boolean countInHours) {
        boolean showSymbols = symbolOptions[0];
        boolean distinguishStartSymbol = symbolOptions[1];
        boolean distinguishDateStartSymbol = symbolOptions[3];
        boolean showIntermediateSymbol = symbolOptions[4];
        if (observationTime < 0) observationTime = 1E8;//Full root system
        int w = initDims[0] * SIZE_FACTOR;
        int h = initDims[1] * SIZE_FACTOR;
        double maxDate = 0;
        ImagePlus imgRSML = new ImagePlus("", new ByteProcessor(w, h));
        ImageProcessor ip = imgRSML.getProcessor();

        //draw lines and dot lines

        /////////////////// draw lines
        for (Root r : rootList) {
            Node n = r.firstNode;
            Node n1;
            int color = (r.order == 1 ? 127 : 255);
            double rMaxDate = r.getDateMax();
            if (maxDate < rMaxDate) maxDate = rMaxDate;
            boolean timeOver = false;
            while (n.child != null && (!timeOver) && (((countInHours ? n.birthTimeHours : n.child.birthTime) <= observationTime))) {//TODO
                n1 = n;
                n = n.child;
                int dotEvery = 0;
                double width = 0;
                if (lineWidths[r.order - 1] <= 1) {
                    dotEvery = n1.hiddenWayToChild ? 2 : 0;
                    width = 1;
                } else {
                    dotEvery = n1.hiddenWayToChild ? 2 : 0;
                    width = n1.hiddenWayToChild ? 1 : lineWidths[r.order - 1];
                }
                if ((countInHours ? n.birthTimeHours : n.birthTime) > observationTime) {
                    double ratio = (observationTime - (countInHours ? n1.birthTimeHours : n1.birthTime)) / ((countInHours ? n.birthTimeHours : n.birthTime) - (countInHours ? n1.birthTimeHours : n1.birthTime));
                    double partialX = (n.x - n1.x) * ratio + n1.x;
                    double partialY = (n.y - n1.y) * ratio + n1.y;
                    drawDotline(ip, ImagePlus.GRAY8, (int) ((n1.x + 0.5) * SIZE_FACTOR), (int) ((n1.y + 0.5) * SIZE_FACTOR), (int) ((partialX + 0.5) * SIZE_FACTOR), (int) ((partialY + 0.5) * SIZE_FACTOR), dotEvery, width, binaryColor ? color : (countInHours ? n.birthTimeHours : n.birthTime));
                    timeOver = true;
                } else {
                    drawDotline(ip, ImagePlus.GRAY8, (int) ((n1.x + 0.5) * SIZE_FACTOR), (int) ((n1.y + 0.5) * SIZE_FACTOR), (int) ((n.x + 0.5) * SIZE_FACTOR), (int) ((n.y + 0.5) * SIZE_FACTOR), dotEvery, width, binaryColor ? color : (countInHours ? n.birthTimeHours : n.birthTime));
                }
            }
        }

        if (dotLineForHidden) {
            for (Root r : rootList) {
                if (r.order < 2) continue;
                Node n = r.firstNode;
                Node nPar = r.parentNode;
                if (nPar == null) continue;
                int dotEvery = 2;
                double width = 2;
                if (!((countInHours ? n.birthTimeHours : n.birthTime) > observationTime)) {
                    drawDotline(ip, ImagePlus.GRAY8, (int) ((nPar.x + 0.5) * SIZE_FACTOR), (int) ((nPar.y + 0.5) * SIZE_FACTOR), (int) ((n.x + 0.5) * SIZE_FACTOR), (int) ((n.y + 0.5) * SIZE_FACTOR), dotEvery, width, binaryColor ? 1 : (countInHours ? n.birthTimeHours : n.birthTime));
                }
            }
        }

        int sum = 0;
        // draw symbols
        for (Root root : rootList) {
            if (!showSymbols) continue;
            Node n = root.firstNode;
            if ((countInHours ? n.birthTimeHours : n.birthTime) > observationTime) continue;//TODO
            Node n1;
            double wid = lineWidths[root.order - 1];
            if (distinguishStartSymbol) {
                //draw starting point as start symbol
                ip.setColor(Color.white);
                ip.drawRect((int) ((n.x + 0.5) * SIZE_FACTOR - 2), (int) ((n.y + 0.5) * SIZE_FACTOR) - 2, 5, 5);
                ip.setColor(Color.black);
                ip.drawRect((int) ((n.x + 0.5) * SIZE_FACTOR - 1), (int) ((n.y + 0.5) * SIZE_FACTOR) - 1, 3, 3);
            } else if (distinguishDateStartSymbol) {
                //draw starting point as date start
                double[] vectOrient = TransformUtils.normalize(new double[]{n.vx, n.vy});
                if (Math.abs(vectOrient[0]) > Math.abs(vectOrient[1]))
                    vectOrient = TransformUtils.multiplyVector(vectOrient, 1 / Math.abs(vectOrient[0]));
                else vectOrient = TransformUtils.multiplyVector(vectOrient, 1 / Math.abs(vectOrient[1]));
                ip.setColor(Color.white);
                ip.setLineWidth((int) wid);
                ip.drawLine((int) (n.x - vectOrient[0] * (wid)), (int) (n.y - vectOrient[1] * (wid)), (int) (n.x + vectOrient[0] * (wid)), (int) (n.y + vectOrient[1] * (wid)));
            } else {
                //draw starting point as any symbol
                ip.setColor(Color.white);
                ip.drawRect((int) ((n.x + 0.5) * SIZE_FACTOR - 1), (int) ((n.y + 0.5) * SIZE_FACTOR) - 1, 3, 3);
                if ((countInHours ? n.birthTimeHours : n.birthTime) >= maxDate)
                    maxDate = (countInHours ? n.birthTimeHours : n.birthTime);
            }
            boolean timeOver = false;
            while (n.child != null && (!timeOver) && ((countInHours ? n.birthTimeHours : n.birthTime) < observationTime)) {
                n1 = n;
                n = n.child;
                if (n.child == null && distinguishDateStartSymbol) continue;
                if ((countInHours ? n.birthTimeHours : n.birthTime) > observationTime) continue;
                double delta = Math.abs((countInHours ? n.birthTimeHours : n.birthTime) - Math.round((countInHours ? n.birthTimeHours : n.birthTime)));
                if (!showIntermediateSymbol && delta > 0.001) continue;
                if (delta <= 0.001 && distinguishDateStartSymbol) {
                    //draw date start symbol
                    double[] vectOrient = TransformUtils.normalize(new double[]{-n.vy, n.vx});
                    if (Math.abs(vectOrient[0]) > Math.abs(vectOrient[1]))
                        vectOrient = TransformUtils.multiplyVector(vectOrient, 1 / Math.abs(vectOrient[0]));
                    else vectOrient = TransformUtils.multiplyVector(vectOrient, 1 / Math.abs(vectOrient[1]));
                    ip.setColor(Color.white);
                    ip.setLineWidth((int) wid);
                    ip.drawLine((int) (n.x - vectOrient[0] * (wid)), (int) (n.y - vectOrient[1] * (wid)), (int) (n.x + vectOrient[0] * (wid)), (int) (n.y + vectOrient[1] * (wid)));
                } else {
                    //draw intermediary point
                    int xCenter = (int) ((n.x + 0.5) * SIZE_FACTOR - 1);
                    int yCenter = (int) ((n.y + 0.5) * SIZE_FACTOR) - 1;
                    ip.setColor(Color.white);
                    ip.drawRect((int) ((n.x + 0.5) * SIZE_FACTOR - 1), (int) ((n.y + 0.5) * SIZE_FACTOR) - 1, 3, 3);
                    ip.setColor(Color.black);
                    ip.drawPixel(xCenter + 1, yCenter);
                    ip.drawPixel(xCenter, yCenter + 1);
                    ip.drawPixel(xCenter + 1, yCenter + 2);
                    ip.drawPixel(xCenter + 2, yCenter + 1);
                }
            }
            //draw end point
            if ((countInHours ? n.birthTimeHours : n.birthTime) <= observationTime) {
                int xCenter = (int) ((n.x + 0.5) * SIZE_FACTOR - 1);
                int yCenter = (int) ((n.y + 0.5) * SIZE_FACTOR) - 1;
                ip.setColor(Color.white);
                ip.drawRect((int) ((n.x + 0.5) * SIZE_FACTOR - 1), (int) ((n.y + 0.5) * SIZE_FACTOR) - 1, 3, 3);
                ip.setColor(Color.white);
                ip.drawPixel(xCenter, yCenter);
                ip.drawPixel(xCenter + 2, yCenter);
                ip.drawPixel(xCenter + 2, yCenter + 2);
                ip.drawPixel(xCenter, yCenter + 2);
                if (root.order <= 1) sum++;
                if (root.order <= 1) {
                    ip.setColor(Color.white);
                    ip.drawPixel(xCenter - 1, yCenter - 1);
                    ip.drawPixel(xCenter + 3, yCenter - 1);
                    ip.drawPixel(xCenter + 3, yCenter + 3);
                    ip.drawPixel(xCenter - 1, yCenter + 3);
                    ip.drawPixel(xCenter - 2, yCenter - 2);
                    ip.drawPixel(xCenter + 4, yCenter - 2);
                    ip.drawPixel(xCenter + 4, yCenter + 4);
                    ip.drawPixel(xCenter - 2, yCenter + 4);
                }
            }
        }

        // draw line between primary and secondary roots
        // This lines connects the closest point of the first node of the secondary root (member of a primary root) to the node in question
        // discontinued dot line


        if (binaryColor) IJ.run(imgRSML, "Red/Green", "");
        else IJ.run(imgRSML, "Fire", "");
        imgRSML.setDisplayRange(0, maxDate + 2);
        return imgRSML;
    }

    /**
     * Creates the gray scale image time lapse.
     *
     * @param imgInitSize      the img init size
     * @param observationTimes the observation times
     * @param lineWidths       the line widths
     * @param deltaModel       the delta model
     * @return the image plus
     */
    public ImagePlus createGrayScaleImageTimeLapse(ImagePlus imgInitSize, double[] observationTimes, double[] lineWidths, double deltaModel) {//TODO : pass into hours
        int[] initDims = new int[]{imgInitSize.getWidth(), imgInitSize.getHeight()};
        int Z = observationTimes.length;
        ImagePlus imgRSML = IJ.createImage("", initDims[0], initDims[1], Z, 8);
        int w = initDims[0];
        int h = initDims[1];
        double maxDate = 0;
        ImageProcessor[] ips = new ImageProcessor[Z];
        for (int z = 0; z < Z; z++) ips[z] = imgRSML.getStack().getProcessor(z + 1);

        //Draw lines and dots
        for (Root r : rootList) {
            Node n = r.firstNode;
            Node n1;
            int lwid = (int) lineWidths[r.order - 1];
            int color = (r.order == 1 ? 1 : r.order == 2 ? 2 : 3);
            int dotEvery = lwid * 2;
            while (n.child != null) {
                n1 = n;
                n = n.child;
                //There is a central line element between n1 and n, that need to be drawn with color and lwid

                //Identify the first index t where line appear in dot
                int indFirstDot = 0;
                for (int indt = 0; indt < Z; indt++)
                    if (n1.birthTimeHours >= (observationTimes[indt] - deltaModel)) indFirstDot = indt;
                indFirstDot++;

                //Identify the first index t where line appear in plain
                int indFirstPlain = indFirstDot;
                for (int indt = indFirstDot; indt < Z; indt++)
                    if (n.birthTimeHours > (observationTimes[indt] - deltaModel)) indFirstPlain = indt;
                indFirstPlain++;
                if (indFirstPlain > Z) indFirstPlain = Z;

                //Draw differently these cases
                for (int indt = indFirstDot; indt < indFirstPlain; indt++) {
                    //In that case, Identify the part to draw and draw it in dot
                    double deltaT = n.birthTimeHours - n1.birthTimeHours;
                    if (deltaT < VitimageUtils.EPSILON) deltaT = 1;
                    double deltaTrelative = (observationTimes[indt] - n1.birthTimeHours) / deltaT;
                    double deltaX = n.x - n1.x;
                    double deltaY = n.y - n1.y;
                    double interX = n1.x + deltaTrelative * deltaX;
                    double interY = n1.y + deltaTrelative * deltaY;
                    ips[indt].setColor(color);
                    ips[indt].setLineWidth(lwid);
                    drawDotline(ips[indt], ImagePlus.GRAY8, (int) n1.x, (int) n1.y, (int) interX, (int) interY, dotEvery, lwid, color);
                    //then draw the parent node
                    double delta = Math.abs(n1.birthTime - Math.round(n1.birthTimeHours));
                    if (true) {
                        //draw date start symbol
                    } else {
                        //draw intermediary point
                        int xCenter = (int) ((n.x + 0.5) - 1);
                        int yCenter = (int) ((n.y + 0.5) - 1);
                        ips[indt].setColor(3);
                        ips[indt].drawRect((int) ((n.x + 0.5) - 1), (int) ((n.y + 0.5)) - 1, 3, 3);
                        ips[indt].setColor(0);
                        ips[indt].drawPixel(xCenter + 1, yCenter);
                        ips[indt].drawPixel(xCenter, yCenter + 1);
                        ips[indt].drawPixel(xCenter + 1, yCenter + 2);
                        ips[indt].drawPixel(xCenter + 2, yCenter + 1);
                    }
                }

                for (int indt = indFirstPlain; indt < Z; indt++) {
                    //In the other case, draw the line in plain
                    ips[indt].setColor(color);
                    ips[indt].setLineWidth(lwid);
                    //ips[indt].drawLine((int) n1.x,(int)n1.y,(int)n.x,(int)n.y);
                    drawDotline(ips[indt], ImagePlus.GRAY8, (int) n1.x, (int) n1.y, (int) n.x, (int) n.y, dotEvery, lwid, color);
                    //then draw the parent node
                    double delta = Math.abs(n1.birthTimeHours - Math.round(n1.birthTimeHours));
                    if (true) {
                        //draw date start symbol
                    } else {
                        //draw intermediary point
                        int xCenter = (int) ((n.x + 0.5) - 1);
                        int yCenter = (int) ((n.y + 0.5) - 1);
                        ips[indt].setColor(3);
                        ips[indt].drawRect((int) ((n.x + 0.5) - 1), (int) ((n.y + 0.5)) - 1, 3, 3);
                        ips[indt].setColor(0);
                        ips[indt].drawPixel(xCenter + 1, yCenter);
                        ips[indt].drawPixel(xCenter, yCenter + 1);
                        ips[indt].drawPixel(xCenter + 1, yCenter + 2);
                        ips[indt].drawPixel(xCenter + 2, yCenter + 1);
                    }
                }
            }
        }

        imgRSML.setDisplayRange(0, 3);
        return imgRSML;
    }

    public ImagePlus createGrayScaleImages(ImagePlus imgRef, double sigmaSmooth, boolean convexHull, boolean skeleton, int width) {
        int w = imgRef.getWidth();
        int h = imgRef.getHeight();
        int t = imgRef.getStackSize();
        ImageStack stack = new ImageStack(); // + t
        for (int i = 1; i <= t; i++) {
            ImagePlus imgRSML = new ImagePlus("", this.createImage(false, width, false, w, h, convexHull, i));
            //imgRSML.show();
            imgRSML = VitimageUtils.gaussianFiltering(imgRSML, sigmaSmooth, sigmaSmooth, sigmaSmooth);
            if (skeleton) {
                ImageProcessor ip = imgRSML.getProcessor();
                for (Root r : rootList) {
                    Node n = r.firstNode;
                    Node n1;
                    while (n.child != null) {
                        n1 = n;
                        n = n.child;
                        if (n.birthTime > i) continue;
                        ip.setColor(Color.white);
                        ip.setLineWidth(width);
                        ip.drawLine((int) (n1.x), (int) (n1.y), (int) (n.x), (int) (n.y));
                    }
                    ip.setColor(Color.white);
                    ip.drawRect((int) (n.x - 1), (int) (n.y - 1), 3, 3);
                    double distLast = 0;
                    while (n.child != null) {
                        n1 = n;
                        n = n.child;
                        if (n.birthTime > i) continue;
                        distLast += distance(n, n1);
                        if (distLast > 5) {
                            ip.setColor(Color.black);
                            ip.drawRect((int) (n.x - 1), (int) (n.y - 1), 3, 3);
                            distLast = 0;
                        }
                    }
                }
            }
            stack.addSlice("", imgRSML.getProcessor());
        }
        return new ImagePlus("", stack);
    }

    /**
     * Creates the gray scale image.
     *
     * @param imgRef      the img ref
     * @param sigmaSmooth the sigma smooth
     * @param convexHull  the convex hull
     * @param skeleton    the skeleton
     * @param width       the width
     * @return the image plus
     */
    public ImagePlus createGrayScaleImage(ImagePlus imgRef, double sigmaSmooth, boolean convexHull, boolean skeleton, int width) {
        int w = imgRef.getWidth();
        int h = imgRef.getHeight();
        ImagePlus imgRSML = new ImagePlus("", this.createImage(false, width, false, w, h, convexHull));
        imgRSML = VitimageUtils.gaussianFiltering(imgRSML, sigmaSmooth, sigmaSmooth, sigmaSmooth);
        if (skeleton) {
            ImageProcessor ip = imgRSML.getProcessor();
            for (Root r : rootList) {
                System.out.println("Got root " + r);
                Node n = r.firstNode;
                Node n1;
                while (n.child != null) {
                    n1 = n;
                    n = n.child;
                    ip.setColor(Color.white);
                    ip.setLineWidth(width);
                    ip.drawLine((int) (n1.x), (int) (n1.y), (int) (n.x), (int) (n.y));
                }
            }
            for (Root r : rootList) {
                Node n = r.firstNode;
                Node n1;
                ip.setColor(Color.white);
                ip.drawRect((int) (n.x - 1), (int) (n.y - 1), 3, 3);
                double distLast = 0;
                while (n.child != null) {
                    n1 = n;
                    n = n.child;
                    distLast += distance(n, n1);
                    if (distLast > 5) {
                        ip.setColor(Color.black);
                        ip.drawRect((int) (n.x - 1), (int) (n.y - 1), 3, 3);
                        distLast = 0;
                    }
                }
            }
        }
        return imgRSML;
    }

    /**
     * Create an image processor based on the roots contained into the root system.
     *
     * @param color      the color
     * @param line       the line
     * @param real       the real
     * @param w          the w
     * @param h          the h
     * @param convexhull the convexhull
     * @return the image processor
     */
    public ImageProcessor createImage(boolean color, int line, boolean real, int w, int h, boolean convexhull) {

        Root r;
        Node n, n1;
        ImagePlus tracing;


        if (color) tracing = IJ.createImage("tracing", "RGBblack", w, h, 1);
        else tracing = IJ.createImage("tracing", "8-bitblack", w, h, 1);


        ImageProcessor tracingP = tracing.getProcessor();

        //if(name == null) fit.checkImageProcessor();
        for (int i = 0; i < rootList.size(); i++) {
            r = rootList.get(i);
            n = r.firstNode;

            if (color) {
                switch (r.isChild()) {
                    case 0:
                        tracing.setColor(new Color(Math.round(255), 0, 0));
                        break;
                    case 1:
                        tracing.setColor(new Color(0, Math.round(255), 0));
                        break;
                    case 2:
                        tracing.setColor(new Color(0, 0, Math.round(255)));
                        break;
                }
            } else tracing.setColor(Color.white);

            while (n.child != null) {
                n1 = n;
                n = n.child;
                if (real) tracingP.setLineWidth((int) ((r.isChild() == 0) ? n.diameter : n.diameter - 1));
                else tracingP.setLineWidth((r.isChild() == 0) ? line : line - 1);
                tracingP.drawLine((int) (n1.x), (int) (n1.y), (int) (n.x), (int) (n.y));
            }
            tracing.setProcessor(tracingP);
            if (convexhull) {
                if (r.isChild() == 0) {
                    tracing.setColor(Color.yellow);
                    PolygonRoi ch = r.getConvexHull();
                    int[] xRoi = ch.getXCoordinates();
                    int[] yRoi = ch.getYCoordinates();
                    Rectangle rect = ch.getBounds();
                    for (int j = 1; j < xRoi.length; j++) {
                        tracingP.drawLine(xRoi[j - 1] + rect.x, yRoi[j - 1] + rect.y, xRoi[j] + rect.x, yRoi[j] + rect.y);
                    }
                    tracingP.drawLine(xRoi[xRoi.length - 1] + rect.x, yRoi[xRoi.length - 1] + rect.y, xRoi[0] + rect.x, yRoi[0] + rect.y);
                }
            }
            tracingP = tracing.getProcessor();
        }

        return tracingP;
    }

    public ImageProcessor createImage(boolean color, int line, boolean real, int w, int h, boolean convexhull, float time) {

        Root r;
        Node n, n1;
        ImagePlus tracing;


        if (color) tracing = IJ.createImage("tracing", "RGBblack", w, h, 1);
        else tracing = IJ.createImage("tracing", "8-bitblack", w, h, 1);


        ImageProcessor tracingP = tracing.getProcessor();

        //if(name == null) fit.checkImageProcessor();
        for (Root root : rootList) {
            r = root;
            n = r.firstNode;

            if (color) {
                switch (r.isChild()) {
                    case 0:
                        tracing.setColor(new Color(Math.round(255), 0, 0));
                        break;
                    case 1:
                        tracing.setColor(new Color(0, Math.round(255), 0));
                        break;
                    case 2:
                        tracing.setColor(new Color(0, 0, Math.round(255)));
                        break;
                }
            } else tracing.setColor(Color.white);

            while (n.child != null) {
                n1 = n;
                n = n.child;
                if (n.birthTime > time) continue;
                if (real) tracingP.setLineWidth((int) ((r.isChild() == 0) ? n.diameter : n.diameter - 1));
                else tracingP.setLineWidth((r.isChild() == 0) ? line : line - 1);
                tracingP.drawLine((int) (n1.x), (int) (n1.y), (int) (n.x), (int) (n.y));
            }
            tracing.setProcessor(tracingP);
            if (convexhull) {
                if (r.isChild() == 0) {
                    tracing.setColor(Color.yellow);
                    PolygonRoi ch = r.getConvexHull();
                    int[] xRoi = ch.getXCoordinates();
                    int[] yRoi = ch.getYCoordinates();
                    Rectangle rect = ch.getBounds();
                    for (int j = 1; j < xRoi.length; j++) {
                        tracingP.drawLine(xRoi[j - 1] + rect.x, yRoi[j - 1] + rect.y, xRoi[j] + rect.x, yRoi[j] + rect.y);
                    }
                    tracingP.drawLine(xRoi[xRoi.length - 1] + rect.x, yRoi[xRoi.length - 1] + rect.y, xRoi[0] + rect.x, yRoi[0] + rect.y);
                }
            }
            tracingP = tracing.getProcessor();
        }

        return tracingP;
    }


    /**
     * Create an image processor based on the roots contained into the root system.
     *
     * @param color      the color
     * @param line       the line
     * @param real       the real
     * @param w          the w
     * @param h          the h
     * @param convexhull the convexhull
     * @param ratioColor the ratio color
     * @return the image processor
     */
    public ImageProcessor createImage(boolean color, int line, boolean real, int w, int h, boolean convexhull, double ratioColor) {

        Root r;
        Node n, n1;
        ImagePlus tracing;


        if (color) tracing = IJ.createImage("tracing", "RGBblack", w, h, 1);
        else tracing = IJ.createImage("tracing", "8-bit", w, h, 1);


        ImageProcessor tracingP = tracing.getProcessor();

        //if(name == null) fit.checkImageProcessor();
        for (int i = 0; i < rootList.size(); i++) {
            r = rootList.get(i);
            n = r.firstNode;

            if (color) {
                switch (r.isChild()) {
                    case 0:
                        tracing.setColor(new Color((int) Math.round(255 * ratioColor), 0, 0));
                        break;
                    case 1:
                        tracing.setColor(new Color(0, (int) Math.round(255 * ratioColor), 0));
                        break;
                    case 2:
                        tracing.setColor(new Color(0, 0, (int) Math.round(255 * ratioColor)));
                        break;
                }
            } else tracing.setColor(Color.black);

            while (n.child != null) {
                n1 = n;
                n = n.child;
                if (real) tracingP.setLineWidth((int) n.diameter);
                else tracingP.setLineWidth(line);
                tracingP.drawLine((int) (n1.x), (int) (n1.y), (int) (n.x), (int) (n.y));
            }
            tracing.setProcessor(tracingP);
            if (convexhull) {
                if (r.isChild() == 0) {
                    tracing.setColor(Color.yellow);
                    PolygonRoi ch = r.getConvexHull();
                    int[] xRoi = ch.getXCoordinates();
                    int[] yRoi = ch.getYCoordinates();
                    Rectangle rect = ch.getBounds();
                    for (int j = 1; j < xRoi.length; j++) {
                        tracingP.drawLine(xRoi[j - 1] + rect.x, yRoi[j - 1] + rect.y, xRoi[j] + rect.x, yRoi[j] + rect.y);
                    }
                    tracingP.drawLine(xRoi[xRoi.length - 1] + rect.x, yRoi[xRoi.length - 1] + rect.y, xRoi[0] + rect.x, yRoi[0] + rect.y);
                }
            }
            tracingP = tracing.getProcessor();
        }

        return tracingP;
    }


    /**
     * Create an image processor based on the roots contained into the root system.
     *
     * @param color      the color
     * @param line       the line
     * @param real       the real
     * @param convexhull the convexhull
     * @return the image processor
     */
    public ImageProcessor createImage(boolean color, int line, boolean real, boolean convexhull) {

        Root r;
        Node n, n1;
        ImagePlus tracing;

        int w = getWidth(false);
        int h = getHeight(true);

        if (color) tracing = IJ.createImage("tracing", "RGB", w + 100, h + 100, 1);
        else tracing = IJ.createImage("tracing", "8-bit", w + 100, h + 100, 1);


        ImageProcessor tracingP = tracing.getProcessor();

        //if(name == null) fit.checkImageProcessor();
        for (int i = 0; i < rootList.size(); i++) {
            r = rootList.get(i);
            n = r.firstNode;

            if (color) {
                switch (r.isChild()) {
                    case 0:
                        tracing.setColor(Color.red);
                        break;
                    case 1:
                        tracing.setColor(Color.green);
                        break;
                    case 2:
                        tracing.setColor(Color.blue);
                        break;
                }
            } else tracing.setColor(Color.black);

            while (n.child != null) {
                n1 = n;
                n = n.child;
                if (real) tracingP.setLineWidth((int) n.diameter);
                else tracingP.setLineWidth(line);
                tracingP.drawLine((int) (n1.x - getMinX() + 50), (int) (n1.y - getMinY() + 50), (int) (n.x - getMinX() + 50), (int) (n.y - getMinY() + 50));
            }
            tracing.setProcessor(tracingP);
            if (convexhull) {
                if (r.isChild() == 0) {
                    tracing.setColor(Color.red);
                    PolygonRoi ch = r.getConvexHull();
                    int[] xRoi = ch.getXCoordinates();
                    int[] yRoi = ch.getYCoordinates();
                    Rectangle rect = ch.getBounds();
                    for (int j = 1; j < xRoi.length; j++) {
                        tracingP.drawLine(xRoi[j - 1] + rect.x, yRoi[j - 1] + rect.y, xRoi[j] + rect.x, yRoi[j] + rect.y);
                    }
                    tracingP.drawLine(xRoi[xRoi.length - 1] + rect.x, yRoi[xRoi.length - 1] + rect.y, xRoi[0] + rect.x, yRoi[0] + rect.y);
                }
            }
            tracingP = tracing.getProcessor();
        }

        return tracingP;
    }


    /**
     * Get the index of the po accession.
     *
     * @param po the po
     * @return the index from po
     */
    public int getIndexFromPo(String po) {
        for (int i = 0; i < FSR.listPo.length; i++) {
            if (po.equals(FSR.listPo[i])) return i;
        }
        return 0;
    }

    /**
     * Get the convexhull of all the roots in the image. Uses the native image functions
     *
     * @return the convex hull
     */
    public PolygonRoi getConvexHull() {

        List<Integer> xList = new ArrayList<Integer>();
        List<Integer> yList = new ArrayList<Integer>();

        // Add all the nodes coordinates
        for (Root r : rootList) {
            Node n = r.firstNode;
            while (n.child != null) {
                xList.add((int) n.x);
                yList.add((int) n.y);
                n = n.child;
            }
            xList.add((int) n.x);
            yList.add((int) n.y);
        }

        int[] xRoiNew = new int[xList.size()];
        int[] yRoiNew = new int[yList.size()];
        for (int l = 0; l < yList.size(); l++) {
            xRoiNew[l] = xList.get(l);
            yRoiNew[l] = yList.get(l);
        }

        Roi roi = new PolygonRoi(xRoiNew, yRoiNew, yRoiNew.length, Roi.POLYGON);
        return new PolygonRoi(roi.getConvexHull(), Roi.POLYGON);
    }

    /**
     * Distance.
     *
     * @param x0 the x 0
     * @param y0 the y 0
     * @param x1 the x 1
     * @param y1 the y 1
     * @return the double
     */
    public double distance(double x0, double y0, double x1, double y1) {
        return Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0));
    }

    //// Reader utilities
    public void initializeMetadata() {
        metadata = new Metadata();
    }

    //// Handling interface methods

    /**
     * Do not use, only for 2D models
     *
     * @param rootModel the root model
     * @param time      the time
     * @return the root model
     */
    @Override
    public IRootModelParser createRootModel(IRootModelParser rootModel, float time) {
        if (rootModel instanceof RootModel) {
            return rootModel;
        } else if (rootModel instanceof RootModel4Parser) {
            RootModel rm = new RootModel();
            nextAutoRootID = 0;
            String unit = ((RootModel4Parser) rootModel).getUnit();
            float res = ((RootModel4Parser) rootModel).getResolution();
            dpi = getDPI(unit, res);
            pixelSize = 2.54f / dpi;
            datafileKey = ((RootModel4Parser) rootModel).getFileKey();
            for (Scene scene : ((RootModel4Parser) rootModel).getScenes()) {
                for (Plant plant : scene.getPlants()) {
                    for (IRootParser root : plant.getFirstOrderRoots()) {
                        new Root(dpi, (Root4Parser) root, true, null, rm, ((RootModel4Parser) rootModel).getSoftware(), time);
                    }
                }
            }
            IJ.log(rootList.size() + " root(s) were created");
            setDPI(dpi);
            return rm;
        } else {
            return null;
        }
    }

    /**
     * Creates the root models.
     *
     * @param rootModels  the root models ordered by date
     * @param scaleFactor the scale factor (pph)
     * @return the root model
     */
    @Override
    public IRootModelParser createRootModels(Map<LocalDate, List<IRootModelParser>> rootModels, float scaleFactor) {
        RootModel rm = new RootModel();
        LocalDate firstDate = rootModels.keySet().iterator().next();
        RootModel4Parser firstRootModel = null;
        try {
            firstRootModel = (RootModel4Parser) rootModels.get(firstDate).get(0);
        } catch (IndexOutOfBoundsException e) {
            // find the first non empty root model
            for (LocalDate date : rootModels.keySet()) {
                if (!rootModels.get(date).isEmpty()) {
                    firstRootModel = (RootModel4Parser) rootModels.get(date).get(0);
                    break;
                }
            }
        }

        rm.imgName = Objects.requireNonNull(firstRootModel).getFileKey();
        String unit = firstRootModel.getUnit();
        float res = firstRootModel.getResolution();
        rm.dpi = getDPI(unit, res);
        rm.pixelSize = 2.54f / rm.dpi;

        List<LocalDate> dates = new ArrayList<>(rootModels.keySet());
        Collections.sort(dates);

        double[] hours = new double[dates.size()];
        hours[0] = 0;
        for (int i = 1; i < dates.size(); i++) {
            hours[i] = hours[i - 1] + ChronoUnit.HOURS.between(dates.get(i - 1).atStartOfDay(), dates.get(i).atStartOfDay());
        }
        rm.hoursCorrespondingToTimePoints = hours;

        Map<String, List<Root4Parser>> rootsMap = new HashMap<>();

        for (LocalDate date : dates) {
            for (IRootModelParser model : rootModels.get(date)) {
                parseRootModel((RootModel4Parser) model, rootsMap);
            }
        }

        for (int level = 1; level <= maxRootOrder; level++) {
            for (String id : rootsMap.keySet()) {
                if (rootsMap.get(id).get(0).getOrder() == level) {
                    buildRootHierarchy(rm, rootsMap, id, null, dates, scaleFactor);
                }
            }
        }

        rm.standardOrderingOfRoots();
        rm.cleanWildRsml();
        rm.resampleFlyingRoots();
        return rm;
    }

    /**
     * Parse root model. Adding keys to the roots map (id -> list of roots)
     * We would end up with (id -> time segmentation of the roots)
     *
     * @param rootModel the root model
     * @param rootsMap  the roots map
     */
    private void parseRootModel(RootModel4Parser rootModel, Map<String, List<Root4Parser>> rootsMap) {
        for (Scene scene : rootModel.getScenes()) {
            for (Plant plant : scene.getPlants()) {
                for (IRootParser root : plant.getFirstOrderRoots()) {
                    rootsMap.computeIfAbsent(root.getId(), k -> new ArrayList<>()).add((Root4Parser) root);
                    parseChildRoots((Root4Parser) root, rootsMap);
                    if (root.getOrder() > maxRootOrder) maxRootOrder = root.getOrder();
                }
            }
        }
    }

    /**
     * Parse child roots. Adding keys to the roots map (id -> list of roots)
     *
     * @param root     the root
     * @param rootsMap the roots map
     */
    private void parseChildRoots(Root4Parser root, Map<String, List<Root4Parser>> rootsMap) {
        for (IRootParser child : root.getChildren()) {
            rootsMap.computeIfAbsent(child.getId(), k -> new ArrayList<>()).add((Root4Parser) child);
            parseChildRoots((Root4Parser) child, rootsMap);
            if (child.getOrder() > maxRootOrder) maxRootOrder = child.getOrder();
        }
    }

    /**
     * Build root hierarchy. Recursive method to build the root hierarchy
     *
     * @param rm          the root model
     * @param rootsMap    the roots map
     * @param id          the id
     * @param parent      the parent
     * @param dates       the dates
     * @param scaleFactor the scale factor
     */
    private void buildRootHierarchy(RootModel rm, Map<String, List<Root4Parser>> rootsMap, String id, Root parent, List<LocalDate> dates, float scaleFactor) {
        List<Root4Parser> rootList = rootsMap.get(id);
        Root root = null;
        root = createRoot(rm, id, rootList, dates, scaleFactor);
        // It is a lateral root - known from the RootModel (not parser
        if (parent != null) {
            parent.attachChild(root);
            root.attachParent(parent);//a15e4ab4-2c61-4fbd-a134-e47167099014
            root.firstNode.parent = (Node) rm.getClosesNodeInCurrentRoot(new Point3d(root.firstNode.x, root.firstNode.y, root.firstNode.birthTime), parent)[0];
        }
        else if (!rm.rootList.isEmpty()) {
            Root checkRoot = looking4Root(rm.rootList, root.getId());
            rm.rootList.add(checkRoot);
            if (checkRoot == null) {
                if (rootList.get(0).getOrder() == 1) rm.rootList.add(root);
                else System.err.println("Problem with Root : " + root);
            }
        }
        else rm.rootList.add(root);

        for (IRootParser child : getTotalChildrenList(rootList)) {
            buildRootHierarchy(rm, rootsMap, child.getId(), root, dates, scaleFactor);
        }
    }

    /**
     * Looking for a certain root with a certain id for correction. Recursive method to find the root in the root list / child lists
     *
     * @param roots the roots
     * @param id    the id
     * @return the root
     */
    private Root looking4Root(List<Root> roots, String id) {
        for (Root r : roots) {
            if (r.rootID.equals(id)) return r;
        }
        for (Root r : roots) {
            Root found = looking4Root(r.childList, id);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Create root. Create a root from a list of root4parser
     *
     * @param rm          the root model
     * @param id          the id
     * @param rootList    the root list
     * @param dates       the dates
     * @param scaleFactor the scale factor
     * @return the root
     */
    private Root createRoot(RootModel rm, String id, List<Root4Parser> rootList, List<LocalDate> dates, float scaleFactor) {
        // Create the root object
        Root root = new Root(null, rm, "", rootList.get(0).getOrder());
        root.rootID = id;
        root.rootKey = rootList.get(0).getLabel();
        root.label = rootList.get(0).getLabel();

        // Useful variables
        boolean first = true; // for the first node
        int countNodes = 0; // count the number of nodes

        // Add all the nodes to the root
        for (Root4Parser root4Parser : rootList) {
            int indexPt = 0;
            for (Point2D points : root4Parser.getGeometry().get2Dpt()) {
                float diameter = getDiameter(root4Parser, indexPt);
                int indexTime = dates.indexOf(root4Parser.currentTime) + 1;
                root.addNode(points.getX() / scaleFactor, points.getY() / scaleFactor, indexTime, indexTime * 24, diameter / scaleFactor, 0, 0, first);
                indexPt++;
                if (first) first = false;
                countNodes++;
            }
            //first = true; //even though technically true, will be adjusted later in the code
        }
        root.lastNode = root.firstNode;
        root.computeDistances();

        // assert that the number of nodes of the newly created root is the same as the sum of the number of points of all the roots
        assert countNodes == root.nNodes : "The number of nodes of the newly created root is not the same as the sum of the number of points of all the roots";


        return root;
    }

    /**
     * Get the diameter of a root at a certain index
     *
     * @param root  the root
     * @param index the index
     * @return the diameter
     */
    private float getDiameter(Root4Parser root, int index) {
        for (Function f : root.getFunctions()) {
            if (f.getName().equals("diameter")) {
                return f.getSamples().get(index).floatValue();
            }
        }
        return 0;
    }

    /**
     * Finalisation de la creation du rootmodel avant blockmatching
     */
    public void adjustRootModel(ImagePlus refImage) {
        Map<Float, List<Node>> nodes = new HashMap<>();

        // Iterate through each root in the root list
        for (Root r : this.rootList) {
            Node firstNode = r.firstNode;
            // Add nodes to a point list for clustering
            while (firstNode != null) {
                nodes.computeIfAbsent(firstNode.birthTime, k -> new ArrayList<>()).add(firstNode);
                firstNode = firstNode.child;
            }

            // Find the time with the maximum number of points
            float timeWithMaxPoints = nodes.keySet().iterator().next();
            float minTime = Float.MAX_VALUE;
            for (Float time : nodes.keySet()) {
                if (nodes.get(time).size() > nodes.get(timeWithMaxPoints).size()) timeWithMaxPoints = time;
                minTime = Math.min(minTime, time);
            }


            // Call the new function to process nodes and calculate means
            calculateNodeMinImageDiff2(r, nodes, timeWithMaxPoints, minTime, refImage);
            //calculateNodeMinLength(r, nodes, timeWithMaxPoints, minTime);
            //calculateNodeMin(r, nodes, timeWithMaxPoints, minTime);
            //calculateNodeMeans(r, nodes, timeWithMaxPoints, minTime);
            //calculateNodeMax(r, nodes, timeWithMaxPoints, minTime);
            nodes.clear();
        }

        for (Root r : this.rootList) {
            if (r.order >= 2) { // TODO generalize
                Object[] result = getClosesNodeParentOrder(new Point3d(r.firstNode.x, r.firstNode.y, r.firstNode.birthTime), r);
                r.firstNode.parent = (Node) result[0];
                r.parentNode = (Node) result[0];
                r.parent = (Root) result[1];
                r.firstNode.parent.birthTime = Math.min(r.firstNode.birthTime, r.firstNode.parent.birthTime);
                r.firstNode.isInsertionPoint = true;
            }
        }

        // Update the root model
        this.resampleFlyingRoots();
        this.standardOrderingOfRoots();
        this.cleanWildRsml();

        // Free memory
        System.gc();
    }

    /**
     * Calculate the mean values for nodes and update the root model.
     */
    private void calculateNodeMeans(Root r, Map<Float, List<Node>> nodes, float timeWithMaxPoints, float minTime) {
        r.firstNode = nodes.get(timeWithMaxPoints).get(0);
        r.firstNode.parent.child = null;
        r.firstNode.parent = null;
        r.lastNode = nodes.get(timeWithMaxPoints).get(nodes.get(timeWithMaxPoints).size() - 1);
        r.lastNode.parent = null;
        r.lastNode.child = null;
        Node n = r.firstNode;
        for (int j = 0; j < nodes.get(nodes.keySet().stream().max(Float::compareTo).get()).size(); j++) {
            double meanX = 0;
            double meanY = 0;
            float minT = Float.MAX_VALUE;
            float maxT = Float.MIN_VALUE;
            int sizeNum = 0;
            for (float key : nodes.keySet()) {
                try {

                    meanX += nodes.get(key).get(j).x;
                    meanY += nodes.get(key).get(j).y;

                    // counting on the fact that there are some errors
                    minT = Math.min(key, minT);
                    maxT = Math.max(key, maxT);

                    sizeNum++;
                } catch (IndexOutOfBoundsException ignored) {
                }
            }
            meanX /= sizeNum;
            meanY /= sizeNum;

            if (minT == Float.MAX_VALUE) {
                System.out.println("Error: minT is still Float.MAX_VALUE");
            }
            n.x = (float) meanX;
            n.y = (float) meanY;
            n.birthTime = minT;
            n.birthTimeHours = minT * 24;

            this.hoursCorrespondingToTimePoints = new double[(int) (maxT - minT) + 1];
            for (int i = 0; i < this.hoursCorrespondingToTimePoints.length; i++) {
                this.hoursCorrespondingToTimePoints[i] = minTime + i;
            }
            n = n.child;
        }
    }

    /**
     * Calculate the mean values for nodes and update the root model.
     */
    private void calculateNodeMax(Root r, Map<Float, List<Node>> nodes, float timeWithMaxPoints, float minTime) {
        float lastTime = nodes.keySet().stream().max(Float::compareTo).get();
        r.firstNode = nodes.get(lastTime).get(0);
        if (r.firstNode.parent == null) {
            System.out.println("Error: parent child is null");
        }
        r.firstNode.parent.child = null;
        r.firstNode.parent = null;
        r.lastNode = nodes.get(timeWithMaxPoints).get(nodes.get(timeWithMaxPoints).size() - 1);
        r.lastNode.parent = null;
        r.lastNode.child = null;
        //r.nNodes =nodes.get(nodes.keySet().stream().max(Float::compareTo).get()).size() + 1;
        //for (int j = 0; j < nodes.get(nodes.keySet().stream().max(Float::compareTo).get()).size(); j++) {
        r.nNodes = nodes.get(timeWithMaxPoints).size();
        Node n = r.firstNode;
        for (int j = 0; j < nodes.get(timeWithMaxPoints).size(); j++) {
            double maxX = 0; // not max value of x but max time x value
            double maxY = 0;
            float minT = Float.MAX_VALUE;
            float maxT = Float.MIN_VALUE;
            for (float key : nodes.keySet()) {
                try {
                    double filter = nodes.get(key).get(j).x;
                    minT = Math.min(key, minT);
                    if (Math.max(key, maxT) > maxT) {
                        maxX = nodes.get(timeWithMaxPoints).get(j).x;
                        maxY = nodes.get(timeWithMaxPoints).get(j).y;
                    }
                    maxT = Math.max(key, maxT);
                } catch (IndexOutOfBoundsException ignored) {
                }
            }

            if (minT == Float.MAX_VALUE) {
                System.out.println("Error: minT is still Float.MAX_VALUE");
            }
            n.x = (float) maxX;
            n.y = (float) maxY;
            n.birthTime = minT;
            n.birthTimeHours = minT * 24;

            this.hoursCorrespondingToTimePoints = new double[(int) (maxT - minT) + 1];
            for (int i = 0; i < this.hoursCorrespondingToTimePoints.length; i++) {
                this.hoursCorrespondingToTimePoints[i] = minTime + i;
            }
            n = n.child;
        }
        // counting the number of nodes in the root
        int count = 0;
        Node n1 = r.firstNode;
        while (n1 != null) {
            count++;
            n1 = n1.child;
        }
        assert r.nNodes == count : "The number of nodes of the newly created root is not the same as the sum of the number of points of all the roots";
    }

    /**
     * Calculate the mean values for nodes and update the root model.
     */
    private void calculateNodeMin(Root r, Map<Float, List<Node>> nodes, float timeWithMaxPoints, float minTime) {
        float lastTime = nodes.keySet().stream().max(Float::compareTo).get();
        r.firstNode = nodes.get(lastTime).get(0);
        r.firstNode.parent.child = null;
        r.firstNode.parent = null;
        r.lastNode = nodes.get(timeWithMaxPoints).get(nodes.get(timeWithMaxPoints).size() - 1);
        r.lastNode.parent = null;
        r.lastNode.child = null;
        Node n = r.firstNode;
        for (int j = 0; j < nodes.get(timeWithMaxPoints).size(); j++) {
            double x = 0; // not max value of x but max time x value
            double y = 0;
            double diameter = 0;
            float minT = Float.MAX_VALUE;
            float maxT = Float.MIN_VALUE;
            for (float key : nodes.keySet()) {
                try {
                    double filter = nodes.get(key).get(j).x;
                    maxT = Math.max(key, maxT);

                    if (Math.min(key, minT) < minT) {
                        x = nodes.get(lastTime).get(j).x;
                        y = nodes.get(lastTime).get(j).y;
                        diameter = nodes.get(timeWithMaxPoints).get(j).diameter;
                    }
                    minT = Math.min(key, minT);
                } catch (IndexOutOfBoundsException ignored) {
                }
            }

            if (minT == Float.MAX_VALUE) {
                System.out.println("Error: minT is still Float.MAX_VALUE");
            }

            Objects.requireNonNull(n).x = (float) x;
            n.y = (float) y;
            n.diameter = (float) diameter;
            n.birthTime = minT;
            n.birthTimeHours = minT * 24;

            this.hoursCorrespondingToTimePoints = new double[(int) (maxT - minT) + 1];
            for (int i = 0; i < this.hoursCorrespondingToTimePoints.length; i++) {
                this.hoursCorrespondingToTimePoints[i] = minTime + i;
            }

            if (!r.firstNode.equals(n) && n.parent.birthTime != n.birthTime) {
                n.vx = n.x - n.parent.x;
                n.vy = n.y - n.parent.y;
            }

            if (n.child != null) n.child.parent = n;
            n = n.child;
        }

        // counting the number of nodes in the root
        int count = 0;
        Node n1 = r.firstNode;
        while (n1 != null) {
            count++;
            n1 = n1.child;
        }
        assert r.nNodes == count : "The number of nodes of the newly created root is not the same as the sum of the number of points of all the roots";
    }

    /**
     * Calculate the mean values for nodes and update the root model.
     */
    private void calculateNodeMinImageDiff(Root r, Map<Float, List<Node>> nodes, float timeWithMaxPoints, float minTime, ImagePlus imp) {
        float target = 0;
        float lastTime = nodes.keySet().stream().max(Float::compareTo).get();
        r.firstNode = nodes.get(timeWithMaxPoints).get(0);
        r.firstNode.x = nodes.get(lastTime).get(0).x;
        r.firstNode.y = nodes.get(lastTime).get(0).y;
        r.firstNode.birthTime = nodes.get(lastTime).get(0).birthTime;
        r.firstNode.birthTimeHours = nodes.get(lastTime).get(0).birthTimeHours;
        r.firstNode.parent.child = null;
        r.firstNode.parent = null;
        r.lastNode = nodes.get(timeWithMaxPoints).get(nodes.get(timeWithMaxPoints).size() - 1);
        r.lastNode.parent = null;
        r.lastNode.child = null;
        Node n = r.firstNode;
        for (int j = 0; j < nodes.get(timeWithMaxPoints).size(); j++) {
            double x = 0; // not max value of x but max time x value
            double y = 0;
            double diameter = 0;
            float minT = Float.MAX_VALUE;
            float maxT = Float.MIN_VALUE;
            float distance2target = Float.MAX_VALUE;
            float imedistance2target = Float.MAX_VALUE;
            for (float key : nodes.keySet()) {
                try {
                    double filter = nodes.get(key).get(j).x;
                    maxT = Math.max(key, maxT);

                    try {
                        x = nodes.get(lastTime - 1).get(j).x;
                        y = nodes.get(lastTime - 1).get(j).y;
                    } catch (Exception e) {
                        x = nodes.get(timeWithMaxPoints).get(j).x;
                        y = nodes.get(timeWithMaxPoints).get(j).y;
                    }
                    distance2target = Math.abs(imp.getPixel((int) nodes.get(key).get(j).x, (int) nodes.get(key).get(j).y)[0] - target);
                    imedistance2target = key;
                    target = imp.getPixel((int) nodes.get(key).get(j).x, (int) nodes.get(key).get(j).y)[0];

                    minT = Math.min(key, minT);
                    diameter = nodes.get(minT).get(j).diameter;
                } catch (IndexOutOfBoundsException ignored) {
                }
            }

            if (minT == Float.MAX_VALUE) {
                System.out.println("Error: minT is still Float.MAX_VALUE");
            }

            try {
                if (Math.abs(imp.getPixel((int) nodes.get(lastTime).get(j).x, (int) nodes.get(lastTime).get(j).y)[0] - target) < Math.abs(imp.getPixel((int) nodes.get(timeWithMaxPoints).get(j).x, (int) nodes.get(timeWithMaxPoints).get(j).y)[0] - target)) {
                    x = nodes.get(lastTime).get(j).x;
                    y = nodes.get(lastTime).get(j).y;
                }
                else {
                    x = nodes.get(timeWithMaxPoints).get(j).x;
                    y = nodes.get(timeWithMaxPoints).get(j).y;
                }
            } catch (Exception e) {
                x = nodes.get(timeWithMaxPoints).get(j).x;
                y = nodes.get(timeWithMaxPoints).get(j).y;
            }
            Objects.requireNonNull(n).x = (float) x;
            n.y = (float) y;
            n.diameter = (float) diameter;
            n.birthTime = minT;
            n.birthTimeHours = minT * 24;

            this.hoursCorrespondingToTimePoints = new double[(int) (maxT - minT) + 1];
            for (int i = 0; i < this.hoursCorrespondingToTimePoints.length; i++) {
                this.hoursCorrespondingToTimePoints[i] = minTime + i;
            }

            if (!r.firstNode.equals(n) && n.parent.birthTime != n.birthTime) {
                n.vx = n.x - n.parent.x;
                n.vy = n.y - n.parent.y;
            }

            if (n.child != null) n.child.parent = n;
            n = n.child;
        }

        // counting the number of nodes in the root
        int count = 0;
        Node n1 = r.firstNode;
        while (n1 != null) {
            count++;
            n1 = n1.child;
        }
        assert r.nNodes == count : "The number of nodes of the newly created root is not the same as the sum of the number of points of all the roots";
    }

    private void calculateNodeMinImageDiff2(Root r, Map<Float, List<Node>> nodes, float timeWithMaxPoints, float minTime, ImagePlus imp) {
        float lastTime = nodes.keySet().stream().max(Float::compareTo).get();

        float time = lastTime -1;
        try {
            nodes.get(time).get(0);
        }
        catch (NullPointerException e) {
            time = timeWithMaxPoints;
        }

        r.firstNode = nodes.get(Math.max(time, lastTime)).get(0);
        r.firstNode.x = nodes.get(time).get(0).x;
        r.firstNode.y = nodes.get(time).get(0).y;
        r.firstNode.birthTime = nodes.get(time).get(0).birthTime;
        r.firstNode.birthTimeHours = nodes.get(time).get(0).birthTimeHours;
        //r.firstNode.parent.child = null;
        //r.firstNode.parent = null;
        r.lastNode = nodes.get(Math.max(time, lastTime)).get(nodes.get(Math.max(time, lastTime)).size() - 1);
        r.lastNode.parent = null;

        r.lastNode.child = null;

        Node n = r.firstNode;
        for (int j = 0; j < nodes.get(Math.max(time, lastTime)).size(); j++) {
            double x = 0; // not max value of x but max time x value
            double y = 0;
            double diameter = 0;
            float minT = Float.MAX_VALUE;
            float maxT = Float.MIN_VALUE;
            for (float key : nodes.keySet()) {
                try {
                    double filter = nodes.get(key).get(j).x;
                    maxT = Math.max(key, maxT);
                    minT = Math.min(key, minT);
                    diameter = nodes.get(minT).get(j).diameter;
                } catch (IndexOutOfBoundsException ignored) {
                }
            }

            try {
                x = nodes.get(time).get(j).x;
                y = nodes.get(time).get(j).y;
            } catch (Exception e) {
                x = nodes.get(lastTime).get(j).x;
                y = nodes.get(lastTime).get(j).y;
            }
            Objects.requireNonNull(n).x = (float) x;
            n.y = (float) y;
            n.diameter = (float) diameter;
            n.birthTime = minT;
            n.birthTimeHours = minT * 24;

            this.hoursCorrespondingToTimePoints = new double[(int) (maxT - minT) + 1];
            for (int i = 0; i < this.hoursCorrespondingToTimePoints.length; i++) {
                this.hoursCorrespondingToTimePoints[i] = minTime + i;
            }

            if (!r.firstNode.equals(n) && n.parent.birthTime != n.birthTime) {
                n.vx = n.x - n.parent.x;
                n.vy = n.y - n.parent.y;
            }

            if (n.child != null) n.child.parent = n;
            n = n.child;
        }

        // counting the number of nodes in the root
        int count = 0;
        Node n1 = r.firstNode;
        while (n1 != null) {
            count++;
            n1 = n1.child;
        }
        assert r.nNodes == count : "The number of nodes of the newly created root is not the same as the sum of the number of points of all the roots";
    }

    /**
     * Calculate the mean values for nodes and update the root model.
     */
    private void calculateNodeMinLength(Root r, Map<Float, List<Node>> nodes, float timeWithMaxPoints, float minTime) {
        r.firstNode = nodes.get(timeWithMaxPoints).get(0);
        r.firstNode.x = nodes.get(timeWithMaxPoints).get(0).x;
        r.firstNode.y = nodes.get(timeWithMaxPoints).get(0).y;
        r.firstNode.birthTime = nodes.get(minTime).get(0).birthTime;
        r.firstNode.parent.child = null;
        r.firstNode.parent = null;
        r.lastNode = nodes.get(timeWithMaxPoints).get(nodes.get(timeWithMaxPoints).size() - 1);
        r.lastNode.parent = null;
        r.lastNode.child = null;
        Node n = r.firstNode;
        for (int j = 0; j < nodes.get(timeWithMaxPoints).size(); j++) {
            double x = 0; // not max value of x but max time x value
            double y = 0;
            double diameter = 0;
            float minLen = Float.MAX_VALUE;
            float minT = Float.MAX_VALUE;
            float maxT = Float.MIN_VALUE;
            for (float key : nodes.keySet()) {
                try {
                    double filter = nodes.get(key).get(j).x;

                    if (Node.distanceBetween((j == 0 ? r.firstNode : Objects.requireNonNull(n).parent), nodes.get(key).get(j)) < minLen) {
                        x = nodes.get(timeWithMaxPoints).get(j).x;
                        y = nodes.get(timeWithMaxPoints).get(j).y;
                        diameter = nodes.get(timeWithMaxPoints).get(j).diameter;
                    }
                    minLen = Math.min((Node.distanceBetween((j == 0 ? r.firstNode : Objects.requireNonNull(n).parent), nodes.get(key).get(j))), minLen);
                    minT = Math.min(key, minT);
                    maxT = Math.max(key, maxT);
                } catch (IndexOutOfBoundsException ignored) {
                }
            }

            if (minLen == Float.MAX_VALUE) {
                System.out.println("Error: minT is still Float.MAX_VALUE");
            }

            Objects.requireNonNull(n).x = (float) x;
            n.y = (float) y;
            n.diameter = (float) diameter;
            n.birthTime = minT;
            n.birthTimeHours = minT * 24;

            this.hoursCorrespondingToTimePoints = new double[(int) (maxT - minT) + 1];
            for (int i = 0; i < this.hoursCorrespondingToTimePoints.length; i++) {
                this.hoursCorrespondingToTimePoints[i] = minTime + i;
            }

            if (!r.firstNode.equals(n) && n.parent.birthTime != n.birthTime) {
                n.vx = n.x - n.parent.x;
                n.vy = n.y - n.parent.y;
            }

            if (n.child != null) n.child.parent = n;
            n = n.child;
        }

        // counting the number of nodes in the root
        int count = 0;
        Node n1 = r.firstNode;
        while (n1 != null) {
            count++;
            n1 = n1.child;
        }
        assert r.nNodes == count : "The number of nodes of the newly created root is not the same as the sum of the number of points of all the roots";
    }

    /**
     * Calculate the mean values for nodes and update the root model.
     */
    private void calculateNodeMaxLengthFromInsertion(Root r, Map<Float, List<Node>> nodes, float timeWithMaxPoints, float minTime) {
        r.firstNode = nodes.get(timeWithMaxPoints).get(0);
        r.firstNode.x = nodes.get(minTime).get(0).x;
        r.firstNode.y = nodes.get(minTime).get(0).y;
        r.firstNode.birthTime = nodes.get(minTime).get(0).birthTime;
        r.firstNode.parent.child = null;
        r.firstNode.parent = null;
        r.lastNode = nodes.get(timeWithMaxPoints).get(nodes.get(timeWithMaxPoints).size() - 1);
        r.lastNode.parent = null;
        r.lastNode.child = null;
        Node n = r.firstNode;
        for (int j = 0; j < nodes.get(timeWithMaxPoints).size(); j++) {
            double x = 0; // not max value of x but max time x value
            double y = 0;
            double diameter = 0;
            float minLen = Float.MAX_VALUE;
            float minT = Float.MAX_VALUE;
            float maxT = Float.MIN_VALUE;
            for (float key : nodes.keySet()) {
                try {
                    double filter = nodes.get(key).get(j).x;

                    if (Node.distanceBetween((j == 0 ? r.firstNode : Objects.requireNonNull(n).parent), nodes.get(key).get(j)) < minLen) {
                        x = nodes.get(timeWithMaxPoints).get(j).x;
                        y = nodes.get(timeWithMaxPoints).get(j).y;
                        diameter = nodes.get(timeWithMaxPoints).get(j).diameter;
                    }
                    minLen = Math.min((Node.distanceBetween((j == 0 ? r.firstNode : Objects.requireNonNull(n).parent), nodes.get(key).get(j))), minLen);
                    minT = Math.min(key, minT);
                    maxT = Math.max(key, maxT);
                } catch (IndexOutOfBoundsException ignored) {
                }
            }

            if (minLen == Float.MAX_VALUE) {
                System.out.println("Error: minT is still Float.MAX_VALUE");
            }

            Objects.requireNonNull(n).x = (float) x;
            n.y = (float) y;
            n.diameter = (float) diameter;
            n.birthTime = minT;
            n.birthTimeHours = minT * 24;

            this.hoursCorrespondingToTimePoints = new double[(int) (maxT - minT) + 1];
            for (int i = 0; i < this.hoursCorrespondingToTimePoints.length; i++) {
                this.hoursCorrespondingToTimePoints[i] = minTime + i;
            }

            if (!r.firstNode.equals(n) && n.parent.birthTime != n.birthTime) {
                n.vx = n.x - n.parent.x;
                n.vy = n.y - n.parent.y;
            }

            if (n.child != null) n.child.parent = n;
            n = n.child;
        }

        // counting the number of nodes in the root
        int count = 0;
        Node n1 = r.firstNode;
        while (n1 != null) {
            count++;
            n1 = n1.child;
        }
        assert r.nNodes == count : "The number of nodes of the newly created root is not the same as the sum of the number of points of all the roots";
    }

    /**
     * Calculate the mean values for nodes and update the root model.
     */
    private void calculateNodeKMeans(Root r, Map<Float, List<Node>> nodes, float timeWithMaxPoints, float minTime) {
        List<DoublePoint> points = new ArrayList<>();
        for (float time : nodes.keySet()) {
            for (Node node : nodes.get(time)) { // , node.birthTime
                points.add(new DoublePoint(new double[]{node.x, node.y, node.birthTime, nodes.get(time).indexOf(node)}));
            }
        }
        // (int) Math.floor((((float)points.size()) / nodes.keySet().size())) + 1
        Clusterer<DoublePoint> clusterer = new KMeansPlusPlusClusterer<>(nodes.get(timeWithMaxPoints).size(), 1000, new CustomDistance());
        List<? extends Cluster<DoublePoint>> clusters = clusterer.cluster(points);

        Map<DoublePoint, DoublePoint> minTime2Center = new HashMap<>();
        for (Cluster<DoublePoint> cluster : clusters) {
            // center is the mean of the cluster
            DoublePoint center = new DoublePoint(new int[]{0, 0, 0, 0});
            for (DoublePoint point : cluster.getPoints()) {
                center.getPoint()[0] += point.getPoint()[0];
                center.getPoint()[1] += point.getPoint()[1];
                center.getPoint()[2] += point.getPoint()[2];
                center.getPoint()[3] += point.getPoint()[3];
            }
            center.getPoint()[0] /= cluster.getPoints().size();
            center.getPoint()[1] /= cluster.getPoints().size();
            center.getPoint()[2] /= cluster.getPoints().size();
            center.getPoint()[3] /= cluster.getPoints().size();

            // associating the point with mintime to center
            DoublePoint mintime = cluster.getPoints().get(0);
            for (DoublePoint point : cluster.getPoints()) {
                if (point.getPoint()[2] < mintime.getPoint()[2]) mintime = point;
            }
            minTime2Center.put(mintime, center);
        }

        Node n = r.firstNode;
        n.parent = null;
        int size = minTime2Center.keySet().size();
        for (int i = 0; i < size; i++) {
            // get the list of nodes with the lowest index
            List<DoublePoint> nodesWithLowestIndex = new ArrayList<>();
            for (DoublePoint point : minTime2Center.keySet()) {
                if (point.getPoint()[2] == minTime2Center.keySet().stream().sorted(Comparator.comparingDouble(o -> o.getPoint()[2])).min(Comparator.comparingDouble(o -> o.getPoint()[2])).get().getPoint()[2]) {
                    nodesWithLowestIndex.add(point);
                }
            }

            // getting the lowest time -> lowest index -> further up position
            DoublePoint nodeWithLowestTime = nodesWithLowestIndex.get(0);
            for (DoublePoint point : nodesWithLowestIndex) {
                if (point.getPoint()[3] < nodeWithLowestTime.getPoint()[3]) nodeWithLowestTime = point;
                else if (point.getPoint()[3] == nodeWithLowestTime.getPoint()[3]) {
                    if (point.getPoint()[1] < nodeWithLowestTime.getPoint()[1]) nodeWithLowestTime = point;
                }
            }

            n.x = (float) minTime2Center.get(nodeWithLowestTime).getPoint()[0];
            n.y = (float) minTime2Center.get(nodeWithLowestTime).getPoint()[1];
            n.birthTime = (float) nodeWithLowestTime.getPoint()[2];
            n.birthTimeHours = (float) nodeWithLowestTime.getPoint()[2] * 24;
            n = n.child;
            nodesWithLowestIndex.clear();
            minTime2Center.remove(nodeWithLowestTime);
        }
        n = n.parent;
        Objects.requireNonNull(n).child = null;
        r.nNodes = size;
        r.lastNode = n;
        r.lastNode.child = null;

    }

    public Map<Root, List<Node>> getInsertionPoints() {
        if (this.insertionPointsMap == null) {
            this.insertionPointsMap = new HashMap<>();
            for (Root r : rootList) {
                this.insertionPointsMap.putIfAbsent(r, new ArrayList<>());
                Node n = r.firstNode;
                this.insertionPointsMap.get(r).add(n);
                n.isInsertionPoint = true;
                while (n.child != null) {
                    if (n.birthTime == n.child.birthTime - 1) {
                        this.insertionPointsMap.get(r).add(n.child);
                        n.child.isInsertionPoint = true;
                    }
                    n = n.child;
                }
            }
            return this.insertionPointsMap;
        }
        return this.insertionPointsMap;
    }

    @Override
    public int compareTo(RootModel o) {
        return 0; // TODO
    }
}

// new distance measure DistanceMeasure
class CustomDistance implements DistanceMeasure {
    @Override
    public double compute(double[] a, double[] b) {
        if ((a[3] == b[3])) return Math.sqrt(Math.pow(a[0] - b[0], 2) + Math.pow(a[1] - b[1], 2));
        else if (a[2] == b[2])
            return (Math.sqrt(Math.pow(a[0] - b[0], 2) + Math.pow(a[1] - b[1], 2)) * Math.pow(a[3] - b[3], 2));
        else
            return Math.sqrt(Math.pow(a[0] - b[0], 2) + Math.pow(a[1] - b[1], 2)) * Math.pow(a[3] - b[3], 2) * Math.pow(a[2] - b[2], 2);
    }
}

class DistanceBTWRootModels implements Comparator<RootModel> {
    @Override
    public int compare(RootModel o1, RootModel o2) {
        return 0;
    }
}