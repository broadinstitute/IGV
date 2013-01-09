/*
 * Copyright (c) 2007-2012 The Broad Institute, Inc.
 * SOFTWARE COPYRIGHT NOTICE
 * This software and its documentation are the copyright of the Broad Institute, Inc. All rights are reserved.
 *
 * This software is supplied without any warranty or guaranteed support whatsoever. The Broad Institute is not responsible for its use, misuse, or functionality.
 *
 * This software is licensed under the terms of the GNU Lesser General Public License (LGPL),
 * Version 2.1 which is available at http://www.opensource.org/licenses/lgpl-2.1.php.
 */

package org.broad.igv.util;

import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import org.broad.igv.Globals;
import org.broad.igv.PreferenceManager;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.tools.IgvTools;
import org.broad.igv.ui.IGV;
import org.broad.tribble.Feature;
import org.broad.tribble.readers.AsciiLineReader;
import org.broad.tribble.util.ftp.FTPClient;
import org.junit.Ignore;
import org.w3c.dom.Document;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.Assert.*;

/**
 * @author jrobinso
 * @date Jul 28, 2010
 */
@Ignore
public class TestUtils {
    public static final String DATA_DIR = "test/data/";
    public static final String TMP_OUTPUT_DIR = DATA_DIR + "out/";
    public static final String defaultGenome = DATA_DIR + "/genomes/hg18.unittest.genome";
    public static final String AVAILABLE_FTP_URL = "ftp://ftp.broadinstitute.org/pub/igv/TEST/test.txt";
    public static final String UNAVAILABLE_FTP_URL = "ftp://www.example.com/file.txt";
    //This is so ant can set the large data directory
    private static String LARGE_DATA_DIR_KEY = "LARGE_DATA_DIR";
    public static String LARGE_DATA_DIR = "test/largedata/";

    static {
        LARGE_DATA_DIR = System.getProperty(LARGE_DATA_DIR_KEY, LARGE_DATA_DIR);
    }

    public static void setUpTestEnvironment() throws IOException {
        Globals.setTesting(true);
        //Globals.setBatch(true);
        File prefsFile = new File("testprefs.properties");
        prefsFile.delete();
        prefsFile.deleteOnExit();
        PreferenceManager.getInstance().setPrefsFile(prefsFile.getAbsolutePath());
        Globals.READ_TIMEOUT = 60 * 1000;
        Globals.CONNECT_TIMEOUT = 60 * 1000;
        FTPClient.READ_TIMEOUT = 60 * 1000;

        //Create output directory if it doesn't exist
        File outDir = new File(DATA_DIR, "out");
        if (!outDir.exists()) {
            outDir.mkdir();
        }
        clearOutputDir();
    }

    /**
     * Loads the session into IGV. This blocks until the session
     * is loaded.
     *
     * @param igv
     * @param sessionPath
     * @throws InterruptedException
     */
    public static void loadSession(IGV igv, String sessionPath) throws InterruptedException {
        igv.doRestoreSession(sessionPath, null, false);
    }

    /**
     * See {@link #createIndex(String, int, int)}
     *
     * @param file
     * @throws IOException
     */
    public static void createIndex(String file) throws IOException {
        createIndex(file, IgvTools.LINEAR_INDEX, IgvTools.LINEAR_BIN_SIZE);
    }

    /**
     * Destroys index file if it exists, and creates new one under
     * the specified parameters
     *
     * @param file
     * @param indexType
     * @param binSize
     * @throws IOException
     */
    public static void createIndex(String file, int indexType, int binSize) throws IOException {
        String indexPath = (new IgvTools()).doIndex(file, null, indexType, binSize);
        File indexFile = new File(indexPath);
        indexFile.deleteOnExit();
    }

    /**
     * Load a test genome
     *
     * @return
     * @throws IOException
     */
    public static Genome loadGenome() throws IOException {
        final String genomeFile = defaultGenome;
        return IgvTools.loadGenome(genomeFile, true);
    }

    public static void clearOutputDir() throws IOException {
        File outputDir = new File(TMP_OUTPUT_DIR);
        if (outputDir.isDirectory()) {
            File[] listFiles = outputDir.listFiles();
            for (File fi : listFiles) {
                //Keep hidden files and directories
                if (!fi.getName().startsWith(".")) {
                    fi.delete();
                }
            }
        }
    }

    /**
     * Returns either 1 or 2, representing the number of
     * bytes used to end a line. Reads only from first line of a file
     *
     * @param filePath
     * @return
     * @throws IOException
     */
    public static int getBytesAtEnd(String filePath) throws IOException {
        InputStream is = new FileInputStream(filePath);
        AsciiLineReader reader = new AsciiLineReader(is);
        String line = reader.readLine();
        int bytesThisLine = (int) reader.getPosition();

        reader.close();

        return bytesThisLine - line.length();

    }

    /**
     * Check that the features are all the same. Checks size of list, chr, start, and end of
     * each feature
     *
     * @param expected
     * @param actIter
     * @throws AssertionError
     */
    public static void assertFeatureListsEqual(Iterator<? extends Feature> expected, Iterator<? extends Feature> actIter) throws AssertionError {
        while (expected.hasNext()) {
            Feature exp = expected.next();
            assertTrue(actIter.hasNext());
            Feature act = actIter.next();
            assertFeaturesEqual(exp, act);
        }
        assertFalse(actIter.hasNext());
    }


    public static void assertFeaturesEqual(Feature exp, Feature act) {
        assertEquals(exp.getChr(), act.getChr());
        assertEquals(exp.getStart(), act.getStart());
        assertEquals(exp.getEnd(), act.getEnd());
    }

    /*
     * The FEST library finds components by name
     * We set the name property to equal the variable name.
     * Intended for testing ONLY
     * @param parent Element in which to set child names. Each field of this Object which is a Component
     *               will have it's name set
     * @param recursive Whether to set names on grand-child components. Note that this is all-or-none,
     *                  if this is true it goes recursively all the way down. Recursion is breadth-first,
     *                  stops at each level when an object has only non-Component fields
     */
    public static void setAllNames(Object parent, boolean recursive) {
        Field[] fields = parent.getClass().getDeclaredFields();
        java.util.List<Component> childComponents = new ArrayList<Component>(fields.length);
        try {
            for (Field f : fields) {
                Component c = null;
                f.setAccessible(true);
                try {
                    c = (Component) f.get(parent);
                } catch (ClassCastException e) {
                    continue;
                }
                //Null valued fields don't throw a CCE
                //We don't overwrite names, this should also prevent
                //infinite recursion
                if (c == null || c.getName() != null) {
                    continue;
                }
                //At this point, we've established
                //that the field in question is a Component
                c.setName(f.getName());
                childComponents.add(c);

                f.setAccessible(false);
            }

            if (recursive) {
                for (Component c : childComponents) {
                    setAllNames(c, recursive);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static void checkDeadlockedThreads() throws IllegalThreadStateException {
        ThreadMXBean tmx = ManagementFactory.getThreadMXBean();
        long[] ids = tmx.findDeadlockedThreads();
        if (ids != null) {
            ThreadInfo[] infos = tmx.getThreadInfo(ids, true, true);
            String log = "The following threads are deadlocked:\n";
            for (ThreadInfo ti : infos) {
                System.out.println(ti);
                log += ti.toString() + "\n";
            }

            throw new IllegalThreadStateException(log);
        }
    }

    public static Timer startDeadlockChecker(final int period) {

        TimerTask checker = new TimerTask() {
            @Override
            public void run() {
                //System.out.println("deadlock checker thread:" + Thread.currentThread().getName());
                checkDeadlockedThreads();
            }
        };
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(checker, 0, period);
        return timer;
    }

    /**
     * Executes {@code predicate} on those objects supplied by {@code supplier},
     * timing each iteration. Goes {@code nTrials} times
     *
     * @param supplier
     * @param predicate
     * @param nTrials
     * @param <T>
     * @return The runtime, in nanoseconds, of each call of predicate with input of supplier.
     *         Array is sorted ascending
     */
    public static <T> long[] timeMethod(Supplier<T> supplier, Predicate<T> predicate, int nTrials) {
        long total = 0;
        long[] times = new long[nTrials];
        System.gc();

        for (int tri = 0; tri < nTrials; tri++) {

            T input = supplier.get();

            long startTime = System.nanoTime();

            predicate.apply(input);

            long endTime = System.nanoTime();
            long elapsed = endTime - startTime;
            times[tri] = elapsed;
            total += elapsed;
        }

        Arrays.sort(times);
        long minTime = times[0];
        long maxTime = times[nTrials - 1];
        long median = times[times.length / 2];
        double average = (total * 1.0 / nTrials);
        double stdev = -1;
        try {
            stdev(times, (long) average);
        } catch (ArithmeticException e) {
            //pass
        }

        System.out.println(String.format("Avg. time: %2.2e sec. Median: %2.2e sec", average * 1.0 / 1e9, median * 1.0 / 1e9));
        System.out.println(String.format("Best: %2.2e sec. Worst: %2.2e sec", minTime * 1.0 / 1e9, maxTime * 1.0 / 1e9));
        System.out.println(String.format("Standard deviation: %2.2e sec", stdev / 1e9));
        System.out.println(String.format("Total time: %2.2e sec", total * 1.0 / 1e9));

        return times;
    }

    public static double average(long[] vals) {
        long sum = 0;
        double n = (double) vals.length;
        for (Long i : vals)
            sum += ((double) i);
        return sum / n;
    }

    public static double stdev(long[] vals, long average) {
        long sum = 0;
        for (Long i : vals)
            sum += Math.pow((i - average), 2);
        return Math.sqrt(sum / (vals.length - 1)); // sample
    }

    private static long benchmarkTime = -1;

    public static long getBenchmarkTime() {
        if (benchmarkTime < 0) {
            //Generate some numbers to average
            Random r = new Random();
            int numNumbers = 1000000;
            long[] vals = new long[numNumbers];
            for (int rr = 0; rr < numNumbers; rr++) {
                vals[rr] = r.nextInt();
            }
            System.gc();

            long startTime = System.nanoTime();

            double avg = average(vals);

            long endTime = System.nanoTime();
            benchmarkTime = endTime - startTime;
            System.out.println("Benchmark Time (ns): " + benchmarkTime);
        }
        return benchmarkTime;
    }



    /**
     * Marshalls {@code inObj} and unmarshalls the result, returning the
     * unmarshalled version
     *
     * @param inObj
     * @return
     * @throws Exception
     */
    public static <T> T marshallUnmarshall(T inObj) throws Exception{

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        JAXBContext jc = JAXBContext.newInstance(inObj.getClass());
        Marshaller m = jc.createMarshaller();
        m.setProperty(Marshaller.JAXB_FRAGMENT, true);

        //This JAXBElement business is necessary because we don't know if we have @XmlRootElement on inObj
        JAXBElement inel = new JAXBElement(new QName("", "obj"), inObj.getClass(), inObj);
        //m.marshal(inel, System.out);
        m.marshal(inel, doc);

        Unmarshaller u = jc.createUnmarshaller();
        JAXBElement el = (JAXBElement) u.unmarshal(doc, inObj.getClass());
        return (T) el.getValue();
    }

    private static Object getField(Object object, Class clazz, String fieldName) throws Exception{
        if(clazz == null) throw new NoSuchFieldException(fieldName + " not found all the way up");
        Field field;
        try{
            field = object.getClass().getDeclaredField(fieldName);
        }catch (NoSuchFieldException e){
            return getField(object, clazz.getSuperclass(), fieldName);
        }
        field.setAccessible(true);
        return field.get(object);
    }

    /**
     * Get the specified field, ignoring access restrictions
     * @param object
     * @param fieldName
     * @return
     */
    public static Object getField(Object object, String fieldName) throws Exception{
        return getField(object, object.getClass(), fieldName);
    }

    private static Object runMethod(Object object, Class clazz, String methodName, Object... args) throws Exception{
        if(clazz == null) throw new NoSuchFieldException(methodName + " not found all the way up");
        Method method;
        try{
            method = object.getClass().getDeclaredMethod(methodName);
        }catch (NoSuchMethodException e){
            return runMethod(object, clazz.getSuperclass(), methodName, args);
        }
        method.setAccessible(true);
        return method.invoke(object, args);
    }

    public static Object runMethod(Object object, String methodName, Object... args) throws Exception{
        return runMethod(object, object.getClass(), methodName, args);
    }

  
}
